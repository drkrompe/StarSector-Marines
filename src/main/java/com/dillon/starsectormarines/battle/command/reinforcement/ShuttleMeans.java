package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.FactionUnitRoster;
import com.dillon.starsectormarines.battle.air.Shuttle;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

/**
 * Air-drop reinforcement means. Picks a viable LZ near the rally via
 * {@link LandingZoneScorer} (walkable, outside building footprints, with a
 * little clearance), mints a single-cycle {@link Shuttle} that flies in from
 * the side-appropriate
 * off-map edge, lands, deboards its capacity into a fresh defender squad,
 * and departs. Reuses the existing shuttle state machine in
 * {@code AirSystem} — this class only writes the spawn-time inputs the
 * sim already consumes for marine drops.
 *
 * <p>Priority slot is between {@link ConvoyMeans} (most readable, needs
 * road graph) and {@link WalkInMeans} (always-feasible floor). A defender
 * rally on a road-less map but near walkable ground gets a shuttle
 * instead of dropping straight to walk-in; a rally in a clogged interior
 * with no LZ within {@link #LZ_SCAN_RADIUS} cells of the rally yields to
 * walk-in.
 *
 * <p>Narrative read: shuttle reinforcement is an "elite strike team"
 * deploying via aircraft. Deboarded units inherit the {@code UnitType.MARINE}
 * stats baked into {@code AirSystem.tryDeboardMarine} — costlier delivery,
 * better troops than the {@link WalkInMeans} militia floor.
 */
public final class ShuttleMeans implements ReinforcementMeans {

    private static final Logger LOG = Global.getLogger(ShuttleMeans.class);

    /** Max search radius (Manhattan) from the rally when scoring an LZ. */
    private static final int LZ_SCAN_RADIUS = 8;

    /**
     * Minimum open-neighbour count an LZ must have. A shuttle is an aircraft —
     * it shouldn't set down in a one-cell pinch between buildings. Lenient (a
     * cell touching a wall still qualifies); the load-bearing constraint is the
     * scorer's walkable / no-building viability rule.
     */
    private static final int SHUTTLE_MIN_CLEARANCE = 2;

    /** Cells the off-map entry sits outside the grid. Mirrors {@code BattleSetup.SHUTTLE_OFFMAP_Y}; duplicated here so the means is self-contained and the existing constant stays {@code private}. */
    private static final float OFFMAP_PAD = 8f;

    /** Default shuttle for SMALL strength. Nimble, 4-capacity — single-squad reinforcement reads as quick-response delivery. */
    private static final ShuttleType DEFAULT_TYPE = ShuttleType.AEROSHUTTLE;

    private final TraversalAxis axis;

    public ShuttleMeans(TraversalAxis axis) {
        this.axis = axis;
    }

    @Override
    public boolean canFulfill(BattleSimulation sim, ReinforcementRequest req) {
        if (!req.hasRally()) return false;
        if (req.side != Faction.DEFENDER) return false;
        // Compound-as-supply gate: shuttle drops are sourced from the
        // defender COMMAND_POST — the strategic-control surface that
        // authorises an air-drop in the first place. Lose every command
        // post and the air arm has nothing to dispatch from.
        if (!sim.getCompoundService().hasAliveCompound(
                TacticalNode.Kind.COMMAND_POST, Faction.DEFENDER)) {
            return false;
        }
        return new LandingZoneScorer(sim.getGrid(), sim.getTopology())
                .bestNear(req.rallyX, req.rallyY, LZ_SCAN_RADIUS, SHUTTLE_MIN_CLEARANCE) != null;
    }

    @Override
    public void dispatch(BattleSimulation sim, ReinforcementRequest req) {
        NavigationGrid grid = sim.getGrid();
        int[] lz = new LandingZoneScorer(grid, sim.getTopology())
                .bestNear(req.rallyX, req.rallyY, LZ_SCAN_RADIUS, SHUTTLE_MIN_CLEARANCE);
        if (lz == null) {
            LOG.warn("ShuttleMeans: no viable LZ within " + LZ_SCAN_RADIUS
                    + " cells of rally=(" + req.rallyX + "," + req.rallyY + ")");
            return;
        }

        float lzX = lz[0] + 0.5f;
        float lzY = lz[1] + 0.5f;
        float[] entry = entryForSide(req.side, axis, lzX, lzY, grid.getWidth(), grid.getHeight());

        Shuttle shuttle = new Shuttle(
                DEFAULT_TYPE, req.side,
                lzX, lzY,
                entry[0], entry[1],
                entry[2], entry[3],
                /*pendingDelay*/ 0f);
        shuttle.totalCycles = 1;
        // Reinforcement shuttles deboard the faction's elite tier (the
        // narrative of "expensive air-drop = stiffening delivery"). Default
        // player shuttles leave deboardUnitType null and get the bulk
        // infantry slot — see roadmap/reinforcement/faction-roster.md.
        shuttle.deboardUnitType = FactionUnitRoster.forFaction(req.side).elite();
        // No marineLoadout / no turret kit — AirSystem deboards plain COMBATANT
        // units and the null assignedRole skips HOVER_STATION (shuttle drops,
        // unloads, and leaves immediately).
        sim.addShuttle(shuttle);
        LOG.info("ShuttleMeans: dispatched " + DEFAULT_TYPE + " side=" + req.side
                + " lz=(" + lz[0] + "," + lz[1] + ") entry=(" + entry[0] + "," + entry[1] + ")");
    }

    /**
     * Entry + exit world coords for a shuttle landing at {@code (lzX, lzY)}.
     * The entry comes from the side appropriate to the requesting faction —
     * defender from the "end" of the {@link TraversalAxis} (the rear),
     * marine from the "start" (the staging side). Mirrors
     * {@code BattleSetup.shuttleEntryFor} for the marine case and inverts
     * the axis edge for defender.
     *
     * @return {@code [entryX, entryY, exitX, exitY]}; exit sits 4 cells
     *         further off-map so the departing leg has a moment of climb.
     */
    private static float[] entryForSide(Faction side, TraversalAxis axis,
                                        float lzX, float lzY, int gridW, int gridH) {
        boolean defender = side == Faction.DEFENDER;
        if (axis == TraversalAxis.SOUTH_TO_NORTH) {
            if (defender) {
                return new float[]{
                        lzX, gridH + OFFMAP_PAD,
                        lzX, gridH + OFFMAP_PAD + 4f};
            }
            return new float[]{
                    lzX, -OFFMAP_PAD,
                    lzX, -OFFMAP_PAD - 4f};
        }
        if (axis == TraversalAxis.WEST_TO_EAST) {
            if (defender) {
                return new float[]{
                        gridW + OFFMAP_PAD, lzY,
                        gridW + OFFMAP_PAD + 4f, lzY};
            }
            return new float[]{
                    -OFFMAP_PAD, lzY,
                    -OFFMAP_PAD - 4f, lzY};
        }
        // Null-axis default — drop from above (high y). Stable, matches the
        // legacy fallback in BattleSetup.shuttleEntryFor.
        return new float[]{
                lzX, gridH + OFFMAP_PAD,
                lzX, gridH + OFFMAP_PAD + 4f};
    }
}

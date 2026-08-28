package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.FactionUnitRoster;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.setup.GroundRosterProfile;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Air-drop reinforcement means. Picks a viable LZ near the rally via
 * {@link LandingZoneScorer} (walkable, outside building footprints, with a
 * little clearance), spawns a single-cycle air craft that flies in from
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
 * <p>Narrative read: shuttle reinforcement is an elite strike team deploying
 * via aircraft. It explicitly selects the requesting faction's elite roster
 * tier, while {@link WalkInMeans} selects bulk infantry.
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
    private final GroundRosterProfile groundRoster;
    private final RiskLevel risk;
    /**
     * Where the defender considers it safe to put a delivery down, or null on a
     * battle with no such authority.
     *
     * <p>The same policy the convoy asks, and asked for the same reason: it is
     * the only thing on the field that knows where the hostile front is. A
     * request's rally is where force is <em>needed</em>, which during a losing
     * fight is exactly where the marines are — land on it and the sortie
     * deboards a squad into whoever just took the position. The policy answers
     * the separate question the reinforcement model already separates out, of
     * where a means may safely arrive.
     */
    private final DeliveryDeploymentPolicy deploymentPolicy;
    /**
     * Sim-seconds a loaded-on-the-ground sortie waits for its squad to march
     * out to the pad before going with whoever arrived.
     *
     * <p>Long enough to cross a ward on foot, short enough that a squad killed
     * on the way does not park an aircraft for the rest of the battle.
     */
    private static final float BOARDING_PATIENCE = 90f;

    /** Hardstands on the garrison's own airfield, in map order. Empty on a map with none. */
    private final List<LandingPad> airfield;
    /** Names the marines this means marches out to its pads. */
    private int nextEmbarkId;

    public ShuttleMeans(TraversalAxis axis) {
        this(axis, null, RiskLevel.LOW);
    }

    public ShuttleMeans(TraversalAxis axis, GroundRosterProfile groundRoster, RiskLevel risk) {
        this(axis, groundRoster, risk, null, List.of());
    }

    public ShuttleMeans(TraversalAxis axis, GroundRosterProfile groundRoster, RiskLevel risk,
                        DeliveryDeploymentPolicy deploymentPolicy, List<LandingPad> landingPads) {
        this.axis = axis;
        this.groundRoster = groundRoster;
        this.risk = risk != null ? risk : RiskLevel.LOW;
        this.deploymentPolicy = deploymentPolicy;
        List<LandingPad> field = new ArrayList<>();
        for (LandingPad pad : landingPads == null ? List.<LandingPad>of() : landingPads) {
            if (pad.purpose == LandingPad.Purpose.GARRISON_AIRFIELD) field.add(pad);
        }
        this.airfield = List.copyOf(field);
    }

    @Override
    public boolean canFulfill(BattleView sim, ReinforcementRequest req) {
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
        // And the field the aircraft actually fly from, on a map that has one.
        // A command post authorises a drop; an airfield is where the lift
        // lives, so taking the field ends air delivery whoever still holds the
        // headquarters. A map with no airfield keeps the command post as its
        // only gate, exactly as before — the field cannot be a requirement on
        // battles that were never given one.
        if (!airfield.isEmpty() && !sim.getCompoundService().hasAliveCompound(
                TacticalNode.Kind.AIRBASE, Faction.DEFENDER)) {
            return false;
        }
        int[] centre = deliveryCentre(req);
        return new LandingZoneScorer(sim.getGrid(), sim.getTopology())
                .bestNear(centre[0], centre[1], LZ_SCAN_RADIUS, SHUTTLE_MIN_CLEARANCE) != null;
    }

    @Override
    public ReinforcementDispatchResult dispatch(BattleControl sim,
                                                ReinforcementRequest req) {
        NavigationGrid grid = sim.getGrid();
        int[] centre = deliveryCentre(req);
        int[] lz = new LandingZoneScorer(grid, sim.getTopology())
                .bestNear(centre[0], centre[1], LZ_SCAN_RADIUS, SHUTTLE_MIN_CLEARANCE);
        if (lz == null) {
            LOG.warn("ShuttleMeans: no viable LZ within " + LZ_SCAN_RADIUS
                    + " cells of centre=(" + centre[0] + "," + centre[1] + ")"
                    + " rally=(" + req.rallyX + "," + req.rallyY + ")");
            return ReinforcementDispatchResult.REJECTED;
        }

        float lzX = lz[0] + 0.5f;
        float lzY = lz[1] + 0.5f;
        float[] entry = sortieFrom(req, lzX, lzY, grid);

        long shuttleId = sim.spawnShuttle(
                DEFAULT_TYPE, req.side,
                lzX, lzY,
                entry[0], entry[1],
                entry[2], entry[3],
                /*pendingDelay*/ 0f);
        ShuttleMission mission = sim.world().mission(shuttleId);
        mission.commandClaim = SquadCommandClaim.reinforcement(req.reason.name());
        mission.totalCycles = 1;
        boolean loadedOnTheGround = !airfield.isEmpty()
                && embarkOnPad(sim, req, mission);
        // Objective assignment (progressive-reinforcement slice 4): resolve the
        // request's objective to a tactical node now, at dispatch time, so the
        // deboarded squad is assigned the moment it lands rather than only once
        // it physically walks to the position — see ObjectiveNodes.
        mission.assignNode = ObjectiveNodes.resolve(sim.getTacticalMap(), req);
        // Reinforcement shuttles deboard the faction's elite tier (the
        // narrative of "expensive air-drop = stiffening delivery"). Default
        // player shuttles leave deboardUnitType null and get the bulk
        // infantry slot — see reinforcement-nouns.md.
        GroundRosterProfile effectiveRoster = groundRoster != null
                ? groundRoster : sim.getGroundRoster();
        mission.deboardUnitType = effectiveRoster != null
                ? effectiveRoster.unitType(GroundRosterProfile.ForceTier.ELITE)
                : FactionUnitRoster.forFaction(req.side).elite();
        if (effectiveRoster != null) {
            mission.marineLoadout = InfantryLoadoutRolls.defenderSquad(
                    DEFAULT_TYPE.capacity, effectiveRoster,
                    GroundRosterProfile.ForceTier.ELITE, risk, sim.random());
        } else {
            mission.marineLoadout = InfantryLoadoutRolls.defenderSquad(
                    DEFAULT_TYPE.capacity, mission.deboardUnitType,
                    risk, sim.random());
        }
        LOG.info("ShuttleMeans: dispatched " + DEFAULT_TYPE + " side=" + req.side
                + " lz=(" + lz[0] + "," + lz[1] + ") entry=(" + entry[0] + "," + entry[1] + ")"
                + " from=" + (airfield.isEmpty() ? "offmap"
                        : loadedOnTheGround ? "airfield-embark" : "airfield"));
        return ReinforcementDispatchResult.COMMITTED;
    }

    /**
     * Hold the craft on its pad and march a squad out to board it.
     *
     * <p>This is the difference between an air arm and a spawner. A sortie that
     * arrives already loaded has no cost and no story: the aircraft is a
     * delivery mechanism that happens to be drawn. A sortie that has to be
     * loaded has both — the garrison commits people it can see, they cross open
     * ground to reach the field, and an attacker who is on the airfield, or
     * merely shooting across it, has stopped the lift without touching the
     * aircraft.
     *
     * <p>The squad walks in from the side's own rear edge, the same place the
     * walk-in means brings one on, and is pointed at the airbase node so the
     * ordinary patrol routing takes it there. Nothing here steers anybody: the
     * craft waits, {@code AirSystem} takes aboard whoever reaches the ramp, and
     * the sortie leaves when it is full or when there is no one else coming.
     *
     * @return false when there is no airbase to march to or nowhere to march
     *         from, leaving the sortie loaded as it always was
     */
    private boolean embarkOnPad(BattleControl sim, ReinforcementRequest req,
                                ShuttleMission mission) {
        TacticalNode field = airbaseNode(sim, req);
        if (field == null) return false;
        int[] primary = WalkInMeans.pickPrimaryCell(sim, req, axis);
        if (primary == null) return false;
        List<int[]> cells = WalkInMeans.collectAdjacentCells(sim.getGrid(),
                new LandingZoneScorer(sim.getGrid(), sim.getTopology()),
                primary[0], primary[1], DEFAULT_TYPE.capacity);
        if (cells.isEmpty()) return false;

        GroundRosterProfile effectiveRoster = groundRoster != null
                ? groundRoster : sim.getGroundRoster();
        UnitType infantryType = effectiveRoster != null
                ? effectiveRoster.unitType(GroundRosterProfile.ForceTier.ELITE)
                : FactionUnitRoster.forFaction(req.side).elite();

        Squad squad = null;
        int marched = 0;
        for (int[] cell : cells) {
            EntitySpec unit = new EntitySpec("e" + (nextEmbarkId++), req.side,
                    infantryType, cell[0], cell[1]);
            if (effectiveRoster != null) {
                InfantryLoadoutRolls.defenderLoadout(effectiveRoster,
                        GroundRosterProfile.ForceTier.ELITE, risk, sim.random()).seedInto(unit);
            } else {
                InfantryLoadoutRolls.defenderSquad(
                        1, infantryType, risk, sim.random())[0].seedInto(unit);
            }
            unit.role(UnitRole.PATROL);
            if (squad == null) {
                int sid = sim.mintSquad(req.side, infantryType);
                SquadCommandClaim.reinforcement(req.reason.name()).apply(sim, sid);
                squad = sim.getSquad(sid);
                if (squad != null) squad.assignedNode = field;
            }
            if (squad != null) unit.squad(squad.id);
            sim.spawn(unit);
            marched++;
        }
        if (squad == null) return false;
        squad.originalSize = marched;

        mission.state = ShuttleState.LOADING;
        mission.marinesRemaining = 0;
        mission.embarkSquadId = squad.id;
        mission.boardingPatience = BOARDING_PATIENCE;
        return true;
    }

    /** This side's airbase, which is where its aircraft are and where a crew walks to. */
    private static TacticalNode airbaseNode(BattleControl sim, ReinforcementRequest req) {
        TacticalMap map = sim.getTacticalMap();
        if (map == null) return null;
        List<TacticalNode> near = map.nearest(req.rallyX, req.rallyY, 1,
                EnumSet.of(TacticalNode.Kind.AIRBASE));
        return near.isEmpty() ? null : near.get(0);
    }

    /**
     * Where to look for a landing zone: the defender's safe band when there is
     * an authority to ask, and the raw rally otherwise.
     *
     * <p>This is the whole of "don't land behind their lines". The rally says
     * where the force is wanted and the objective says what it is for; neither
     * says where an aircraft can survive touching down, and on a losing track
     * the answer to all three used to be the same cell.
     */
    private int[] deliveryCentre(ReinforcementRequest req) {
        if (deploymentPolicy == null) return new int[]{ req.rallyX, req.rallyY };
        DeliveryDeployment deployment = deploymentPolicy.deploymentFor(req);
        if (deployment == null) return new int[]{ req.rallyX, req.rallyY };
        return new int[]{ deployment.hintX(), deployment.hintY() };
    }

    /**
     * Entry and exit for this sortie: the garrison's own airfield when it has
     * one, and the map edge when it does not.
     *
     * <p>A shuttle that materialises past the edge of the world is the placeholder
     * an authored field replaces. Flying the sortie off a hardstand costs nothing
     * in the lifecycle — a mission already carries its entry and its exit, and
     * neither has to be off-map — and it puts the air arm somewhere: the craft
     * lift from the field, deliver, and come home to it.
     *
     * <p>The nearest pad to the landing zone, because the only thing to choose
     * between four hardstands is the length of the flight.
     */
    private float[] sortieFrom(ReinforcementRequest req, float lzX, float lzY,
                               NavigationGrid grid) {
        LandingPad home = null;
        int best = Integer.MAX_VALUE;
        for (LandingPad pad : airfield) {
            int dx = Math.round(lzX) - pad.centerX;
            int dy = Math.round(lzY) - pad.centerY;
            int distance = dx * dx + dy * dy;
            if (distance < best) {
                best = distance;
                home = pad;
            }
        }
        if (home == null) {
            return entryForSide(req.side, axis, lzX, lzY, grid.getWidth(), grid.getHeight());
        }
        float padX = home.centerX + 0.5f;
        float padY = home.centerY + 0.5f;
        return new float[]{ padX, padY, padX, padY };
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

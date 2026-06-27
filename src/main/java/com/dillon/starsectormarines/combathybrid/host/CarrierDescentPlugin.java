package com.dillon.starsectormarines.combathybrid.host;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.air.Shuttle;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.combathybrid.bridge.GroundBattleConfig;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * S3d descent foundation — the in-combat trigger that hands one carrier over to a {@link
 * CarrierDescentBrain}. Press <b>L</b> ("land") during a SIM_COUPLED bridge battle: this picks the
 * first live carrier-side ship and {@code setShipAI}s the descent brain onto it, which then flies it
 * to the live ground band (the targetable-structure centroid, the same point {@link
 * CarrierEngagementPlugin} steers the fleet toward). Once the carrier settles in orbit ({@link
 * CarrierDescentBrain#hasArrived()}), this plugin launches one sim-native drop-ship ({@code Shuttle})
 * from the carrier's projected cell (D1b): it descends through the air layer, lands, and pours its
 * squad onto the band — the drop-ship scene at its simplest, before painted DZs or AA.
 *
 * <p>The trigger is keyed off an in-combat input event ({@code processInputPreCoreControls}) rather
 * than the campaign hotkey listener, because the takeover only makes sense once the combat instance
 * is live. {@code L} is safe in the spectator canvas: there is no player ship (player-ship control
 * is disabled each frame) and {@link SpectatorCanvasPlugin} consumes only WASD / RMB / scroll, so
 * {@code L} passes through to here. One takeover per battle (a probe demonstrates the mechanism, not
 * a fleet-wide descent); re-pressing is a no-op while a ship is descending.
 *
 * <p>Session-policy plugin, installed by {@link CombatBridgeSession#enterEngine}. Reachable only via
 * the dev probe today.
 */
@DebugOnly
public final class CarrierDescentPlugin extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(CarrierDescentPlugin.class);

    /** "L" for land — the in-combat key that triggers one carrier's descent. */
    private static final int TRIGGER_KEY = Keyboard.KEY_L;

    /**
     * Minimum descent leg (cells) for the launched drop-ship. The carrier settles within
     * {@code ARRIVE_RADIUS} (400 wu = 20 cells) of the band centroid, so entry (carrier cell) and LZ
     * (band cell) can land nearly on top of each other — and the altitude lerp is driven by leg
     * distance, so a zero-length leg shows no descent. When the gap is below this, the entry is
     * pushed back along the carrier→LZ bearing so the fall always reads.
     */
    private static final float MIN_DROP_LEG_CELLS = 12f;

    private final GroundBattleConfig config;
    private final FleetSide carrierSide;
    private CombatEngineAPI engine;
    private ShipAPI descending;          // the single carrier we've taken over (null until L is pressed)
    private CarrierDescentBrain brain;   // its installed brain — polled for the arrival cue
    private boolean dropLaunched;        // one drop-ship sortie per takeover (D1b)

    public CarrierDescentPlugin(GroundBattleConfig config, FleetSide carrierSide) {
        this.config = config;
        this.carrierSide = carrierSide;
    }

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
    }

    @Override
    public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            if (e.isKeyDownEvent() && e.getEventValue() == TRIGGER_KEY) {
                e.consume();
                triggerDescent();
                break;
            }
        }
    }

    private void triggerDescent() {
        if (engine == null) return;
        if (descending != null && descending.isAlive()) {
            LOG.info("ground-bridge(descent): " + descending.getHullSpec().getHullId()
                    + " is already descending; ignoring trigger.");
            return;
        }

        ShipAPI carrier = pickCarrier(engine.getFleetManager(carrierSide));
        if (carrier == null) {
            LOG.warn("ground-bridge(descent): no live carrier-side ship to take over.");
            return;
        }

        Vector2f target = new Vector2f();
        config.targetableCentroid(target);
        brain = new CarrierDescentBrain(carrier, target);
        carrier.setShipAI(brain);
        descending = carrier;
        dropLaunched = false;
        LOG.info("ground-bridge(descent): took over " + carrier.getHullSpec().getHullId()
                + " — descending to ground band at (" + (int) target.x + ", " + (int) target.y + ").");
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        // Once the carrier is in orbit, drop. The sim ticks in SimProxyMirror.advance (same frame,
        // earlier in the plugin order), so addShuttle here never races the air state-machine loop.
        if (!dropLaunched && brain != null && descending != null && descending.isAlive()
                && brain.hasArrived()) {
            launchDrop();
        }
    }

    /**
     * Spawns one sim-native drop-ship from the orbiting carrier. Entry = the carrier's combat-world
     * position projected to a cell; LZ = the ground-band centroid. A pure-transport
     * {@link ShuttleType#AEROSHUTTLE} on {@link Faction#MARINE} — the sim's {@code AirSystem} flies
     * it INCOMING (altitude-scaling down the leg), lands it, and deboards its squad onto the band.
     * D2 replaces the single centroid LZ with a painted DZ + threat-scored scatter.
     */
    private void launchDrop() {
        dropLaunched = true;
        BattleSimulation sim = config.sim();

        Vector2f entry = new Vector2f();
        config.worldToCell(descending.getLocation().x, descending.getLocation().y, entry);

        Vector2f lzWorld = new Vector2f();
        config.targetableCentroid(lzWorld);
        Vector2f lz = new Vector2f();
        config.worldToCell(lzWorld.x, lzWorld.y, lz);

        // Snap the LZ to a walkable cell. The centroid is the structure centroid, which can land on a
        // (non-walkable) structure cell or inside a walled compound; the deboard scan only reaches 5
        // cells, so a dropship there would never deboard and would wedge in LANDED forever. Land the
        // shuttle on the nearest walkable cell instead. (D2's threat-scored scatter supersedes this.)
        int[] lzCell = nearestWalkableCell(sim.getGrid(), (int) Math.floor(lz.x), (int) Math.floor(lz.y));
        lz.set(lzCell[0] + 0.5f, lzCell[1] + 0.5f);

        // Guarantee a visible descent leg (see MIN_DROP_LEG_CELLS): if the carrier settled almost on
        // the LZ, push the entry back along the carrier→LZ bearing (or straight up if dead-on).
        float dx = entry.x - lz.x, dy = entry.y - lz.y;
        float d = (float) Math.hypot(dx, dy);
        if (d < MIN_DROP_LEG_CELLS) {
            if (d < 1e-3f) {
                entry.set(lz.x, lz.y + MIN_DROP_LEG_CELLS);
            } else {
                entry.set(lz.x + dx / d * MIN_DROP_LEG_CELLS, lz.y + dy / d * MIN_DROP_LEG_CELLS);
            }
        }

        // Exit back the way it came (up to the carrier). pendingDelay 0 = launch immediately.
        Shuttle drop = new Shuttle(ShuttleType.AEROSHUTTLE, Faction.MARINE,
                lz.x, lz.y, entry.x, entry.y, entry.x, entry.y, 0f);
        sim.addShuttle(drop);
        LOG.info("ground-bridge(descent): launched drop-ship from carrier cell ("
                + (int) entry.x + ", " + (int) entry.y + ") to LZ (" + (int) lz.x + ", " + (int) lz.y + ").");
    }

    /** First live, non-fighter ship on the carrier side. */
    private ShipAPI pickCarrier(CombatFleetManagerAPI fm) {
        for (DeployedFleetMemberAPI dfm : fm.getDeployedCopyDFM()) {
            if (dfm.isFighterWing()) continue;
            ShipAPI ship = dfm.getShip();
            if (ship != null && ship.isAlive()) return ship;
        }
        return null;
    }

    /**
     * Nearest walkable cell to {@code (startX,startY)} by 4-neighbour BFS over the sim grid, falling
     * back to the start cell if nothing walkable is reachable (impossible in a real battle — there's
     * always walkable ground — so the fallback only guards a degenerate empty grid). One-shot per
     * launch, so the unbounded expansion is cheap.
     */
    private static int[] nearestWalkableCell(NavigationGrid grid, int startX, int startY) {
        if (grid.inBounds(startX, startY) && grid.isWalkable(startX, startY)) {
            return new int[]{startX, startY};
        }
        Set<Long> seen = new HashSet<>();
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{startX, startY});
        seen.add(((long) startX << 32) | (startY & 0xFFFFFFFFL));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            for (int[] d : dirs) {
                int nx = p[0] + d[0], ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                long k = ((long) nx << 32) | (ny & 0xFFFFFFFFL);
                if (!seen.add(k)) continue;
                if (grid.isWalkable(nx, ny)) return new int[]{nx, ny};
                q.add(new int[]{nx, ny});
            }
        }
        return new int[]{startX, startY};
    }
}

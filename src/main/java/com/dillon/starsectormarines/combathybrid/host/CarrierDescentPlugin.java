package com.dillon.starsectormarines.combathybrid.host;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.air.DropZoneScatter;
import com.dillon.starsectormarines.battle.air.Shuttle;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
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
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import org.apache.log4j.Logger;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 * S3d drop-ship invasion — the in-combat <b>"land here"</b> trigger. <b>Left-click</b> a point in a
 * SIM_COUPLED bridge battle to designate a wide landing zone: this picks the first live carrier-side
 * ship, {@code setShipAI}s a {@link CarrierDescentBrain} onto it aimed at the clicked point, and
 * flies it there to establish orbit. Once it settles ({@link CarrierDescentBrain#hasArrived()}), the
 * plugin scatters a wave of sim-native dropships ({@code Shuttle}s) across the zone ({@link
 * DropZoneScatter}) — they descend, land, and pour their squads onto the ground. Re-clicking before
 * arrival re-aims the landing zone ("no — land <em>there</em>").
 *
 * <p>The trigger rides an in-combat input event ({@code processInputPreCoreControls}) because it only
 * makes sense once the combat instance is live. Left-click is safe in the spectator canvas: there is
 * no player ship (player-ship control is disabled each frame) and {@link SpectatorCanvasPlugin}
 * consumes only WASD / RMB / scroll, so LMB passes through to here. One <em>active</em> takeover at a
 * time (a probe demonstrates the mechanism, not a fleet-wide descent): clicks are ignored once the
 * carrier has dropped; once it dies, a fresh click can take over another (and drop again).
 *
 * <p>Session-policy plugin, installed by {@link CombatBridgeSession#enterEngine}. Reachable only via
 * the dev probe today.
 */
@DebugOnly
public final class CarrierDescentPlugin extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(CarrierDescentPlugin.class);

    /**
     * Minimum descent leg (cells) for the launched drop-ship. The carrier settles within
     * {@code ARRIVE_RADIUS} (400 wu — a ship-physics arrival tolerance, deliberately <em>not</em>
     * cell-scaled: it's sized to a real hull's mass/turn radius, so it stays in world units as the
     * cell density changes; at 7 wu/cell that's ~57 cells) of the band centroid, so entry (carrier
     * cell) and LZ (band cell) can land nearly on top of each other — and the altitude lerp is driven by leg
     * distance, so a zero-length leg shows no descent. When the gap is below this, the entry is
     * pushed back along the carrier→LZ bearing so the fall always reads.
     */
    private static final float MIN_DROP_LEG_CELLS = 12f;

    /** Cells past the grid edge a drop-ship egresses to when its host carrier has left the field. */
    private static final float OFFGRID_MARGIN_CELLS = 8f;

    /** Radius (cells) of the "land here" drop zone around the clicked point — a wide LZ, not a pinpoint. */
    private static final float ZONE_RADIUS_CELLS = 30f;
    /** Dropships in a wave — the swarm size. */
    private static final int DROP_COUNT = 5;
    /** Minimum spacing (cells) between landing cells so squads scatter instead of stacking. */
    private static final float MIN_SPACING_CELLS = 5f;
    /** Enemy-density sample radius (cells) feeding the per-cell threat score. */
    private static final float THREAT_RADIUS_CELLS = 6f;
    /** Per-dropship launch stagger (sim-seconds) so the wave ejects in sequence, not all at once. */
    private static final float STAGGER_SEC = 0.6f;

    private final GroundBattleConfig config;
    private final FleetSide carrierSide;
    private final List<Shuttle> drops = new ArrayList<>();   // launched wave — exits track the carrier each frame
    private final Random scatterRng = new Random();          // jitters the DZ scatter (transient battle → no seed needed)
    private final Vector2f dropZoneWorld = new Vector2f();    // the clicked "land here" point (combat-world coords)
    private CombatEngineAPI engine;
    private ShipAPI descending;          // the single carrier we've taken over (null until first click)
    private CarrierDescentBrain brain;   // its installed brain — polled for the arrival cue
    private boolean dropLaunched;        // one drop wave per takeover (D1b)

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
            if (e.isLMBDownEvent()) {
                Vector2f world = new Vector2f();
                if (cursorWorld(world)) {
                    e.consume();
                    designateLandingZone(world);
                }
                break;
            }
        }
    }

    /**
     * "Land here": designate the drop zone at {@code worldTarget}. With no active takeover, takes over
     * the first live carrier and aims a fresh {@link CarrierDescentBrain} at the point; with a carrier
     * already en route (not yet dropped), re-aims it ("land there instead"). Ignored once the wave has
     * dropped — that takeover is committed.
     */
    private void designateLandingZone(Vector2f worldTarget) {
        if (engine == null) return;

        if (descending != null && descending.isAlive()) {
            if (dropLaunched || brain == null) {
                LOG.info("ground-bridge(descent): " + descending.getHullSpec().getHullId()
                        + " has already committed its drop; ignoring re-designation.");
                return;
            }
            dropZoneWorld.set(worldTarget);
            brain.setTarget(worldTarget);   // re-aim mid-approach
            LOG.info("ground-bridge(descent): re-aimed landing zone to ("
                    + (int) worldTarget.x + ", " + (int) worldTarget.y + ").");
            return;
        }

        ShipAPI carrier = pickCarrier(engine.getFleetManager(carrierSide));
        if (carrier == null) {
            LOG.warn("ground-bridge(descent): no live carrier-side ship to take over.");
            return;
        }
        // Fresh takeover: a leftover wave from a now-dead carrier must not be re-homed onto this new
        // mothership — send it off-grid for good and stop tracking it before we re-point retargeting.
        flushOrphanedDrops();
        dropZoneWorld.set(worldTarget);
        brain = new CarrierDescentBrain(carrier, worldTarget);
        carrier.setShipAI(brain);
        descending = carrier;
        dropLaunched = false;
        LOG.info("ground-bridge(descent): took over " + carrier.getHullSpec().getHullId()
                + " — landing zone designated at (" + (int) worldTarget.x + ", " + (int) worldTarget.y + ").");
    }

    /** Sends any still-airborne drops from a prior (dead-carrier) wave off-grid and forgets them, so
     *  {@link #retargetDropExit} doesn't re-home a previous takeover's shuttles onto a fresh carrier. */
    private void flushOrphanedDrops() {
        for (Shuttle s : drops) {
            if (s.mission.state != Shuttle.State.GONE) sendOffGrid(s);
        }
        drops.clear();
    }

    /**
     * Cursor → combat-world via the viewport's explicit rectangle (getLLX/getVisibleWidth), NOT the
     * {@code convert*} helpers: under the spectator's {@code setExternalControl} camera those read the
     * inert viewMult and drift with zoom, while the rectangle getters reflect the canvas's own
     * {@code vp.set(...)}. Mouse (0,0) is bottom-left = the lower-left corner the getters report. See
     * {@link SeeThroughPlugin}. Returns false (no viewport yet) without writing {@code out}.
     */
    private boolean cursorWorld(Vector2f out) {
        if (engine == null) return false;
        ViewportAPI vp = engine.getViewport();
        if (vp == null) return false;
        float screenW = Math.max(1, Display.getWidth());
        float screenH = Math.max(1, Display.getHeight());
        out.set(vp.getLLX() + Mouse.getX() * (vp.getVisibleWidth() / screenW),
                vp.getLLY() + Mouse.getY() * (vp.getVisibleHeight() / screenH));
        return true;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        // Once the carrier is in orbit, drop. The sim ticks in SimProxyMirror.advance (same frame,
        // earlier in the plugin order), so addShuttle here never races the air state-machine loop.
        if (!dropLaunched && brain != null && descending != null && descending.isAlive()
                && brain.hasArrived()) {
            launchDrop();
        }
        retargetDropExit();
    }

    /**
     * Keeps every launched dropship's exit pointed at the host carrier, so once a craft has deboarded
     * it flies home and docks — the carrier holds a near-stationary orbit, so tracking its live cell
     * each frame reads as a return-to-mothership rather than a fixed waypoint. If the carrier has left
     * the field (destroyed / gone), the dropships instead egress straight off the top of the grid: an
     * alternative escape rather than vanishing where they stand. GONE craft are pruned.
     * {@code exitX/exitY} are read live by AirSystem's DEPARTING tick, so this just re-steers in flight.
     */
    private void retargetDropExit() {
        if (drops.isEmpty()) return;
        boolean carrierLive = descending != null && descending.isAlive();
        Vector2f c = new Vector2f();
        if (carrierLive) config.worldToCell(descending.getLocation().x, descending.getLocation().y, c);
        for (Iterator<Shuttle> it = drops.iterator(); it.hasNext(); ) {
            Shuttle s = it.next();
            if (s.mission.state == Shuttle.State.GONE) {
                it.remove();
                continue;
            }
            if (carrierLive) {
                s.mission.exitX = c.x;
                s.mission.exitY = c.y;
            } else {
                sendOffGrid(s);
            }
        }
    }

    /** Point a drop-ship's egress straight off the top of the grid — the carrier-gone / orphaned escape. */
    private void sendOffGrid(Shuttle s) {
        s.mission.exitX = s.mission.lzX;
        s.mission.exitY = config.gridH() + OFFGRID_MARGIN_CELLS;
    }

    /**
     * Launches a scattered wave of sim-native dropships from the orbiting carrier. Entry = the
     * carrier's combat-world position projected to a cell; the {@link DropZoneScatter} engine picks up
     * to {@value #DROP_COUNT} low-threat, spaced landing cells across the designated "land here" zone
     * (the clicked point + {@link #ZONE_RADIUS_CELLS}). Each is a pure-transport
     * {@link ShuttleType#AEROSHUTTLE} on {@link Faction#MARINE} — the sim's {@code AirSystem} flies it
     * INCOMING (altitude-scaling down the leg), lands it, and deboards its squad.
     */
    private void launchDrop() {
        dropLaunched = true;
        // Requires AirProvider.INTERNAL — dropships are the sim's own shuttles, so addShuttle below
        // fail-louds under EXTERNAL. The bridge runs INTERNAL by design (S3d); see BattleSimulation.
        BattleSimulation sim = config.sim();
        NavigationGrid grid = sim.getGrid();
        TacticalScoring scoring = sim.getTacticalScoring();

        Vector2f entry = new Vector2f();
        config.worldToCell(descending.getLocation().x, descending.getLocation().y, entry);

        // Zone center = the designated "land here" point, clamped into the grid (an off-map click
        // lands the wave at the nearest edge instead of wedging a shuttle off-grid) then snapped to
        // walkable ground.
        Vector2f center = new Vector2f();
        config.worldToCell(dropZoneWorld.x, dropZoneWorld.y, center);
        int cx = Math.max(0, Math.min(config.gridW() - 1, (int) Math.floor(center.x)));
        int cy = Math.max(0, Math.min(config.gridH() - 1, (int) Math.floor(center.y)));
        int[] centerCell = nearestWalkableCell(grid, cx, cy);

        // Scatter the wave: low-threat, spaced landing cells across the zone (paratrooper drop). Threat
        // = enemy-combatant density at the cell; walkable filter keeps drops off walls/structures.
        List<int[]> cells = DropZoneScatter.sample(
                centerCell[0], centerCell[1], ZONE_RADIUS_CELLS, DROP_COUNT, MIN_SPACING_CELLS,
                (x, y) -> grid.inBounds(x, y) && grid.isWalkable(x, y),
                (x, y) -> scoring.countCombatantsWithin(Faction.DEFENDER, x, y, THREAT_RADIUS_CELLS),
                scatterRng);
        if (cells.isEmpty()) cells = List.of(centerCell);   // degenerate zone — at least drop on the centroid

        for (int i = 0; i < cells.size(); i++) {
            spawnDrop(sim, entry.x, entry.y, cells.get(i)[0] + 0.5f, cells.get(i)[1] + 0.5f, i * STAGGER_SEC);
        }
        LOG.info("ground-bridge(descent): launched " + cells.size() + "-ship drop wave over zone cell ("
                + centerCell[0] + ", " + centerCell[1] + ").");
    }

    /**
     * Spawns one dropship from the carrier cell ({@code entryX},{@code entryY}) to LZ
     * ({@code lzX},{@code lzY}) after {@code pendingDelay} sim-seconds (the wave stagger). The entry is
     * pushed back along the carrier→LZ bearing to at least {@link #MIN_DROP_LEG_CELLS} so the
     * altitude-lerp descent always reads (the lerp is leg-distance driven). Exit starts at the carrier
     * and is retargeted live by {@link #retargetDropExit}.
     */
    private void spawnDrop(BattleSimulation sim, float entryX, float entryY, float lzX, float lzY, float pendingDelay) {
        float ex = entryX, ey = entryY;
        float dx = ex - lzX, dy = ey - lzY;
        float d = (float) Math.hypot(dx, dy);
        if (d < MIN_DROP_LEG_CELLS) {
            if (d < 1e-3f) {
                ex = lzX;
                ey = lzY + MIN_DROP_LEG_CELLS;
            } else {
                ex = lzX + dx / d * MIN_DROP_LEG_CELLS;
                ey = lzY + dy / d * MIN_DROP_LEG_CELLS;
            }
        }
        Shuttle s = new Shuttle(ShuttleType.AEROSHUTTLE, Faction.MARINE,
                lzX, lzY, ex, ey, ex, ey, pendingDelay);
        sim.addShuttle(s);
        drops.add(s);
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

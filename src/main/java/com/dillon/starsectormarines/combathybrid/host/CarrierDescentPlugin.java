package com.dillon.starsectormarines.combathybrid.host;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.air.DropZoneScatter;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
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
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
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
 * transport deploys its invasion as a sequence of scattered dropship waves ({@link DropZoneScatter})
 * over the orbit window, then peels off when the manifest empties — and if it's lost mid-window the
 * undeployed waves go with it (D4, the stake). Re-clicking before the first wave re-aims the landing
 * zone ("no — land <em>there</em>").
 *
 * <p>The trigger rides an in-combat input event ({@code processInputPreCoreControls}) because it only
 * makes sense once the combat instance is live. Left-click is safe in the spectator canvas: there is
 * no player ship (player-ship control is disabled each frame) and {@link SpectatorCanvasPlugin}
 * consumes only WASD / RMB / scroll, so LMB passes through to here. One <em>active</em> takeover at a
 * time (a probe demonstrates the mechanism, not a fleet-wide descent): re-clicks are ignored while a
 * carrier is deploying its invasion; once the orbit window closes (it peels off) or the carrier dies,
 * a fresh click can launch another.
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

    /** Base "land here" drop-zone radius (cells) — the cold spread, a wide LZ, not a pinpoint. */
    private static final float ZONE_RADIUS_CELLS = 30f;
    /** Cap on the threat-widened radius (cells) so a very hot DZ doesn't scatter across the whole map. */
    private static final float ZONE_RADIUS_MAX_CELLS = 60f;
    /** Fraction the DZ radius widens per enemy combatant in the base zone — hot drops scatter wider + more isolating (D3). */
    private static final float HOT_SPREAD_PER_ENEMY = 0.05f;
    /** Dropships in a wave — the swarm size. */
    private static final int DROP_COUNT = 5;
    /** Minimum spacing (cells) between landing cells so squads scatter instead of stacking. */
    private static final float MIN_SPACING_CELLS = 5f;
    /** Enemy-density sample radius (cells) feeding the per-cell threat score. */
    private static final float THREAT_RADIUS_CELLS = 6f;
    /** Per-dropship launch stagger (sim-seconds) so the wave ejects in sequence, not all at once. */
    private static final float STAGGER_SEC = 0.6f;

    /** Sim-seconds the transport holds orbit between waves — the cadence of the orbit window (D4). */
    private static final float WAVE_INTERVAL_SEC = 6f;
    /**
     * Marine pool (depth, D5) the invasion draws on when the player fleet carries none — a probe
     * fallback so the demo always shows a multi-wave drop. A real fleet's marine count is used uncapped
     * when present (pillar 2: the game never caps the invasion you engineered). ~3 full waves
     * ({@code DROP_COUNT × AEROSHUTTLE.capacity}).
     */
    private static final int DEFAULT_PROBE_POOL = 60;

    private final GroundBattleConfig config;
    private final FleetSide carrierSide;
    private final List<Long> drops = new ArrayList<>();      // launched wave (air entity ids) — exits track the carrier each frame
    private final Random scatterRng = new Random();          // jitters the DZ scatter (transient battle → no seed needed)
    private final Vector2f dropZoneWorld = new Vector2f();    // the clicked "land here" point (combat-world coords)
    private CombatEngineAPI engine;
    private ShipAPI descending;          // the single carrier we've taken over (null until first click)
    private CarrierDescentBrain brain;   // its installed brain — polled for the arrival cue
    private int marinePool;              // marines still aboard to deploy this invasion (depth, D5); 0 = exhausted / forfeit
    private boolean committed;           // true once the first wave deploys — locks the re-aim
    private float waveTimer;             // sim-seconds until the next wave (counts down once the carrier is in orbit)

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
     * the first live carrier and aims a fresh {@link CarrierDescentBrain} at the point, loading its
     * invasion manifest; with a carrier already en route (no wave deployed yet), re-aims it ("land
     * there instead"). Ignored once the first wave has dropped — the invasion is committed.
     */
    private void designateLandingZone(Vector2f worldTarget) {
        if (engine == null) return;

        if (descending != null && descending.isAlive()) {
            if (brain == null || committed) {
                LOG.info("ground-bridge(descent): " + descending.getHullSpec().getHullId()
                        + " is already deploying its invasion; ignoring re-designation.");
                return;
            }
            dropZoneWorld.set(worldTarget);
            brain.setTarget(worldTarget);   // re-aim mid-approach (before the first wave commits)
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
        marinePool = readFleetMarines();   // depth = the fleet you brought; waveTimer 0 → first wave on arrival
        committed = false;
        waveTimer = 0f;
        LOG.info("ground-bridge(descent): took over " + carrier.getHullSpec().getHullId()
                + " — landing zone designated at (" + (int) worldTarget.x + ", " + (int) worldTarget.y
                + "); " + marinePool + " marines aboard to deploy.");
    }

    /** Sends any still-airborne drops from a prior (dead-carrier) wave off-grid and forgets them, so
     *  {@link #retargetDropExit} doesn't re-home a previous takeover's shuttles onto a fresh carrier. */
    private void flushOrphanedDrops() {
        for (long id : drops) {
            ShuttleMission m = config.sim().world().mission(id);
            if (m != null && m.state != ShuttleState.GONE) sendOffGrid(m);
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
        if (brain != null && descending != null) {
            if (!descending.isAlive()) {
                forfeitUndeployed();   // the stake (D4): transport lost mid-window → marines still aboard are forfeit
            } else if (brain.hasArrived() && marinePool > 0) {
                // Orbit window: hold station and deploy the marine pool one wave per WAVE_INTERVAL_SEC
                // until it's exhausted (D5 depth). The sim ticks in SimProxyMirror.advance (earlier in
                // the plugin order), so spawnShuttle inside launchWave never races the air loop.
                waveTimer -= amount;
                if (waveTimer <= 0f) {
                    launchWave();
                    committed = true;
                    waveTimer = WAVE_INTERVAL_SEC;
                    if (marinePool <= 0) departCarrier();   // pool exhausted → the window closes
                }
            }
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
        for (Iterator<Long> it = drops.iterator(); it.hasNext(); ) {
            long id = it.next();
            ShuttleMission m = config.sim().world().mission(id);
            // null = the craft reached terminal GONE and the air system destroyed
            // its entity; drop the stale id (same forget-it path as the GONE check).
            if (m == null || m.state == ShuttleState.GONE) {
                it.remove();
                continue;
            }
            if (carrierLive) {
                m.exitX = c.x;
                m.exitY = c.y;
            } else {
                sendOffGrid(m);
            }
        }
    }

    /** Point a drop-ship's egress straight off the top of the grid — the carrier-gone / orphaned escape. */
    private void sendOffGrid(ShuttleMission m) {
        m.exitX = m.lzX;
        m.exitY = config.gridH() + OFFGRID_MARGIN_CELLS;
    }

    /**
     * The stake (D4): the transport was lost mid-window, so every wave still in its belly is forfeit —
     * those marines never reach the ground ("what got down is what you've got"). The diegetic hard
     * failure that makes the sky fight matter. Wired for the skybattle feature that can actually kill
     * the carrier; in the probe today the carrier has no death source, so this is latent.
     */
    private void forfeitUndeployed() {
        if (marinePool <= 0) return;
        LOG.info("ground-bridge(descent): transport lost — " + marinePool
                + " marines still aboard forfeit with it.");
        marinePool = 0;
    }

    /**
     * The invasion's depth (D5): marines aboard the player fleet — the diegetic currency, "the fleet you
     * brought." Used uncapped when present (pillar 2 never caps the invasion you engineered); falls back
     * to {@link #DEFAULT_PROBE_POOL} only when the fleet carries none, so the probe always shows a drop.
     *
     * <p>Read straight off the campaign fleet's cargo — {@link PlayerFleetStash} detaches ships, not
     * cargo, so the count is intact during the spectator battle. Production should route this through the
     * campaign→battle bridge (a {@code TargetProfile}-style field on {@link GroundBattleConfig}) rather
     * than reaching back to the campaign from combat; direct read is fine for the {@code @DebugOnly} probe.
     */
    private static int readFleetMarines() {
        CampaignFleetAPI fleet = Global.getSector() != null ? Global.getSector().getPlayerFleet() : null;
        if (fleet == null || fleet.getCargo() == null) return DEFAULT_PROBE_POOL;
        int marines = (int) fleet.getCargo().getMarines();
        return marines > 0 ? marines : DEFAULT_PROBE_POOL;
    }

    /**
     * Orbit window closed — the marine pool is exhausted, so the transport peels off ("can't stay
     * forever"). Steer it off the top of the grid; once it's leaving it's done its job. Re-aiming is
     * already locked ({@code committed}), so it won't be re-tasked.
     */
    private void departCarrier() {
        Vector2f exit = new Vector2f();
        config.cellToWorld(config.gridW() / 2, config.gridH() + (int) OFFGRID_MARGIN_CELLS, exit);
        brain.setTarget(exit);
        LOG.info("ground-bridge(descent): orbit window closed — full manifest deployed; transport peeling off.");
        // Release the takeover so a fresh click can launch another invasion (with this carrier or a
        // sibling) — otherwise, with no carrier-death source in the probe, you'd get exactly one
        // invasion per battle. The carrier keeps its installed brain steering it off-grid; we just stop
        // tracking it, so trailing drops egress off-grid (retargetDropExit's carrier-gone branch)
        // rather than chasing a transport we've let go.
        descending = null;
        brain = null;
    }

    /**
     * Launches a scattered wave of sim-native dropships from the orbiting carrier. Entry = the
     * carrier's combat-world position projected to a cell; the {@link DropZoneScatter} engine picks up
     * to {@value #DROP_COUNT} low-threat, spaced landing cells across the designated "land here" zone
     * (the clicked point; the spread radius widens from {@link #ZONE_RADIUS_CELLS} with the zone's
     * threat — hot DZs scatter wider + more isolating, D3). Each is a pure-transport
     * {@link ShuttleType#AEROSHUTTLE} on {@link Faction#MARINE} — the sim's {@code AirSystem} flies it
     * INCOMING (altitude-scaling down the leg), lands it, and deboards its squad.
     */
    private void launchWave() {
        // Requires AirProvider.INTERNAL — dropships are the sim's own shuttles, so spawnShuttle below
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

        // Hot/cold spread: a DZ crowded with defenders scatters wider + more isolating (paratrooper
        // chaos), a cold one lands tight. Zone threat = enemy density in the base radius.
        int zoneThreat = scoring.countCombatantsWithin(Faction.DEFENDER, centerCell[0], centerCell[1], ZONE_RADIUS_CELLS);
        float radius = Math.min(ZONE_RADIUS_MAX_CELLS, ZONE_RADIUS_CELLS * (1f + HOT_SPREAD_PER_ENEMY * zoneThreat));

        // This wave's throughput: up to DROP_COUNT dropships, AEROSHUTTLE.capacity marines each, drawn
        // from the pool. The pool (depth) caps the wave when it's nearly empty — the last wave is partial.
        int capacity = ShuttleType.AEROSHUTTLE.capacity;
        int waveMarines = Math.min(marinePool, DROP_COUNT * capacity);
        int shipsWanted = (waveMarines + capacity - 1) / capacity;   // ceil — the last ship may be partial

        // Scatter the wave: low-threat, spaced landing cells across the zone. Per-cell threat =
        // enemy-combatant density; walkable filter keeps drops off walls/structures.
        List<int[]> cells = DropZoneScatter.sample(
                centerCell[0], centerCell[1], radius, shipsWanted, MIN_SPACING_CELLS,
                (x, y) -> grid.inBounds(x, y) && grid.isWalkable(x, y),
                (x, y) -> scoring.countCombatantsWithin(Faction.DEFENDER, x, y, THREAT_RADIUS_CELLS),
                scatterRng);
        if (cells.isEmpty()) cells = List.of(centerCell);   // degenerate zone — at least drop on the centroid

        // Distribute the wave's marines across the scattered cells (capacity per ship, last ship partial).
        // A tight zone may yield fewer cells than wanted — then fewer deploy and the rest stay aboard.
        int deployed = 0, shipsOut = 0;
        for (int[] cell : cells) {
            if (deployed >= waveMarines) break;
            int forShip = Math.min(capacity, waveMarines - deployed);
            spawnDrop(sim, entry.x, entry.y, cell[0] + 0.5f, cell[1] + 0.5f, forShip, shipsOut * STAGGER_SEC);
            deployed += forShip;
            shipsOut++;
        }
        marinePool -= deployed;
        LOG.info("ground-bridge(descent): wave — " + shipsOut + "-ship drop (" + deployed
                + " marines) over zone cell (" + centerCell[0] + ", " + centerCell[1] + "), threat "
                + zoneThreat + ", spread radius " + (int) radius + "; " + marinePool + " marines remaining aboard.");
    }

    /**
     * Spawns one dropship from the carrier cell ({@code entryX},{@code entryY}) to LZ
     * ({@code lzX},{@code lzY}) carrying {@code marineCount} marines (the last ship of a partial wave
     * carries fewer than {@code capacity}), launching after {@code pendingDelay} sim-seconds (the wave
     * stagger). The entry is pushed back along the carrier→LZ bearing to at least
     * {@link #MIN_DROP_LEG_CELLS} so the altitude-lerp descent always reads (the lerp is leg-distance
     * driven). Exit starts at the carrier and is retargeted live by {@link #retargetDropExit}.
     */
    private void spawnDrop(BattleSimulation sim, float entryX, float entryY, float lzX, float lzY,
                           int marineCount, float pendingDelay) {
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
        long id = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.MARINE,
                lzX, lzY, ex, ey, ex, ey, pendingDelay);
        sim.world().mission(id).marinesRemaining = Math.min(marineCount, ShuttleType.AEROSHUTTLE.capacity);
        drops.add(id);
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

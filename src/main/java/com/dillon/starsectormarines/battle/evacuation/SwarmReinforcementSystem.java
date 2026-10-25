package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The swarm director (`colony-breach-nouns.md`, Swarm director): maintains
 * rescue-mission swarm pressure.
 *
 * <p>Until a Colony Breach's tremor it restores its population floor with
 * deterministic perimeter waves, at a fraction of the floor while the colony
 * seeps. From the tremor on ({@link #beginBoreSourcing}) spawning has a
 * source: every body comes out of a living bore, as a timed surge sized by the
 * bore's tier and the biomass it was fed, released as a tight stream from its
 * mouth. The population target is then the sum of the living bores'
 * capacities, so a collapsed bore takes its share with it for good and a
 * battle whose bores are all collapsed spawns nothing more. Strains come from
 * {@link BoreStrainTable} by the surging bore's tier, every body through
 * {@link SwarmStrains}.
 */
public final class SwarmReinforcementSystem {

    public static final float WAVE_INTERVAL_SECONDS = 6f;
    public static final float POPULATION_FLOOR_FRACTION = 0.70f;
    public static final int PERIMETER_BAND_CELLS = 6;
    public static final int MIN_CIVILIAN_ENTRY_DISTANCE = 14;
    public static final int MIN_MARINE_ENTRY_DISTANCE = 8;
    /**
     * The share of the population floor the director restores during Colony
     * Breach's opening seepage: a thin trickle at the perimeter until the
     * tremor, then the full floor.
     */
    public static final float SEEPAGE_FLOOR_FRACTION = 0.40f;
    private static final int MIN_WAVE_SIZE = 4;
    private static final int MIN_RUNNER_SPACING = 2;
    /** Ticks between two bodies of one surge leaving the mouth with emergence off: a stream, not a crowd. */
    public static final int SURGE_RELEASE_SPACING_TICKS = 2;
    /** Most bodies that break out of the hole together (`colony-breach-nouns.md`, Emergence). */
    public static final int CLUTCH_MAX = 3;
    /** Shortest and longest pause between two clutches, ticks. */
    public static final int CLUTCH_GAP_MIN_TICKS = 10, CLUTCH_GAP_MAX_TICKS = 24;
    /** The mouth cells a body breaks out on: the hole and the ring round it. */
    private static final int HOLE_CELLS = 9;
    /** A due surge waits until at least this share of it fits under the target. */
    public static final float SURGE_MIN_FIT = 0.5f;
    /** Ticks a held surge waits before it asks again. */
    private static final int SURGE_RECHECK_TICKS = 30;

    /** One body of a planned surge: its strain, its stage, and whether it is the bore's Matriarch. */
    record Planned(BoreStrainTable.Slot slot, SwarmStage stage, boolean matriarch) {
        float weight() { return stage.populationWeight; }
    }

    /**
     * One surge on its way out of a bore's mouth, planned body by body when it
     * starts so its pack leader can go first and its weight is known.
     */
    private static final class Release {
        final SwarmBore bore;
        final BoreTier tier;
        final int surgeIndex;
        final List<Planned> bodies;
        final int size;
        int released;
        int nextTick;
        /** A scavenger pack: its Runners fetch the swarm's dead before they fight. */
        boolean scavengers;
        /** The pack this surge's bodies are joining now; 0 while packs are off. */
        int pack;
        /** The muster that pack forms up at ({@link SwarmEmergence}); 0 while emergence is off. */
        int muster;
        int clutches;

        Release(SwarmBore bore, int surgeIndex, List<Planned> bodies, int tick) {
            this.bore = bore;
            this.tier = bore.tier();
            this.surgeIndex = surgeIndex;
            this.bodies = bodies;
            this.size = bodies.size();
            this.nextTick = tick;
        }

        float weightLeft() {
            float w = 0f;
            for (int i = released; i < size; i++) w += bodies.get(i).weight();
            return w;
        }
    }

    /** Whether bodies are born at a growth stage. {@code -Dbattle.swarm.growthStages=false} is the control: all adults. */
    public static boolean growthStagesByDefault() {
        return Boolean.parseBoolean(System.getProperty("battle.swarm.growthStages", "true"));
    }

    private final CivilianEvacuationTracker tracker;
    private CivilianEvacuationPlacement placement;
    private int targetPopulation;
    private int populationFloor;
    private int waveSize;
    private long seed;
    private int waveIndex;
    private float accumulator;
    private boolean configured;
    private boolean seepage;
    private int snapshotRevision;
    private String snapshotSignature = "";
    private final Map<Long, SwarmPressureSnapshot.Approach> approachByRunner =
            new HashMap<>();
    private List<SwarmPressureSnapshot.WaveIntent> ownedWave = List.of();
    private SwarmPressureSnapshot snapshot;
    private SwarmBores bores;
    private boolean boreSourcing;
    private int boreTarget;
    /** The share of the living bores' capacities the field is held at; a mission can hold the swarm back between crises. */
    private float pressureShare = 1f;
    private final List<Release> releases = new ArrayList<>();
    private List<SwarmPressureSnapshot.WaveIntent> surgeWave = new ArrayList<>();
    private final boolean growthStages;

    public SwarmReinforcementSystem(CivilianEvacuationTracker tracker) {
        if (tracker == null) throw new IllegalArgumentException("tracker is required");
        this.tracker = tracker;
        this.growthStages = growthStagesByDefault();
    }

    /** Configures one rescue battle; later calls fail closed. */
    public boolean configure(CivilianEvacuationPlacement placement,
                             int targetPopulation, long seed,
                             BattleSimulation sim) {
        if (sim == null || !configure(placement, targetPopulation, seed)) {
            return false;
        }
        ownedWave = captureOpeningWave(sim);
        refreshSnapshot(sim, "OPENING_PERIMETER");
        return true;
    }

    /** Test/setup compatibility overload; the first tick publishes its picture. */
    public boolean configure(CivilianEvacuationPlacement placement,
                             int targetPopulation, long seed) {
        if (configured || placement == null || targetPopulation <= 0) return false;
        this.placement = placement;
        this.targetPopulation = targetPopulation;
        this.populationFloor = Math.max(1,
                (int) Math.ceil(targetPopulation * POPULATION_FLOOR_FRACTION));
        this.waveSize = Math.max(MIN_WAVE_SIZE,
                (int) Math.ceil(targetPopulation * 0.25f));
        this.seed = seed;
        configured = true;
        return true;
    }

    /**
     * Configures a battle with no rescue ({@code cordon-nouns.md}): the
     * director is bore-sourced from the first tick over {@code bores}, which
     * setup has already opened, and nothing seeps in at the perimeter. With no
     * cohort there is no rescue to end it; it spawns while a bore lives.
     */
    public boolean configureFromBores(SwarmBores bores, long seed, BattleSimulation sim) {
        if (configured || bores == null || sim == null) return false;
        this.seed = seed;
        this.targetPopulation = borePopulationTarget(bores.bores());
        this.populationFloor = 1;
        this.waveSize = MIN_WAVE_SIZE;
        configured = true;
        beginBoreSourcing(bores);
        refreshSnapshot(sim, "OPENING_BORES");
        return true;
    }

    public void tick(float dt, BattleSimulation sim) {
        if (!configured) return;
        if (snapshot == null) {
            ownedWave = captureOpeningWave(sim);
            refreshSnapshot(sim, "OPENING_PERIMETER");
        }
        if (placement != null && (tracker.isSealed() || tracker.activeCount() == 0)) {
            refreshSnapshot(sim, "OBJECTIVE_TERMINAL");
            return;
        }
        if (boreSourcing) {
            refreshSnapshot(sim, tickBores(sim));
            return;
        }
        accumulator = Math.min(WAVE_INTERVAL_SECONDS, accumulator + Math.max(0f, dt));
        int live = liveRunnerCount(sim);
        int floor = activeFloor();
        if (live >= floor || accumulator < WAVE_INTERVAL_SECONDS) {
            refreshSnapshot(sim, live < floor
                    ? "REASSEMBLING_WAVE"
                    : seepage ? "PERIMETER_SEEPAGE"
                    : sim.isCivilianShelterProtected()
                    ? "OPENING_SCREEN" : "PRESSURE_COHORT_SCREEN");
            return;
        }

        int ceiling = seepage ? floor : targetPopulation;
        int requested = Math.min(waveSize, ceiling - live);
        if (requested <= 0) {
            refreshSnapshot(sim, "PRESSURE_POPULATION_MET");
            return;
        }
        List<SwarmPressureSnapshot.WaveIntent> spawned = spawnWave(sim, requested);
        accumulator = 0f;
        if (!spawned.isEmpty()) {
            waveIndex++;
            ownedWave = List.copyOf(spawned);
        }
        refreshSnapshot(sim, spawned.isEmpty()
                ? "NO_LEGAL_PERIMETER_ENTRY" : "RESTORE_PRESSURE_FLOOR");
    }

    public boolean isConfigured() {
        return configured;
    }

    /** Starts the opening perimeter seepage: the floor is restored only to a fraction of itself. */
    public void beginSeepage() {
        seepage = true;
    }

    /** Ends the seepage; the director restores its full floor from here on. Idempotent. */
    public void endSeepage() {
        seepage = false;
    }

    /** Whether the director is still in its opening seepage. */
    public boolean isSeeping() {
        return seepage;
    }

    /**
     * From here on every body comes out of a living bore in {@code bores}; the
     * perimeter is closed. Called at the tremor. Idempotent.
     */
    public void beginBoreSourcing(SwarmBores bores) {
        if (bores == null) throw new IllegalArgumentException("bore sourcing needs the battle's bores");
        this.bores = bores;
        boreSourcing = true;
        seepage = false;
    }

    /**
     * Holds the bore-sourced field at {@code share} of the living bores'
     * capacities: the Cordon's ordinary pressure between tides, the whole of
     * it while a tide runs ({@code cordon-nouns.md}). Surges already leaving
     * their mouths finish.
     */
    public void setPressureShare(float share) {
        if (!(share > 0f && share <= 1f)) throw new IllegalArgumentException("pressure share in (0, 1]: " + share);
        pressureShare = share;
    }

    public float pressureShare() { return pressureShare; }

    /** Whether spawning comes only from bores. */
    public boolean isBoreSourcing() {
        return boreSourcing;
    }

    /**
     * The population target the living bores hold the field at: their
     * capacities summed. A sleeping bore holds none of the field: the bodies it
     * would send are not on it yet, and the bores awake do not field them for it.
     */
    public static int borePopulationTarget(List<SwarmBore> bores) {
        int target = 0;
        for (SwarmBore bore : bores) if (!bore.collapsed() && !bore.dormant()) target += bore.tier().capacity;
        return target;
    }

    /**
     * One bore-sourced tick: streams the surges already leaving their mouths,
     * then starts a surge at each living bore whose clock is due and whose
     * pulse fits under the target. Returns the snapshot reason.
     */
    private String tickBores(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        List<SwarmBore> all = bores.bores();
        boreTarget = Math.round(borePopulationTarget(all) * pressureShare);
        boolean released = false;
        for (int i = 0; i < releases.size(); ) {
            Release release = releases.get(i);
            if (release.bore.collapsed()) {
                releases.remove(i);
                continue;
            }
            if (tick >= release.nextTick && releaseClutch(sim, release, tick)) released = true;
            if (release.released >= release.size) releases.remove(i);
            else i++;
        }
        float live = liveWeight(sim);
        float inFlight = 0f;
        for (Release release : releases) inFlight += release.weightLeft();
        boolean surged = false;
        // With no rescue every bore presses its own stretch of front, so headroom goes to the bore that
        // has surged least rather than to whichever is listed first: listed first, the west end of a
        // Cordon's front took nearly all of it.
        List<SwarmBore> order = all;
        if (placement == null) {
            order = new ArrayList<>(all);
            order.sort(Comparator.comparingInt(SwarmBore::surges).thenComparingInt(SwarmBore::number));
        }
        for (SwarmBore bore : order) {
            if (!bore.surgeDue(tick) || releasing(bore)) continue;
            float headroom = boreTarget - live - inFlight;
            int size = bore.surgeSize();
            // Held back to a share, a big bore's pulse would never fit and only the small bores would
            // ever surge; with no rescue it goes out at the share's size instead.
            float fit = placement == null ? SURGE_MIN_FIT * pressureShare : SURGE_MIN_FIT;
            if (headroom < Math.max(1, (int) Math.ceil(size * fit))) {
                bore.deferSurge(tick + SURGE_RECHECK_TICKS);
                continue;
            }
            List<Planned> planned = plan(bore, bore.surges(), Math.min(size, headroom));
            float bodies = 0f;
            for (Planned body : planned) bodies += body.weight();
            if (!surged) surgeWave = new ArrayList<>();
            Release release = new Release(bore, bore.surges(), planned, tick);
            // every second surge goes out to fetch the swarm's dead, while there are dead to fetch
            release.scavengers = bore.surges() % 2 == 1
                    && sim.swarmHarvest().recyclableCount(sim) >= SwarmHarvest.SCAVENGER_PACK_MIN_DEAD;
            releases.add(release);
            float jitter = 2f * SwarmBores.unit(SwarmBores.mix(
                    seed ^ (bore.number() * 0x9E3779B97F4A7C15L) ^ (bore.surges() * 0xC2B2AE3D27D4EB4FL))) - 1f;
            bore.surged(tick, jitter);
            bores.surged(bore, tick);
            inFlight += bodies;
            waveIndex++;
            surged = true;
        }
        if (surged || released) ownedWave = List.copyOf(surgeWave);
        if (surged || released) return "BORE_SURGE";
        if (all.isEmpty()) return "AWAITING_ERUPTION";
        if (bores.livingCount() == 0) return "NO_LIVING_BORE";
        if (live >= boreTarget) return "PRESSURE_POPULATION_MET";
        return releases.isEmpty() ? "BORES_QUIET" : "BORE_SURGE";
    }

    /**
     * The bodies of one surge: drawn body by body, strain and stage each keyed
     * by the bore, the pulse and the body's index, until the surge's weight
     * reaches {@code budget} (`colony-breach-nouns.md`, Age mix: the director
     * counts weight, so a fed bore sends fewer, older bodies). A bore that owes
     * its Matriarch sends her first; otherwise the pulse's first elder, its pack
     * leader, is moved to the head of the stream.
     */
    List<Planned> plan(SwarmBore bore, int surgeIndex, float budget) {
        BoreTier tier = bore.tier();
        boolean owed = BoreStrainTable.owesMatriarch(bore.mother(), tier, bore.matriarchReleased());
        int[] mix = SwarmAgeMix.weights(tier, bore.biomassFed());
        List<Planned> bodies = new ArrayList<>();
        float weight = 0f;
        for (int i = 0; weight < budget; i++) {
            long roll = SwarmBores.mix(seed ^ (bore.number() * 0x9E3779B97F4A7C15L)
                    ^ ((long) surgeIndex << 20) ^ i);
            boolean matriarch = owed && i == 0;
            BoreStrainTable.Slot slot = BoreStrainTable.pick(tier, roll, matriarch);
            SwarmStage stage = !growthStages ? SwarmStage.ADULT
                    : matriarch ? SwarmStageStats.matriarchStage((int) bore.biomassFed())
                    : SwarmAgeMix.pick(mix, SwarmAgeMix.roll(seed, bore.number(), surgeIndex, i));
            Planned body = new Planned(slot, stage, matriarch);
            bodies.add(body);
            weight += body.weight();
        }
        if (!owed) {
            for (int i = 1; i < bodies.size(); i++) {
                if (bodies.get(i).stage() == SwarmStage.ELDER) {
                    bodies.add(0, bodies.remove(i));
                    break;
                }
            }
        }
        return bodies;
    }

    private boolean releasing(SwarmBore bore) {
        for (Release release : releases) if (release.bore == bore) return true;
        return false;
    }

    /**
     * Lets the next clutch of a surge break out of the hole: one to {@link #CLUTCH_MAX} bodies at once, seeded by
     * the bore, the pulse and the clutch, then a seeded pause (`colony-breach-nouns.md`, Emergence). The Matriarch
     * comes out alone. With emergence off, one body every {@link #SURGE_RELEASE_SPACING_TICKS}.
     */
    private boolean releaseClutch(BattleSimulation sim, Release release, int tick) {
        if (!sim.swarmEmergence().enabled()) {
            release.nextTick = tick + SURGE_RELEASE_SPACING_TICKS;
            return releaseOne(sim, release, tick);
        }
        long roll = SwarmBores.mix(seed ^ (release.bore.number() * 0xD1B54A32D192ED03L)
                ^ ((long) release.surgeIndex << 24) ^ release.clutches);
        release.clutches++;
        int clutch = release.bodies.get(release.released).matriarch() ? 1 : 1 + (int) ((roll >>> 8) % CLUTCH_MAX);
        release.nextTick = tick + CLUTCH_GAP_MIN_TICKS
                + (int) ((roll >>> 32) % (CLUTCH_GAP_MAX_TICKS - CLUTCH_GAP_MIN_TICKS + 1));
        boolean any = false;
        for (int i = 0; i < clutch && release.released < release.size; i++) {
            if (i > 0 && release.bodies.get(release.released).matriarch()) break;
            if (!releaseOne(sim, release, tick)) break;
            any = true;
        }
        // A choked hole tries again soon rather than after a whole pause.
        if (!any) release.nextTick = tick + SURGE_RELEASE_SPACING_TICKS;
        return any;
    }

    /** Puts the next body of a surge on the first free mouth cell (the hole first, as it emerges); false for none. */
    private boolean releaseOne(BattleSimulation sim, Release release, int tick) {
        NavigationGrid grid = sim.getGrid();
        byte[] occupancy = sim.getOccupancyMap();
        List<int[]> mouth = SwarmBores.mouthCells(grid, release.bore);
        SwarmEmergence emergence = sim.swarmEmergence();
        int[] cell = null;
        int hole = emergence.enabled() ? Math.min(HOLE_CELLS, mouth.size()) : 0;
        for (int i = 0; i < hole && cell == null; i++) {
            int[] candidate = mouth.get((release.released + i) % hole);
            if ((occupancy[grid.index(candidate[0], candidate[1])] & 0xFF) == 0) cell = candidate;
        }
        for (int i = 0; i < mouth.size() && cell == null; i++) {
            int[] candidate = mouth.get((release.released + i) % mouth.size());
            if ((occupancy[grid.index(candidate[0], candidate[1])] & 0xFF) == 0) cell = candidate;
        }
        if (cell == null) return false;
        SwarmBore bore = release.bore;
        Planned planned = release.bodies.get(release.released);
        BoreStrainTable.Slot slot = planned.slot();
        String strain = slot.name().charAt(0) + slot.name().substring(1).toLowerCase();
        long body = SwarmStrains.spawn(sim, "Bore " + release.bore.beatKey() + " " + strain + " "
                + (release.bore.bodiesSpawned() + 1), slot.unitType, cell[0], cell[1], planned.stage());
        // The pulse is one pack, or several of at most eight: each opens on its first body, which
        // leads it if it is an elder or the Matriarch (the pulse's first elder was moved to its head).
        if (SwarmPacks.startsPack(release.released, release.size)) {
            release.pack = planned.matriarch() || planned.stage() == SwarmStage.ELDER
                    ? sim.swarmPacks().open(body) : sim.swarmPacks().openLeaderless(body);
        } else {
            sim.swarmPacks().join(release.pack, body);
        }
        if (emergence.enabled()) {
            emergence.begin(sim, body, bore.x() + 0.5f, bore.y() + 0.5f);
            if (SwarmPacks.startsPack(release.released, release.size)) {
                release.muster = emergence.openMuster(sim, bore.x(), bore.y());
            }
            if (slot.unitType == UnitType.SWARM_RUNNER) emergence.joinMuster(sim, release.muster, body);
            if (release.released + 1 >= release.size || SwarmPacks.startsPack(release.released + 1, release.size)) {
                emergence.closeMuster(release.muster, tick
                        + Math.round(SwarmEmergence.emergeSeconds(slot.unitType) / BattleSimulation.TICK_DT));
            }
        }
        release.released++;
        release.bore.released(planned.stage());
        if (release.scavengers && slot.unitType == UnitType.SWARM_RUNNER) sim.swarmHarvest().markScavenger(body);
        if (planned.matriarch()) bores.matriarchEmerged(bore, tick, body);
        SwarmPressureSnapshot.Approach approach = approach(cell[0], cell[1], grid.getWidth(), grid.getHeight());
        approachByRunner.put(body, approach);
        surgeWave.add(new SwarmPressureSnapshot.WaveIntent(body, approach, cell[0], cell[1],
                SwarmPressureSnapshot.TargetContext.ROAMING, "BORE_SURGE_" + release.bore.beatKey()));
        return true;
    }

    private int activeFloor() {
        return seepage
                ? Math.max(1, (int) Math.ceil(populationFloor * SEEPAGE_FLOOR_FRACTION))
                : populationFloor;
    }

    public int targetPopulation() {
        return targetPopulation;
    }

    public SwarmPressureSnapshot snapshot() { return snapshot; }

    private List<SwarmPressureSnapshot.WaveIntent> spawnWave(
            BattleSimulation sim, int requested) {
        NavigationGrid grid = sim.getGrid();
        boolean[] reachable = SwarmDefenseRoster.reachableFromShelter(grid, placement);
        byte[] occupancy = sim.getOccupancyMap();
        List<Integer> candidates = new ArrayList<>();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                int edgeDistance = Math.min(Math.min(x, grid.getWidth() - 1 - x),
                        Math.min(y, grid.getHeight() - 1 - y));
                int cell = grid.index(x, y);
                if (edgeDistance > PERIMETER_BAND_CELLS
                        || !reachable[cell]
                        || (occupancy[cell] & 0xFF) != 0
                        || SwarmDefenseRoster.insideShelterZone(x, y, placement)
                        || SwarmDefenseRoster.insideLiftZone(x, y, placement)
                        || tooCloseToProtectedUnit(x, y, sim)) {
                    continue;
                }
                candidates.add(cell);
            }
        }
        long waveSeed = seed ^ ((long) waveIndex * 0x9E3779B97F4A7C15L);
        candidates.sort(Comparator
                .comparingLong((Integer cell) -> priority(waveSeed, cell))
                .thenComparingInt(Integer::intValue));

        List<Integer> selected = selectAcrossApproaches(candidates, requested,
                grid.getWidth(), grid.getHeight());
        List<SwarmPressureSnapshot.WaveIntent> intents = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            int cell = selected.get(i);
            int x = cell % grid.getWidth();
            int y = cell / grid.getWidth();
            long runner = sim.spawn(new EntitySpec(
                    "Roving Swarm Runner " + (waveIndex * waveSize + i + 1),
                    Faction.DEFENDER, UnitType.SWARM_RUNNER, x, y)
                    .role(UnitRole.SWARM_PRESSURE));
            intents.add(new SwarmPressureSnapshot.WaveIntent(runner,
                    approach(x, y, grid.getWidth(), grid.getHeight()), x, y,
                    sim.isCivilianShelterProtected()
                            ? SwarmPressureSnapshot.TargetContext.ROAMING
                            : SwarmPressureSnapshot.TargetContext.MARINE_SCREEN,
                    "RESTORE_PRESSURE_FLOOR"));
            approachByRunner.put(runner,
                    approach(x, y, grid.getWidth(), grid.getHeight()));
        }
        return List.copyOf(intents);
    }

    private List<Integer> selectAcrossApproaches(
            List<Integer> candidates, int requested, int width, int height) {
        Map<SwarmPressureSnapshot.Approach, List<Integer>> byApproach =
                new EnumMap<>(SwarmPressureSnapshot.Approach.class);
        for (SwarmPressureSnapshot.Approach approach
                : SwarmPressureSnapshot.Approach.values()) {
            byApproach.put(approach, new ArrayList<>());
        }
        for (int cell : candidates) {
            byApproach.get(approach(cell % width, cell / width, width, height))
                    .add(cell);
        }
        int[] cursors = new int[SwarmPressureSnapshot.Approach.values().length];
        List<Integer> selected = new ArrayList<>();
        int start = Math.floorMod(waveIndex,
                SwarmPressureSnapshot.Approach.values().length);
        boolean progressed = true;
        while (selected.size() < requested && progressed) {
            progressed = false;
            for (int offset = 0;
                 offset < SwarmPressureSnapshot.Approach.values().length
                         && selected.size() < requested; offset++) {
                SwarmPressureSnapshot.Approach side =
                        SwarmPressureSnapshot.Approach.values()[
                                (start + offset)
                                        % SwarmPressureSnapshot.Approach.values().length];
                List<Integer> cells = byApproach.get(side);
                int cursor = cursors[side.ordinal()];
                while (cursor < cells.size()
                        && !spacedFromSelected(cells.get(cursor), selected,
                        width)) cursor++;
                cursors[side.ordinal()] = cursor + 1;
                if (cursor >= cells.size()) continue;
                selected.add(cells.get(cursor));
                progressed = true;
            }
        }
        return selected;
    }

    private List<SwarmPressureSnapshot.WaveIntent> captureOpeningWave(
            BattleSimulation sim) {
        List<SwarmPressureSnapshot.WaveIntent> result = new ArrayList<>();
        int width = sim.getGrid().getWidth();
        int height = sim.getGrid().getHeight();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long runner = sim.liveUnitAt(i);
            if (!isOwnedRunner(runner, sim)) continue;
            int x = sim.world().cellX(runner);
            int y = sim.world().cellY(runner);
            result.add(new SwarmPressureSnapshot.WaveIntent(runner,
                    approach(x, y, width, height), x, y,
                    SwarmPressureSnapshot.TargetContext.ROAMING,
                    "OPENING_PERIMETER"));
            approachByRunner.put(runner, approach(x, y, width, height));
        }
        result.sort(Comparator.comparingLong(
                SwarmPressureSnapshot.WaveIntent::runnerId));
        return List.copyOf(result);
    }

    private void refreshSnapshot(BattleSimulation sim, String reason) {
        SwarmPressureSnapshot.Phase phase = placement != null && (tracker.isSealed()
                || tracker.activeCount() == 0)
                ? SwarmPressureSnapshot.Phase.TERMINAL
                : sim.isCivilianShelterProtected()
                ? SwarmPressureSnapshot.Phase.OPENING
                : SwarmPressureSnapshot.Phase.COHORT_RELEASED;
        Map<SwarmPressureSnapshot.Approach, Integer> approaches =
                new EnumMap<>(SwarmPressureSnapshot.Approach.class);
        Map<SwarmPressureSnapshot.TargetContext, Integer> targets =
                new EnumMap<>(SwarmPressureSnapshot.TargetContext.class);
        int live = 0;
        int[] byStage = new int[SwarmStage.values().length];
        int width = sim.getGrid().getWidth();
        int height = sim.getGrid().getHeight();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long runner = sim.liveUnitAt(i);
            if (!isOwnedRunner(runner, sim)) continue;
            live++;
            byStage[sim.identity().swarmStage(runner).ordinal()]++;
            SwarmPressureSnapshot.Approach assigned =
                    approachByRunner.computeIfAbsent(runner,
                            ignored -> approach(sim.world().cellX(runner),
                                    sim.world().cellY(runner), width, height));
            approaches.merge(assigned, 1, Integer::sum);
            targets.merge(targetContext(runner, sim), 1, Integer::sum);
        }
        List<SwarmPressureSnapshot.ApproachState> approachStates =
                new ArrayList<>();
        for (SwarmPressureSnapshot.Approach side
                : SwarmPressureSnapshot.Approach.values()) {
            int waveRunners = 0;
            for (SwarmPressureSnapshot.WaveIntent intent : ownedWave) {
                if (intent.approach() == side
                        && sim.resolveUnit(intent.runnerId()) != 0L) {
                    waveRunners++;
                }
            }
            approachStates.add(new SwarmPressureSnapshot.ApproachState(side,
                    approaches.getOrDefault(side, 0), waveRunners));
        }
        List<SwarmPressureSnapshot.TargetState> targetStates = new ArrayList<>();
        for (SwarmPressureSnapshot.TargetContext context
                : SwarmPressureSnapshot.TargetContext.values()) {
            targetStates.add(new SwarmPressureSnapshot.TargetState(context,
                    targets.getOrDefault(context, 0)));
        }
        List<SwarmPressureSnapshot.BoreState> boreStates = boreStates(sim);
        StringBuilder boreSignature = new StringBuilder();
        for (SwarmPressureSnapshot.BoreState bore : boreStates) {
            boreSignature.append(bore.key()).append(':').append(bore.tier()).append(':')
                    .append(bore.alive()).append(':').append(bore.surges()).append(':')
                    .append(bore.feeds()).append(':').append((int) (bore.integrity() * 10f)).append(':')
                    .append(bore.sporeColonies()).append(';');
        }
        SwarmPressureSnapshot.StageCounts living = SwarmPressureSnapshot.StageCounts.of(byStage);
        String signature = phase + "|" + reason + "|" + live + "|" + living + "|"
                + waveIndex + "|" + approachStates + "|" + targetStates
                + "|" + ownedWave + "|" + boreSignature;
        if (!signature.equals(snapshotSignature)) {
            snapshotSignature = signature;
            snapshotRevision++;
        }
        snapshot = new SwarmPressureSnapshot(snapshotRevision,
                sim.getSimTickIndex(), phase, reason,
                boreSourcing ? boreTarget : targetPopulation,
                populationFloor, live, waveIndex, approachStates,
                targetStates, ownedWave, boreSourcing, boreStates, living);
    }

    private List<SwarmPressureSnapshot.BoreState> boreStates(BattleSimulation sim) {
        if (bores == null) return List.of();
        List<SwarmPressureSnapshot.BoreState> states = new ArrayList<>();
        for (SwarmBore bore : bores.bores()) {
            long id = bore.entityId();
            boolean alive = !bore.collapsed() && sim.resolveUnit(id) != 0L;
            float integrity = 0f;
            if (alive) {
                StructureDef structure = sim.turretState().structure(id);
                integrity = (sim.world().hp(id) + sim.world().armor(id))
                        / (structure.maxStructure + structure.armorCapacity);
            }
            states.add(new SwarmPressureSnapshot.BoreState(bore.beatKey(), bore.mother(), id,
                    bore.x(), bore.y(), bore.tier(), alive, integrity, bore.growth(),
                    bore.biomassFed(), bore.feeds(), bore.surges(), bore.bodiesSpawned(),
                    SwarmBores.livingColonies(sim, bore), bore.nextSurgeTick(), bore.eruptedTick(), bore.collapsedTick(),
                    SwarmPressureSnapshot.StageCounts.of(growthStages
                            ? SwarmAgeMix.weights(bore.tier(), bore.biomassFed()) : new int[]{0, 100, 0}),
                    new SwarmPressureSnapshot.StageCounts(bore.releasedAt(SwarmStage.WHELP),
                            bore.releasedAt(SwarmStage.ADULT), bore.releasedAt(SwarmStage.ELDER))));
        }
        return states;
    }

    private SwarmPressureSnapshot.TargetContext targetContext(
            long runner, BattleSimulation sim) {
        long target = sim.resolveUnit(sim.combat().targetId(runner));
        if (target == 0L) return SwarmPressureSnapshot.TargetContext.ROAMING;
        if (tracker.state(target) == CivilianEvacuationTracker.State.ACTIVE) {
            return SwarmPressureSnapshot.TargetContext.COHORT_CONTACT;
        }
        return SwarmPressureSnapshot.TargetContext.MARINE_SCREEN;
    }

    private static boolean isOwnedRunner(long unit, BattleSimulation sim) {
        return sim.identity().faction(unit) == Faction.DEFENDER
                && sim.identity().type(unit).isSwarm() && !sim.identity().type(unit).isSwarmWorker()
                && sim.role().role(unit) == UnitRole.SWARM_PRESSURE;
    }

    private static SwarmPressureSnapshot.Approach approach(
            int x, int y, int width, int height) {
        int north = y;
        int east = width - 1 - x;
        int south = height - 1 - y;
        int west = x;
        int minimum = Math.min(Math.min(north, east), Math.min(south, west));
        if (north == minimum) return SwarmPressureSnapshot.Approach.NORTH;
        if (east == minimum) return SwarmPressureSnapshot.Approach.EAST;
        if (south == minimum) return SwarmPressureSnapshot.Approach.SOUTH;
        return SwarmPressureSnapshot.Approach.WEST;
    }

    private boolean tooCloseToProtectedUnit(int x, int y, BattleSimulation sim) {
        int civilianDistanceSquared = MIN_CIVILIAN_ENTRY_DISTANCE
                * MIN_CIVILIAN_ENTRY_DISTANCE;
        for (int i = 0, n = tracker.registeredCount(); i < n; i++) {
            long civilian = tracker.entityIdAt(i);
            if (tracker.state(civilian) != CivilianEvacuationTracker.State.ACTIVE
                    || sim.resolveUnit(civilian) == 0L) continue;
            if (distanceSquared(x, y, civilian, sim) < civilianDistanceSquared) return true;
        }
        int marineDistanceSquared = MIN_MARINE_ENTRY_DISTANCE
                * MIN_MARINE_ENTRY_DISTANCE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE) continue;
            if (distanceSquared(x, y, unit, sim) < marineDistanceSquared) return true;
        }
        return false;
    }

    /**
     * The living swarm's weight against the population target: each body
     * counts its stage's weight, so an elder fills more of the target than a
     * whelp. With every body an adult it is the head count. Skitterers are not
     * counted (`colony-breach-nouns.md`, Skitterer).
     */
    private static float liveWeight(BattleSimulation sim) {
        float weight = 0f;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == Faction.DEFENDER && fights(sim.identity().type(unit))) {
                weight += sim.identity().swarmStage(unit).populationWeight;
            }
        }
        return weight;
    }

    private static int liveRunnerCount(BattleSimulation sim) {
        int count = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == Faction.DEFENDER && fights(sim.identity().type(unit))) count++;
        }
        return count;
    }

    /** A swarm body the population counts: every strain but the skitterer, which its bore broods under its own cap. */
    private static boolean fights(UnitType type) {
        return type.isSwarm() && !type.isSwarmWorker();
    }

    private static float distanceSquared(int x, int y, long unit,
                                         BattleSimulation sim) {
        float dx = x + 0.5f - sim.world().x(unit);
        float dy = y + 0.5f - sim.world().y(unit);
        return dx * dx + dy * dy;
    }

    private static boolean spacedFromSelected(int cell, List<Integer> selected,
                                              int width) {
        int x = cell % width;
        int y = cell / width;
        for (int other : selected) {
            int dx = x - other % width;
            int dy = y - other / width;
            if (dx * dx + dy * dy < MIN_RUNNER_SPACING * MIN_RUNNER_SPACING) {
                return false;
            }
        }
        return true;
    }

    private static long priority(long seed, int cell) {
        long value = seed ^ (cell * 0x9E3779B97F4A7C15L);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}

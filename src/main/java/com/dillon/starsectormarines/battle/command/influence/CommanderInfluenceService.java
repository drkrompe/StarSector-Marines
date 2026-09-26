package com.dillon.starsectormarines.battle.command.influence;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.IdentityService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Captures each faction's beliefs serially and publishes their immutable fields
 * together. Lifecycle, demand and metrics reads are simulation-thread confined;
 * only captured values and worker counters cross into the propagation worker.
 */
public final class CommanderInfluenceService implements AutoCloseable {

    public static final String ASYNC_PROPERTY = "battle.influence.async";
    public static final int BLOCK_SIZE = 8;
    public static final int UPDATE_INTERVAL_TICKS = 15;

    private final NavigationGrid grid;
    private final UnitRosterService roster;
    private final LongSupplier topologyRevision;
    private final CasualtyMemory casualties;
    private InfluenceTopology cachedTopology;
    private long cachedTopologyRevision = Long.MIN_VALUE;
    private volatile SnapshotPair snapshots;
    private int lastUpdateTick = Integer.MIN_VALUE;
    private final ExecutorService worker;
    private Pending pending;
    private boolean activated;
    private boolean closed;
    private int lastBoundaryTick = Integer.MIN_VALUE;
    private int lastSubmissionTick = Integer.MIN_VALUE;
    private long submitted, published, discarded, failed;
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong workerNanos = new AtomicLong();
    private final AtomicLong maxWorkerNanos = new AtomicLong();

    public record Metrics(boolean asynchronous, long submitted, long completed,
                          long published, long discarded, long failed,
                          long workerNanos, long maxWorkerNanos,
                          boolean inFlight, int publishedCaptureTick) { }

    private record SnapshotPair(CommanderInfluenceSnapshot marine,
                                CommanderInfluenceSnapshot defender) { }
    private record Sources(Faction faction, List<InfluenceSource> friendly,
                           List<InfluenceSource> hostile, float[] losses,
                           List<CommanderContact> contacts) { }
    private record Capture(int tick, long topologyRevision,
                           InfluenceTopology topology, int width, int height,
                           Sources marine, Sources defender) { }
    private record Pending(Capture capture, BuildTask task) { }

    private static final class BuildTask extends FutureTask<SnapshotPair> {
        private volatile boolean ready;

        private BuildTask(Callable<SnapshotPair> computation) { super(computation); }

        @Override
        protected void done() { ready = true; }
    }

    public CommanderInfluenceService(NavigationGrid grid, UnitRosterService roster) {
        this(grid, roster, () -> 0L);
    }

    public CommanderInfluenceService(NavigationGrid grid, UnitRosterService roster,
                                     LongSupplier topologyRevision) {
        this(grid, roster, topologyRevision, (ExecutorService) null);
    }

    public CommanderInfluenceService(NavigationGrid grid, UnitRosterService roster,
                                     LongSupplier topologyRevision, boolean asynchronous) {
        this(grid, roster, topologyRevision, asynchronous ? newWorker() : null);
    }

    CommanderInfluenceService(NavigationGrid grid, UnitRosterService roster,
                              LongSupplier topologyRevision, ExecutorService worker) {
        this.grid = grid;
        this.roster = roster;
        this.worker = worker;
        this.topologyRevision = Objects.requireNonNull(
                topologyRevision, "topologyRevision");
        int width = (grid.getWidth() + BLOCK_SIZE - 1) / BLOCK_SIZE;
        int height = (grid.getHeight() + BLOCK_SIZE - 1) / BLOCK_SIZE;
        this.casualties = new CasualtyMemory(roster, BLOCK_SIZE,
                grid.getWidth(), grid.getHeight());
        snapshots = new SnapshotPair(emptySnapshot(Faction.MARINE, width, height),
                emptySnapshot(Faction.DEFENDER, width, height));
    }

    private static ExecutorService newWorker() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), task -> {
                    Thread thread = new Thread(task, "BattleSim-Influence");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    /** The loss memory this service publishes; subscribe it to the death dispatcher. */
    public CasualtyMemory casualties() { return casualties; }

    /**
     * Serial per-tick advance of the loss memory alone. Kept off
     * {@link #tick(int)} because that one is called lazily by whoever wants a
     * snapshot, and the loss memory is read by every mover's repath rather
     * than by a diagnostic - it has to age on the clock whether or not anybody
     * asks for the influence field. Cheap by construction: a decay over the
     * block grid and one expansion per side that has lost anybody, with none
     * of the topology propagation a snapshot costs.
     */
    public void advanceCasualties(int simTick) {
        casualties.advance(simTick);
    }

    public void tick(int simTick) {
        if (closed) return;
        if (worker != null) {
            // Activate recurring refreshes on first demand. Sparse commander
            // pulses must not keep receiving the previous pulse's old input.
            // A read never publishes in the middle of planning.
            activated = true;
            return;
        }
        if (lastUpdateTick != Integer.MIN_VALUE
                && simTick - lastUpdateTick < UPDATE_INTERVAL_TICKS) return;
        refresh(simTick);
    }

    /** Serial boundary after current beliefs/losses, before command and GOAP. Never waits. */
    public void advanceAsync(int simTick) {
        if (closed || worker == null || lastBoundaryTick == simTick) return;
        lastBoundaryTick = simTick;
        if (pending != null && pending.task().ready) {
            Pending finished = pending;
            pending = null;
            try {
                // FutureTask.isDone also admits its transient COMPLETING state,
                // where get can still wait. done() marks the terminal state.
                SnapshotPair pair = finished.task().get();
                if (finished.capture().topologyRevision() == topologyRevision.getAsLong()) {
                    snapshots = pair;
                    lastUpdateTick = finished.capture().tick();
                    published++;
                } else {
                    discarded++;
                    // Keep the cadence even under repeated demolition: stale
                    // results cannot turn into a full topology capture per tick.
                }
            } catch (CancellationException e) {
                discarded++;
            } catch (ExecutionException e) {
                failed++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failed++;
                return;
            }
        }
        if (pending != null || !activated) return;
        if (lastSubmissionTick != Integer.MIN_VALUE
                && simTick - lastSubmissionTick < UPDATE_INTERVAL_TICKS) return;
        Capture capture = capture(simTick);
        BuildTask task = new BuildTask(() -> {
            long start = System.nanoTime();
            try {
                return buildPair(capture, null);
            } finally {
                long elapsed = System.nanoTime() - start;
                workerNanos.addAndGet(elapsed);
                maxWorkerNanos.accumulateAndGet(elapsed, Math::max);
                completed.incrementAndGet();
            }
        });
        lastSubmissionTick = simTick;
        pending = new Pending(capture, task);
        try {
            worker.execute(task);
            submitted++;
        } catch (RejectedExecutionException e) {
            pending = null;
            failed++;
        }
    }

    /** Immediate deterministic rebuild used by the fixed cadence and focused tests. */
    public void refresh(int simTick) {
        if (closed) return;
        cancelPending();
        snapshots = buildPair(capture(simTick), TickInnerProfile.currentIfBound());
        lastUpdateTick = simTick;
        lastSubmissionTick = simTick;
    }

    private Capture capture(int simTick) {
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        long start = System.nanoTime();
        InfluenceTopology topology = topologyForCurrentRevision(profile);
        if (profile != null) {
            profile.record(TickInnerProfile.Bucket.INFLUENCE_TOPOLOGY_LOOKUP,
                    System.nanoTime() - start);
        }
        return new Capture(simTick, cachedTopologyRevision, topology,
                grid.getWidth(), grid.getHeight(),
                captureSources(Faction.MARINE, profile),
                captureSources(Faction.DEFENDER, profile));
    }

    private InfluenceTopology topologyForCurrentRevision(TickInnerProfile profile) {
        long revision = topologyRevision.getAsLong();
        if (cachedTopology == null || cachedTopologyRevision != revision) {
            long rebuildStart = System.nanoTime();
            cachedTopology = new InfluenceTopology(grid, BLOCK_SIZE);
            if (profile != null) {
                profile.record(TickInnerProfile.Bucket.INFLUENCE_TOPOLOGY_REBUILD,
                        System.nanoTime() - rebuildStart);
            }
            cachedTopologyRevision = revision;
        }
        return cachedTopology;
    }

    public CommanderInfluenceSnapshot snapshot(Faction faction) {
        SnapshotPair pair = snapshots;
        if (faction == Faction.MARINE) return pair.marine();
        if (faction == Faction.DEFENDER) return pair.defender();
        return null;
    }

    private CommanderInfluenceSnapshot emptySnapshot(Faction faction, int width, int height) {
        return new CommanderInfluenceSnapshot(faction, -1, BLOCK_SIZE,
                width, height, grid.getWidth(), grid.getHeight(),
                new float[width * height], new float[width * height],
                new float[width * height], List.of());
    }

    private Sources captureSources(Faction faction, TickInnerProfile profile) {
        long start = System.nanoTime();
        List<InfluenceSource> friendlySources = friendlySources(faction);
        List<CommanderContact> contacts = aggregateContacts(faction);
        List<InfluenceSource> hostileSources = new ArrayList<>(contacts.size());
        for (CommanderContact contact : contacts) {
            hostileSources.add(new InfluenceSource(contact.cellX(), contact.cellY(),
                    contact.confidence() * contact.strength()));
        }
        Sources captured = new Sources(faction, List.copyOf(friendlySources),
                List.copyOf(hostileSources), casualties.copyFor(faction),
                List.copyOf(contacts));
        if (profile != null) {
            profile.record(TickInnerProfile.Bucket.INFLUENCE_SOURCES,
                    System.nanoTime() - start);
        }
        return captured;
    }

    private static SnapshotPair buildPair(Capture capture, TickInnerProfile profile) {
        return new SnapshotPair(buildSnapshot(capture, capture.marine(), profile),
                buildSnapshot(capture, capture.defender(), profile));
    }

    private static CommanderInfluenceSnapshot buildSnapshot(
            Capture capture, Sources sources, TickInnerProfile profile) {
        long start = System.nanoTime();
        InfluenceTopology topology = capture.topology();
        float[] friendly = InfluenceFieldBuilder.propagate(topology, sources.friendly());
        float[] hostile = InfluenceFieldBuilder.propagate(topology, sources.hostile());
        if (profile != null) {
            profile.record(TickInnerProfile.Bucket.INFLUENCE_PROPAGATE,
                    System.nanoTime() - start);
        }
        return new CommanderInfluenceSnapshot(sources.faction(), capture.tick(), BLOCK_SIZE,
                topology.blockWidth(), topology.blockHeight(),
                capture.width(), capture.height(),
                friendly, hostile, sources.losses(), sources.contacts());
    }

    public Metrics metrics() {
        return new Metrics(worker != null, submitted, completed.get(), published,
                discarded, failed, workerNanos.get(), maxWorkerNanos.get(),
                pending != null, snapshots.marine().updatedTick());
    }

    private void cancelPending() {
        if (pending == null) return;
        pending.task().cancel(true);
        pending = null;
        discarded++;
        if (worker instanceof ThreadPoolExecutor executor) executor.purge();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        cancelPending();
        if (worker != null) worker.shutdownNow();
    }

    private List<InfluenceSource> friendlySources(Faction faction) {
        List<InfluenceSource> sources = new ArrayList<>();
        World world = roster.world();
        IdentityService identity = roster.identity();
        long[] dense = roster.denseArray();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long unit = dense[i];
            if (identity.faction(unit) != faction || !identity.type(unit).combatant) continue;
            sources.add(new InfluenceSource(world.cellX(unit), world.cellY(unit), 1f));
        }
        return sources;
    }

    private List<CommanderContact> aggregateContacts(Faction faction) {
        Map<Long, CommanderContact> merged = new LinkedHashMap<>();
        IdentityService identity = roster.identity();
        for (Squad squad : roster.getSquads()) {
            if (squad.faction != faction || squad.aliveMembers <= 0) continue;
            for (BelievedContact belief : squad.believedContacts()) {
                if (!roster.isLive(belief.unitId())
                        || identity.faction(belief.unitId()) == faction
                        || !identity.type(belief.unitId()).combatant) {
                    continue;
                }
                CommanderContact candidate = new CommanderContact(
                        belief.unitId(), belief.lastSeenCellX(), belief.lastSeenCellY(),
                        belief.lastSeenTick(), belief.confidence(),
                        commandStrength(identity.type(belief.unitId())),
                        belief.source(), squad.id);
                CommanderContact old = merged.get(candidate.unitId());
                if (old == null || prefer(candidate, old)) {
                    merged.put(candidate.unitId(), candidate);
                }
            }
        }
        List<CommanderContact> contacts = new ArrayList<>(merged.values());
        contacts.sort(Comparator.comparingLong(CommanderContact::unitId));
        return contacts;
    }

    private static float commandStrength(
            com.dillon.starsectormarines.battle.unit.UnitType type) {
        if (type.isMech()) return 8f;
        if (type.isTurret() || type.isDroneHub()) return 4f;
        // A works crew with sidearms is armed and is not a garrison. Counted as
        // riflemen, a manned motor pool and airfield would read to a commander
        // as a dozen more defenders holding the rear.
        if (type.isTechnician()) return 0.25f;
        return 1f;
    }

    private static boolean prefer(CommanderContact candidate, CommanderContact old) {
        int confidence = Float.compare(candidate.confidence(), old.confidence());
        if (confidence != 0) return confidence > 0;
        if (candidate.observedTick() != old.observedTick()) {
            return candidate.observedTick() > old.observedTick();
        }
        return candidate.reporterSquadId() < old.reporterSquadId();
    }
}

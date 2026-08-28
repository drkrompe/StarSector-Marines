package com.dillon.starsectormarines.battle.deployable;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.marine.DeployableCoverSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Data owner for carrier-placed cover screens: the queue of completed
 * placements waiting to be built, the live screens, and each one's remaining
 * time on the field.
 *
 * <h2>Why this is not {@link PointDefenseService} with different numbers</h2>
 * The two deployables share the carried item's channel — commit, freeze the
 * carrier, spend one piece of hardware, enqueue from the parallel dispatch and
 * do every world mutation in the serial {@link #tick} — and they share nothing
 * else, because what they leave behind is not the same kind of thing.
 *
 * <p>A placed emplacement is an <b>actor</b>. It stands on a cell, it is an
 * entity in the roster, it has hit points and armour, it can be shot, and it
 * dies. Everything the shipped deployable foundation supplies — the spawn
 * spec, the structure catalog, {@code UnitRole.STRUCTURE}, the durability
 * pipeline, the death cascade — is machinery for making an actor.
 *
 * <p>A placed cover screen is a <b>property of a boundary</b>. There is no
 * entity: cover in this game is stored per cell per facing, and a barricade is
 * the thing that puts some there. So this service does not spawn anything. It
 * asks {@link MapEditor#placeDeployedBarrier} for a shared-edge feature, which
 * publishes its cover to the two cells the edge joins, and it asks for that
 * feature back when the clock runs out. That is the honest answer to whether
 * the deployable category generalises: the placement channel does, the placed
 * object does not.
 *
 * <h2>The three bounds</h2>
 * <ul>
 *   <li><b>One boundary</b> — a screen covers exactly one of a cell's four
 *       edges, and the facing that reads it is the facing a shot crosses it
 *       from. It cannot make its cell safer from behind.</li>
 *   <li><b>Lifetime</b> — a finite time standing, after which it is retired
 *       through the same removal path an explosion would have used, leaving
 *       the boundary exactly as it was found.</li>
 *   <li><b>Structure</b> — the profile carries a little structure that
 *       blast wall-damage depletes, so a squad that cannot shoot through a
 *       barricade can still blow it apart.</li>
 * </ul>
 *
 * <p>Nothing here makes the grid less permissive. The profile a carried screen
 * may place leaves the navigation transition open, so no path is invalidated,
 * no zone is severed, and a unit cannot be stranded by a placement — which is
 * the condition on which runtime construction is admitted at all. See
 * {@link MapEditor#placeDeployedBarrier}.
 */
public final class DeployedCoverService {

    private final MapEditor mapEditor;
    private final NavigationGrid grid;
    private final ArrayList<PendingPlacement> pending = new ArrayList<>();
    private final ArrayList<LiveScreen> active = new ArrayList<>();
    private final HashMap<Long, Integer> reservedFacing = new HashMap<>();
    private volatile List<ScreenView> snapshot = List.of();
    private long nextId = 1L;

    public DeployedCoverService(MapEditor mapEditor, NavigationGrid grid) {
        this.mapEditor = mapEditor;
        this.grid = grid;
    }

    /**
     * Records a completed placement channel. Called from the parallel dispatch,
     * so it only enqueues; the boundary feature is built at the next serial
     * {@link #tick}.
     */
    public synchronized void queuePlacement(long carrierId, Faction faction,
                                            int cellX, int cellY, Direction facing,
                                            DeployableCoverSpec spec) {
        pending.add(new PendingPlacement(carrierId, faction, cellX, cellY, facing, spec));
    }

    /**
     * Builds queued placements, ages every live screen, drops screens that were
     * destroyed by something else, and retires the ones that ran out of time.
     */
    public synchronized void tick(float dt, BattleControl sim) {
        buildPending();
        for (Iterator<LiveScreen> it = active.iterator(); it.hasNext(); ) {
            LiveScreen screen = it.next();
            // Blown apart by an explosion, or removed by anything else that
            // owns the boundary. The identity is the liveness test: a feature
            // that is no longer the one on that edge is gone, and re-retiring
            // it would take out whatever replaced it.
            if (grid.getEdgeBarrier(screen.cellX, screen.cellY, screen.direction)
                    != screen.barrier) {
                it.remove();
                continue;
            }
            screen.remainingLifetime -= dt;
            if (screen.remainingLifetime <= 0f) {
                mapEditor.retireDeployedBarrier(screen.barrier);
                it.remove();
            }
        }
        publishSnapshot();
    }

    /**
     * Remembers which boundary a carrier committed to while the placement
     * channel runs. The facing is decided when the marine drops to a knee, not
     * when they finish: a barricade half-built against a shooter who dies
     * mid-channel still ends up where the marine was putting it, rather than
     * swinging round to whatever is threatening them a second later.
     */
    public synchronized void reserveFacing(long carrierId, int facing) {
        reservedFacing.put(carrierId, facing);
    }

    /** The committed facing, or {@code -1} when the carrier holds no reservation. */
    public synchronized int reservedFacing(long carrierId) {
        Integer facing = reservedFacing.get(carrierId);
        return facing == null ? -1 : facing;
    }

    public synchronized void releaseFacing(long carrierId) {
        reservedFacing.remove(carrierId);
    }

    /** Live screens, for the renderer and for the carrier's do-not-stack gate. */
    public List<ScreenView> activeScreens() { return snapshot; }

    /**
     * True when the boundary on {@code facing} of cell {@code (cellX, cellY)}
     * already offers cover — from a wall, an authored barrier, a fixture, or a
     * screen somebody already put there. The carrier's opportunity gate reads
     * this so a marine does not build a second barricade on ground that is
     * already covered, and does not try to build one where a feature already
     * stands.
     *
     * <p>Deliberately faction-blind. Cover is a property of the boundary, and
     * a wall the defenders built protects a marine crouched behind it exactly
     * as well as one the marines brought.
     */
    public boolean isCovered(int cellX, int cellY, int facing) {
        return grid.getCoverAtFacing(cellX, cellY, facing) > 0;
    }

    private void buildPending() {
        if (pending.isEmpty()) return;
        for (PendingPlacement placement : pending) {
            SharedEdgeBarrier.Kind kind = kindOf(placement.spec);
            SharedEdgeBarrier barrier = mapEditor.placeDeployedBarrier(
                    placement.cellX, placement.cellY, placement.facing, kind);
            // A declined placement is an ordinary outcome, not an error: the
            // ground may have been claimed between the carrier committing and
            // this pass running. The hardware is already spent, exactly as a
            // pod placed into a spot that turned out to be useless is.
            if (barrier == null) continue;
            active.add(new LiveScreen(nextId++, placement.carrierId, placement.faction,
                    barrier, placement.cellX, placement.cellY, placement.facing,
                    placement.spec.lifetimeSeconds()));
        }
        pending.clear();
    }

    /**
     * Resolves the authored profile name. The catalog already rejected an
     * unknown or movement-blocking profile at load, so a failure here is a
     * programming error rather than bad data.
     */
    private static SharedEdgeBarrier.Kind kindOf(DeployableCoverSpec spec) {
        return SharedEdgeBarrier.Kind.valueOf(spec.barrierKind());
    }

    private void publishSnapshot() {
        ArrayList<ScreenView> views = new ArrayList<>(active.size());
        for (LiveScreen screen : active) {
            views.add(new ScreenView(screen.id, screen.carrierId, screen.faction,
                    screen.barrier.cellX(), screen.barrier.cellY(),
                    screen.barrier.direction(), screen.barrier.midpointX(),
                    screen.barrier.midpointY(), screen.remainingLifetime,
                    screen.totalLifetime));
        }
        snapshot = List.copyOf(views);
    }

    /**
     * Read-only view of one live screen. Reports the canonical edge it occupies
     * rather than the cell the carrier placed it from, because the boundary is
     * what the thing actually is.
     */
    public record ScreenView(long id, long carrierId, Faction faction,
                             int cellX, int cellY, Direction direction,
                             float midpointX, float midpointY,
                             float remainingLifetime, float totalLifetime) {
    }

    private record PendingPlacement(long carrierId, Faction faction, int cellX, int cellY,
                                    Direction facing, DeployableCoverSpec spec) {
    }

    private static final class LiveScreen {
        final long id;
        /** The marine who set it down. Retained so an after-action can name them; the screen has no telemetry of its own. */
        final long carrierId;
        final Faction faction;
        final SharedEdgeBarrier barrier;
        /** The placing cell and facing, kept for the liveness lookup — canonicalization may have moved the identity onto the neighbour. */
        final int cellX;
        final int cellY;
        final Direction direction;
        final float totalLifetime;
        float remainingLifetime;

        LiveScreen(long id, long carrierId, Faction faction, SharedEdgeBarrier barrier,
                   int cellX, int cellY, Direction direction, float lifetime) {
            this.id = id;
            this.carrierId = carrierId;
            this.faction = faction;
            this.barrier = barrier;
            this.cellX = cellX;
            this.cellY = cellY;
            this.direction = direction;
            this.totalLifetime = lifetime;
            this.remainingLifetime = lifetime;
        }
    }
}

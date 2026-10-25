package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.nav.SwarmFlowFields;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitType;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

/**
 * How a body comes up out of a bore (`colony-breach-nouns.md`, Emergence): it claws up out of the hole and is held
 * there a moment, then scatters to a muster spot a few cells out and waits for the rest of its pack.
 *
 * <p>An <em>emerging</em> body is on the field from its first tick (it is counted, sensed and shot as any body
 * is) but does nothing: no move, no blow, no quarry ({@link #emerging}). Its picture rises out of the dark of the
 * hole ({@link #progress}), and the hole throws a ring of dust as it breaks out.
 *
 * <p>A <em>muster</em> is one pack's gathering: a Runner of it that is out of the hole and has nothing to fight
 * stands at its own spot round the head of the pack's lane, until the pack's last body is out or
 * {@link #MUSTER_MAX_SECONDS} have passed since its first ({@link #musterCell}). Prey in its sense breaks it.
 *
 * <p>Written serially (the director's release and {@link #tick}); read by the parallel unit update, which never
 * writes it. Deterministic: every spread is seeded off the body's id. {@code -Dbattle.swarm.emergence=false} is the
 * control: bodies come out whole and go at once, as a stream one every couple of ticks.
 */
public final class SwarmEmergence {

    public static final String ENABLED_PROPERTY = "battle.swarm.emergence";
    /** Longest a pack waits at its muster for its last body. */
    public static final float MUSTER_MAX_SECONDS = 4f;
    /** Cells along the pack's lane from the hole to the middle of its muster. */
    static final int MUSTER_LANE_CELLS = 4;
    /** Cells round the muster's middle its members stand at. */
    static final float MUSTER_SPREAD = 1.6f;
    /** A muster spot within this of the body counts as reached: it mills there. */
    static final float MUSTER_REACHED = 0.8f;
    /** Dust puffs a break-out throws, round the hole. */
    static final int DUST_PUFFS = 5;

    public static boolean enabledByDefault() {
        return Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"));
    }

    /** Seconds a body of {@code type} takes to climb out of the hole. */
    public static float emergeSeconds(UnitType type) {
        return switch (type) {
            case SWARM_BRUTE -> 1.1f;
            case SWARM_SPITTER -> 0.9f;
            case SWARM_MATRIARCH -> 1.8f;
            case SWARM_WORKER -> 0.6f;
            default -> 0.7f;
        };
    }

    private boolean enabled = enabledByDefault();
    private final Long2IntOpenHashMap startTick = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap untilTick = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap musterOf = new Long2IntOpenHashMap();
    private final Long2LongOpenHashMap musterSpot = new Long2LongOpenHashMap();
    private final Int2IntOpenHashMap musterUntil = new Int2IntOpenHashMap();
    private final Int2IntOpenHashMap musterHeadX = new Int2IntOpenHashMap();
    private final Int2IntOpenHashMap musterHeadY = new Int2IntOpenHashMap();
    private int nextMuster = 1;

    public boolean enabled() { return enabled; }

    /** A scene's control: with emergence off, bodies come out whole and go at once. */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /**
     * Brings {@code body} up out of the hole centred on ({@code holeX}, {@code holeY}): holds it for its strain's
     * {@link #emergeSeconds}, turns it to face out of the hole and throws the hole's dust. Serial.
     */
    public void begin(BattleSimulation sim, long body, float holeX, float holeY) {
        if (!enabled) return;
        int tick = sim.getSimTickIndex();
        float seconds = emergeSeconds(sim.identity().type(body));
        startTick.put(body, tick);
        untilTick.put(body, tick + Math.max(1, Math.round(seconds / BattleSimulation.TICK_DT)));
        float x = sim.world().x(body), y = sim.world().y(body);
        float dx = x - holeX, dy = y - holeY;
        if (dx * dx + dy * dy < 1e-4f) {
            double a = 2 * Math.PI * SwarmBores.unit(SwarmBores.mix(body * 0x9E3779B97F4A7C15L));
            dx = (float) Math.cos(a);
            dy = (float) Math.sin(a);
        }
        BattleComponents c = sim.getBattleComponents();
        if (sim.getEntityWorld().has(body, c.LAYERED_ANIMATION)) {
            // A heading's facing is its bearing less a quarter turn, as every rig reads it.
            sim.getEntityWorld().setFloat(body, c.LAYERED_ANIMATION, BattleComponents.LAYERED_FACING_DEGREES,
                    (float) Math.toDegrees(Math.atan2(dy, dx)) - 90f);
        }
        boolean heavy = sim.identity().type(body) == UnitType.SWARM_BRUTE
                || sim.identity().type(body) == UnitType.SWARM_MATRIARCH;
        float radius = heavy ? 0.7f : 0.45f;
        double turn = 2 * Math.PI * SwarmBores.unit(SwarmBores.mix(body ^ 0xD6E8FEB86659FD93L));
        for (int i = 0; i < DUST_PUFFS; i++) {
            double a = turn + 2 * Math.PI * i / DUST_PUFFS;
            float ux = (float) Math.cos(a), uy = (float) Math.sin(a);
            sim.emitGroundDust(x + ux * 0.25f, y + uy * 0.25f, ux * 1.4f, uy * 1.4f, radius,
                    EffectsService.DUST_WHEEL);
        }
    }

    /** Whether {@code body} is still climbing out: it does nothing this tick. */
    public boolean emerging(long body, int tick) {
        return untilTick.get(body) > tick;
    }

    /**
     * How far out of the hole {@code body} is drawn at {@code tick}, 0 at its first tick and 1 once it is out (or
     * for any body that never emerged). Presentation reads it, on the tick's settled state.
     */
    public float progress(long body, float tick) {
        if (!untilTick.containsKey(body)) return 1f;
        int start = startTick.get(body), until = untilTick.get(body);
        if (tick >= until) return 1f;
        return Math.max(0f, (tick - start) / (float) (until - start));
    }

    /**
     * Opens a muster for a pack whose first body leaves the hole at ({@code holeX}, {@code holeY}) now: its middle
     * is {@link #MUSTER_LANE_CELLS} down the prey field's lane from there, so the pack forms up facing where it is
     * going. Returns its id, 0 when emergence is off.
     */
    public int openMuster(BattleSimulation sim, int holeX, int holeY) {
        if (!enabled) return 0;
        int id = nextMuster++;
        NavigationGrid grid = sim.getGrid();
        SwarmFlowFields.Field field = grid.swarmFlowFields().field(SwarmGoal.PREY.ordinal());
        int[] lane = field == null ? null : field.descend(grid, holeX, holeY, MUSTER_LANE_CELLS);
        int n = lane == null ? 0 : Paths.cellCount(lane);
        int hx = n > 0 ? Paths.cellX(lane, n - 1) : holeX, hy = n > 0 ? Paths.cellY(lane, n - 1) : holeY;
        musterHeadX.put(id, hx);
        musterHeadY.put(id, hy);
        musterUntil.put(id, sim.getSimTickIndex() + Math.round(MUSTER_MAX_SECONDS / BattleSimulation.TICK_DT));
        return id;
    }

    /** Puts {@code body} in muster {@code id} at its own seeded spot round the muster's middle. */
    public void joinMuster(BattleSimulation sim, int id, long body) {
        if (id == 0 || !musterUntil.containsKey(id)) return;
        NavigationGrid grid = sim.getGrid();
        int hx = musterHeadX.get(id), hy = musterHeadY.get(id);
        long roll = SwarmBores.mix(body * 0xBF58476D1CE4E5B9L + id);
        int sx = hx, sy = hy;
        for (int attempt = 0; attempt < 6; attempt++) {
            long r = SwarmBores.mix(roll + attempt);
            double a = 2 * Math.PI * SwarmBores.unit(r);
            float d = MUSTER_SPREAD * (0.35f + 0.65f * SwarmBores.unit(r >>> 17));
            int x = Math.round(hx + (float) Math.cos(a) * d), y = Math.round(hy + (float) Math.sin(a) * d);
            if (grid.inBounds(x, y) && grid.isWalkable(x, y)) {
                sx = x;
                sy = y;
                break;
            }
        }
        musterOf.put(body, id);
        musterSpot.put(body, ((long) sx << 32) | (sy & 0xFFFFFFFFL));
    }

    /**
     * Closes muster {@code id} once its last body is out of the hole at {@code outTick}: its members go on together
     * then, never later than the muster's cap.
     */
    public void closeMuster(int id, int outTick) {
        if (id == 0 || !musterUntil.containsKey(id)) return;
        musterUntil.put(id, Math.min(musterUntil.get(id), outTick));
    }

    /**
     * The cell {@code body} holds at while its pack musters, packed as {@code x << 32 | y}, or -1 when it is not
     * mustering at {@code tick}.
     */
    public long musterCell(long body, int tick) {
        int id = musterOf.get(body);
        if (id == 0 || musterUntil.get(id) <= tick) return -1L;
        return musterSpot.get(body);
    }

    /**
     * A Runner out of the hole with nothing to fight walks to its muster spot and mills there. Returns whether it
     * did, so its behaviour does nothing else this tick.
     */
    static boolean muster(long runner, BattleSimulation sim) {
        long cell = sim.swarmEmergence().musterCell(runner, sim.getSimTickIndex());
        if (cell < 0L) return false;
        int x = (int) (cell >> 32), y = (int) cell;
        float dx = x + 0.5f - sim.world().x(runner), dy = y + 0.5f - sim.world().y(runner);
        if (dx * dx + dy * dy <= MUSTER_REACHED * MUSTER_REACHED) {
            sim.clearPath(runner);
            return true;
        }
        SwarmMovement.pathDirect(runner, x, y, false, sim);
        return true;
    }

    /** Serial, once a tick: forgets the bodies that are out and the musters that have gone. */
    public void tick(BattleSimulation sim) {
        if (untilTick.isEmpty() && musterUntil.isEmpty()) return;
        int tick = sim.getSimTickIndex();
        // Kept a second past its end, so presentation sees a body finish rising rather than snap.
        untilTick.long2IntEntrySet().removeIf(e -> {
            boolean gone = e.getIntValue() + 30 < tick || sim.resolveUnit(e.getLongKey()) == 0L;
            if (gone) startTick.remove(e.getLongKey());
            return gone;
        });
        musterUntil.int2IntEntrySet().removeIf(e -> {
            boolean gone = e.getIntValue() <= tick;
            if (gone) {
                musterHeadX.remove(e.getIntKey());
                musterHeadY.remove(e.getIntKey());
            }
            return gone;
        });
        musterOf.long2IntEntrySet().removeIf(e -> {
            boolean gone = !musterUntil.containsKey(e.getIntValue());
            if (gone) musterSpot.remove(e.getLongKey());
            return gone;
        });
    }
}

package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.nav.SwarmFlowFields;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The swarm's packs (`colony-breach-nouns.md`, Pack, pack leader; Pack
 * movement): which bodies of a pulse run together, who leads them, and what
 * the pack as a whole is doing this tick.
 *
 * <p>A pulse is stamped into packs as it leaves the mouth, at most
 * {@link #MAX_PACK} bodies to a pack and split evenly. The leader is the
 * pack's first elder; a pack without one moves around its first-released
 * living body (its <em>anchor</em>, which is movement only and carries none of
 * a leader's bonuses). A leader's death breaks the pack for good.
 *
 * <p>{@link #prepare} runs serially before the unit update and is the only
 * place pack state changes once bodies are out: it reads tick-start
 * positions, so every member reads the same plan whatever order the workers
 * move them in. Members only read it ({@link SwarmPackMovement}).
 *
 * <p>{@code -Dbattle.swarm.pack=false} (or {@link BattleSimulation#setSwarmPackEnabled})
 * is the control: packs are still stamped, so the record is the same, but no
 * body moves as a pack.
 */
public final class SwarmPacks {

    public static final String ENABLED_PROPERTY = "battle.swarm.pack";

    /** The most bodies one pack takes; a bigger pulse splits evenly. */
    public static final int MAX_PACK = 8;
    /** Cells from the anchor within which a member keeps its place in the pack. */
    public static final float LEASH = 8f;
    /** Ticks off the leash after which a member leaves the pack. */
    static final int STRAY_TICKS = 60;
    /** Lateral cells between neighbouring ranks of the fan on open ground. */
    static final float SPACING = 1.25f;
    /** Cells each rank out from the centre runs behind the anchor: the fan's sweep. */
    static final float DEPTH_PER_RANK = 0.7f;
    /** Cells of the pack's lane, taken off the prey field from the anchor. */
    static final int LANE_CELLS = 14;
    /** Most ticks between lane descents; the lane is also taken again once the anchor runs off its head. */
    static final int LANE_REFRESH_TICKS = 15;
    /** Lane cells ahead of the anchor whose room the fan narrows to. */
    static final int CLEARANCE_LOOKAHEAD = 6;
    /** Cells either side of the lane probed for room to spread. */
    static final int CLEARANCE_PROBE = 6;
    /** Anchor-to-quarry distance at which a travelling pack starts to stalk. */
    public static final float HUNT_RANGE = 15f;
    /** Longest stalk before the feint, ticks: long enough for a flanker to swing a quarter round. */
    static final int STALK_TICKS = 120;
    /** One feint: in, then back out, ticks. */
    static final int FEINT_TICKS = 60;
    /** The part of a feint spent closing. */
    static final int FEINT_IN_TICKS = 36;
    /** A pack stalks only once its lane has this share of open-ground room, so it is out of the street first. */
    static final float STALK_ROOM = 0.6f;
    /** Ticks between one member's commit to the rush and the next. */
    static final int RUSH_STAGGER_TICKS = 5;
    /** Cells from the quarry a flanker swings out to. */
    static final float FLANK_RADIUS = 7f;
    /** Cells from the quarry the pack's centre holds while it stalks. */
    static final float STANDOFF = 7f;
    /** Cells from the quarry a feint closes to. */
    static final float FEINT_REACH = 2.5f;
    /** Members lost while stalking or feinting that commit the pack at once. */
    static final int PROVOKED_LOSSES = 2;
    /** A rushing pack whose quarry is this far beyond hunt range for this long re-forms. */
    static final int REFORM_TICKS = 60;
    /** Ticks between merge and rejoin passes. */
    static final int REGROUP_TICKS = 15;
    /** Leaderless packs whose anchors come this close merge if they fit in one pack. */
    static final float MERGE_DISTANCE = 3f;
    /** Packs below this many living bodies hunt as individuals. */
    static final int MIN_HUNTING_PACK = 3;

    /** What a pack as a whole is doing. */
    public enum Phase { TRAVEL, STALK, FEINT, RUSH }

    public static boolean defaultEnabled() {
        return Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"));
    }

    private final List<Pack> packs = new ArrayList<>();
    /** The packs still standing (not spent, not broken), oldest first: what each tick plans. */
    private final List<Pack> live = new ArrayList<>();
    private final Map<Long, Pack> byBody = new HashMap<>();
    /** Bodies that left a pack or whose pack broke, in the order they did. */
    private final List<Long> unattached = new ArrayList<>();
    private final long seed;
    private int nextPackId = 1;
    /** The pack each pulse still leaving its mouth is filling, and how many more bodies it takes. */
    private final Map<Object, Pack> filling = new HashMap<>();
    private final Map<Object, Integer> fillingRemaining = new HashMap<>();

    public SwarmPacks(long seed) {
        this.seed = seed;
    }

    /**
     * How a pulse of {@code size} bodies splits into packs: as few packs as
     * fit {@link #MAX_PACK}, as even as they can be, larger first.
     */
    public static int[] packSizes(int size) {
        if (size <= 0) return new int[0];
        int count = (size + MAX_PACK - 1) / MAX_PACK;
        int[] sizes = new int[count];
        for (int i = 0; i < count; i++) sizes[i] = size / count + (i < size % count ? 1 : 0);
        return sizes;
    }

    /**
     * Lateral rank of the {@code k}-th non-anchor member: +1, -1, +2, -2, ...
     * so the fan fills out from the centre on alternate sides.
     */
    public static int rank(int k) {
        int r = k / 2 + 1;
        return (k & 1) == 0 ? r : -r;
    }

    /**
     * Stamps {@code body} into the pack being filled for pulse {@code pulseKey}
     * of {@code pulseSize} bodies. Serial: the director releases bodies
     * outside the unit update.
     */
    public void joinPulse(Object pulseKey, int pulseSize, int indexInPulse, long body, BattleSimulation sim) {
        Pack open = filling.get(pulseKey);
        int remaining = fillingRemaining.getOrDefault(pulseKey, 0);
        if (open == null || remaining <= 0 || open.spent || open.broken) {
            int[] sizes = packSizes(pulseSize);
            int consumed = 0;
            remaining = 1;
            for (int s : sizes) {
                if (indexInPulse < consumed + s) {
                    remaining = s - (indexInPulse - consumed);
                    break;
                }
                consumed += s;
            }
            open = newPack(sim);
            filling.put(pulseKey, open);
        }
        open.add(body, sim);
        byBody.put(body, open);
        remaining--;
        if (remaining <= 0 || indexInPulse + 1 >= pulseSize) {
            filling.remove(pulseKey);
            fillingRemaining.remove(pulseKey);
        } else {
            fillingRemaining.put(pulseKey, remaining);
        }
    }

    /** Forms one pack of {@code bodies} in that order; for harnesses that place bodies directly. */
    public void form(long[] bodies, BattleSimulation sim) {
        Pack pack = newPack(sim);
        for (long body : bodies) {
            pack.add(body, sim);
            byBody.put(body, pack);
        }
    }

    private Pack newPack(BattleSimulation sim) {
        Pack pack = new Pack(nextPackId++, sim.getSimTickIndex(), seed);
        packs.add(pack);
        live.add(pack);
        return pack;
    }

    /** The pack {@code body} belongs to, or {@code null}. */
    public Pack packOf(long body) {
        return byBody.get(body);
    }

    /** Every pack ever formed, oldest first; broken and spent packs stay on the record. */
    public List<Pack> packs() {
        return packs;
    }

    /**
     * Serial, before the unit update: prunes the dead, breaks packs whose
     * leader died, lets strays go and unattached bodies rejoin, and plans
     * each live pack's tick (lane, fan spacing, hunt phase, member goals).
     */
    public void prepare(BattleSimulation sim) {
        if (live.isEmpty() && unattached.isEmpty()) return;
        int tick = sim.getSimTickIndex();
        boolean moving = sim.isSwarmPackEnabled() && sim.isSwarmFlowFieldEnabled();
        for (Pack pack : live) {
            if (pack.spent) continue;
            pack.prune(sim, this, moving);
            if (!moving || pack.spent || pack.broken) continue;
            pack.plan(sim, tick, this);
        }
        if (moving && tick % REGROUP_TICKS == 0) regroup(sim);
        live.removeIf(pack -> pack.spent || pack.broken);
    }

    void release(long body) {
        byBody.remove(body);
        unattached.add(body);
    }

    private void regroup(BattleSimulation sim) {
        // Two leaderless packs travelling together become one.
        for (int i = 0; i < live.size(); i++) {
            Pack a = live.get(i);
            if (!a.mergeable()) continue;
            for (int j = i + 1; j < live.size(); j++) {
                Pack b = live.get(j);
                if (!b.mergeable() || a.count + b.count > MAX_PACK) continue;
                if (distance(sim, a.anchor, b.anchor) > MERGE_DISTANCE) continue;
                for (int k = 0; k < b.count; k++) {
                    a.add(b.members[k], sim);
                    byBody.put(b.members[k], a);
                }
                b.count = 0;
                b.spent = true;
            }
        }
        // A body without a pack joins the nearest living leader's pack that has room.
        for (int i = 0; i < unattached.size(); ) {
            long body = unattached.get(i);
            if (sim.resolveUnit(body) == 0L) {
                unattached.remove(i);
                continue;
            }
            Pack best = null;
            float bestDistance = LEASH;
            for (Pack pack : live) {
                if (pack.spent || pack.broken || pack.leader == 0L || pack.count >= MAX_PACK
                        || pack.phase != Phase.TRAVEL) continue;
                float d = distance(sim, body, pack.leader);
                if (d <= bestDistance) {
                    best = pack;
                    bestDistance = d;
                }
            }
            if (best == null) {
                i++;
                continue;
            }
            best.add(body, sim);
            byBody.put(body, best);
            unattached.remove(i);
        }
    }

    static float distance(BattleSimulation sim, long a, long b) {
        float dx = sim.world().x(a) - sim.world().x(b);
        float dy = sim.world().y(a) - sim.world().y(b);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    /** One pack. Fields are written by {@link SwarmPacks#prepare} only. */
    public static final class Pack {
        final int id;
        final int formedTick;
        final long seed;
        long[] members = new long[MAX_PACK];
        int count;
        /** The pack's first elder, for as long as it lives; 0 for a leaderless pack. */
        long leader;
        /** Who the pack moves around this tick: the leader, else the first living member. */
        long anchor;
        boolean broken;
        int brokenTick = -1;
        boolean spent;
        Phase phase = Phase.TRAVEL;
        int phaseTick;
        long quarry;
        int feints;
        int lossesSincePhase;
        int farTicks;
        float approachX;
        float approachY;

        // per-member plan, indexed like members
        int[] rank = new int[MAX_PACK];
        int[] strayTicks = new int[MAX_PACK];
        int[] goalX = new int[MAX_PACK];
        int[] goalY = new int[MAX_PACK];
        boolean[] flanker = new boolean[MAX_PACK];
        int[] commitTick = new int[MAX_PACK];
        float[] gaitPeriod = new float[MAX_PACK];
        float[] gaitOffset = new float[MAX_PACK];
        /** How far back of its rank's place a member runs, as a share of the rank's depth. */
        float[] depthShare = new float[MAX_PACK];
        float[] swayPeriod = new float[MAX_PACK];

        // the lane plan
        int[] lane = new int[0];
        int laneTick = Integer.MIN_VALUE;
        float headingX = 1f;
        float headingY = 0f;
        float spacing = SPACING;
        /** The anchor's position at the start of the tick, which is what members read. */
        float anchorX;
        float anchorY;

        Pack(int id, int formedTick, long seed) {
            this.id = id;
            this.formedTick = formedTick;
            this.seed = seed;
        }

        public int id() { return id; }
        public long leader() { return leader; }
        public long anchor() { return anchor; }
        public boolean isBroken() { return broken; }
        public int brokenTick() { return brokenTick; }
        public Phase phase() { return phase; }
        public long quarry() { return quarry; }
        public int size() { return count; }
        public long member(int i) { return members[i]; }
        public float headingX() { return headingX; }
        public float headingY() { return headingY; }
        public float spacing() { return spacing; }

        /** Whether this pack moves as a pack this tick. */
        public boolean active() {
            return !broken && !spent && anchor != 0L;
        }

        int indexOf(long body) {
            for (int i = 0; i < count; i++) if (members[i] == body) return i;
            return -1;
        }

        void add(long body, BattleSimulation sim) {
            if (count == members.length) grow();
            members[count] = body;
            strayTicks[count] = 0;
            long roll = mix(seed ^ body * 0x9E3779B97F4A7C15L);
            gaitPeriod[count] = 1.2f + 0.9f * ((roll & 0xFFFF) / 65535f);
            gaitOffset[count] = ((roll >>> 16) & 0xFFFF) / 65535f;
            depthShare[count] = 0.4f + 0.8f * ((roll >>> 32) & 0xFFFF) / 65535f;
            swayPeriod[count] = 2.2f + 1.6f * ((roll >>> 48) & 0xFFFF) / 65535f;
            count++;
            if (leader == 0L && !broken && sim.identity().swarmStage(body) == SwarmStage.ELDER) {
                leader = body;
            }
        }

        private void grow() {
            int n = members.length * 2;
            members = Arrays.copyOf(members, n);
            rank = Arrays.copyOf(rank, n);
            strayTicks = Arrays.copyOf(strayTicks, n);
            goalX = Arrays.copyOf(goalX, n);
            goalY = Arrays.copyOf(goalY, n);
            flanker = Arrays.copyOf(flanker, n);
            commitTick = Arrays.copyOf(commitTick, n);
            gaitPeriod = Arrays.copyOf(gaitPeriod, n);
            gaitOffset = Arrays.copyOf(gaitOffset, n);
            depthShare = Arrays.copyOf(depthShare, n);
            swayPeriod = Arrays.copyOf(swayPeriod, n);
        }

        boolean mergeable() {
            return !spent && !broken && leader == 0L && anchor != 0L && phase == Phase.TRAVEL;
        }

        /** Drops the dead, breaks on the leader's death, picks the anchor, lets strays go. */
        void prune(BattleSimulation sim, SwarmPacks packs, boolean moving) {
            int kept = 0;
            for (int i = 0; i < count; i++) {
                long body = members[i];
                if (sim.resolveUnit(body) == 0L) {
                    lossesSincePhase++;
                    continue;
                }
                copy(i, kept++);
            }
            count = kept;
            if (leader != 0L && sim.resolveUnit(leader) == 0L) {
                broken = true;
                brokenTick = sim.getSimTickIndex();
                leader = 0L;
                anchor = 0L;
                for (int i = 0; i < count; i++) packs.release(members[i]);
                count = 0;
                return;
            }
            if (count == 0) {
                spent = true;
                anchor = 0L;
                return;
            }
            anchor = leader;
            if (anchor == 0L) {
                for (int i = 0; i < count; i++) {
                    if (!sim.swarmHarvest().isCarrier(members[i])) {
                        anchor = members[i];
                        break;
                    }
                }
            }
            if (anchor == 0L || !moving) return;
            kept = 0;
            for (int i = 0; i < count; i++) {
                long body = members[i];
                if (body != anchor && !sim.swarmHarvest().isCarrier(body)
                        && distance(sim, body, anchor) > LEASH) {
                    if (++strayTicks[i] > STRAY_TICKS) {
                        packs.release(body);
                        continue;
                    }
                } else {
                    strayTicks[i] = 0;
                }
                copy(i, kept++);
            }
            count = kept;
        }

        private void copy(int from, int to) {
            if (from == to) return;
            members[to] = members[from];
            strayTicks[to] = strayTicks[from];
            goalX[to] = goalX[from];
            goalY[to] = goalY[from];
            flanker[to] = flanker[from];
            commitTick[to] = commitTick[from];
            gaitPeriod[to] = gaitPeriod[from];
            gaitOffset[to] = gaitOffset[from];
            depthShare[to] = depthShare[from];
            swayPeriod[to] = swayPeriod[from];
        }

        void plan(BattleSimulation sim, int tick, SwarmPacks packs) {
            if (anchor == 0L) return;
            anchorX = sim.world().x(anchor);
            anchorY = sim.world().y(anchor);
            int k = 0;
            for (int i = 0; i < count; i++) {
                rank[i] = members[i] == anchor ? 0 : rank(k++);
            }
            planLane(sim, tick);
            planHunt(sim, tick);
        }

        private void planLane(BattleSimulation sim, int tick) {
            if (tick - laneTick < LANE_REFRESH_TICKS && laneStillUnder(sim)) return;
            laneTick = tick;
            NavigationGrid grid = sim.getGrid();
            SwarmFlowFields.Field field = grid.swarmFlowFields().field(SwarmGoal.PREY.ordinal());
            int ax = sim.world().cellX(anchor);
            int ay = sim.world().cellY(anchor);
            int[] descended = field == null ? null : field.descend(grid, ax, ay, LANE_CELLS);
            lane = descended == null ? new int[0] : descended;
            int n = Paths.cellCount(lane);
            if (n >= 2) {
                int far = Math.min(4, n - 1);
                float dx = Paths.cellX(lane, far) - Paths.cellX(lane, 0);
                float dy = Paths.cellY(lane, far) - Paths.cellY(lane, 0);
                float length = (float) Math.sqrt(dx * dx + dy * dy);
                if (length > 0f) {
                    headingX = dx / length;
                    headingY = dy / length;
                }
            }
            spacing = SPACING;
            int maxRank = 0;
            for (int i = 0; i < count; i++) maxRank = Math.max(maxRank, Math.abs(rank[i]));
            if (maxRank == 0 || n == 0) return;
            int room = CLEARANCE_PROBE;
            for (int s = 0; s < Math.min(n, CLEARANCE_LOOKAHEAD + 1); s += 3) {
                room = Math.min(room, roomBeside(grid, Paths.cellX(lane, s) + 0.5f,
                        Paths.cellY(lane, s) + 0.5f, -headingY, headingX));
                room = Math.min(room, roomBeside(grid, Paths.cellX(lane, s) + 0.5f,
                        Paths.cellY(lane, s) + 0.5f, headingY, -headingX));
            }
            spacing = Math.max(0f, Math.min(SPACING, (room - 0.5f) / maxRank));
        }

        /** Whether the anchor still stands on (or by) the lane's first few cells. */
        private boolean laneStillUnder(BattleSimulation sim) {
            int n = Paths.cellCount(lane);
            if (n < 2) return false;
            int ax = sim.world().cellX(anchor);
            int ay = sim.world().cellY(anchor);
            for (int i = 0; i < Math.min(n - 1, 4); i++) {
                if (Math.abs(Paths.cellX(lane, i) - ax) <= 1 && Math.abs(Paths.cellY(lane, i) - ay) <= 1) {
                    return true;
                }
            }
            return false;
        }

        private static int roomBeside(NavigationGrid grid, float x, float y, float px, float py) {
            for (int t = 1; t <= CLEARANCE_PROBE; t++) {
                int cx = (int) Math.floor(x + px * t);
                int cy = (int) Math.floor(y + py * t);
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) return t - 1;
            }
            return CLEARANCE_PROBE;
        }

        private void planHunt(BattleSimulation sim, int tick) {
            long target = sim.combat().targetId(anchor);
            boolean live = target != 0L && sim.resolveUnit(target) != 0L;
            float distance = live ? SwarmPacks.distance(sim, anchor, target) : Float.MAX_VALUE;
            switch (phase) {
                case TRAVEL:
                    if (live && distance <= HUNT_RANGE && count >= MIN_HUNTING_PACK
                            && (spacing >= STALK_ROOM * SPACING || distance <= STANDOFF + 1f)) {
                        quarry = target;
                        startStalk(sim, tick);
                    }
                    break;
                case STALK:
                case FEINT:
                    if (!live) {
                        enter(Phase.TRAVEL, tick);
                        quarry = 0L;
                        break;
                    }
                    quarry = target;
                    if (lossesSincePhase >= PROVOKED_LOSSES || count < MIN_HUNTING_PACK) {
                        startRush(sim, tick);
                    } else if (phase == Phase.STALK
                            && (tick - phaseTick >= STALK_TICKS || flankersInPlace(sim))) {
                        enter(Phase.FEINT, tick);
                    } else if (phase == Phase.FEINT && tick - phaseTick >= feints * FEINT_TICKS) {
                        startRush(sim, tick);
                    }
                    break;
                case RUSH:
                    if (live) quarry = target;
                    farTicks = !live || distance > HUNT_RANGE + 5f ? farTicks + 1 : 0;
                    if (farTicks > REFORM_TICKS) {
                        enter(Phase.TRAVEL, tick);
                        quarry = 0L;
                    }
                    break;
                default:
                    break;
            }
            if (phase == Phase.STALK || phase == Phase.FEINT) planGoals(sim, tick);
        }

        private void enter(Phase next, int tick) {
            phase = next;
            phaseTick = tick;
            lossesSincePhase = 0;
            farTicks = 0;
        }

        private void startStalk(BattleSimulation sim, int tick) {
            enter(Phase.STALK, tick);
            float cx = 0f;
            float cy = 0f;
            for (int i = 0; i < count; i++) {
                cx += sim.world().x(members[i]);
                cy += sim.world().y(members[i]);
            }
            cx /= count;
            cy /= count;
            float ax = cx - sim.world().x(quarry);
            float ay = cy - sim.world().y(quarry);
            float length = (float) Math.sqrt(ax * ax + ay * ay);
            approachX = length > 1e-3f ? ax / length : -headingX;
            approachY = length > 1e-3f ? ay / length : -headingY;
            feints = 1 + (int) (mix(seed ^ id * 0xC2B2AE3D27D4EB4FL ^ tick) & 1L);
            for (int i = 0; i < count; i++) flanker[i] = Math.abs(rank[i]) >= 2;
        }

        private void startRush(BattleSimulation sim, int tick) {
            enter(Phase.RUSH, tick);
            // Flankers first, from the outside in; the centre and the anchor last.
            int order = 0;
            int maxRank = 0;
            for (int i = 0; i < count; i++) maxRank = Math.max(maxRank, Math.abs(rank[i]));
            for (int r = maxRank; r >= 0; r--) {
                for (int i = 0; i < count; i++) {
                    if (Math.abs(rank[i]) == r) commitTick[i] = tick + RUSH_STAGGER_TICKS * order++;
                }
            }
        }

        private boolean flankersInPlace(BattleSimulation sim) {
            boolean any = false;
            for (int i = 0; i < count; i++) {
                if (!flanker[i] || i >= goalX.length) continue;
                any = true;
                float dx = goalX[i] + 0.5f - sim.world().x(members[i]);
                float dy = goalY[i] + 0.5f - sim.world().y(members[i]);
                if (dx * dx + dy * dy > 2.5f * 2.5f) return false;
            }
            return any;
        }

        /** Where each member stands to stalk or feint this tick. */
        private void planGoals(BattleSimulation sim, int tick) {
            NavigationGrid grid = sim.getGrid();
            float qx = sim.world().x(quarry);
            float qy = sim.world().y(quarry);
            boolean feintIn = phase == Phase.FEINT && (tick - phaseTick) % FEINT_TICKS < FEINT_IN_TICKS;
            for (int i = 0; i < count; i++) {
                float angle;
                float radius;
                if (flanker[i]) {
                    int r = Math.abs(rank[i]);
                    angle = (float) Math.toRadians(Math.min(115f, 50f + 30f * (r - 2))) * Math.signum(rank[i]);
                    radius = FLANK_RADIUS;
                } else {
                    angle = (float) Math.toRadians(14f) * Math.signum(rank[i]);
                    radius = feintIn ? FEINT_REACH : STANDOFF;
                }
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);
                float dx = approachX * cos - approachY * sin;
                float dy = approachX * sin + approachY * cos;
                int[] cell = walkableNear(grid, qx + dx * radius, qy + dy * radius);
                if (cell == null) {
                    goalX[i] = (int) Math.floor(qx);
                    goalY[i] = (int) Math.floor(qy);
                } else {
                    goalX[i] = cell[0];
                    goalY[i] = cell[1];
                }
            }
        }

        private static int[] walkableNear(NavigationGrid grid, float x, float y) {
            int cx = (int) Math.floor(x);
            int cy = (int) Math.floor(y);
            for (int r = 0; r <= 3; r++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dx = -r; dx <= r; dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                        int nx = cx + dx;
                        int ny = cy + dy;
                        if (grid.inBounds(nx, ny) && grid.isWalkable(nx, ny)) return new int[] {nx, ny};
                    }
                }
            }
            return null;
        }
    }

    /** Whether {@code body} is a strain that runs as a pack member (Runners; the rest keep their own gait). */
    static boolean runsWithPack(UnitType type) {
        return type == UnitType.SWARM_RUNNER;
    }
}

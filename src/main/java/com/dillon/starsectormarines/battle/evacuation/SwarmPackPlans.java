package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.nav.SwarmFlowFields;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What each pack is doing as it moves (`colony-breach-nouns.md`, Pack
 * movement). Who is in a pack and who leads it is {@link SwarmPacks}'s; this
 * is the plan a standing pack's Runners move on this tick: its anchor, its
 * lane and fan, and its hunt (stalk, feint, staggered rush).
 *
 * <p>{@link #prepare} runs serially before the unit update, from tick-start
 * positions, and is the only writer; {@link SwarmPackMovement} reads it from
 * the parallel update, so every member reads the same plan whatever order the
 * workers move them in.
 *
 * <p>{@code -Dbattle.swarm.pack=false} (or {@link BattleSimulation#setSwarmPackEnabled})
 * is the control: packs still form, lead and break, but every body moves alone.
 */
public final class SwarmPackPlans {

    public static final String ENABLED_PROPERTY = "battle.swarm.pack";

    /** Cells from the anchor within which a member keeps its place in the pack. */
    public static final float LEASH = SwarmPacks.LEASH;
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
    /** Packs below this many Runners hunt as individuals. */
    static final int MIN_HUNTING_PACK = 3;

    /** Fewest bodies a sub-pack peels off with: never a lone stray. */
    public static final int MIN_SUB_PACK = 2;
    public static final String SPLIT_PROPERTY = "battle.swarm.packSplit";

    /** What a pack as a whole is doing. */
    public enum Phase { TRAVEL, STALK, FEINT, RUSH }

    public static boolean defaultEnabled() {
        return Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"));
    }

    /** Plans by pack id; a pack with no Runner left, or no longer standing, has none. */
    private final Map<Integer, Plan> plans = new HashMap<>();
    private final long seed;
    private final SwarmPackPress press;
    /** Whether packs press and split ({@link SwarmPackPress}); {@code -Dbattle.swarm.packSplit=false} is the control. */
    private boolean splitEnabled = Boolean.parseBoolean(System.getProperty(SPLIT_PROPERTY, "true"));

    public boolean splitEnabled() { return splitEnabled; }

    public void setSplitEnabled(boolean enabled) { splitEnabled = enabled; }

    SwarmPackPress press() { return press; }

    /** The quarry {@code body}'s sub-pack was sent at, which it hunts in place of its own choice; 0 for none. */
    public long assignedQuarry(long body) { return press.assignedQuarry(body); }

    /** Whether {@code body} runs in a sub-pack it does not lead, so taking the sub-pack's quarry is not its own cry. */
    public boolean followsLead(long body) { return press.followsLead(body); }

    /** How many Runners can strike {@code target} at once; see {@link SwarmPackPress}. */
    public static int strikeSlots(BattleSimulation sim, long target, long striker) {
        return SwarmPackPress.strikeSlots(sim, target, striker);
    }

    public SwarmPackPlans(long seed) {
        this.seed = seed;
        this.press = new SwarmPackPress(seed);
    }

    /** The plan pack {@code packId} moves on this tick, or {@code null}. */
    public Plan planFor(int packId) {
        return plans.get(packId);
    }

    /**
     * Serial, before the unit update: gathers each standing pack's Runners,
     * then plans its tick (anchor, lane, fan spacing, hunt phase, member goals).
     */
    public void prepare(BattleSimulation sim) {
        SwarmPacks packs = sim.swarmPacks();
        if (!sim.isSwarmPackEnabled() || !sim.isSwarmFlowFieldEnabled() || !packs.enabled()) {
            plans.clear();
            press.clear();
            return;
        }
        Map<Integer, List<Long>> members = new HashMap<>();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long body = sim.liveUnitAt(i);
            if (sim.identity().type(body) != UnitType.SWARM_RUNNER) continue;
            // A scavenger fetches the dead, and a forager hunts the colony's people, on its own errand; neither anchors
            // nor keeps a place.
            if (sim.swarmHarvest().isScavenger(body) || sim.swarmHarvest().isForager(body)) continue;
            int id = packs.packIdOf(body);
            if (id == 0 || !packs.isStanding(id)) continue;
            members.computeIfAbsent(id, k -> new ArrayList<>()).add(body);
        }
        plans.keySet().retainAll(members.keySet());
        if (members.isEmpty()) {
            press.clear();
            return;
        }
        int[] ids = members.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
        int tick = sim.getSimTickIndex();
        for (int id : ids) {
            long[] bodies = members.get(id).stream().mapToLong(Long::longValue).sorted().toArray();
            Plan plan = plans.computeIfAbsent(id, k -> new Plan(k, seed, press));
            plan.update(bodies, packs.leaderOfPack(id), sim, tick);
        }
        if (splitEnabled) press.prepare(sim, plans, ids);
        else press.clear();
    }

    /** Lateral rank of the {@code k}-th member after the anchor: +1, -1, +2, -2, ... */
    public static int rank(int k) {
        int r = k / 2 + 1;
        return (k & 1) == 0 ? r : -r;
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

    /** A member's own seeded roll: its stride, its weave and how far back of its place it runs. */
    static long roll(long seed, long body) {
        return mix(seed ^ body * 0x9E3779B97F4A7C15L);
    }

    /** One standing pack's plan for this tick. Written by {@link SwarmPackPlans#prepare} only. */
    public static final class Plan {
        final int id;
        final long seed;
        private final SwarmPackPress press;
        /** The pack's Runners this tick, by entity id. */
        long[] members = new long[0];
        int count;
        /** Who the pack moves round: its leader while it lives, else its first living Runner. */
        long anchor;
        float anchorX;
        float anchorY;
        Phase phase = Phase.TRAVEL;
        int phaseTick;
        long quarry;
        int feints;
        int lossesSincePhase;
        int farTicks;
        float approachX;
        float approachY;

        // per member, aligned with members
        int[] rank = new int[0];
        boolean[] flanker = new boolean[0];
        int[] commitTick = new int[0];
        int[] goalX = new int[0];
        int[] goalY = new int[0];
        float[] gaitPeriod = new float[0];
        float[] gaitOffset = new float[0];
        float[] depthShare = new float[0];
        float[] swayPeriod = new float[0];

        // the lane
        int[] lane = new int[0];
        int laneTick = Integer.MIN_VALUE;
        float headingX = 1f;
        float headingY = 0f;
        float spacing = SPACING;

        Plan(int id, long seed) {
            this(id, seed, new SwarmPackPress(seed));
        }

        Plan(int id, long seed, SwarmPackPress press) {
            this.id = id;
            this.seed = seed;
            this.press = press;
        }

        public int packId() { return id; }
        public long anchor() { return anchor; }
        public Phase phase() { return phase; }
        public long quarry() { return quarry; }
        public int size() { return count; }
        public long member(int i) { return members[i]; }
        public int[] lane() { return lane; }
        public float spacing() { return spacing; }
        /** Sub-packs peeled off this pack onto other quarry, oldest first. */
        public int subPackCount() { return press.subPacksOf(id).size(); }
        public long subQuarry(int s) { return press.subPacksOf(id).get(s).quarry; }
        public long subLead(int s) { return press.subPacksOf(id).get(s).lead; }
        public int subSize(int s) { return press.subPacksOf(id).get(s).members.length; }

        int indexOf(long body) {
            for (int i = 0; i < count; i++) if (members[i] == body) return i;
            return -1;
        }

        void update(long[] bodies, long leader, BattleSimulation sim, int tick) {
            regather(bodies, sim);
            anchor = leader != 0L && sim.resolveUnit(leader) != 0L ? leader : 0L;
            if (anchor == 0L) {
                for (long body : members) {
                    if (!sim.swarmHarvest().isCarrier(body)) {
                        anchor = body;
                        break;
                    }
                }
            }
            if (anchor == 0L) return;
            anchorX = sim.world().x(anchor);
            anchorY = sim.world().y(anchor);
            int k = 0;
            for (int i = 0; i < count; i++) rank[i] = members[i] == anchor ? 0 : SwarmPackPlans.rank(k++);
            planLane(sim, tick);
            planHunt(sim, tick);
        }

        /** Takes this tick's members, carrying each survivor's hunt state over and counting the fallen. */
        private void regather(long[] bodies, BattleSimulation sim) {
            int n = bodies.length;
            boolean[] nextFlanker = new boolean[n];
            int[] nextCommit = new int[n];
            int[] nextGoalX = new int[n];
            int[] nextGoalY = new int[n];
            for (int i = 0; i < n; i++) {
                int old = indexOf(bodies[i]);
                if (old >= 0) {
                    nextFlanker[i] = flanker[old];
                    nextCommit[i] = commitTick[old];
                    nextGoalX[i] = goalX[old];
                    nextGoalY[i] = goalY[old];
                } else {
                    nextCommit[i] = Integer.MAX_VALUE;
                }
            }
            for (int i = 0; i < count; i++) {
                if (Arrays.binarySearch(bodies, members[i]) < 0 && sim.resolveUnit(members[i]) == 0L) {
                    lossesSincePhase++;
                }
            }
            members = bodies;
            count = n;
            flanker = nextFlanker;
            commitTick = nextCommit;
            goalX = nextGoalX;
            goalY = nextGoalY;
            rank = new int[n];
            gaitPeriod = new float[n];
            gaitOffset = new float[n];
            depthShare = new float[n];
            swayPeriod = new float[n];
            for (int i = 0; i < n; i++) {
                long roll = roll(seed, bodies[i]);
                gaitPeriod[i] = 1.2f + 0.9f * ((roll & 0xFFFF) / 65535f);
                gaitOffset[i] = ((roll >>> 16) & 0xFFFF) / 65535f;
                depthShare[i] = 0.4f + 0.8f * ((roll >>> 32) & 0xFFFF) / 65535f;
                swayPeriod[i] = 2.2f + 1.6f * ((roll >>> 48) & 0xFFFF) / 65535f;
            }
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
                float x = Paths.cellX(lane, s) + 0.5f;
                float y = Paths.cellY(lane, s) + 0.5f;
                room = Math.min(room, roomBeside(grid, x, y, -headingY, headingX));
                room = Math.min(room, roomBeside(grid, x, y, headingY, -headingX));
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
            float distance = live ? SwarmPackPlans.distance(sim, anchor, target) : Float.MAX_VALUE;
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
                        startRush(tick);
                    } else if (phase == Phase.STALK
                            && (tick - phaseTick >= STALK_TICKS || flankersInPlace(sim))) {
                        enter(Phase.FEINT, tick);
                    } else if (phase == Phase.FEINT && tick - phaseTick >= feints * FEINT_TICKS) {
                        startRush(tick);
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

        private void startRush(int tick) {
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
                if (!flanker[i]) continue;
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
                goalX[i] = cell == null ? (int) Math.floor(qx) : cell[0];
                goalY[i] = cell == null ? (int) Math.floor(qy) : cell[1];
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
}

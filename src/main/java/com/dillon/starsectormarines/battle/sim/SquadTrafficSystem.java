package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.decision.goap.SquadRouteGoalProvider;
import com.dillon.starsectormarines.battle.decision.goap.action.AttackMove;
import com.dillon.starsectormarines.battle.decision.goap.action.EnterZone;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.Arrays;

/**
 * Optional, quiet-infantry traffic hints. One serial snapshot owns negotiation;
 * workers only consume it at the ordinary objective-travel seam. No path search,
 * occupancy reservation, position write, or cross-squad lock occurs in prepare.
 * The existing movement owner validates and applies each requested displacement.
 */
public final class SquadTrafficSystem {
    public static final String PROPERTY = "battle.squad.traffic";
    private final boolean yielding = Boolean.parseBoolean(System.getProperty("battle.squad.trafficYield", "true"));
    private static final int MAX_SQUADS = 4096;
    private static final int LOOKAHEAD_CELLS = 8;
    private final NavigationGrid grid;
    private final UnitRosterService roster;
    private final SquadTrafficSolver solver = new SquadTrafficSolver();
    private final Int2ObjectOpenHashMap<State> retained = new Int2ObjectOpenHashMap<>();
    private final Int2IntOpenHashMap slots = new Int2IntOpenHashMap();
    private final State[] states = new State[MAX_SQUADS];
    private final int[] ids = new int[MAX_SQUADS];
    private final int[] factions = new int[MAX_SQUADS];
    private final float[] x = new float[MAX_SQUADS], y = new float[MAX_SQUADS];
    private final float[] hx = new float[MAX_SQUADS], hy = new float[MAX_SQUADS];
    private final float[] radius = new float[MAX_SQUADS], openness = new float[MAX_SQUADS];
    private final float[] offsets = new float[MAX_SQUADS], ages = new float[MAX_SQUADS];
    private final float[] speeds = new float[MAX_SQUADS];
    private int[][] preparedPaths = new int[0][];
    private long[] anchorMembers = new long[0], anchorVersions = new long[0];
    private State[] anchorOwners = new State[0];
    private int[][] anchorPaths = new int[0][];
    private float[] anchorX = new float[0], anchorY = new float[0];
    private float[] anchorNormalX = new float[0], anchorNormalY = new float[0];
    private float[] routeBias = new float[0], memberOffsets = new float[0];
    private long tick;
    private long controlled;
    private int count;

    private static final class State {
        Squad squad;
        SquadPlan.Step step;
        long epoch, lastTick, representative, laneVersion;
        int goalX, goalY, members;
        float x, y, hx, hy, previousHx, previousHy, offset, age, previousOpen;
    }

    public SquadTrafficSystem(NavigationGrid grid, UnitRosterService roster) {
        this.grid = grid;
        this.roster = roster;
        slots.defaultReturnValue(-1);
    }

    /** Called only before dispatch; no backing array is changed until join. */
    public void prepare(BattleView sim, long controlledUnit, float dt) {
        tick++;
        controlled = controlledUnit;
        slots.clear();
        count = 0;
        if (tick % 60 == 0) {
            var iterator = retained.values().iterator();
            while (iterator.hasNext()) if (iterator.next().lastTick < tick - 90) iterator.remove();
        }
        for (Squad squad : sim.getSquads()) {
            if (squad.aliveMembers == 0) {
                retained.remove(squad.id);
                continue;
            }
            if (!quiet(squad)) continue;
            SquadPlan plan = squad.currentPlan;
            SquadPlan.Step step = plan == null ? null : plan.currentStep();
            if (step == null || !(step.action instanceof EnterZone || step.action instanceof AttackMove)) continue;
            SquadRouteGoalProvider.Goal goal = ((SquadRouteGoalProvider) step.action).squadRouteGoal(squad, sim);
            if (goal == null || !grid.inBounds(goal.x(), goal.y()) || count == MAX_SQUADS) continue;
            State state = retained.get(squad.id);
            if (state == null) {
                if (retained.size() == MAX_SQUADS) continue;
                state = new State();
                retained.put(squad.id, state);
            }
            if (state.lastTick != tick - 1 || state.goalX != goal.x() || state.goalY != goal.y()) {
                state.offset = state.age = 0f;
                state.laneVersion++;
            }
            state.squad = squad;
            state.step = step;
            state.epoch = squad.routingEpoch;
            state.goalX = goal.x();
            state.goalY = goal.y();
            state.members = 0;
            state.representative = 0;
            state.x = state.y = state.hx = state.hy = 0f;
            state.lastTick = tick;
            slots.put(squad.id, count);
            states[count++] = state;
        }
        int live = roster.liveCount();
        if (preparedPaths.length < live) {
            int capacity = Math.max(live, preparedPaths.length * 2 + 64);
            preparedPaths = Arrays.copyOf(preparedPaths, capacity);
            anchorMembers = Arrays.copyOf(anchorMembers, capacity);
            anchorVersions = Arrays.copyOf(anchorVersions, capacity);
            anchorOwners = Arrays.copyOf(anchorOwners, capacity);
            anchorPaths = Arrays.copyOf(anchorPaths, capacity);
            anchorX = Arrays.copyOf(anchorX, capacity);
            anchorY = Arrays.copyOf(anchorY, capacity);
            anchorNormalX = Arrays.copyOf(anchorNormalX, capacity);
            anchorNormalY = Arrays.copyOf(anchorNormalY, capacity);
            routeBias = Arrays.copyOf(routeBias, capacity);
            memberOffsets = Arrays.copyOf(memberOffsets, capacity);
        }
        Arrays.fill(preparedPaths, null);
        long[] dense = roster.denseArray();
        MovementService movement = roster.movement();
        World world = roster.world();
        for (int i = 0; i < live; i++) {
            long member = dense[i];
            if (member == controlled || !roster.squad().hasSquad(member) || !movement.has(member)
                    || !roster.identity().type(member).usesInfantryTraining() || sim.isRiding(member)) continue;
            int slot = slots.get(roster.squad().squadId(member));
            if (slot < 0) continue;
            State state = states[slot];
            int[] path = movement.path(member);
            int cells = Paths.cellCount(path);
            if (cells < 2 || movement.pathIdx(member) >= cells || Paths.destX(path) != state.goalX
                    || Paths.destY(path) != state.goalY || state.step.slotOf(member) == null) continue;
            int cursor = Math.max(1, movement.pathIdx(member));
            int end = Math.min(cells - 1, cursor + 2);
            float dx = Paths.cellX(path, end) - Paths.cellX(path, cursor - 1);
            float dy = Paths.cellY(path, end) - Paths.cellY(path, cursor - 1);
            float length = (float) Math.hypot(dx, dy);
            if (length < 0.01f) continue;
            state.x += world.x(member);
            state.y += world.y(member);
            state.hx += dx / length;
            state.hy += dy / length;
            state.members++;
            if (state.representative == 0) state.representative = member;
            preparedPaths[i] = path;
        }
        int candidates = count;
        count = 0;
        slots.clear();
        for (int i = 0; i < candidates; i++) {
            State state = states[i];
            float coherence = (float) Math.hypot(state.hx, state.hy);
            if (state.members == 0 || coherence < state.members * 0.7f) {
                state.offset = state.age = 0f;
                continue;
            }
            state.x /= state.members;
            state.y /= state.members;
            state.hx /= coherence;
            state.hy /= coherence;
            if (state.hx * state.previousHx + state.hy * state.previousHy < 0.8f) {
                state.offset = 0f;
                state.laneVersion++;
            }
            state.previousHx = state.hx;
            state.previousHy = state.hy;
            states[count] = state;
            ids[count] = state.squad.id;
            // Friendly grouping follows the faction relation, not an assumed two-sided battle.
            factions[count] = state.squad.faction.friendlyTo(Faction.MARINE)
                    ? Faction.MARINE.ordinal() : state.squad.faction.ordinal();
            x[count] = state.x;
            y[count] = state.y;
            hx[count] = state.hx;
            hy[count] = state.hy;
            radius[count] = Math.min(3f, 1f + (float) Math.sqrt(state.members) * 0.55f);
            openness[count] = openness(state);
            if (state.previousOpen < 0.9f && openness[count] >= 0.9f) state.laneVersion++;
            state.previousOpen = openness[count];
            offsets[count] = state.offset;
            ages[count] = state.age;
            slots.put(state.squad.id, count++);
        }
        solver.solve(count, ids, factions, x, y, hx, hy, radius, openness, offsets, ages, speeds, dt,
                this::visiblePair);
        for (int i = 0; i < count; i++) {
            states[i].offset = offsets[i];
            states[i].age = ages[i];
        }
        for (int i = 0; i < live; i++) {
            int[] path = preparedPaths[i];
            if (path == null) continue;
            long member = dense[i];
            int slot = slots.get(roster.squad().squadId(member));
            if (slot < 0) { preparedPaths[i] = null; continue; }
            State state = states[slot];
            float startX = Paths.cellX(path, 0) + 0.5f;
            float startY = Paths.cellY(path, 0) + 0.5f;
            if (anchorMembers[i] != member || anchorOwners[i] != state
                    || anchorVersions[i] != state.laneVersion || openness[slot] < 0.9f) {
                anchorMembers[i] = member;
                anchorOwners[i] = state;
                anchorVersions[i] = state.laneVersion;
                routeBias[i] = 0f;
            } else if (anchorPaths[i] != path) {
                // Project only the change between route origins, in the old
                // local frame. Absolute world projections under a slightly
                // changing heading create a spurious sideways demand.
                routeBias[i] -= (startX - anchorX[i]) * anchorNormalX[i]
                        + (startY - anchorY[i]) * anchorNormalY[i];
            }
            anchorX[i] = startX;
            anchorY[i] = startY;
            anchorNormalX[i] = -hy[slot];
            anchorNormalY[i] = hx[slot];
            anchorPaths[i] = path;
            // Replanning starts at the displaced body. Keep the old lane as
            // reference, rather than adding another three cells on each repath.
            float translated = offsets[slot] + routeBias[i];
            memberOffsets[i] = Math.max(-3f, Math.min(3f, translated));
        }
        TickInnerProfile profile = TickInnerProfile.current();
        if (profile != null) {
            profile.recordCount(TickInnerProfile.Bucket.SQUAD_TRAFFIC_SQUAD, count);
            profile.recordCount(TickInnerProfile.Bucket.SQUAD_TRAFFIC_CANDIDATE, solver.candidateVisits());
        }
    }

    private boolean visiblePair(int a, int b) {
        // Centroids can fall inside a wall when a squad straddles a portal.
        // Conservative refusal is preferable to inventing traffic through it.
        return MovementService.trafficSegmentClear(grid, x[a], y[a], x[b], y[b]);
    }

    private float openness(State state) {
        int[] path = roster.movement().path(state.representative);
        int first = Math.max(0, roster.movement().pathIdx(state.representative) - 1);
        int end = Math.min(Paths.cellCount(path), first + LOOKAHEAD_CELLS + 1);
        float result = 1f;
        for (int i = first; i < end; i++) {
            int cx = Paths.cellX(path, i), cy = Paths.cellY(path, i);
            float width = widthAt(cx, cy, state.hx, state.hy);
            // A constraint eight cells ahead starts releasing the offset now;
            // do not wait for bodies to hit the mouth of a passage.
            result = Math.min(result, Math.max(width, Math.max(0f, (i - first - 2) / 6f)));
            if (i > first && i + 1 < end) {
                int dx = Paths.cellX(path, i + 1) - cx;
                int dy = Paths.cellY(path, i + 1) - cy;
                float length = (float) Math.hypot(dx, dy);
                if (length > 0f && (dx * state.hx + dy * state.hy) / length < 0.8f) {
                    result = Math.min(result, Math.max(0f, (i - first - 2) / 6f));
                }
            }
        }
        // Arrival retires lateral movement in MovementService. It is not a
        // terrain bottleneck and must not request queuing on open ground.
        return result;
    }

    private float widthAt(int cx, int cy, float dx, float dy) {
        if (!grid.inBounds(cx, cy) || grid.isDoorway(cx, cy)) return 0f;
        float px = cx + 0.5f, py = cy + 0.5f;
        for (int r = 1; r <= 4; r++) {
            if (!MovementService.trafficSegmentClear(grid, px, py, px - dy * r, py + dx * r)
                    || !MovementService.trafficSegmentClear(grid, px, py, px + dy * r, py - dx * r)) {
                return Math.max(0f, (r - 2f) / 3f);
            }
        }
        return 1f;
    }

    private static boolean quiet(Squad squad) {
        return !squad.isMechSquad() && !squad.isDroneSquad()
                && !squad.hasDirectContactThisTick() && !squad.advanceEngageCommitted;
    }

    /** Member-owned movement only, and only while snapshot intent still matches. */
    public boolean advance(long member, Squad squad, int goalX, int goalY, float dt) {
        if (member == controlled || !quiet(squad)) return false;
        int slot = slots.get(squad.id);
        if (slot < 0) return false;
        State state = states[slot];
        SquadPlan plan = squad.currentPlan;
        int index = roster.indexOf(member);
        if (index < 0 || index >= preparedPaths.length || plan == null || plan.currentStep() != state.step
                || squad.routingEpoch != state.epoch || goalX != state.goalX || goalY != state.goalY
                || preparedPaths[index] == null || roster.movement().path(member) != preparedPaths[index]) return false;
        float offset = memberOffsets[index];
        float speed = yielding ? speeds[slot] : 1f;
        // Zero preference still needs a legal join from a previously shifted
        // body. Never hand that return to the unswept legacy follower.
        long start = System.nanoTime();
        roster.movement().advanceAlongPath(roster.world(), member, dt,
                -hy[slot] * offset, hx[slot] * offset, speed);
        TickInnerProfile profile = TickInnerProfile.current();
        if (profile != null) profile.record(TickInnerProfile.Bucket.SQUAD_TRAFFIC_MOVE, System.nanoTime() - start);
        return true;
    }
}

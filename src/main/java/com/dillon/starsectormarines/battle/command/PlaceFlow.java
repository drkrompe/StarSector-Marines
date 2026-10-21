package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntPredicate;

/**
 * The anticipation tier for a commander with places to man and no command axis (`garrisonables.md`, flow, then local
 * assignment): believed pressure areas ({@link GarrisonFlow#areas}) that press a place pull free roving squads to wait
 * near those places before the pressure there is high enough to man them, so that local assignment finds somebody who
 * can arrive before the attack does. Side-neutral and place-general: it is given one side's areas, its warm and hot
 * places, its free squads and a reachability test, and answers which squad waits where; it knows nothing of missions,
 * forts or which side it serves. The mission defenders' {@link MissionGarrisons} is its first caller.
 *
 * <p><b>Where.</b> The staging cell is measured from the area toward the places it presses
 * ({@link GarrisonFlow#towardPlaces}), so it needs no rear. <b>Not every fight is into prepared positions</b>: an area's
 * squads alternate ({@link GarrisonFlow#nextIsForward}) between <b>back</b>, waiting at the places for local assignment
 * to man them, and <b>forward</b>, an area defence {@link GarrisonFlow#FORWARD_STANDOFF_CELLS} from the attack toward
 * the places, which meets the attack in the street and is never sent to man. {@link #FORWARD_PROPERTY} off stages every
 * squad back.
 *
 * <p><b>How many.</b> {@link GarrisonFlow#demand} per area less what the area already has local — squads waiting for
 * it, squads manning in it, free squads within {@link GarrisonFlow#LOCAL_ETA_TICKS} of its staging cell; pickets before
 * massing ({@link GarrisonFlow#sendOrder}); at most {@link #MAX_STAGE_PER_PLAN} a plan and
 * {@link #stagedCap} in all. An area that stays quiet for {@link #COOL_PLANS} plans gives its squads back.
 *
 * <p>Pure planning over the caller's frozen inputs; the caller turns posts into orders and forgets squads it stops
 * offering. One instance per commander.
 */
final class PlaceFlow {

    /** {@code -Dbattle.mission.flow=false} switches the mission defenders' flow tier off: reactive manning as before. */
    static final String PROPERTY = "battle.mission.flow";
    /** {@code -Dbattle.mission.flow.forward=false} stages every squad back at the places; nobody meets the attack forward. */
    static final String FORWARD_PROPERTY = "battle.mission.flow.forward";
    /** Consecutive plans an area must be quiet before its squads are given back. UNTUNED; shorter than Conquest's 20. */
    static final int COOL_PLANS = 12;
    /** Most squads newly staged in one plan, across all areas. UNTUNED. */
    static final int MAX_STAGE_PER_PLAN = 2;
    /** Squads tried, nearest first, for one area's post before the area is passed over this plan. */
    static final int CANDIDATES_TRIED = 3;
    /** Circle of the area defence a back squad waits on. */
    static final int STAGE_RADIUS_CELLS = 3;
    /** Circle of the area defence a forward squad meets the attack on: wide enough to fight in, not to wander. UNTUNED. */
    static final int FORWARD_RADIUS_CELLS = 5;
    /** Rings searched for a cell to stand on around the desired one. */
    static final int SNAP_RADIUS = 8;

    /**
     * Largest share of the roving pool waiting at once. Half, where Conquest's larger pool keeps 40%: a mission's
     * reserve is a handful of squads, and at 40% a pool of four could stage only one. The other half stays where the
     * mission keeps it. UNTUNED.
     */
    static final float MAX_STAGED_SHARE = 0.5f;

    /**
     * How many squads may wait at once from a roving pool of this size: half of it, rounded down, so a lone reserve squad
     * stays where the mission keeps it. This, not the reserve floor, bounds staging: a waiting squad is not spent, it is
     * the reserve standing nearer the pressure, and a mission's reserve of two would otherwise never stage at all.
     */
    static int stagedCap(int poolSize) {
        return (int) (poolSize * MAX_STAGED_SHARE);
    }

    static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty(PROPERTY, "true"));
    }

    static boolean forwardEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(FORWARD_PROPERTY, "true"));
    }

    /** A free squad that may be staged: not manning, not on a duty, not in a fight, strong enough. */
    record Candidate(int squadId, int x, int y) { }

    /** One squad's waiting order, and whether it waits forward. */
    static final class Post {
        final ObjectiveAssignment order;
        final boolean forward;
        final int areaX;
        final int areaY;
        int quietPlans;

        Post(ObjectiveAssignment order, boolean forward, int areaX, int areaY) {
            this.order = order;
            this.forward = forward;
            this.areaX = areaX;
            this.areaY = areaY;
        }
    }

    private final Map<Integer, Post> posts = new TreeMap<>();
    int stageOrders;
    int forwardOrders;
    int releases;
    int peakStaged;

    /** The squad's post, or {@code null}. */
    Post post(int squadId) {
        return posts.get(squadId);
    }

    /** Every squad with a post, in id order. */
    Map<Integer, Post> posts() {
        return posts;
    }

    /** The squad no longer waits: it was sent to man, taken back by its commander, or is gone. */
    void forget(int squadId) {
        posts.remove(squadId);
    }

    /** The area (index into {@code areas}) the squad waits for, or -1. */
    static int areaOf(Post post, List<GarrisonFlow.Area> areas) {
        return post == null ? -1 : GarrisonFlow.matchingArea(areas, post.areaX, post.areaY);
    }

    /**
     * Ages every post against this plan's areas and gives back those whose area has been quiet for {@link #COOL_PLANS}
     * plans. Returns the squads given back.
     */
    List<Integer> cool(List<GarrisonFlow.Area> areas) {
        List<Integer> out = new ArrayList<>();
        for (Map.Entry<Integer, Post> entry : new ArrayList<>(posts.entrySet())) {
            Post post = entry.getValue();
            if (areaOf(post, areas) >= 0) {
                post.quietPlans = 0;
                continue;
            }
            if (++post.quietPlans < COOL_PLANS) continue;
            posts.remove(entry.getKey());
            out.add(entry.getKey());
            releases++;
        }
        return out;
    }

    /**
     * Stages free squads for the areas that press a place and lack squads.
     *
     * @param warm      places under flow pressure this plan ({@link GarrisonDirector#warmPlaces})
     * @param hot       places under manning pressure ({@link GarrisonDirector#hotPlaces})
     * @param manned    whether a place is manned (by our order or its own garrison)
     * @param heldIn    squads manning in each area, by area index (the caller's holders)
     * @param free      squads that may be staged, in a stable order
     * @param poolSize  the roving pool the staged share is measured against
     * @param stand     whether a cell may be stood on
     * @param quiet     whether a cell is quiet enough to wait on
     * @return the squads newly staged this plan
     */
    List<Integer> stage(List<GarrisonFlow.Area> areas, int[] warm, int[] hot, IntPredicate manned,
                        GarrisonableRegistry places, int[] heldIn, List<Candidate> free, int poolSize,
                        HeldGround.Reach reach, GarrisonFlow.CellOk stand, GarrisonFlow.CellOk quiet) {
        int n = areas.size();
        List<Integer> out = new ArrayList<>();
        if (n == 0) return out;
        // What each area presses: the mean of its unmanned warm places (of all of them once every one is manned).
        long[] sumX = new long[n];
        long[] sumY = new long[n];
        int[] count = new int[n];
        long[] allX = new long[n];
        long[] allY = new long[n];
        int[] all = new int[n];
        for (int place : warm) {
            int a = GarrisonFlow.matchingArea(areas, places.centreX(place), places.centreY(place));
            if (a < 0) continue;
            allX[a] += places.centreX(place);
            allY[a] += places.centreY(place);
            all[a]++;
            if (manned.test(place)) continue;
            sumX[a] += places.centreX(place);
            sumY[a] += places.centreY(place);
            count[a]++;
        }
        int[] unmannedHot = new int[n];
        for (int place : hot) {
            if (manned.test(place)) continue;
            int a = GarrisonFlow.matchingArea(areas, places.centreX(place), places.centreY(place));
            if (a >= 0) unmannedHot[a]++;
        }
        int[] back = new int[n];
        int[] forward = new int[n];
        for (Post post : posts.values()) {
            int a = areaOf(post, areas);
            if (a < 0) continue;
            if (post.forward) forward[a]++;
            else back[a]++;
        }
        int[][] target = new int[n][];
        int[][] stage = new int[n][];
        for (int i = 0; i < n; i++) {
            GarrisonFlow.Area area = areas.get(i);
            if (all[i] == 0 || area.weight() < GarrisonFlow.AREA_START_WEIGHT) continue;
            target[i] = count[i] > 0
                    ? new int[]{(int) (sumX[i] / count[i]), (int) (sumY[i] / count[i])}
                    : new int[]{(int) (allX[i] / all[i]), (int) (allY[i] / all[i])};
            stage[i] = backCell(area, target[i], 0, stand, quiet);
        }
        // Free squads already near an area's staging cell are local to it: nothing needs to move for them.
        int[] nearby = new int[n];
        for (Candidate squad : free) {
            int best = -1;
            int bestEta = Integer.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (stage[i] == null) continue;
                int eta = GarrisonDirector.etaTicks(squad.x(), squad.y(), stage[i][0], stage[i][1]);
                if (eta <= GarrisonFlow.LOCAL_ETA_TICKS && eta < bestEta) {
                    bestEta = eta;
                    best = i;
                }
            }
            if (best >= 0) nearby[best]++;
        }
        int[] unmet = new int[n];
        int[] local = new int[n];
        for (int i = 0; i < n; i++) {
            if (stage[i] == null) continue;
            int held = i < heldIn.length ? heldIn[i] : 0;
            back[i] += held + nearby[i];
            local[i] = back[i] + forward[i];
            unmet[i] = Math.max(0, GarrisonFlow.demand(areas.get(i).weight(), unmannedHot[i]) - local[i]);
        }
        int allowed = Math.max(0, stagedCap(poolSize) - posts.size());
        int[] order = GarrisonFlow.sendOrder(unmet, local, Math.min(MAX_STAGE_PER_PLAN, allowed));
        boolean[] used = new boolean[free.size()];
        boolean[] failed = new boolean[n];
        for (int i : order) {
            if (failed[i]) continue;
            GarrisonFlow.Area area = areas.get(i);
            int[] meet = forwardEnabled() && GarrisonFlow.nextIsForward(back[i], forward[i])
                    ? GarrisonFlow.forwardOf(area.centreX(), area.centreY(), target[i][0], target[i][1]) : null;
            boolean isForward = meet != null;
            int[] cell = isForward ? GarrisonFlow.snap(meet[0], meet[1], SNAP_RADIUS, stand)
                    : back[i] == 0 ? stage[i] : backCell(area, target[i], back[i], stand, quiet);
            if (cell == null) {
                failed[i] = true;
                continue;
            }
            int pick = nearest(free, used, cell, reach);
            if (pick < 0) {
                failed[i] = true;
                continue;
            }
            used[pick] = true;
            Candidate squad = free.get(pick);
            ObjectiveAssignment orderOf = ObjectiveAssignment.defendArea(squad.squadId(), cell[0], cell[1],
                    isForward ? FORWARD_RADIUS_CELLS : STAGE_RADIUS_CELLS);
            posts.put(squad.squadId(), new Post(orderOf, isForward, area.centreX(), area.centreY()));
            out.add(squad.squadId());
            if (isForward) {
                forward[i]++;
                forwardOrders++;
            } else {
                back[i]++;
                stageOrders++;
            }
        }
        peakStaged = Math.max(peakStaged, posts.size());
        return out;
    }

    private static int[] backCell(GarrisonFlow.Area area, int[] target, int spread, GarrisonFlow.CellOk stand,
                                  GarrisonFlow.CellOk quiet) {
        int[] desired = GarrisonFlow.towardPlaces(area.centreX(), area.centreY(), target[0], target[1], spread);
        int[] cell = GarrisonFlow.snap(desired[0], desired[1], SNAP_RADIUS,
                (x, y) -> stand.test(x, y) && quiet.test(x, y));
        return cell != null ? cell : GarrisonFlow.snap(desired[0], desired[1], SNAP_RADIUS, stand);
    }

    /** The nearest unused candidate (of the first {@link #CANDIDATES_TRIED} by distance) that can reach the cell, or -1. */
    private static int nearest(List<Candidate> free, boolean[] used, int[] cell, HeldGround.Reach reach) {
        List<Integer> byDistance = new ArrayList<>();
        for (int i = 0; i < free.size(); i++) if (!used[i]) byDistance.add(i);
        byDistance.sort(Comparator.<Integer>comparingLong(i -> {
            long dx = free.get(i).x() - cell[0];
            long dy = free.get(i).y() - cell[1];
            return dx * dx + dy * dy;
        }).thenComparingInt(i -> free.get(i).squadId()));
        int tried = 0;
        for (int i : byDistance) {
            if (tried++ >= CANDIDATES_TRIED) break;
            Candidate squad = free.get(i);
            if (reach.to(squad.x(), squad.y(), cell[0], cell[1])) return i;
        }
        return -1;
    }
}

package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * How a pack fights once it is on its quarry (`colony-breach-nouns.md`, Pack
 * movement: the press). A body has room round it for only so many Runners to
 * strike at once, its <b>strike slots</b>. The members holding them
 * <b>hold</b>: they wheel round the quarry inside their reach, biting as they
 * pass, rather than planting. The rest <b>wait</b>: they circle a wider ring
 * the other way, weaving in and out, and swap in as a holder's turn ends. A
 * pack with more on one body than its slots and a reserve peels the surplus
 * off as a <b>sub-pack</b> onto the nearest other prey it can see from there;
 * the sub-pack's <b>lead</b> is the member nearest that quarry. A sub-pack
 * re-merges with its pack the moment its quarry dies, is the pack's own
 * quarry, or it is down to a lone body.
 *
 * <p>{@link #prepare} runs serially at the end of {@link SwarmPackPlans#prepare},
 * from tick-start positions and targets, and is the only writer; the parallel
 * unit update reads roles and assignments through {@link SwarmPackPlans}.
 * {@code -Dbattle.swarm.packSplit=false} is the control: every body in reach
 * plants and strikes, and every member takes the pack's quarry.
 */
final class SwarmPackPress {

    /** A member whose target is this close is in the press. */
    static final float PRESS_RANGE = 4.5f;
    /** Gap a striking Runner leaves past touching its quarry's body. */
    static final float SLOT_REACH = 0.25f;
    /** Room round the ring each striker takes beyond its own width. */
    static final float SLOT_GAP = 0.75f;
    static final int MIN_SLOTS = 2;
    static final int MAX_SLOTS = 8;
    /** Members kept on a saturated quarry beyond its slots, to cycle in. */
    static final int RESERVE = 1;
    /** Ticks a holder keeps its slot while others wait for it. */
    static final int TURN_TICKS = 45;
    /** Ticks a member that gave up its slot waits before it may take one again. */
    static final int YIELD_TICKS = 30;
    /** Cells from the quarry a waiting member circles at, and how far it weaves in and out. */
    static final float WAIT_RADIUS = 3f;
    static final float WAIT_WEAVE = 0.7f;
    /** How far round the ring ahead of itself a member aims, in cells of arc. */
    static final float CARROT_CELLS = 1.1f;
    /** Pace while holding (wheeling inside reach) and while waiting (circling outside). */
    static final float HOLD_PACE = 0.45f;
    static final float WAIT_PACE = 0.75f;
    /** Cells from a pack's quarry within which it looks for other prey for a sub-pack. */
    static final float SPLIT_RADIUS = 12f;
    /** Least ticks between one pack's splits. */
    static final int SPLIT_COOLDOWN_TICKS = 30;

    /** A member's part in the press this tick. */
    enum Role { NONE, HOLD, WAIT }

    /** One sub-pack: members of pack {@code packId} sent at {@code quarry}, headed by {@code lead}. */
    static final class SubPack {
        final int packId;
        final long quarry;
        long lead;
        long[] members;

        SubPack(int packId, long quarry, long lead, long[] members) {
            this.packId = packId;
            this.quarry = quarry;
            this.lead = lead;
            this.members = members;
        }
    }

    private final long seed;
    /** Body to {role ordinal, tick the role began}. */
    private final Map<Long, int[]> roles = new HashMap<>();
    /** Body to the press it is in: {quarry, ring direction}, for the movement read. */
    private final Map<Long, long[]> pressOf = new HashMap<>();
    /** Last tick's {@link #pressOf}, which a holder's turn and a yield are counted against. */
    private Map<Long, long[]> lastPressOf = new HashMap<>();
    /** Sub-packs by pack id, oldest first. */
    private final Map<Integer, List<SubPack>> subPacks = new TreeMap<>();
    /** Body to the sub-pack it runs with. */
    private final Map<Long, SubPack> subOf = new HashMap<>();
    private final Map<Integer, Integer> lastSplit = new HashMap<>();
    private final LongBucket scratch = new LongBucket();

    SwarmPackPress(long seed) {
        this.seed = seed;
    }

    // ---- read by the parallel update ----

    Role role(long body) {
        int[] r = roles.get(body);
        return r == null ? Role.NONE : Role.values()[r[0]];
    }

    /** The quarry of the press {@code body} is in, or 0. */
    long pressQuarry(long body) {
        long[] p = pressOf.get(body);
        return p == null ? 0L : p[0];
    }

    /** +1 or -1: which way round its quarry the body's ring turns. */
    int ringDirection(long body) {
        long[] p = pressOf.get(body);
        return p == null ? 1 : (int) p[1];
    }

    /** The quarry {@code body}'s sub-pack was sent at, or 0 for a body running with its pack. */
    long assignedQuarry(long body) {
        SubPack sub = subOf.get(body);
        return sub == null ? 0L : sub.quarry;
    }

    /** Whether {@code body} runs in a sub-pack it does not lead: it takes the quarry without a cry of its own. */
    boolean followsLead(long body) {
        SubPack sub = subOf.get(body);
        return sub != null && sub.lead != body;
    }

    List<SubPack> subPacksOf(int packId) {
        List<SubPack> list = subPacks.get(packId);
        return list == null ? List.of() : list;
    }

    // ---- serial ----

    void clear() {
        roles.clear();
        pressOf.clear();
        lastPressOf.clear();
        subPacks.clear();
        subOf.clear();
        lastSplit.clear();
    }

    /** How many Runners can strike {@code target} at once: the ring round its body, a Runner's width apiece. */
    static int strikeSlots(BattleSimulation sim, long target, long striker) {
        float ring = sim.getRoster().radius(target) + sim.getRoster().radius(striker) + SLOT_REACH;
        float width = 2f * sim.getRoster().radius(striker) + SLOT_GAP;
        int slots = (int) Math.floor(2 * Math.PI * ring / width);
        return Math.max(MIN_SLOTS, Math.min(MAX_SLOTS, slots));
    }

    void prepare(BattleSimulation sim, Map<Integer, SwarmPackPlans.Plan> plans, int[] ids) {
        int tick = sim.getSimTickIndex();
        retireSubPacks(sim, plans);

        // Everyone in a press, grouped by quarry over every pack, so two packs on one body share its slots.
        TreeMap<Long, List<Long>> engaged = new TreeMap<>();
        Map<Long, Long> anyStriker = new HashMap<>();
        for (int id : ids) {
            SwarmPackPlans.Plan plan = plans.get(id);
            if (plan == null || plan.anchor == 0L) continue;
            for (int i = 0; i < plan.count; i++) {
                long body = plan.members[i];
                long target = sim.resolveUnit(sim.combat().targetId(body));
                if (target == 0L || !sim.world().isAlive(target)) continue;
                float distance = distance(sim, body, target);
                if (distance > PRESS_RANGE) continue;
                if (!subOf.containsKey(body) && !pressing(plan, i, distance, tick)) continue;
                engaged.computeIfAbsent(target, k -> new ArrayList<>()).add(body);
                anyStriker.putIfAbsent(target, body);
            }
        }
        Map<Long, int[]> nextRoles = new HashMap<>();
        lastPressOf = new HashMap<>(pressOf);
        pressOf.clear();
        for (Map.Entry<Long, List<Long>> entry : engaged.entrySet()) {
            long target = entry.getKey();
            List<Long> bodies = entry.getValue();
            bodies.sort(Long::compare);
            int slots = strikeSlots(sim, target, anyStriker.get(target));
            allocate(sim, target, bodies, slots, tick, nextRoles);
            long direction = (mix(seed ^ target * 0x9E3779B97F4A7C15L) & 1L) == 0L ? 1L : -1L;
            for (long body : bodies) {
                boolean hold = nextRoles.get(body)[0] == Role.HOLD.ordinal();
                pressOf.put(body, new long[] {target, hold ? direction : -direction});
            }
        }
        roles.clear();
        roles.putAll(nextRoles);

        for (int id : ids) {
            SwarmPackPlans.Plan plan = plans.get(id);
            if (plan == null || plan.anchor == 0L) continue;
            split(sim, plan, engaged, tick);
        }
    }

    /**
     * Whether member {@code i} fights in the press this tick rather than on the
     * pack's hunt: once committed, or with its victim right in front of it. A
     * sub-pack's members have no hunt to wait on and always press.
     */
    private static boolean pressing(SwarmPackPlans.Plan plan, int i, float distance, int tick) {
        if (distance <= SwarmPackMovement.COMMIT_RANGE) return true;
        switch (plan.phase) {
            case TRAVEL: return true;
            case RUSH: return tick >= plan.commitTick[i];
            default: return false;
        }
    }

    /**
     * Gives out a quarry's slots: a holder keeps its slot until its turn is up
     * while others wait for one, then the longest-waiting member takes it.
     */
    private void allocate(BattleSimulation sim, long target, List<Long> bodies, int slots, int tick,
                          Map<Long, int[]> next) {
        if (bodies.size() <= slots) {
            for (long body : bodies) next.put(body, keep(body, Role.HOLD, tick));
            return;
        }
        List<Long> holding = new ArrayList<>();
        List<Long> expired = new ArrayList<>();
        List<Long> waiting = new ArrayList<>();
        for (long body : bodies) {
            int[] r = roles.get(body);
            boolean held = r != null && r[0] == Role.HOLD.ordinal() && pressQuarryWas(body, target);
            if (held && tick - r[1] < TURN_TICKS) holding.add(body);
            else if (held) expired.add(body);
            else waiting.add(body);
        }
        // Longest waiting first; one that only just gave up its slot sits a while out.
        waiting.sort((a, b) -> {
            int sa = since(a, tick), sb = since(b, tick);
            if (sa != sb) return Integer.compare(sa, sb);
            int c = Float.compare(distance(sim, a, target), distance(sim, b, target));
            return c != 0 ? c : Long.compare(a, b);
        });
        int free = slots - Math.min(slots, holding.size());
        List<Long> taking = new ArrayList<>();
        for (long body : waiting) {
            if (free == 0) break;
            int[] r = roles.get(body);
            boolean yielding = r != null && r[0] == Role.WAIT.ordinal() && tick - r[1] < YIELD_TICKS
                    && pressQuarryWas(body, target);
            if (yielding) continue;
            taking.add(body);
            free--;
        }
        // Nobody ready to take a slot: the holders whose turn is up keep theirs.
        while (free > 0 && !expired.isEmpty()) {
            holding.add(expired.remove(0));
            free--;
        }
        for (long body : holding) next.put(body, keep(body, Role.HOLD, tick));
        for (long body : taking) next.put(body, new int[] {Role.HOLD.ordinal(), tick});
        for (long body : bodies) {
            if (next.containsKey(body)) continue;
            next.put(body, keep(body, Role.WAIT, tick));
        }
    }

    private boolean pressQuarryWas(long body, long target) {
        long[] p = lastPressOf.get(body);
        return p != null && p[0] == target;
    }

    private int since(long body, int tick) {
        int[] r = roles.get(body);
        return r == null ? tick : r[1];
    }

    private int[] keep(long body, Role role, int tick) {
        int[] r = roles.get(body);
        return r != null && r[0] == role.ordinal() ? r : new int[] {role.ordinal(), tick};
    }

    /** Dissolves every sub-pack whose quarry is gone or is the pack's own, or that is down to one body. */
    private void retireSubPacks(BattleSimulation sim, Map<Integer, SwarmPackPlans.Plan> plans) {
        for (Map.Entry<Integer, List<SubPack>> entry : subPacks.entrySet()) {
            SwarmPackPlans.Plan plan = plans.get(entry.getKey());
            entry.getValue().removeIf(sub -> {
                sub.members = Arrays.stream(sub.members)
                        .filter(b -> sim.resolveUnit(b) != 0L && plan != null && plan.indexOf(b) >= 0)
                        .toArray();
                long quarry = sim.resolveUnit(sub.quarry);
                boolean done = plan == null || plan.anchor == 0L
                        || quarry == 0L || !sim.world().isAlive(quarry)
                        || sim.swarmHarvest().isCarried(quarry)
                        || quarry == sim.resolveUnit(plan.quarry)
                        || quarry == sim.resolveUnit(sim.combat().targetId(plan.anchor))
                        || sub.members.length < SwarmPackPlans.MIN_SUB_PACK;
                if (!done && Arrays.stream(sub.members).noneMatch(b -> b == sub.lead)) sub.lead = sub.members[0];
                return done;
            });
        }
        subPacks.values().removeIf(List::isEmpty);
        subOf.clear();
        for (List<SubPack> list : subPacks.values()) {
            for (SubPack sub : list) for (long body : sub.members) subOf.put(body, sub);
        }
    }

    /**
     * Peels the surplus off a saturated quarry: members beyond its slots and
     * the reserve, nearest the other prey first, go at it as a sub-pack.
     */
    private void split(BattleSimulation sim, SwarmPackPlans.Plan plan, Map<Long, List<Long>> engaged, int tick) {
        long quarry = sim.resolveUnit(sim.combat().targetId(plan.anchor));
        if (quarry == 0L || !sim.world().isAlive(quarry)) return;
        List<Long> onQuarry = engaged.get(quarry);
        if (onQuarry == null) return;
        int slots = strikeSlots(sim, quarry, onQuarry.get(0));
        if (onQuarry.size() < slots) return;
        Integer last = lastSplit.get(plan.id);
        if (last != null && tick - last < SPLIT_COOLDOWN_TICKS) return;
        // The pack's own bodies hunting this quarry, those not holding a slot first.
        List<Long> main = new ArrayList<>();
        for (int i = 0; i < plan.count; i++) {
            long body = plan.members[i];
            if (body == plan.anchor || subOf.containsKey(body)) continue;
            if (sim.swarmHarvest().isCarrier(body)) continue;
            if (sim.resolveUnit(sim.combat().targetId(body)) != quarry) continue;
            main.add(body);
        }
        int holders = 0;
        List<Long> spare = new ArrayList<>();
        for (long body : main) {
            if (role(body) == Role.HOLD && onQuarry.contains(body)) holders++;
            else spare.add(body);
        }
        // Keep the reserve nearest the quarry; the anchor and the other packs' bodies may fill slots too.
        spare.sort((a, b) -> {
            int c = Float.compare(distance(sim, a, quarry), distance(sim, b, quarry));
            return c != 0 ? c : Long.compare(a, b);
        });
        int keep = Math.min(spare.size(), RESERVE);
        List<Long> surplus = new ArrayList<>(spare.subList(keep, spare.size()));
        if (surplus.size() < SwarmPackPlans.MIN_SUB_PACK) return;

        List<Long> taken = new ArrayList<>();
        taken.add(quarry);
        for (SubPack sub : subPacksOf(plan.id)) taken.add(sim.resolveUnit(sub.quarry));
        boolean split = false;
        while (surplus.size() >= SwarmPackPlans.MIN_SUB_PACK) {
            long other = nextQuarry(sim, quarry, surplus, taken);
            if (other == 0L) break;
            int room = strikeSlots(sim, other, surplus.get(0)) + RESERVE;
            final long target = other;
            surplus.sort((a, b) -> {
                int c = Float.compare(distance(sim, a, target), distance(sim, b, target));
                return c != 0 ? c : Long.compare(a, b);
            });
            int size = Math.min(room, surplus.size());
            if (surplus.size() - size == 1) size = surplus.size();
            long[] members = new long[size];
            for (int i = 0; i < size; i++) members[i] = surplus.get(i);
            surplus.subList(0, size).clear();
            SubPack sub = new SubPack(plan.id, other, members[0], members);
            Arrays.sort(sub.members);
            subPacks.computeIfAbsent(plan.id, k -> new ArrayList<>()).add(sub);
            for (long body : sub.members) subOf.put(body, sub);
            taken.add(other);
            split = true;
        }
        if (split) lastSplit.put(plan.id, tick);
    }

    /**
     * The nearest prey to the surplus, other than those already taken, within
     * {@link #SPLIT_RADIUS} of the pack's quarry and in sight from it: what the
     * pack can see from where it fights.
     */
    private long nextQuarry(BattleSimulation sim, long quarry, List<Long> surplus, List<Long> taken) {
        float qx = sim.world().x(quarry);
        float qy = sim.world().y(quarry);
        float cx = 0f, cy = 0f;
        for (long body : surplus) {
            cx += sim.world().x(body);
            cy += sim.world().y(body);
        }
        cx /= surplus.size();
        cy /= surplus.size();
        boolean shelterProtected = sim.isCivilianShelterProtected();
        long best = 0L;
        float bestDistance = Float.MAX_VALUE;
        for (Faction prey : SwarmPressureBehavior.PREY) {
            sim.getUnitIndex().gatherFaction(qx, qy, SPLIT_RADIUS + 1.5f, prey, scratch);
            for (int i = 0; i < scratch.size; i++) {
                long candidate = scratch.ids[i];
                if (taken.contains(candidate) || sim.resolveUnit(candidate) == 0L
                        || !sim.world().isAlive(candidate) || sim.isRiding(candidate)) continue;
                if (!SwarmPressureBehavior.eligiblePreyKnownFaction(candidate, shelterProtected, sim)) continue;
                float x = sim.world().x(candidate);
                float y = sim.world().y(candidate);
                if (Math.hypot(x - qx, y - qy) > SPLIT_RADIUS) continue;
                if (!SwarmPressureBehavior.canSense((int) Math.floor(qx), (int) Math.floor(qy), x, y,
                        SPLIT_RADIUS, sim)) continue;
                float d = (x - cx) * (x - cx) + (y - cy) * (y - cy);
                if (d < bestDistance || (d == bestDistance && candidate < best)) {
                    best = candidate;
                    bestDistance = d;
                }
            }
        }
        return best;
    }

    static float distance(BattleSimulation sim, long a, long b) {
        return SwarmPackPlans.distance(sim, a, b);
    }

    private static long mix(long value) {
        return SwarmPackPlans.mix(value);
    }
}

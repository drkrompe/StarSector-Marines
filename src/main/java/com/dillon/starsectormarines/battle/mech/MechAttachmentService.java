package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.modifier.UnitModifier;
import com.dillon.starsectormarines.battle.modifier.UnitStat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;

/**
 * Battle-local mailbox and state owner for <b>attachments</b>: a player mech squad that follows one
 * player infantry squad ({@code mechs-nouns.md}, attachment).
 *
 * <p>The UI queues raw requests; {@link MechAttachmentSystem} validates them in the serial command
 * phase, which is also the only place the relation changes. The parallel unit pass only reads it,
 * plus the per-chassis slot cache each mech writes for itself.
 *
 * <p>Keyed by the mech squad: after every player drop lands as its own one-mech squad the relation is
 * effectively per mech, and a legacy multi-mech squad attaches whole. Several mech squads may follow
 * one infantry squad.
 */
public final class MechAttachmentService {

    /** Sentinel for "not attached", matching {@code Squad.NO_SQUAD}'s convention. */
    public static final int NONE = -1;

    /** One raw UI request: attach {@code mechSquadId} to {@code anchorSquadId}, or detach it when that is {@link #NONE}. */
    record Request(int mechSquadId, int anchorSquadId) { }

    private final List<Request> pending = new ArrayList<>();
    /** Mech squad id to the infantry squad it follows, in id order so serial passes iterate deterministically. */
    private final Map<Integer, Integer> anchors = new TreeMap<>();
    /** Each attached chassis's rank among same-role followers of its anchor, rebuilt serially every tick. */
    private final Map<Long, Integer> ranks = new ConcurrentHashMap<>();
    /** The last useful axis of each anchor (unit vector), kept when the anchor stops so followers hold their sides. */
    private final Map<Integer, float[]> axes = new ConcurrentHashMap<>();
    /** The slot cell each chassis is walking to; written by that chassis alone in the unit pass. */
    private final Map<Long, int[]> slots = new ConcurrentHashMap<>();
    /** Anchor members marching at their followers' pace, to the speed multiplier that holds them to it. */
    private final Map<Long, Float> paced = new ConcurrentHashMap<>();
    private static final Set<UnitStat> PACE_STATS = Set.of(UnitStat.MOVE_SPEED);

    /** Queues an attachment of the mech squad to the infantry squad, validated on the next fixed tick. */
    public void requestAttach(int mechSquadId, int anchorSquadId) {
        pending.add(new Request(mechSquadId, anchorSquadId));
    }

    /** Queues the release of the mech squad's attachment, if it has one. */
    public void requestDetach(int mechSquadId) {
        pending.add(new Request(mechSquadId, NONE));
    }

    /** The infantry squad this mech squad follows, or {@link #NONE}. */
    public int anchorOf(int mechSquadId) {
        Integer anchor = anchors.get(mechSquadId);
        return anchor == null ? NONE : anchor;
    }

    /** Whether the mech squad follows an infantry squad. */
    public boolean isAttached(int mechSquadId) {
        return anchors.containsKey(mechSquadId);
    }

    /** The mech squads following {@code anchorSquadId}, in id order. */
    public List<Integer> followersOf(int anchorSquadId) {
        if (anchors.isEmpty()) return List.of();
        List<Integer> followers = new ArrayList<>(2);
        for (Map.Entry<Integer, Integer> entry : anchors.entrySet()) {
            if (entry.getValue() == anchorSquadId) followers.add(entry.getKey());
        }
        return followers;
    }

    /** Every standing attachment, mech squad to anchor, in mech squad id order. */
    public Map<Integer, Integer> attachments() {
        return Collections.unmodifiableMap(anchors);
    }

    public boolean hasAttachments() {
        return !anchors.isEmpty();
    }

    List<Request> drainPending() {
        if (pending.isEmpty()) return List.of();
        List<Request> drained = new ArrayList<>(pending);
        pending.clear();
        return drained;
    }

    void attach(int mechSquadId, int anchorSquadId) {
        anchors.put(mechSquadId, anchorSquadId);
    }

    void detach(int mechSquadId) {
        anchors.remove(mechSquadId);
    }

    int rankOf(long mech) {
        Integer rank = ranks.get(mech);
        return rank == null ? 0 : rank;
    }

    void setRanks(Map<Long, Integer> next) {
        ranks.clear();
        ranks.putAll(next);
    }

    float[] axisOf(int anchorSquadId) {
        return axes.get(anchorSquadId);
    }

    void setAxis(int anchorSquadId, float[] axis) {
        axes.put(anchorSquadId, axis);
    }

    void retainAxes(IntPredicate keep) {
        axes.keySet().removeIf(id -> !keep.test(id));
    }

    /**
     * The anchor's march pace as a stat modifier on its marines: out of contact, a squad with mechs
     * attached walks no faster than they can, so a Brawler can get ahead of it and stay there.
     */
    public UnitModifier paceModifier() {
        return new UnitModifier() {
            @Override public String name(long unit) { return "unitModifierPacingArmour"; }
            @Override public Set<UnitStat> stats() { return PACE_STATS; }
            @Override public boolean appliesTo(long unit) { return paced.containsKey(unit); }
            @Override public float multiplier(long unit, UnitStat stat) {
                Float pace = paced.get(unit);
                return pace == null ? 1f : pace;
            }
        };
    }

    void clearPace() {
        paced.clear();
    }

    void setPace(long member, float multiplier) {
        paced.put(member, multiplier);
    }

    int[] slotOf(long mech) {
        return slots.get(mech);
    }

    void setSlot(long mech, int[] slot) {
        if (slot == null) slots.remove(mech);
        else slots.put(mech, slot);
    }
}

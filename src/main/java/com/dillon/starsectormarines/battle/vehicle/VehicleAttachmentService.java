package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Attachments for vehicles: a player vehicle that follows one player infantry squad, as a mech does through
 * {@code MechAttachmentService} ({@code mechs-nouns.md}, attachment). Out of contact it drives to a point behind the
 * squad's centre, farther back for a standoff hull, which fights over the squad from there; a direct hull keeps
 * close. Its guns fire on their own picture as it goes. A direct order to the vehicle ends the attachment, as does its
 * squad's loss.
 *
 * <p>Requests queue and are validated in the serial command phase ({@link #tick}), the only place the relation
 * changes. Keyed by hull id, in id order so iteration is deterministic. Several vehicles may follow one squad; each
 * takes its own side of the squad's track by its rank among them.
 */
public final class VehicleAttachmentService {

    /** How far behind its squad's centre a standoff hull keeps, in cells. UNTUNED. */
    static final float STANDOFF_BEHIND_CELLS = 10f;
    /** How far behind its squad's centre any other hull keeps, in cells. UNTUNED. */
    static final float CLOSE_BEHIND_CELLS = 4f;
    /** How far to the side each follower after the first stands, alternating sides, in cells. UNTUNED. */
    static final float SIDE_STEP_CELLS = 3f;
    /** How far the point a hull should stand on may drift from its last order before it is ordered again. */
    static final float REORDER_CELLS = 4f;
    /** Fewest ticks between two orders to one hull, so a moving squad does not replan it every tick. */
    static final int REORDER_TICKS = 30;
    /** Sentinel for "not attached". */
    public static final int NONE = -1;

    private record Request(long hull, int squadId) { }

    private final List<Request> pending = new ArrayList<>();
    private final Map<Long, Integer> anchors = new TreeMap<>();
    /** Per hull: the last point ordered, and the tick it was ordered on. */
    private final Map<Long, float[]> ordered = new TreeMap<>();
    /** Per squad: its last centre and the last useful axis it moved along (unit vector). */
    private final Map<Integer, float[]> tracks = new TreeMap<>();

    /** Queues an attachment of the vehicle to the infantry squad, validated on the next fixed tick. */
    public void requestAttach(long hull, int squadId) { pending.add(new Request(hull, squadId)); }

    /** Queues the release of the vehicle's attachment, if it has one. */
    public void requestDetach(long hull) { pending.add(new Request(hull, NONE)); }

    /** The infantry squad this vehicle follows, or {@link #NONE}. */
    public int anchorOf(long hull) {
        Integer anchor = anchors.get(hull);
        return anchor == null ? NONE : anchor;
    }

    /** Every attachment, hull to squad, in hull order. */
    public Map<Long, Integer> attachments() { return Collections.unmodifiableMap(anchors); }

    /**
     * The point a follower stands on: {@code behind} cells back along the squad's axis from its centre, and
     * {@code side} cells to the axis's left (negative: right). Pure.
     */
    static float[] followPoint(float centreX, float centreY, float axisX, float axisY, float behind, float side) {
        return new float[]{centreX - axisX * behind - axisY * side, centreY - axisY * behind + axisX * side};
    }

    /** The side offset of the {@code rank}th follower of a squad: centred, then left, right, further left. Pure. */
    static float sideOffset(int rank) {
        if (rank == 0) return 0f;
        int step = (rank + 1) / 2;
        return (rank % 2 == 1 ? 1 : -1) * step * SIDE_STEP_CELLS;
    }

    /** Applies queued requests, drops attachments whose hull or squad is gone, and moves each follower. Serial. */
    public void tick(BattleSimulation sim) {
        ConvoyService convoy = sim.convoy();
        for (Request request : pending) {
            if (request.squadId() == NONE) { anchors.remove(request.hull()); ordered.remove(request.hull()); continue; }
            if (!followerValid(convoy, request.hull()) || !anchorValid(sim, request.squadId())) continue;
            anchors.put(request.hull(), request.squadId());
            ordered.remove(request.hull());
        }
        pending.clear();
        if (anchors.isEmpty()) return;
        Map<Integer, Integer> ranks = new TreeMap<>();
        VehicleMoveOrderService orders = sim.getVehicleMoveOrderService();
        int tick = sim.getSimTickIndex();
        for (var it = anchors.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            long hull = entry.getKey();
            int squadId = entry.getValue();
            float[] centre = centre(sim, squadId);
            if (!followerValid(convoy, hull) || centre == null) {
                it.remove();
                ordered.remove(hull);
                continue;
            }
            float[] axis = axis(squadId, centre);
            int rank = ranks.merge(squadId, 1, Integer::sum) - 1;
            float behind = convoy.engagementStance(hull) == VehicleType.Stance.STANDOFF
                    ? STANDOFF_BEHIND_CELLS : CLOSE_BEHIND_CELLS;
            float[] at = followPoint(centre[0], centre[1], axis[0], axis[1], behind, sideOffset(rank));
            float[] last = ordered.get(hull);
            boolean drifted = last == null || Math.hypot(at[0] - last[0], at[1] - last[1]) > REORDER_CELLS;
            if (orders == null || !drifted || (last != null && tick - (int) last[2] < REORDER_TICKS)) continue;
            int x = Math.max(0, Math.min(sim.getGrid().getWidth() - 1, (int) Math.floor(at[0])));
            int y = Math.max(0, Math.min(sim.getGrid().getHeight() - 1, (int) Math.floor(at[1])));
            orders.requestMove(hull, x, y);
            ordered.put(hull, new float[]{at[0], at[1], tick});
        }
    }

    private static boolean followerValid(ConvoyService convoy, long hull) {
        return convoy.isVehicle(hull) && convoy.isPresent(hull) && convoy.faction(hull) == Faction.MARINE;
    }

    private static boolean anchorValid(BattleSimulation sim, int squadId) {
        Squad squad = sim.getSquad(squadId);
        return squad != null && squad.faction == Faction.MARINE && !squad.isMechSquad() && !squad.isDroneSquad()
                && centre(sim, squadId) != null;
    }

    /** The centre of the squad's living members, or {@code null} when none lives. */
    private static float[] centre(BattleSimulation sim, int squadId) {
        float x = 0f, y = 0f;
        int alive = 0;
        for (int i = 0, n = sim.squadMemberCount(squadId); i < n; i++) {
            long member = sim.squadMemberAt(squadId, i);
            if (member == 0L || !sim.world().hasPosition(member) || !sim.world().isAlive(member)) continue;
            x += sim.world().x(member);
            y += sim.world().y(member);
            alive++;
        }
        return alive == 0 ? null : new float[]{x / alive, y / alive};
    }

    /** The squad's axis: the way its centre last moved a useful distance, east until it has moved. */
    private float[] axis(int squadId, float[] centre) {
        float[] track = tracks.get(squadId);
        if (track == null) {
            tracks.put(squadId, new float[]{centre[0], centre[1], 1f, 0f});
            return new float[]{1f, 0f};
        }
        float dx = centre[0] - track[0], dy = centre[1] - track[1];
        float length = (float) Math.hypot(dx, dy);
        if (length >= 2f) {
            track[0] = centre[0];
            track[1] = centre[1];
            track[2] = dx / length;
            track[3] = dy / length;
        }
        return new float[]{track[2], track[3]};
    }
}

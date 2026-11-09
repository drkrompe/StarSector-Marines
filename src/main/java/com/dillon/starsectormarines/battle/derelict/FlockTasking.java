package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.drone.FlockTask;
import com.dillon.starsectormarines.battle.derelict.FlockCommandPort.Contact;
import com.dillon.starsectormarines.battle.derelict.FlockCommandPort.Report;

import java.util.List;

/**
 * Which task the network gives one flock this pulse (`derelict-lore-guide.md`, Tactics and command): the flocks are
 * the alarm and the screen. A pure rule over what the flock's home zone knows.
 *
 * <ul>
 *   <li>A flock under a third of its strength returns to its cradle, whatever else holds.</li>
 *   <li>In the <b>contingency</b> a flock whose node stands strikes the haul's escort, wherever it is: the leash is
 *       off.</li>
 *   <li>A flock of a dormant zone stays docked (or goes home if it is up).</li>
 *   <li>A flock of an alert zone goes up and screens over its node.</li>
 *   <li>A flock of an awake zone strikes the nearest contact inside its perimeter, pursues one between the perimeter
 *       and the leash, screens the perimeter's edge toward one beyond the leash, and with no contact probes toward
 *       where its zone was stirred.</li>
 * </ul>
 */
public final class FlockTasking {

    /** Below this share of its full strength a flock goes home to re-form. */
    public static final float RETURN_STRENGTH = 1f / 3f;
    /** Cells past its zone's perimeter a flock may go to probe, pursue and screen. UNTUNED. */
    public static final int LEASH_CELLS = 14;

    /** The task, its anchor and its target (-1 none); a null order leaves a docked flock docked. */
    public record Order(FlockTask task, int anchorX, int anchorY, long targetUnitId) { }

    private FlockTasking() {
    }

    /**
     * @param contacts what the network holds: its flocks' and its units' contacts
     * @param haul     the haul's escort, nearest first preferred by distance
     * @param stirX    where the zone was last stirred, for a probe with nothing seen
     */
    public static Order decide(Report flock, SecurityZone zone, boolean contingency, List<Contact> contacts,
                               List<Contact> haul, int stirX, int stirY) {
        if (flock.strength() < RETURN_STRENGTH) return homeward(flock, zone);
        if (contingency && zone.online() && !haul.isEmpty()) {
            Contact target = nearest(haul, flock.cellX(), flock.cellY());
            return new Order(FlockTask.STRIKE, target.cellX(), target.cellY(), target.unitId());
        }
        switch (zone.state()) {
            case DORMANT:
                return homeward(flock, zone);
            case ALERT:
                return new Order(FlockTask.SCREEN, zone.nodeCellX, zone.nodeCellY, -1L);
            default:
                break;
        }
        Contact target = nearest(contacts, zone.nodeCellX, zone.nodeCellY);
        if (target == null) return new Order(FlockTask.PROBE, stirX, stirY, -1L);
        int reach = WalkerPosture.chebyshev(target.cellX(), target.cellY(), zone.nodeCellX, zone.nodeCellY);
        if (reach <= zone.perimeterCells) {
            return new Order(FlockTask.STRIKE, target.cellX(), target.cellY(), target.unitId());
        }
        if (reach <= zone.perimeterCells + LEASH_CELLS) {
            return new Order(FlockTask.PURSUE, target.cellX(), target.cellY(), target.unitId());
        }
        int[] edge = edgeToward(zone, target.cellX(), target.cellY());
        return new Order(FlockTask.SCREEN, edge[0], edge[1], -1L);
    }

    private static Order homeward(Report flock, SecurityZone zone) {
        return flock.docked() ? null : new Order(FlockTask.RETURN, zone.nodeCellX, zone.nodeCellY, -1L);
    }

    /** The point on the zone's perimeter on the line from its node toward a cell. */
    static int[] edgeToward(SecurityZone zone, int x, int y) {
        int dx = x - zone.nodeCellX;
        int dy = y - zone.nodeCellY;
        int span = Math.max(Math.abs(dx), Math.abs(dy));
        if (span == 0) return new int[] {zone.nodeCellX, zone.nodeCellY};
        float scale = Math.min(1f, zone.perimeterCells / (float) span);
        return new int[] {zone.nodeCellX + Math.round(dx * scale), zone.nodeCellY + Math.round(dy * scale)};
    }

    private static Contact nearest(List<Contact> contacts, int x, int y) {
        Contact best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Contact contact : contacts) {
            int distance = WalkerPosture.chebyshev(contact.cellX(), contact.cellY(), x, y);
            if (distance < bestDistance || (distance == bestDistance && contact.unitId() < best.unitId())) {
                best = contact;
                bestDistance = distance;
            }
        }
        return best;
    }
}

package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.derelict.FlockCommandPort.Contact;

import java.util.List;

/**
 * Where a derelict walker stands and what it does this pulse (`derelict-lore-guide.md`, Tactics and command): the
 * heavies are the anchor. A pure rule over what the walker's zone knows; the director turns the answer into an order.
 *
 * <ul>
 *   <li>A walker in a dormant or alert zone holds its post.</li>
 *   <li>In an awake zone a <b>line frame</b> comes to the nearest unit a flock has pinned inside its own perimeter,
 *       and otherwise holds its post; it never follows past the perimeter.</li>
 *   <li>A <b>vault frame</b> comes only to a pin within {@link #VAULT_REACH_CELLS} of its post: the vault's last
 *       guard, an interior guardian, does not leave the vault, in the contingency either.</li>
 *   <li>A <b>siege frame</b> holds its post and fires on the freshest contact the flocks see within its reach.</li>
 *   <li>In the <b>contingency</b> a line frame whose node stands walks to the egress cell nearest its post and holds
 *       it, through its own ground and past its perimeter; a siege frame fires on the haul's escort first; the vault
 *       frame stays on the vault and fights what comes into its reach.
 *       A zone whose node is dead is out of the call and keeps its own posture.</li>
 * </ul>
 */
public final class WalkerPosture {

    /** How far from its post, in cells, a vault frame comes to a pin. UNTUNED. */
    public static final int VAULT_REACH_CELLS = 10;

    public enum Kind {
        /** Stand at the post. */
        HOLD,
        /** Close on a pinned unit at {@code cell}. */
        CLOSE,
        /** Stand at the post and fire on the spotted unit at {@code cell}. */
        FIRE,
        /** Walk to the egress cell and hold it. */
        HOLD_EGRESS
    }

    /** The answer: what to do, the cell it is about, and the unit (-1 none). */
    public record Order(Kind kind, int cellX, int cellY, long unitId) {

        static Order hold(int x, int y) {
            return new Order(Kind.HOLD, x, y, -1L);
        }
    }

    private WalkerPosture() {
    }

    /**
     * @param frame       the walker's loadout
     * @param zone        the zone it belongs to
     * @param contingency whether the vault has been breached
     * @param postX       its post: a choke or court of its layer
     * @param pins        units flocks are striking, anywhere
     * @param sightings   contacts flocks see, anywhere
     * @param haul        units of the haul's escort, for the siege frame's contingency priority
     * @param egress      egress cells as {@code {x, y}}; empty when the mission has none
     * @param reachCells  the siege frame's reach, in cells
     */
    public static Order decide(DerelictWalkerFrame frame, SecurityZone zone, boolean contingency,
                               int postX, int postY, List<Contact> pins, List<Contact> sightings,
                               List<Long> haul, List<int[]> egress, float reachCells) {
        boolean called = contingency && zone.nodeAlive();
        if (frame.spots()) {
            if (zone.state() != SecurityZone.State.AWAKE && !called) return Order.hold(postX, postY);
            Contact spot = called ? freshest(sightings, haul, postX, postY, reachCells) : null;
            if (spot == null) spot = freshest(sightings, null, postX, postY, reachCells);
            return spot == null ? Order.hold(postX, postY)
                    : new Order(Kind.FIRE, spot.cellX(), spot.cellY(), spot.unitId());
        }
        if (called && frame != DerelictWalkerFrame.VAULT && !egress.isEmpty()) {
            int[] best = nearest(egress, postX, postY);
            return new Order(Kind.HOLD_EGRESS, best[0], best[1], -1L);
        }
        if (zone.state() != SecurityZone.State.AWAKE && !called) return Order.hold(postX, postY);
        Contact pin = null;
        int pinDistance = Integer.MAX_VALUE;
        for (Contact contact : pins) {
            if (!zone.inside(contact.cellX(), contact.cellY())) continue;
            int distance = chebyshev(contact.cellX(), contact.cellY(), postX, postY);
            if (frame == DerelictWalkerFrame.VAULT && distance > VAULT_REACH_CELLS) continue;
            if (distance < pinDistance || (distance == pinDistance && contact.unitId() < pin.unitId())) {
                pin = contact;
                pinDistance = distance;
            }
        }
        return pin == null ? Order.hold(postX, postY)
                : new Order(Kind.CLOSE, pin.cellX(), pin.cellY(), pin.unitId());
    }

    /** The most recently seen contact within reach of the post, among {@code only} when given; ties by unit id. */
    private static Contact freshest(List<Contact> sightings, List<Long> only, int postX, int postY, float reach) {
        Contact best = null;
        for (Contact contact : sightings) {
            if (only != null && !only.contains(contact.unitId())) continue;
            float dx = contact.cellX() - postX;
            float dy = contact.cellY() - postY;
            if (dx * dx + dy * dy > reach * reach) continue;
            if (best == null || contact.observedTick() > best.observedTick()
                    || (contact.observedTick() == best.observedTick() && contact.unitId() < best.unitId())) {
                best = contact;
            }
        }
        return best;
    }

    static int[] nearest(List<int[]> cells, int x, int y) {
        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int[] cell : cells) {
            int distance = chebyshev(cell[0], cell[1], x, y);
            if (distance < bestDistance) {
                best = cell;
                bestDistance = distance;
            }
        }
        return best;
    }

    static int chebyshev(int ax, int ay, int bx, int by) {
        return Math.max(Math.abs(ax - bx), Math.abs(ay - by));
    }
}

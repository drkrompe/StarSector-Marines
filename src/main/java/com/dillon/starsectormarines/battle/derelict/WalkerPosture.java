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
 *   <li>A <b>rounds walker</b> (`derelict-watch.md`) walks its zone's round while the zone is dormant or alert, and
 *       awake comes to the nearest pin inside its perimeter like a line frame, walking the round when it holds none;
 *       in the contingency it walks to the egress as a line frame does.</li>
 *   <li>A <b>hall frame</b> keeps its hall as a line frame keeps its layer, and in the contingency walks to the egress
 *       only where its way there fits it; otherwise it holds its hall.</li>
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
        HOLD_EGRESS,
        /** Walk the zone's round: on to the round's place at {@code cell}. */
        ROUND,
        /** A hall frame shut in its hall: walk to the dead door at {@code cell} and force it. */
        FORCE
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
        return decide(frame, zone, contingency, postX, postY, null, true, pins, sightings, haul, egress, reachCells);
    }

    /**
     * As the other {@code decide}, for every frame of the line.
     *
     * @param round      the place of the zone's round the walker is on, {@code {x, y}}; null with no round (a rounds
     *                   walker then holds its post)
     * @param egressFits whether the walker's way to the egress fits it (a hall frame's may not)
     */
    public static Order decide(DerelictWalkerFrame frame, SecurityZone zone, boolean contingency,
                               int postX, int postY, int[] round, boolean egressFits, List<Contact> pins,
                               List<Contact> sightings, List<Long> haul, List<int[]> egress, float reachCells) {
        boolean called = contingency && zone.online();
        Order idle = frame == DerelictWalkerFrame.ROUNDS && round != null
                ? new Order(Kind.ROUND, round[0], round[1], -1L) : Order.hold(postX, postY);
        if (frame.spots()) {
            if (zone.state() != SecurityZone.State.AWAKE && !called) return Order.hold(postX, postY);
            Contact spot = called ? freshest(sightings, haul, postX, postY, reachCells) : null;
            if (spot == null) spot = freshest(sightings, null, postX, postY, reachCells);
            return spot == null ? Order.hold(postX, postY)
                    : new Order(Kind.FIRE, spot.cellX(), spot.cellY(), spot.unitId());
        }
        if (called && frame != DerelictWalkerFrame.VAULT && !egress.isEmpty() && egressFits) {
            int[] best = nearest(egress, postX, postY);
            return new Order(Kind.HOLD_EGRESS, best[0], best[1], -1L);
        }
        if (zone.state() != SecurityZone.State.AWAKE && !called) return idle;
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
        return pin == null ? idle : new Order(Kind.CLOSE, pin.cellX(), pin.cellY(), pin.unitId());
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

package com.dillon.starsectormarines.battle.derelict;

import java.util.List;

/**
 * The MG-12 tender (`derelict-watch.md`, Field repair): the derelict line's smallest walker, unarmed, whose welding arm
 * lays plate back onto its zone's walkers in the fight. The rules are here; the director walks the tender and lays what
 * it welds.
 *
 * <p>While its zone sleeps it stands at its post. Once the zone is awake (or the contingency is on) it takes the zone's
 * most worn walker below {@link #TENDER_BELOW} as its charge and keeps it until the charge is back to
 * {@link #TENDER_WHOLE} or gone; with no charge it shadows the most worn walker, ready. Within {@link #TENDER_REACH} of
 * its charge it welds at {@link #TENDER_RATE} from the stock it carries. Unlike the repair drone it keeps working under
 * fire, and it never raises a wreck. Dry, it walks to its zone's service bay for another load; with the bay gone it
 * goes back to its post and stays.
 */
public final class FieldTender {

    /** A walker whose plate is below this share of its whole becomes the tender's charge. UNTUNED. */
    public static final float TENDER_BELOW = 0.85f;
    /** A charge back to this share of its plate is let go. */
    public static final float TENDER_WHOLE = 0.98f;
    /** How near its charge (cells, either axis, centre to centre) the tender welds: past a heavy's flank. UNTUNED. */
    public static final float TENDER_REACH = 3.5f;
    /** Plate a second the tender welds onto its charge. UNTUNED. */
    public static final float TENDER_RATE = 8f;
    /** Plate the tender carries at once: its first load, and each load from its bay. UNTUNED. */
    public static final float TENDER_STOCK = 400f;

    public enum Kind {
        /** Stand at the post. */
        STAND_BY,
        /** Walk to the point: a charge out of reach, the walker it shadows, or the bay to reload at. */
        FOLLOW,
        /** Weld plate onto {@code entityId}, in reach. */
        WELD,
        /** Take on a load at the bay, in reach. */
        RELOAD
    }

    /** What the tender does now: where it walks, and whom it welds (0 none). */
    public record Goal(Kind kind, float x, float y, long entityId) { }

    private float stock = TENDER_STOCK;
    private long charge;

    public float stock() {
        return stock;
    }

    /** The walker the tender has taken in hand, or 0. */
    public long charge() {
        return charge;
    }

    /**
     * The tender's goal this pulse.
     *
     * @param awake   whether its zone is awake or the contingency on
     * @param charges its zone's walkers but itself, each with the share of its plate it keeps
     * @param bay     its zone's service bay while it stands, or null
     */
    public Goal decide(float x, float y, float postX, float postY, boolean awake, List<RepairDrone.Charge> charges,
                       RepairDrone.Bay bay) {
        if (stock <= 0f) {
            charge = 0L;
            if (bay == null || bay.stock() <= 0f) return new Goal(Kind.STAND_BY, postX, postY, 0L);
            Kind kind = within(x, y, bay.x(), bay.y()) ? Kind.RELOAD : Kind.FOLLOW;
            return new Goal(kind, bay.x(), bay.y(), 0L);
        }
        if (!awake) {
            charge = 0L;
            return new Goal(Kind.STAND_BY, postX, postY, 0L);
        }
        RepairDrone.Charge held = null;
        RepairDrone.Charge worst = null;
        for (RepairDrone.Charge c : charges) {
            if (c.entityId() == charge && c.share() < TENDER_WHOLE) held = c;
            if (worst == null || c.share() < worst.share()) worst = c;
        }
        if (held == null) {
            charge = 0L;
            if (worst != null && worst.share() < TENDER_BELOW) {
                held = worst;
                charge = worst.entityId();
            }
        }
        if (held == null) {
            if (worst == null) return new Goal(Kind.STAND_BY, postX, postY, 0L);
            return new Goal(Kind.FOLLOW, worst.x(), worst.y(), 0L);
        }
        Kind kind = within(x, y, held.x(), held.y()) ? Kind.WELD : Kind.FOLLOW;
        return new Goal(kind, held.x(), held.y(), held.entityId());
    }

    private static boolean within(float x, float y, float tx, float ty) {
        return Math.max(Math.abs(x - tx), Math.abs(y - ty)) <= TENDER_REACH;
    }

    /** Draws up to {@code seconds} of welding from the load; returns the plate to lay. */
    public float draw(float seconds) {
        float points = Math.min(stock, TENDER_RATE * seconds);
        stock -= points;
        return points;
    }

    /** Puts back what could not be laid (the charge was already whole). */
    public void refund(float points) {
        stock += Math.max(0f, points);
    }

    /** Takes on a load at the bay: as much as the bay gives toward a full {@link #TENDER_STOCK}. */
    public void reload(ServiceBay bay) {
        stock += bay.issue(TENDER_STOCK - stock);
    }
}

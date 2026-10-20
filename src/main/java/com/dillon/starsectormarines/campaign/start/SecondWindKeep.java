package com.dillon.starsectormarines.campaign.start;

import java.util.Comparator;
import java.util.List;

/**
 * The rule that the Second Wind is never lost for good (owner, 2026-10-20), as pure decisions.
 *
 * <p>She is the company's founding hull and the one ship the story is written around, so no outcome takes
 * her away for good: selling and scuttling her are undone, recovery off a held field is always offered, and a
 * loss sends her to a yard. Losing her still costs something: she comes back with one more d-mod, a repair
 * bill and some days in that yard. {@link SecondWindKeeper} applies these decisions to the campaign.
 */
public final class SecondWindKeep {

    /** Days she spends in the yard after a loss before she can be collected. */
    public static final int YARD_DAYS = 21;

    /** The yard's bill as a share of her hull's base value: 2,250 credits on a Cerberus. */
    public static final float YARD_BILL_SHARE = 0.25f;

    /** D-mods a loss adds while she is under {@link #D_MOD_CAP}. */
    public static final int D_MODS_PER_LOSS = 1;

    /** Vanilla's ceiling on d-mods: combat never takes a hull past five. */
    public static final int D_MOD_CAP = 5;

    private SecondWindKeep() { }

    /** What to do about her this frame. */
    public enum Action {
        /** She is where she should be: in the fleet, in storage the player chose, or in the yard. */
        NONE,
        /** She is missing, but a dialog or screen is still open and may yet put her back (recovery). */
        WAIT,
        /** She went down in a fight and nobody brought her home: the yard takes her. */
        SEND_TO_YARD,
        /** She left the fleet outside a fight (scuttled, given away, a respawn): she comes straight back. */
        RETURN_TO_FLEET
    }

    /**
     * Where she has gone and what follows.
     *
     * @param inFleet she is in the player's fleet
     * @param inStorage she is in a storage submarket, which is the player's to choose
     * @param inYard she is already in a yard after a loss
     * @param casualty she was destroyed or disabled in the player's last engagement
     * @param uiBusy a dialog or core screen is open, so recovery may still be underway
     */
    public static Action decide(boolean inFleet, boolean inStorage, boolean inYard, boolean casualty,
                                boolean uiBusy) {
        if (inFleet || inStorage || inYard) return Action.NONE;
        if (uiBusy) return Action.WAIT;
        return casualty ? Action.SEND_TO_YARD : Action.RETURN_TO_FLEET;
    }

    /** D-mods a loss adds to a hull already carrying {@code current}; none once she is at the cap. */
    public static int dModsToAdd(int current) {
        return Math.max(0, Math.min(D_MODS_PER_LOSS, D_MOD_CAP - Math.max(0, current)));
    }

    /** The yard's bill for a hull of this base value. */
    public static int yardBill(float hullBaseValue) {
        return Math.max(0, Math.round(Math.max(0f, hullBaseValue) * YARD_BILL_SHARE));
    }

    /** Whether she can be collected once {@code daysInYard} have passed since the loss. */
    public static boolean ready(float daysInYard) {
        return daysInYard >= YARD_DAYS;
    }

    /** Whole days still to wait, never negative; zero means she is ready. */
    public static int daysLeft(float daysInYard) {
        return Math.max(0, (int) Math.ceil(YARD_DAYS - daysInYard));
    }

    /**
     * How much of a scuttling's yield to take back: what her scuttling gives, bounded by what the holds
     * actually gained since she was last seen, so nothing the fleet owned before is taken.
     */
    public static float scuttleClawback(float yield, float before, float now) {
        return Math.max(0f, Math.min(Math.max(0f, yield), now - before));
    }

    /**
     * A port that could take her in.
     *
     * @param startPort the company's start port, which she knows
     * @param friendly independent, the player's own, or a faction on good terms
     * @param distance from where she was lost, in any consistent unit
     */
    public record Port(String marketId, boolean startPort, boolean friendly, float distance) { }

    /**
     * The yard she goes to: the start port while it still has a yard open to the company, else the nearest
     * friendly or independent yard, else the nearest one that will have her at all. Null when there is none,
     * and the candidates are only ports that are eligible in the first place.
     */
    public static Port pickYard(List<Port> eligible) {
        if (eligible == null || eligible.isEmpty()) return null;
        for (Port port : eligible) if (port.startPort()) return port;
        Comparator<Port> nearest = Comparator.comparingDouble(Port::distance).thenComparing(Port::marketId);
        return eligible.stream().filter(Port::friendly).min(nearest)
                .orElseGet(() -> eligible.stream().min(nearest).orElse(null));
    }
}

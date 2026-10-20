package com.dillon.starsectormarines.campaign.start;

/**
 * The rule that the Second Wind is never lost for good (owner, 2026-10-20), as pure decisions.
 *
 * <p>She is the company's founding hull and the ship the campaign is written around. She can be lost - in a
 * fight, sold, scuttled - but never for good: once she is gone, any port with a yard offers her back for a bill,
 * one d-mod worse, through the buy-back screen that stands in for Marine operations until she is home. Campaign
 * missions are flown from her: they need her in the fleet with the company aboard. {@link SecondWindKeeper}
 * applies these decisions to the campaign.
 */
public final class SecondWindKeep {

    /** The buy-back bill as a share of her hull's base value: 2,250 credits on a Cerberus. */
    public static final float BUY_BACK_SHARE = 0.25f;

    /** D-mods a loss adds while she is under {@link #D_MOD_CAP}. */
    public static final int D_MODS_PER_LOSS = 1;

    /** Vanilla's ceiling on d-mods: combat never takes a hull past five. */
    public static final int D_MOD_CAP = 5;

    private SecondWindKeep() { }

    /** How she left the fleet, which decides Castle's story and the bill. */
    public enum Cause {
        /** Lost in a fight and not recovered: salvagers towed her into a yard. */
        BATTLE,
        /** Sold at a market. */
        SOLD,
        /** Scuttled from the fleet screen. */
        SCUTTLED,
        /** Gone some other way (a quest took her, another mod): treated like a sale. */
        OTHER;

        /** Whether Castle finds her on the open market, as against a yard after a fight. */
        public boolean onTheMarket() { return this != BATTLE; }
    }

    /** What to do about her this frame. */
    public enum Action {
        /** She is where she should be: in the fleet, in storage the player chose, or already recorded lost. */
        NONE,
        /** She is missing, but a dialog or screen is still open and may yet put her back (recovery). */
        WAIT,
        /** She is gone: record the loss, and offer her back at the next port. */
        LOSE
    }

    /**
     * @param inFleet she is in the player's fleet
     * @param inStorage she is in a storage submarket, which is the player's to choose
     * @param lost her loss is already recorded and waiting to be bought back
     * @param uiBusy a dialog or core screen is open, so recovery may still be underway
     */
    public static Action decide(boolean inFleet, boolean inStorage, boolean lost, boolean uiBusy) {
        if (inFleet || inStorage || lost) return Action.NONE;
        return uiBusy ? Action.WAIT : Action.LOSE;
    }

    /** Why she went: a reported sale, then a fight she did not come home from, then salvage that turned up. */
    public static Cause cause(boolean sold, boolean casualty, boolean salvageGained) {
        if (sold) return Cause.SOLD;
        if (casualty) return Cause.BATTLE;
        return salvageGained ? Cause.SCUTTLED : Cause.OTHER;
    }

    /** D-mods a loss adds to a hull already carrying {@code current}; none once she is at the cap. */
    public static int dModsToAdd(int current) {
        return Math.max(0, Math.min(D_MODS_PER_LOSS, D_MOD_CAP - Math.max(0, current)));
    }

    /**
     * The buy-back bill: a quarter of her hull's value, and never less than what letting her go paid out (the
     * sale price, or the value of a scuttling's salvage), so selling her and buying her back is never a profit.
     */
    public static int bill(float hullBaseValue, float proceeds) {
        int share = Math.round(Math.max(0f, hullBaseValue) * BUY_BACK_SHARE);
        return Math.max(share, Math.round(Math.max(0f, proceeds)));
    }

    /**
     * What a scuttling put in the holds: her scuttling yield, bounded by what the holds actually gained since she
     * was last seen. Zero means she was not scuttled.
     */
    public static float salvageGained(float yield, float before, float now) {
        return Math.max(0f, Math.min(Math.max(0f, yield), now - before));
    }

    /** Whether campaign missions can be flown, and if not, what the player has to do first. */
    public enum Gate {
        /** She is in the fleet and the company lives aboard her. */
        OPEN,
        /** She is lost and waiting to be bought back. */
        LOST,
        /** She is in storage. */
        STORED,
        /** She is in the fleet, but the company has not been quartered aboard her. */
        NOT_ABOARD
    }

    /**
     * @param secondWindStart this campaign began with the Second Wind; other starts are never gated
     * @param inFleet she is in the player's fleet
     * @param lost her loss is recorded
     * @param companyAboard the company's designated ship is her
     */
    public static Gate gate(boolean secondWindStart, boolean inFleet, boolean lost, boolean companyAboard) {
        if (!secondWindStart) return Gate.OPEN;
        if (lost) return Gate.LOST;
        if (!inFleet) return Gate.STORED;
        return companyAboard ? Gate.OPEN : Gate.NOT_ABOARD;
    }
}

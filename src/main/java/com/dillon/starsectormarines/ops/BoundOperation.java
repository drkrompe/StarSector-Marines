package com.dillon.starsectormarines.ops;

/**
 * The rule for a dialog the campaign opened for one operation: a raid, a ruins expedition or an invasion handed to
 * the company from the game's own menus ({@code ui-nouns.md}, Bound operation). The contract board is not this
 * dialog's business, so its route on the page bar leads to the operation's briefing instead; the ship's rooms stay
 * open for last adjustments; and once the operation has settled the panel closes, so the campaign commits it at once.
 */
final class BoundOperation {

    private BoundOperation() {
    }

    /**
     * Where a request for {@code requested} goes.
     *
     * @param bound whether this dialog was opened for one operation
     * @param pending whether that operation is still to be fought (its mission is still selected)
     */
    static ScreenId route(ScreenId requested, boolean bound, boolean pending) {
        if (!bound || requested != ScreenId.MISSION_SELECT) return requested;
        return pending ? ScreenId.BRIEFING : ScreenId.COMPANY_HQ;
    }

    /** Where the briefing's Back goes: the board for ordinary work, the company for a bound operation. */
    static ScreenId briefingBack(boolean bound, boolean debug) {
        if (debug) return ScreenId.DEBUG_MISSIONS;
        return bound ? ScreenId.COMPANY_HQ : ScreenId.MISSION_SELECT;
    }

    /** The string key naming the board's route while it leads to this operation, or null for the board's own. */
    static String legendKey(MissionSource source) {
        if (source == null) return null;
        return switch (source) {
            case MARKET_RAID -> "openingUiMarineOpsPageNavRaid";
            case RUINS_EXPEDITION -> "openingUiMarineOpsPageNavExpedition";
            case NEX_INVASION -> "openingUiMarineOpsPageNavInvasion";
            default -> "openingUiMarineOpsPageNavOperation";
        };
    }
}

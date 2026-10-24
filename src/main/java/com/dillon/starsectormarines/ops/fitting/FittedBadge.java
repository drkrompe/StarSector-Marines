package com.dillon.starsectormarines.ops.fitting;

import com.dillon.starsectormarines.i18n.Strings;

/**
 * The one badge a stores card or a socket's slot card wears, in every fitting room: FITTED, on
 * the part the selected machine already carries, and nothing on any other card.
 *
 * <p>A slot card may instead carry a warning of its own (a factory-locked socket, a required
 * part missing); that is the socket's state, not what is on hand.
 *
 * <p>The rest a card says without words. A card with none on hand is greyed and shows its
 * bill; a bill the hold cannot meet is the bill's own red chips.
 */
public final class FittedBadge {

    private FittedBadge() { }

    public static String label(boolean fitted) {
        return fitted ? Strings.get("openingMechLabFitted") : "";
    }

    public static String classes(boolean fitted) {
        return fitted ? "catalog-badge in-cargo" : "catalog-badge hidden";
    }

    /** The same badge on a socket's slot card. */
    public static String slotClasses(boolean fitted) {
        return fitted ? "slot-badge armed" : "slot-badge hidden";
    }
}

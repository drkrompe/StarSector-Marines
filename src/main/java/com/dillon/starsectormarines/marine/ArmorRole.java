package com.dillon.starsectormarines.marine;

import org.json.JSONException;

import java.util.Locale;

/**
 * What a marine wearing this pattern is <b>for</b> — a closed vocabulary, not a
 * quality band ({@code role-and-access.md}, {@code powered-assault-armor-roles.md}).
 *
 * <p><b>Role is not tier.</b> A role names a job that a squad needs filled at
 * every level of equipment it can afford: a company that grows rich buys better
 * scouts, it does not stop having them. The catalog does not yet honour that —
 * most roles exist at a single tier — and closing this vocabulary is what makes
 * the gap nameable, because a free-text label cannot be counted.
 *
 * <p><b>Role is not provenance either.</b> Pirate manufacture and a garrison
 * billet are real distinctions the catalog already carries elsewhere — in a
 * pattern's tradition and its price — and neither earns a role of its own. The
 * labels this vocabulary replaced included two of exactly that kind, which is
 * how five display words collapse to four jobs plus the unpowered kit.
 */
public enum ArmorRole {

    /** Unpowered field kit. Not a job — the absence of a suit, kept nameable so it is not mistaken for one. */
    UNPOWERED("unpowered"),

    /** Recon and sensing: sealed, fast, lightly protected, out ahead of everyone else. */
    RECON("recon"),

    /** Riflemen. The bulk of any squad, and the role every other one is measured against. */
    LINE("line"),

    /** Entry and breaching: getting through a door that is being defended. */
    ASSAULT("assault"),

    /** The heavy or indirect weapon, and whatever else the squad needs carried for it. */
    SUPPORT("support");

    /** The key an armour catalog entry declares. */
    public final String key;

    ArmorRole(String key) {
        this.key = key;
    }

    /**
     * @param armorId named in the failure so an author knows which catalog entry
     *                to fix, in the style the rest of the equipment catalogs use.
     */
    public static ArmorRole parse(String key, String armorId) throws JSONException {
        if (key != null) {
            String normalized = key.trim().toLowerCase(Locale.ROOT);
            for (ArmorRole role : values()) {
                if (role.key.equals(normalized)) return role;
            }
        }
        throw new JSONException("Armor '" + armorId + "' has unknown role '" + key
                + "'. Known roles: " + keys()
                + ". A role is the job a marine in this pattern does, not how good"
                + " the pattern is and not who made it — price is the tier and"
                + " provenance is the tradition.");
    }

    /** The declarable keys, for a parse failure that teaches the vocabulary. */
    public static String keys() {
        StringBuilder out = new StringBuilder();
        for (ArmorRole role : values()) {
            if (out.length() > 0) out.append(", ");
            out.append(role.key);
        }
        return out.toString();
    }

    /** Title-cased for the Armory card. */
    public String displayName() {
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }
}

package com.dillon.starsectormarines.marine;

import org.json.JSONException;

import java.util.Locale;

/**
 * Who builds a pattern — its provenance, as data rather than as a sentence in
 * its description ({@code role-and-access.md},
 * {@code powered-assault-armor-roles.md}).
 *
 * <p>This is the third axis, and it is deliberately independent of the other
 * two. A tradition is not a quality: the Blackforge shops turn out a tier-II rig
 * and a tier-III one and both are recognisably theirs. A tradition is not a role
 * either: the Hegemony builds a scout suit, a line suit, a battlesuit and a
 * weapons carrier, and they are all Hegemony.
 *
 * <p>What it is for is <b>resolution</b>. A squad's armour plan says what jobs
 * its billets do and whose kit it draws on; the tradition is what turns "a recon
 * suit" into a Janus rather than a Pathfinder, without the plan naming a pattern
 * and thereby fixing its tier as well ({@link SquadArmorPlan}).
 */
public enum ArmorTradition {

    /** Frontier assembly, station surplus, salvage, and whatever the crew had aboard. */
    INDEPENDENT("independent"),
    HEGEMONY("hegemony"),
    TRITACHYON("tritachyon"),
    PERSEAN("persean"),
    LUDDIC_CHURCH("luddic_church"),
    KNIGHTS_OF_LUDD("knights_of_ludd"),
    LUDDIC_PATH("luddic_path"),
    SINDRIAN_DIKTAT("sindrian_diktat"),
    LIONS_GUARD("lions_guard"),
    PIRATES("pirates");

    /** The key an armour catalog entry declares. */
    public final String key;

    ArmorTradition(String key) {
        this.key = key;
    }

    /** Vanilla faction-definition logo used when this tradition appears in inspection UI. */
    public String factionLogo() {
        return switch (this) {
            case INDEPENDENT -> "graphics/factions/neutral_traders.png";
            case HEGEMONY -> "graphics/factions/hegemony.png";
            case TRITACHYON -> "graphics/factions/tritachyon.png";
            case PERSEAN -> "graphics/factions/persean_league.png";
            case LUDDIC_CHURCH, KNIGHTS_OF_LUDD -> "graphics/factions/luddic_church.png";
            case LUDDIC_PATH -> "graphics/factions/luddic_path.png";
            case SINDRIAN_DIKTAT -> "graphics/factions/sindrian_diktat.png";
            case LIONS_GUARD -> "graphics/factions/lg.png";
            case PIRATES -> "graphics/factions/pirates.png";
        };
    }

    /**
     * @param armorId named in the failure so an author knows which catalog entry
     *                to fix.
     */
    public static ArmorTradition parse(String key, String armorId) throws JSONException {
        if (key != null) {
            String normalized = key.trim().toLowerCase(Locale.ROOT);
            for (ArmorTradition tradition : values()) {
                if (tradition.key.equals(normalized)) return tradition;
            }
        }
        throw new JSONException("Armor '" + armorId + "' has unknown tradition '" + key
                + "'. Known traditions: " + keys()
                + ". A tradition is who builds the pattern, not how good it is and not"
                + " what job its wearer does.");
    }

    /** The declarable keys, for a parse failure that teaches the vocabulary. */
    public static String keys() {
        StringBuilder out = new StringBuilder();
        for (ArmorTradition tradition : values()) {
            if (out.length() > 0) out.append(", ");
            out.append(tradition.key);
        }
        return out.toString();
    }
}

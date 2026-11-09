package com.dillon.starsectormarines.battle.appearance;

import java.util.Locale;

/**
 * Appearance-only paint family for the modular mech chassis and authored
 * weapon casings. The campaign faction id selects a livery; the livery never
 * selects geometry, loadout, stats, doctrine, or tactical allegiance.
 */
public enum MechLivery {
    /** Unpainted production assets and the fail-safe for an incomplete family. */
    BASE(null),
    HEGEMONY("hegemony"),
    TRI_TACHYON("tri-tachyon"),
    PERSEAN_LEAGUE("persean-league"),
    LUDDIC_CHURCH("luddic-church"),
    KNIGHTS_OF_LUDD("knights-of-ludd"),
    LUDDIC_PATH("luddic-path"),
    SINDRIAN_DIKTAT("sindrian-diktat"),
    LIONS_GUARD("lions-guard"),
    PIRATES("pirates"),
    INDEPENDENT("independent");

    private final String assetFolder;

    MechLivery(String assetFolder) {
        this.assetFolder = assetFolder;
    }

    /**
     * Whether this livery wears bodies of its own with legs to match, rather than the shared outlines repainted: the
     * low-tech bodies (the Hegemony's masters and the Independent and pirate paints of them, as the Hegemony vehicle
     * line is painted; art-source/mech-modular-topdown/sources/lowtech) and Tri-Tachyon's high-tech row
     * (sources/hightech). Its folder then holds a {@code foot.png} and a {@code thigh-bone.png}; every other livery
     * walks on the shared legs.
     */
    public boolean ownsLegs() {
        return this == HEGEMONY || this == INDEPENDENT || this == PIRATES || this == TRI_TACHYON;
    }

    /** Folder below {@code factions/}, or {@code null} for the base assets. */
    public String assetFolder() {
        return assetFolder;
    }

    /**
     * Resolves exact vanilla campaign ids at the campaign/presentation seam.
     * Unknown and modded human factions use the authored Independent/mercenary
     * practical fallback. Automated factions are deliberately not painted as
     * human mercenaries; blank identity keeps the neutral base art.
     */
    public static MechLivery forFactionId(String factionId) {
        if (factionId == null || factionId.isBlank()) return BASE;
        switch (factionId.trim().toLowerCase(Locale.ROOT)) {
            case "hegemony": return HEGEMONY;
            case "tritachyon": return TRI_TACHYON;
            case "persean": return PERSEAN_LEAGUE;
            case "luddic_church": return LUDDIC_CHURCH;
            case "knights_of_ludd": return KNIGHTS_OF_LUDD;
            case "luddic_path": return LUDDIC_PATH;
            case "sindrian_diktat": return SINDRIAN_DIKTAT;
            case "lions_guard": return LIONS_GUARD;
            case "pirates": return PIRATES;
            case "independent":
            case "mercenary":
                return INDEPENDENT;
            case "remnant":
            case "derelict":
            case "omega":
                return BASE;
            default:
                return INDEPENDENT;
        }
    }
}

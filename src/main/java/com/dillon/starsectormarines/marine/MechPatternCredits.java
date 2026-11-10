package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.FactionMechLoadouts;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.HashMap;
import java.util.Map;

/**
 * Who builds each chassis and vehicle pattern, in the voice of the loadout cards: the faction
 * whose crest it wears and the procurement line it comes off. The credit is presentation; which
 * faction's markets weight a pattern up is {@link MechPatternPicker}'s, and the two agree.
 */
public final class MechPatternCredits {

    /**
     * @param factionId the faction whose crest the pattern wears, or {@code "independent"} /
     *                  {@code "pirates"} for a field rig
     * @param crest     the faction's square crest
     * @param provenanceKey the procurement line, a strings key
     */
    public record Credit(String factionId, String crest, String provenanceKey) { }

    private static final String HEGEMONY = "graphics/factions/crest_hegemony.png";
    private static final String TRI_TACHYON = "graphics/factions/crest_tritachyon.png";
    private static final String LIONS_GUARD = "graphics/factions/crest_lg.png";
    private static final String PATH = "graphics/factions/crest_luddic_path.png";
    private static final String INDEPENDENT = "graphics/factions/crest_neutral_traders.png";
    private static final String PIRATES = "graphics/factions/crest_pirates.png";
    private static final String PERSEAN_LEAGUE = "graphics/factions/crest_persean_league.png";
    private static final String SINDRIAN_DIKTAT = "graphics/factions/crest_sindrian_diktat.png";

    private static final Map<MechPattern, Credit> CREDITS = new HashMap<>();

    static {
        // The three walkers are Ordnance Board patterns (owner: low-tech Hegemony machines whose makers make sense):
        // the Diktat builds the Warden under licence and the League copies the Bulwark, but the drawings are the Board's.
        String hegemony = FactionMechLoadouts.HEGEMONY_ID;
        chassis(MechVariant.HOUND, hegemony, HEGEMONY, "patternCreditHound");
        chassis(MechVariant.WARDEN, hegemony, HEGEMONY, "patternCreditWarden");
        chassis(MechVariant.BULWARK, hegemony, HEGEMONY, "patternCreditBulwark");
        chassis(MechVariant.CRAWLER, FactionMechLoadouts.TRI_TACHYON_ID, TRI_TACHYON, "patternCreditCrawler");
        // The low-tech variants: a pirate field rig and two Pather conversions of the Board's frames.
        chassis(MechVariant.HAULER, MechPatternPicker.PIRATES_ID, PIRATES, "patternCreditHauler");
        chassis(MechVariant.FOUNDRY_BREAKER, FactionMechLoadouts.LUDDIC_PATH_ID, PATH, "patternCreditFoundryBreaker");
        chassis(MechVariant.PILGRIM, FactionMechLoadouts.LUDDIC_PATH_ID, PATH, "patternCreditPilgrim");
        // The flamer rigs: a pirate fuel rig on the Hound frame, a Pather conversion of the Warden.
        chassis(MechVariant.TORCH, MechPatternPicker.PIRATES_ID, PIRATES, "patternCreditTorch");
        chassis(MechVariant.CENSER, FactionMechLoadouts.LUDDIC_PATH_ID, PATH, "patternCreditCenser");

        vehicle(VehicleType.HEAVY_APC, hegemony, HEGEMONY);
        vehicle(VehicleType.LRM_CARRIER, hegemony, HEGEMONY);
        vehicle(VehicleType.BREAKTHROUGH_TANK, hegemony, HEGEMONY);
        vehicle(VehicleType.MEDIUM_TANK, hegemony, HEGEMONY);
        vehicle(VehicleType.FIRE_SUPPORT_APC, FactionMechLoadouts.LIONS_GUARD_ID, LIONS_GUARD);
        vehicle(VehicleType.HOVER_SCOUT, FactionMechLoadouts.TRI_TACHYON_ID, TRI_TACHYON);
        vehicle(VehicleType.DRONE_SKIMMER, FactionMechLoadouts.TRI_TACHYON_ID, TRI_TACHYON);
        vehicle(VehicleType.HEAVY_SKIMMER, FactionMechLoadouts.TRI_TACHYON_ID, TRI_TACHYON);
        // The midline carriers: the light and medium are League yard patterns, the heavy the Diktat's line pattern.
        vehicle(VehicleType.LIGHT_CARRIER, MechPatternPicker.PERSEAN_LEAGUE_ID, PERSEAN_LEAGUE);
        vehicle(VehicleType.MEDIUM_CARRIER, MechPatternPicker.PERSEAN_LEAGUE_ID, PERSEAN_LEAGUE);
        vehicle(VehicleType.HEAVY_CARRIER, MechPatternPicker.SINDRIAN_DIKTAT_ID, SINDRIAN_DIKTAT);
        vehicle(VehicleType.LASER_TANK, FactionMechLoadouts.TRI_TACHYON_ID, TRI_TACHYON);
        vehicle(VehicleType.MILITIA_TECHNICAL, "independent", INDEPENDENT);
        vehicle(VehicleType.SUPPLY_TRUCK, "independent", INDEPENDENT);
        vehicle(VehicleType.SCOUT_CAR, "independent", INDEPENDENT);
        vehicle(VehicleType.SRM_TECHNICAL, "pirates", PIRATES);
    }

    private MechPatternCredits() { }

    private static void chassis(MechVariant variant, String factionId, String crest, String key) {
        CREDITS.put(MechPattern.of(variant), new Credit(factionId, crest, key));
    }

    private static void vehicle(VehicleType type, String factionId, String crest) {
        CREDITS.put(MechPattern.of(type), new Credit(factionId, crest, "patternCredit" + type.name()));
    }

    /** The credit for a chassis or vehicle pattern, or null for one nobody has written up. */
    public static Credit of(MechPattern pattern) {
        return pattern == null ? null : CREDITS.get(pattern);
    }
}

package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Fail-loud catalog of campaign-faction ground doctrine profiles. */
public final class GroundRosterRegistry {

    private static final Logger LOG = Global.getLogger(GroundRosterRegistry.class);

    public static final List<String> BUILTIN_CATALOGS = List.of(
            "data/marines/faction-ground-rosters.roster.json");

    private static volatile GroundRosterRegistry installed;

    private final Map<String, GroundRosterProfile> byId = new LinkedHashMap<>();
    private final Map<String, GroundRosterProfile> byFactionId = new LinkedHashMap<>();
    private String fallbackProfileId;

    public static GroundRosterRegistry installed() { return installed; }
    public static void install(GroundRosterRegistry registry) { installed = registry; }

    /** Loads after weapons and special equipment because profile references validate eagerly. */
    public static void loadBuiltins() {
        GroundRosterRegistry registry = new GroundRosterRegistry();
        for (String path : BUILTIN_CATALOGS) {
            try {
                registry.ingest(Global.getSettings().loadJSON(path, true));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load ground-roster catalog " + path, e);
            }
        }
        registry.validateCompleteness();
        install(registry);
        LOG.info("Ground-roster registry installed with " + registry.size() + " profiles");
    }

    public void ingest(JSONObject root) throws JSONException {
        String declaredFallback = root.getString("fallbackProfile");
        if (fallbackProfileId != null && !fallbackProfileId.equals(declaredFallback)) {
            throw new JSONException("Conflicting ground-roster fallback profiles '"
                    + fallbackProfileId + "' and '" + declaredFallback + "'");
        }
        fallbackProfileId = declaredFallback;

        JSONArray profiles = root.getJSONArray("profiles");
        for (int i = 0; i < profiles.length(); i++) {
            GroundRosterProfile profile = parseProfile(profiles.getJSONObject(i));
            GroundRosterProfile previous = byId.put(profile.id(), profile);
            if (previous != null) {
                throw new JSONException("Duplicate ground-roster profile id '" + profile.id() + "'");
            }
            for (String factionId : profile.factionIds()) {
                String key = normalizeFactionId(factionId);
                GroundRosterProfile priorFaction = byFactionId.put(key, profile);
                if (priorFaction != null) {
                    throw new JSONException("Faction id '" + factionId
                            + "' is assigned to both '" + priorFaction.id()
                            + "' and '" + profile.id() + "'");
                }
            }
        }
    }

    public void validateCompleteness() {
        if (fallbackProfileId == null || !byId.containsKey(fallbackProfileId)) {
            throw new IllegalStateException("Ground-roster fallback profile '"
                    + fallbackProfileId + "' is not defined");
        }
    }

    public static GroundRosterProfile resolve(String factionId) {
        GroundRosterRegistry registry = requireInstalled();
        GroundRosterProfile exact = registry.byFactionId.get(normalizeFactionId(factionId));
        return exact != null ? exact : registry.byId.get(registry.fallbackProfileId);
    }

    public static GroundRosterProfile requireProfile(String profileId) {
        GroundRosterRegistry registry = requireInstalled();
        GroundRosterProfile profile = registry.byId.get(profileId);
        if (profile == null) {
            throw new IllegalArgumentException("Unknown ground-roster profile id '"
                    + profileId + "'. Known ids: " + registry.byId.keySet());
        }
        return profile;
    }

    public int size() { return byId.size(); }

    private static GroundRosterRegistry requireInstalled() {
        GroundRosterRegistry registry = installed;
        if (registry == null) {
            throw new IllegalStateException("Ground-roster registry is not installed");
        }
        return registry;
    }

    private static GroundRosterProfile parseProfile(JSONObject json) throws JSONException {
        String id = json.getString("id");
        Set<String> factionIds = new LinkedHashSet<>();
        JSONArray factionArray = json.getJSONArray("factionIds");
        for (int i = 0; i < factionArray.length(); i++) {
            factionIds.add(factionArray.getString(i));
        }
        if (factionIds.isEmpty()) {
            throw new JSONException("Ground-roster profile '" + id + "' has no faction ids");
        }

        GroundRosterProfile.Issue bulk = parseIssue(id, "bulk", json.getJSONObject("bulk"));
        GroundRosterProfile.Issue elite = parseIssue(id, "elite", json.getJSONObject("elite"));
        List<MechVariant> heavySupport = new ArrayList<>();
        JSONArray support = json.getJSONArray("heavySupport");
        for (int i = 0; i < support.length(); i++) {
            heavySupport.add(MechVariant.fromId(support.getString(i)));
        }
        return new GroundRosterProfile(id, factionIds, bulk, elite, heavySupport);
    }

    private static GroundRosterProfile.Issue parseIssue(
            String profileId, String tier, JSONObject json) throws JSONException {
        UnitType unitType;
        try {
            unitType = UnitType.valueOf(json.getString("unitType"));
        } catch (IllegalArgumentException e) {
            throw new JSONException("Profile '" + profileId + "' " + tier
                    + " has unknown unitType: " + e.getMessage());
        }
        if (!unitType.usesInfantryTraining()) {
            throw new JSONException("Profile '" + profileId + "' " + tier
                    + " unitType must be trained infantry, got " + unitType);
        }
        return new GroundRosterProfile.Issue(
                unitType,
                parsePrimaries(profileId, tier, json.getJSONArray("primaries")),
                parseGrades(profileId, tier, json.getJSONObject("gradesByRisk")),
                parseArmor(profileId, tier, json.getJSONObject("armorByRisk")),
                parseSpecials(profileId, tier, json.getJSONObject("specialsByRisk")));
    }

    private static GroundRosterProfile.WeightedTable<MarineWeapon> parsePrimaries(
            String profileId, String tier, JSONArray array) throws JSONException {
        List<GroundRosterProfile.Entry<MarineWeapon>> entries = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.getJSONObject(i);
            String weaponId = entry.getString("id");
            if (WeaponRegistry.require(weaponId).mount != MountClass.MARINE_PRIMARY) {
                throw new JSONException("Profile '" + profileId + "' " + tier
                        + " references non-primary weapon '" + weaponId + "'");
            }
            entries.add(new GroundRosterProfile.Entry<>(
                    MarineWeapon.fromId(weaponId), entry.getInt("weight")));
        }
        return new GroundRosterProfile.WeightedTable<>(entries);
    }

    private static Map<RiskLevel, GroundRosterProfile.WeightedTable<EquipmentGrade>> parseGrades(
            String profileId, String tier, JSONObject json) throws JSONException {
        EnumMap<RiskLevel, GroundRosterProfile.WeightedTable<EquipmentGrade>> result =
                new EnumMap<>(RiskLevel.class);
        for (RiskLevel risk : RiskLevel.values()) {
            JSONArray array = json.getJSONArray(riskKey(risk));
            List<GroundRosterProfile.Entry<EquipmentGrade>> entries = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.getJSONObject(i);
                try {
                    EquipmentGrade grade = EquipmentGrade.valueOf(
                            entry.getString("id").toUpperCase(Locale.ROOT));
                    entries.add(new GroundRosterProfile.Entry<>(grade, entry.getInt("weight")));
                } catch (IllegalArgumentException e) {
                    throw new JSONException("Profile '" + profileId + "' " + tier
                            + " has unknown equipment grade: " + e.getMessage());
                }
            }
            result.put(risk, new GroundRosterProfile.WeightedTable<>(entries));
        }
        return result;
    }

    private static Map<RiskLevel, GroundRosterProfile.WeightedTable<MarineArmorPattern>> parseArmor(
            String profileId, String tier, JSONObject json) throws JSONException {
        EnumMap<RiskLevel, GroundRosterProfile.WeightedTable<MarineArmorPattern>> result =
                new EnumMap<>(RiskLevel.class);
        for (RiskLevel risk : RiskLevel.values()) {
            JSONArray array = json.getJSONArray(riskKey(risk));
            List<GroundRosterProfile.Entry<MarineArmorPattern>> entries = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.getJSONObject(i);
                try {
                    entries.add(new GroundRosterProfile.Entry<>(
                            MarineArmorPattern.fromId(entry.getString("id")),
                            entry.getInt("weight")));
                } catch (IllegalArgumentException e) {
                    throw new JSONException("Profile '" + profileId + "' " + tier
                            + " has unknown armor: " + e.getMessage());
                }
            }
            result.put(risk, new GroundRosterProfile.WeightedTable<>(entries));
        }
        return result;
    }

    private static Map<RiskLevel, GroundRosterProfile.WeightedTable<MarineSecondary>> parseSpecials(
            String profileId, String tier, JSONObject json) throws JSONException {
        EnumMap<RiskLevel, GroundRosterProfile.WeightedTable<MarineSecondary>> result =
                new EnumMap<>(RiskLevel.class);
        for (RiskLevel risk : RiskLevel.values()) {
            JSONArray array = json.getJSONArray(riskKey(risk));
            List<GroundRosterProfile.Entry<MarineSecondary>> entries = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.getJSONObject(i);
                String specialId = entry.getString("id");
                MarineSecondary handle = null;
                if (!"none".equals(specialId)) {
                    SpecialEquipmentRegistry.require(specialId);
                    handle = SpecialEquipmentRegistry.compatibilityHandle(specialId);
                    if (handle == null) {
                        throw new JSONException("Profile '" + profileId + "' " + tier
                                + " special '" + specialId + "' has no battle compatibility handle");
                    }
                }
                entries.add(new GroundRosterProfile.Entry<>(handle, entry.getInt("weight")));
            }
            result.put(risk, new GroundRosterProfile.WeightedTable<>(entries));
        }
        return result;
    }

    private static String riskKey(RiskLevel risk) {
        return risk.name().toLowerCase(Locale.ROOT);
    }

    private static String normalizeFactionId(String factionId) {
        return factionId == null ? "" : factionId.trim().toLowerCase(Locale.ROOT);
    }
}

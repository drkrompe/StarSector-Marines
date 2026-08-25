package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.catalog.CatalogSource;
import com.dillon.starsectormarines.catalog.MarineCatalogManifest.CatalogFile;
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
    private final Map<String, CatalogSource> profileSourceById = new LinkedHashMap<>();
    private final Map<String, CatalogSource> factionSourceById = new LinkedHashMap<>();
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

    public static void loadContributions(List<CatalogFile> catalogs) {
        GroundRosterRegistry registry = new GroundRosterRegistry();
        for (CatalogFile catalog : catalogs) {
            try {
                registry.ingest(catalog.loadJson(), catalog.source());
            } catch (Exception failure) {
                throw new IllegalStateException("Failed to ingest ground-roster catalog "
                        + catalog.source().describe(), failure);
            }
        }
        registry.validateCompleteness();
        install(registry);
        LOG.info("Ground-roster registry installed with " + registry.size()
                + " profiles from " + catalogs.size() + " contributed catalogs");
    }

    public void ingest(JSONObject root) throws JSONException {
        ingest(root, CatalogSource.unspecified("<in-memory ground-roster catalog>"));
    }

    public void ingest(JSONObject root, CatalogSource source) throws JSONException {
        String declaredFallback = root.optString("fallbackProfile", null);
        if (declaredFallback != null && declaredFallback.isBlank()) declaredFallback = null;
        if (declaredFallback != null && fallbackProfileId != null
                && !fallbackProfileId.equals(declaredFallback)) {
            throw new JSONException("Conflicting ground-roster fallback profiles '"
                    + fallbackProfileId + "' and '" + declaredFallback + "'");
        }
        if (declaredFallback != null) fallbackProfileId = declaredFallback;

        JSONArray profiles = root.getJSONArray("profiles");
        for (int i = 0; i < profiles.length(); i++) {
            GroundRosterProfile profile = parseProfile(profiles.getJSONObject(i));
            GroundRosterProfile previous = byId.get(profile.id());
            if (previous != null) {
                throw new JSONException("Duplicate ground-roster profile id '" + profile.id()
                        + "': first declared by " + profileSourceById.get(profile.id()).describe()
                        + ", then by " + source.describe());
            }
            byId.put(profile.id(), profile);
            profileSourceById.put(profile.id(), source);
            for (String factionId : profile.factionIds()) {
                String key = normalizeFactionId(factionId);
                GroundRosterProfile priorFaction = byFactionId.get(key);
                if (priorFaction != null) {
                    throw new JSONException("Faction id '" + factionId
                            + "' is assigned to both '" + priorFaction.id()
                            + "' (" + factionSourceById.get(key).describe() + ") and '"
                            + profile.id() + "' (" + source.describe() + ")");
                }
                byFactionId.put(key, profile);
                factionSourceById.put(key, source);
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

    private static GroundRosterProfile.WeightedTable<WeaponDef> parsePrimaries(
            String profileId, String tier, JSONArray array) throws JSONException {
        List<GroundRosterProfile.Entry<WeaponDef>> entries = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.getJSONObject(i);
            String weaponId = entry.getString("id");
            WeaponDef weapon = WeaponRegistry.require(weaponId);
            if (weapon.mount != MountClass.MARINE_PRIMARY) {
                throw new JSONException("Profile '" + profileId + "' " + tier
                        + " references non-primary weapon '" + weaponId + "'");
            }
            entries.add(new GroundRosterProfile.Entry<>(weapon, entry.getInt("weight")));
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

    private static Map<RiskLevel, GroundRosterProfile.WeightedTable<MarineArmorCatalogDef>> parseArmor(
            String profileId, String tier, JSONObject json) throws JSONException {
        EnumMap<RiskLevel, GroundRosterProfile.WeightedTable<MarineArmorCatalogDef>> result =
                new EnumMap<>(RiskLevel.class);
        for (RiskLevel risk : RiskLevel.values()) {
            JSONArray array = json.getJSONArray(riskKey(risk));
            List<GroundRosterProfile.Entry<MarineArmorCatalogDef>> entries = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.getJSONObject(i);
                entries.add(new GroundRosterProfile.Entry<>(
                        MarineArmorCatalogRegistry.require(entry.getString("id")),
                        entry.getInt("weight")));
            }
            result.put(risk, new GroundRosterProfile.WeightedTable<>(entries));
        }
        return result;
    }

    private static Map<RiskLevel, GroundRosterProfile.WeightedTable<SpecialEquipmentDef>> parseSpecials(
            String profileId, String tier, JSONObject json) throws JSONException {
        EnumMap<RiskLevel, GroundRosterProfile.WeightedTable<SpecialEquipmentDef>> result =
                new EnumMap<>(RiskLevel.class);
        for (RiskLevel risk : RiskLevel.values()) {
            JSONArray array = json.getJSONArray(riskKey(risk));
            List<GroundRosterProfile.Entry<SpecialEquipmentDef>> entries = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.getJSONObject(i);
                String specialId = entry.getString("id");
                SpecialEquipmentDef special = null;
                if (!"none".equals(specialId)) {
                    special = SpecialEquipmentRegistry.require(specialId);
                }
                entries.add(new GroundRosterProfile.Entry<>(special, entry.getInt("weight")));
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

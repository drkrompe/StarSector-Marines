package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.catalog.CatalogSource;
import com.dillon.starsectormarines.catalog.MarineCatalogManifest.CatalogFile;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Additive, data-authored collectible templates and their base-game cargo issue costs. */
public final class EquipmentTemplateCatalog {

    private static final Logger LOG = Global.getLogger(EquipmentTemplateCatalog.class);
    private static final List<MarineWeapon> PLAYER_PRIMARIES = List.of(
            MarineWeapon.FIELD_RIFLE,
            MarineWeapon.PULSE_RIFLE,
            MarineWeapon.SMG,
            MarineWeapon.SQUAD_AUTOMATIC,
            MarineWeapon.DMR);

    private static volatile EquipmentTemplateCatalog installed;

    private final Map<String, EquipmentTemplateCard> byId = new LinkedHashMap<>();
    private final Map<String, CatalogSource> sourceById = new LinkedHashMap<>();

    public static EquipmentTemplateCatalog installed() {
        return installed;
    }

    public static void install(EquipmentTemplateCatalog catalog) {
        installed = catalog;
    }

    public static void loadContributions(List<CatalogFile> catalogs) {
        EquipmentTemplateCatalog registry = new EquipmentTemplateCatalog();
        for (CatalogFile catalog : catalogs) {
            try {
                registry.ingest(catalog.loadJson(), catalog.source());
            } catch (Exception failure) {
                throw new IllegalStateException("Failed to ingest equipment-template catalog "
                        + catalog.source().describe(), failure);
            }
        }
        install(registry);
        LOG.info("Equipment-template catalog installed with " + registry.size()
                + " cards from " + catalogs.size() + " contributed catalogs");
    }

    public void ingest(JSONObject root) throws JSONException {
        ingest(root, CatalogSource.unspecified("<in-memory equipment-template catalog>"));
    }

    public void ingest(JSONObject root, CatalogSource source) throws JSONException {
        parsePrimaries(root.optJSONArray("primaries"), source);
        parseArmor(root.optJSONArray("armor"), source);
        parseSpecials(root.optJSONArray("specialEquipment"), source);
    }

    public static List<MarineWeapon> playerPrimaries() {
        return PLAYER_PRIMARIES;
    }

    public static String primaryId(MarineWeapon weapon, EquipmentGrade grade) {
        if (weapon == null) throw new IllegalArgumentException("Primary template weapon is required");
        return primaryId(weapon.id, grade);
    }

    public static String primaryId(String weaponId, EquipmentGrade grade) {
        if (grade == null) throw new IllegalArgumentException("Primary template grade is required");
        return "equipment-template:" + weaponId + ":"
                + grade.name().toLowerCase(Locale.ROOT);
    }

    public static String armorId(MarineArmorPattern armor) {
        if (armor == null) throw new IllegalArgumentException("Armor template is required");
        return armorId(armor.id);
    }

    public static String armorId(String armorId) {
        return "equipment-template:" + armorId;
    }

    public static String specialId(MarineSecondary special) {
        if (special == null) throw new IllegalArgumentException("Special template is required");
        return specialId(special.specialEquipmentId);
    }

    public static String specialId(String specialId) {
        return "equipment-template:" + specialId;
    }

    public static EquipmentTemplateCard primary(MarineWeapon weapon, EquipmentGrade grade) {
        return require(primaryId(weapon, grade));
    }

    public static EquipmentTemplateCard primary(String weaponId, EquipmentGrade grade) {
        return require(primaryId(weaponId, grade));
    }

    public static EquipmentTemplateCard armor(MarineArmorPattern armor) {
        return require(armorId(armor));
    }

    public static EquipmentTemplateCard armor(String armorId) {
        return require(armorId(armorId));
    }

    public static EquipmentTemplateCard special(MarineSecondary special) {
        return require(specialId(special));
    }

    public static EquipmentTemplateCard special(String specialId) {
        return require(specialId(specialId));
    }

    public static EquipmentTemplateCard require(String id) {
        EquipmentTemplateCatalog catalog = requireInstalled();
        EquipmentTemplateCard card = catalog.byId.get(id);
        if (card == null) {
            throw new IllegalArgumentException("Unknown equipment template id '" + id
                    + "'. Known ids: " + catalog.byId.keySet());
        }
        return card;
    }

    public static boolean contains(String id) {
        return id != null && requireInstalled().byId.containsKey(id);
    }

    public static List<EquipmentTemplateCard> all() {
        return List.copyOf(requireInstalled().byId.values());
    }

    public CatalogSource sourceOf(String id) {
        return sourceById.get(id);
    }

    public Collection<EquipmentTemplateCard> entries() {
        return byId.values();
    }

    public int size() {
        return byId.size();
    }

    private void parsePrimaries(JSONArray array, CatalogSource source) throws JSONException {
        if (array == null) return;
        for (int index = 0; index < array.length(); index++) {
            JSONObject entry = array.getJSONObject(index);
            String weaponId = requireText(entry, "equipmentId");
            WeaponDef weapon = WeaponRegistry.require(weaponId);
            if (weapon.mount != MountClass.MARINE_PRIMARY) {
                throw new JSONException("Primary equipment template references non-primary weapon '"
                        + weaponId + "'");
            }
            JSONObject grades = entry.getJSONObject("grades");
            for (EquipmentGrade grade : EquipmentGrade.values()) {
                String gradeKey = grade.name().toLowerCase(Locale.ROOT);
                if (!grades.has(gradeKey)) continue;
                register(new EquipmentTemplateCard(primaryId(weaponId, grade),
                        weapon.catalogName(grade.tier), EquipmentTemplateCard.Kind.PRIMARY,
                        weaponId, grade,
                        parseCost(grades.getJSONObject(gradeKey))), source);
            }
        }
    }

    private void parseArmor(JSONArray array, CatalogSource source) throws JSONException {
        if (array == null) return;
        for (int index = 0; index < array.length(); index++) {
            JSONObject entry = array.getJSONObject(index);
            String armorId = requireText(entry, "equipmentId");
            MarineArmorCatalogDef armor = MarineArmorCatalogRegistry.require(armorId);
            register(new EquipmentTemplateCard(armorId(armorId), armor.displayName(),
                    EquipmentTemplateCard.Kind.ARMOR, armorId, null,
                    parseCost(entry.getJSONObject("issueCost"))),
                    source);
        }
    }

    private void parseSpecials(JSONArray array, CatalogSource source) throws JSONException {
        if (array == null) return;
        for (int index = 0; index < array.length(); index++) {
            JSONObject entry = array.getJSONObject(index);
            String specialId = requireText(entry, "equipmentId");
            SpecialEquipmentDef special = SpecialEquipmentRegistry.require(specialId);
            register(new EquipmentTemplateCard(specialId(specialId), special.displayName(),
                    EquipmentTemplateCard.Kind.SPECIAL, specialId, null,
                    parseCost(entry.getJSONObject("issueCost"))), source);
        }
    }

    private void register(EquipmentTemplateCard card, CatalogSource source) throws JSONException {
        if (byId.containsKey(card.id())) {
            throw new JSONException("Duplicate equipment template id '" + card.id()
                    + "': first declared by " + sourceById.get(card.id()).describe()
                    + ", then by " + source.describe());
        }
        byId.put(card.id(), card);
        sourceById.put(card.id(), source);
    }

    private static EquipmentTemplateCost parseCost(JSONObject json) {
        return new EquipmentTemplateCost(
                json.optInt("supplies", 0),
                json.optInt("heavyArmaments", 0),
                json.optInt("heavyMachinery", 0),
                json.optInt("food", 0));
    }

    private static String requireText(JSONObject json, String key) throws JSONException {
        String value = json.optString(key, null);
        if (value == null || value.isBlank()) {
            throw new JSONException("Equipment template entry is missing '" + key + "'");
        }
        return value.trim();
    }

    private static EquipmentTemplateCatalog requireInstalled() {
        EquipmentTemplateCatalog catalog = installed;
        if (catalog == null) {
            throw new IllegalStateException("Equipment-template catalog is not installed");
        }
        return catalog;
    }
}

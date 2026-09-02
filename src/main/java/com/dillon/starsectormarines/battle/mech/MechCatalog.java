package com.dillon.starsectormarines.battle.mech;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.Map;

/**
 * What a mech chassis and the hardware bolted to it <em>are</em>, in words.
 *
 * <p>{@link MechVariant} and {@link MechWeaponComponent} are enums of numbers and
 * a display name; neither carries a designation, a chassis role, or a field
 * note, and there is nowhere sensible to put three paragraphs of provenance in
 * an enum constructor. Marine primaries have carried exactly this copy in their
 * own catalog since the weapon registry landed, and this is that arrangement for
 * mechs: identity and prose as authored data, stats where they already live.
 *
 * <p><b>Presentation only.</b> Nothing the simulation reads comes from here.
 * Provenance follows {@code equipment-lore-catalog.md}, which owns the canonical
 * origin of every item that has one.
 *
 * <p><b>Fail-loud.</b> An entry naming an id that does not exist, or a catalog
 * missing an entry for any enum constant, stops load. The alternative is a spec
 * sheet that silently says nothing about a chassis the player is being asked to
 * buy, which is the same defect this class exists to remove.
 */
public final class MechCatalog {

    private static final Logger LOG = Logger.getLogger(MechCatalog.class);

    public static final String CONTENT_PATH = "data/mechs/mech-catalog.mech.json";

    private static volatile MechCatalog installed;

    private final Map<MechVariant, ChassisEntry> chassis;
    private final Map<MechWeaponComponent, ComponentEntry> components;

    /**
     * One chassis's identity as a player reads it.
     *
     * @param designation the catalog code, e.g. {@code HND-3}
     * @param role        what the chassis is for, in the register the armour
     *                    catalog's roles use — not a {@link MechRole}, which is
     *                    doctrine a player assigns and can change
     * @param description provenance prose; no numbers, because every number a
     *                    screen shows comes from {@link MechVariant}
     */
    public record ChassisEntry(String designation, String role, String description) { }

    /**
     * One fitted component's identity.
     *
     * @param designation the catalog code, e.g. {@code SRM-15}
     * @param description provenance prose
     */
    public record ComponentEntry(String designation, String description) { }

    public MechCatalog(Map<MechVariant, ChassisEntry> chassis,
                       Map<MechWeaponComponent, ComponentEntry> components) {
        this.chassis = Map.copyOf(chassis);
        this.components = Map.copyOf(components);
    }

    public static MechCatalog installed() {
        return installed;
    }

    public static void install(MechCatalog catalog) {
        if (catalog == null) throw new IllegalArgumentException("catalog cannot be null");
        installed = catalog;
    }

    /** Reads and installs the shipped catalog. Throws rather than degrading. */
    public static void loadBuiltins() {
        try {
            MechCatalog catalog = parse(Global.getSettings().loadJSON(CONTENT_PATH, true));
            install(catalog);
            LOG.info("Mech catalog installed with " + catalog.chassis.size() + " chassis and "
                    + catalog.components.size() + " components");
        } catch (Exception failure) {
            throw new IllegalStateException("Failed to load mech catalog " + CONTENT_PATH,
                    failure);
        }
    }

    public static MechCatalog parse(JSONObject root) throws JSONException {
        int schemaVersion = root.optInt("schemaVersion", 1);
        if (schemaVersion < 1) {
            throw new JSONException("Unsupported mech catalog schemaVersion: " + schemaVersion);
        }
        Map<MechVariant, ChassisEntry> chassis = new EnumMap<>(MechVariant.class);
        JSONArray chassisArray = root.getJSONArray("chassis");
        for (int index = 0; index < chassisArray.length(); index++) {
            JSONObject entry = chassisArray.getJSONObject(index);
            String variantId = requireText(entry, "variant", "mech catalog chassis entry");
            MechVariant variant;
            try {
                variant = MechVariant.fromId(variantId);
            } catch (IllegalArgumentException unknown) {
                throw new JSONException("Mech catalog names unknown chassis '" + variantId + "'");
            }
            if (chassis.containsKey(variant)) {
                throw new JSONException("Duplicate mech catalog chassis '" + variantId + "'");
            }
            chassis.put(variant, new ChassisEntry(
                    requireText(entry, "designation", variantId),
                    requireText(entry, "role", variantId),
                    requireText(entry, "description", variantId)));
        }

        Map<MechWeaponComponent, ComponentEntry> components =
                new EnumMap<>(MechWeaponComponent.class);
        JSONArray componentArray = root.getJSONArray("components");
        for (int index = 0; index < componentArray.length(); index++) {
            JSONObject entry = componentArray.getJSONObject(index);
            String componentId = requireText(entry, "component", "mech catalog component entry");
            MechWeaponComponent component = MechWeaponComponent.findById(componentId);
            if (component == null) {
                throw new JSONException("Mech catalog names unknown component '"
                        + componentId + "'");
            }
            if (components.containsKey(component)) {
                throw new JSONException("Duplicate mech catalog component '" + componentId + "'");
            }
            components.put(component, new ComponentEntry(
                    requireText(entry, "designation", componentId),
                    requireText(entry, "description", componentId)));
        }

        MechCatalog catalog = new MechCatalog(chassis, components);
        catalog.validateCompleteness();
        return catalog;
    }

    /** Every chassis and every fitted component must be describable. */
    public void validateCompleteness() {
        for (MechVariant variant : MechVariant.values()) {
            if (!chassis.containsKey(variant)) {
                throw new IllegalStateException("Mech catalog is missing chassis '"
                        + variant.id + "'");
            }
        }
        for (MechWeaponComponent component : MechWeaponComponent.values()) {
            if (!components.containsKey(component)) {
                throw new IllegalStateException("Mech catalog is missing component '"
                        + component.id + "'");
            }
        }
    }

    public static ChassisEntry require(MechVariant variant) {
        MechCatalog catalog = requireInstalled();
        ChassisEntry entry = catalog.chassis.get(variant);
        if (entry == null) {
            throw new IllegalArgumentException("Mech catalog has no chassis '" + variant + "'");
        }
        return entry;
    }

    public static ComponentEntry require(MechWeaponComponent component) {
        MechCatalog catalog = requireInstalled();
        ComponentEntry entry = catalog.components.get(component);
        if (entry == null) {
            throw new IllegalArgumentException("Mech catalog has no component '"
                    + component + "'");
        }
        return entry;
    }

    public ChassisEntry chassis(MechVariant variant) {
        return chassis.get(variant);
    }

    public ComponentEntry component(MechWeaponComponent component) {
        return components.get(component);
    }

    private static MechCatalog requireInstalled() {
        MechCatalog catalog = installed;
        if (catalog == null) {
            throw new IllegalStateException("Mech catalog is not installed");
        }
        return catalog;
    }

    private static String requireText(JSONObject json, String key, String owner)
            throws JSONException {
        String value = json.optString(key, null);
        if (value == null || value.isBlank()) {
            throw new JSONException("Mech catalog entry '" + owner + "' is missing '" + key + "'");
        }
        return value.trim();
    }
}

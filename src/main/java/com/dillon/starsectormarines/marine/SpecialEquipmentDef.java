package com.dillon.starsectormarines.marine;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/** Stable, data-authored loadout identity for one special-equipment item. */
public record SpecialEquipmentDef(
        String id,
        String displayName,
        String catalogSubtitle,
        String catalogDescription,
        SpecialActivation activation,
        String weaponId,
        SpecialResourceMode resourceMode,
        int startingAmmo,
        SpecialAiPolicy aiPolicy,
        SpecialEquipmentPresentationDef presentation,
        SmokeGrenadeSpec smokeGrenadeSpec,
        SatchelChargeSpec satchelChargeSpec) implements Serializable {

    /** Parses one entry from {@code *.equipment.json}; malformed required data fails load. */
    public static SpecialEquipmentDef parse(JSONObject json) throws JSONException {
        String id = requireText(json, "id", "special-equipment entry");
        JSONObject catalog = json.getJSONObject("catalog");
        JSONObject activationJson = json.getJSONObject("activation");
        JSONObject resource = json.getJSONObject("resource");
        JSONObject ai = json.getJSONObject("ai");
        SpecialActivation activation = SpecialActivation.fromKey(
                requireText(activationJson, "type", id), id);
        SpecialAiPolicy aiPolicy = SpecialAiPolicy.fromKey(
                requireText(ai, "policy", id), id);
        SpecialResourceMode resourceMode = SpecialResourceMode.fromKey(
                requireText(resource, "mode", id), id);
        int startingAmmo = resource.optInt("startingAmmo", 0);
        if (startingAmmo < 0) {
            throw new JSONException("Special equipment '" + id
                    + "' startingAmmo cannot be negative");
        }
        if (resourceMode == SpecialResourceMode.AMMUNITION && startingAmmo <= 0) {
            throw new JSONException("Ammunition-gated special equipment '" + id
                    + "' must declare positive startingAmmo");
        }
        if (resourceMode == SpecialResourceMode.COOLDOWN && startingAmmo != 0) {
            throw new JSONException("Cooldown-gated special equipment '" + id
                    + "' cannot declare startingAmmo");
        }

        String weaponId = optionalText(activationJson, "weaponId");
        SpecialEquipmentPresentationDef presentation =
                SpecialEquipmentPresentationDef.parse(json.getJSONObject("presentation"), id);
        SmokeGrenadeSpec smoke = null;
        SatchelChargeSpec satchel = null;
        switch (activation) {
            case DIRECT_EXPLOSIVE, DIRECT_PRECISION -> {
                if (weaponId == null) {
                    throw new JSONException("Weapon-like special equipment '" + id
                            + "' must declare activation.weaponId");
                }
                requirePolicy(aiPolicy, SpecialAiPolicy.HARDENED_DIRECT_FIRE, id);
            }
            case ARC_EXPLOSIVE -> {
                if (weaponId == null) {
                    throw new JSONException("Weapon-like special equipment '" + id
                            + "' must declare activation.weaponId");
                }
                requirePolicy(aiPolicy, SpecialAiPolicy.SOFT_CLUSTER_INDIRECT, id);
                if (resourceMode != SpecialResourceMode.AMMUNITION) {
                    throw new JSONException("Arc explosive equipment '" + id
                            + "' must use the ammunition resource mode");
                }
                if (presentation.thrown() == null || presentation.carrierLayer() == null) {
                    throw new JSONException("Arc explosive equipment '" + id
                            + "' requires carrier and thrown presentation recipes");
                }
            }
            case UTILITY_SMOKE -> {
                requireNoWeapon(weaponId, id);
                requirePolicy(aiPolicy, SpecialAiPolicy.SQUAD_SMOKE_SCREEN, id);
                smoke = parseSmoke(activationJson, id);
                if (presentation.thrown() == null || presentation.fieldSpritePath() == null) {
                    throw new JSONException("Smoke equipment '" + id
                            + "' requires thrown and field presentation recipes");
                }
            }
            case UTILITY_SATCHEL -> {
                requireNoWeapon(weaponId, id);
                requirePolicy(aiPolicy, SpecialAiPolicy.CONTACT_DEMOLITION, id);
                satchel = parseSatchel(activationJson, resource, id);
                if (resourceMode != SpecialResourceMode.COOLDOWN) {
                    throw new JSONException("Satchel equipment '" + id
                            + "' must use the cooldown resource mode");
                }
                if (presentation.carrierLayer() == null || presentation.deployed() == null) {
                    throw new JSONException("Satchel equipment '" + id
                            + "' requires carrier and deployed presentation recipes");
                }
            }
        }

        return new SpecialEquipmentDef(
                id,
                requireText(catalog, "displayName", id),
                requireText(catalog, "subtitle", id),
                requireText(catalog, "description", id),
                activation,
                weaponId,
                resourceMode,
                startingAmmo,
                aiPolicy,
                presentation,
                smoke,
                satchel);
    }

    public String aimSpritePath() {
        return presentation.aimSpritePath();
    }

    public String armoryIconPath() {
        return presentation.armoryIconPath();
    }

    private static SmokeGrenadeSpec parseSmoke(JSONObject json, String id) throws JSONException {
        return new SmokeGrenadeSpec(
                positive(json, "throwRange", id),
                positive(json, "throwDuration", id),
                positive(json, "flightSeconds", id),
                positive(json, "arcHeight", id),
                positive(json, "cloudRadius", id),
                positive(json, "cloudDuration", id));
    }

    private static SatchelChargeSpec parseSatchel(JSONObject activation, JSONObject resource,
                                                   String id) throws JSONException {
        return new SatchelChargeSpec(
                positive(activation, "contactRange", id),
                positive(activation, "plantDuration", id),
                positive(activation, "fuseSeconds", id),
                positive(resource, "cooldownSeconds", id),
                positive(activation, "blastRadius", id),
                positive(activation, "damage", id),
                positive(activation, "penetration", id));
    }

    private static float positive(JSONObject json, String key, String id) throws JSONException {
        float value = (float) json.getDouble(key);
        if (value <= 0f) {
            throw new JSONException("Special equipment '" + id + "' " + key
                    + " must be positive");
        }
        return value;
    }

    private static void requireNoWeapon(String weaponId, String id) throws JSONException {
        if (weaponId != null) {
            throw new JSONException("Utility special equipment '" + id
                    + "' cannot declare activation.weaponId");
        }
    }

    private static void requirePolicy(SpecialAiPolicy actual, SpecialAiPolicy expected,
                                      String id) throws JSONException {
        if (actual != expected) {
            throw new JSONException("Special equipment '" + id + "' activation requires AI policy '"
                    + expected.key + "', got '" + actual.key + "'");
        }
    }

    private static String requireText(JSONObject json, String key, String owner)
            throws JSONException {
        String value = optionalText(json, key);
        if (value == null) {
            throw new JSONException(owner + " is missing required text field '" + key + "'");
        }
        return value;
    }

    private static String optionalText(JSONObject json, String key) {
        String value = json.optString(key, null);
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() || "null".equals(trimmed) ? null : trimmed;
    }
}

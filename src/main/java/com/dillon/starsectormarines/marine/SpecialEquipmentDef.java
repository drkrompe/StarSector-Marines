package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.Color;
import java.io.Serializable;
import java.util.Locale;

/** Stable, data-authored loadout identity for one special-equipment item. */
public record SpecialEquipmentDef(
        String id,
        String displayName,
        String catalogSubtitle,
        String catalogDescription,
        String catalogFactionLogo,
        SpecialActivation activation,
        String weaponId,
        SpecialResourceMode resourceMode,
        int startingAmmo,
        SpecialAiPolicy aiPolicy,
        SpecialEquipmentPresentationDef presentation,
        SmokeGrenadeSpec smokeGrenadeSpec,
        SatchelChargeSpec satchelChargeSpec,
        CloseContactSpec closeContactSpec,
        DeployableEmplacementSpec deployableEmplacementSpec,
        DeployableCoverSpec deployableCoverSpec) implements Serializable {

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
        CloseContactSpec closeContact = null;
        DeployableEmplacementSpec deployable = null;
        DeployableCoverSpec deployableCover = null;
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
                if (presentation.thrown() == null || presentation.field() == null) {
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
            case UTILITY_DEPLOYABLE -> {
                requireNoWeapon(weaponId, id);
                requirePolicy(id, aiPolicy, SpecialAiPolicy.AREA_DENIAL_EMPLACEMENT,
                        SpecialAiPolicy.DIRECTIONAL_COVER_SCREEN);
                // One activation, two shapes of placed thing. The emplacement
                // policy leaves an actor behind; the cover policy leaves a
                // property of a boundary behind. They share the carried item's
                // channel — commit, freeze, spend one — and nothing else, so
                // each names its own spec rather than pretending one record
                // describes both.
                if (aiPolicy == SpecialAiPolicy.DIRECTIONAL_COVER_SCREEN) {
                    deployableCover = parseDeployableCover(activationJson, id);
                } else {
                    deployable = parseDeployable(activationJson, id);
                }
                if (resourceMode != SpecialResourceMode.AMMUNITION) {
                    throw new JSONException("Deployable equipment '" + id
                            + "' must use the ammunition resource mode — a placement"
                            + " spends hardware the marine physically carried");
                }
                if (presentation.carrierLayer() == null) {
                    throw new JSONException("Deployable equipment '" + id
                            + "' requires a carrier presentation recipe");
                }
            }
            case CLOSE_CONTACT -> {
                if (weaponId == null) {
                    throw new JSONException("Weapon-like special equipment '" + id
                            + "' must declare activation.weaponId");
                }
                requirePolicy(id, aiPolicy, SpecialAiPolicy.CONTACT_BREACH_CHANNEL,
                        SpecialAiPolicy.CONTACT_REACTION_STRIKE);
                closeContact = parseCloseContact(activationJson, resource, id);
                if (resourceMode != SpecialResourceMode.COOLDOWN) {
                    throw new JSONException("Close-contact equipment '" + id
                            + "' must use the cooldown resource mode");
                }
                if (presentation.carrierLayer() == null) {
                    throw new JSONException("Close-contact equipment '" + id
                            + "' requires a carrier presentation recipe");
                }
                if (presentation.thrown() != null || presentation.field() != null) {
                    throw new JSONException("Close-contact equipment '" + id
                            + "' has no thrown or deployed field payload");
                }
            }
        }

        return new SpecialEquipmentDef(
                id,
                requireText(catalog, "displayName", id),
                requireText(catalog, "subtitle", id),
                requireText(catalog, "description", id),
                optionalText(catalog, "factionLogo"),
                activation,
                weaponId,
                resourceMode,
                startingAmmo,
                aiPolicy,
                presentation,
                smoke,
                satchel,
                closeContact,
                deployable,
                deployableCover);
    }

    /** True when this item acts only from honest physical contact. */
    public boolean isCloseContactWeapon() {
        return activation == SpecialActivation.CLOSE_CONTACT;
    }

    public String aimSpritePath() {
        return presentation.aimSpritePath();
    }

    public String armoryIconPath() {
        return presentation.armoryIconPath();
    }

    /** Referenced weapon behavior for a weapon-like activation. */
    public WeaponDef weaponDef() {
        if (weaponId == null) {
            throw new IllegalStateException(displayName + " is utility equipment, not a weapon");
        }
        return WeaponRegistry.require(weaponId);
    }

    public boolean usesAmmunition() {
        return resourceMode == SpecialResourceMode.AMMUNITION;
    }

    public boolean hasAvailableUse(int ammo) {
        return !usesAmmunition() || ammo > 0;
    }

    public boolean isDirectFireWeapon() {
        return activation == SpecialActivation.DIRECT_EXPLOSIVE
                || activation == SpecialActivation.DIRECT_PRECISION;
    }

    public float aimDuration() {
        if (deployableEmplacementSpec != null) return deployableEmplacementSpec.deployDuration();
        if (deployableCoverSpec != null) return deployableCoverSpec.deployDuration();
        if (smokeGrenadeSpec != null) return smokeGrenadeSpec.throwDuration();
        if (satchelChargeSpec != null) return satchelChargeSpec.plantDuration();
        if (closeContactSpec != null) return closeContactSpec.channelSeconds();
        return weaponDef().aimDuration;
    }

    public String fireSoundId() { return weaponDef().fireSoundId; }
    public String impactSoundId() { return weaponDef().impactSoundId; }
    public String projectileSpritePath() {
        return weaponId != null ? weaponDef().projectileSpritePath : null;
    }
    public float projectileVisualCells() {
        return weaponId != null ? weaponDef().projectileVisualCells : 0f;
    }
    public Color tracerColor() { return weaponDef().tracerColor; }
    public float range() { return weaponDef().range; }
    public float damage() { return weaponDef().damage; }
    public float accuracy() { return weaponDef().accuracy; }
    public float cooldown() { return weaponDef().cooldown; }
    public float penetration() { return weaponDef().penetration; }
    public float flightSec() { return weaponDef().flightSec; }
    public float arcHeight() { return weaponDef().arcHeight; }
    public float hitSpread() { return weaponDef().hitSpread; }
    public float aoeRadius() { return weaponDef().aoeRadius; }
    public int wallDamage() { return weaponDef().wallDamage; }
    public float wallDamageRadius() { return weaponDef().wallDamageRadius; }
    public float roundVelocity() { return weaponDef().roundVelocity; }

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

    private static DeployableEmplacementSpec parseDeployable(JSONObject activation, String id)
            throws JSONException {
        return new DeployableEmplacementSpec(
                requireText(activation, "structureId", id),
                positive(activation, "deployDuration", id),
                positive(activation, "lifetimeSeconds", id));
    }

    /**
     * The named profile is resolved and rejected at load, not at placement:
     * a barricade whose kind does not exist, or whose kind would close a
     * navigation transition, is a broken catalog rather than a battle that
     * fails once a marine happens to reach for it.
     */
    private static DeployableCoverSpec parseDeployableCover(JSONObject activation, String id)
            throws JSONException {
        String kindName = requireText(activation, "barrierKind", id);
        SharedEdgeBarrier.Kind kind;
        try {
            kind = SharedEdgeBarrier.Kind.valueOf(kindName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new JSONException("Cover deployable '" + id
                    + "' names unknown shared-edge profile '" + kindName + "'");
        }
        if (kind.blocksMovement()) {
            throw new JSONException("Cover deployable '" + id + "' names profile '"
                    + kindName + "', which closes its navigation transition;"
                    + " a carried screen may only place a profile that leaves"
                    + " movement alone");
        }
        return new DeployableCoverSpec(kind.name(),
                positive(activation, "deployDuration", id),
                positive(activation, "lifetimeSeconds", id));
    }

    private static CloseContactSpec parseCloseContact(JSONObject activation, JSONObject resource,
                                                      String id) throws JSONException {
        return new CloseContactSpec(
                positive(activation, "contactRange", id),
                positive(activation, "channelSeconds", id),
                positive(resource, "cooldownSeconds", id));
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

    /** One activation may admit several policies when each names a distinct legal contact. */
    private static void requirePolicy(String id, SpecialAiPolicy actual,
                                      SpecialAiPolicy... permitted) throws JSONException {
        for (SpecialAiPolicy candidate : permitted) {
            if (actual == candidate) return;
        }
        StringBuilder keys = new StringBuilder();
        for (SpecialAiPolicy candidate : permitted) {
            if (keys.length() > 0) keys.append(" or ");
            keys.append('\'').append(candidate.key).append('\'');
        }
        throw new JSONException("Special equipment '" + id + "' activation requires AI policy "
                + keys + ", got '" + actual.key + "'");
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

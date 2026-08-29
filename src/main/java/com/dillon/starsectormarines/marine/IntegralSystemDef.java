package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * One capability an armour pattern carries in the suit itself
 * ({@code integral-armor-systems.md}).
 *
 * <p><b>Per pattern, not per role.</b> A role says what job a suit is for;
 * which concrete suit the player recovered is the part that should surprise
 * them. Most patterns declare nothing, which is what makes a system a reason to
 * want a particular suit rather than a tax on the tier.
 *
 * <p><b>It is part of the suit, not a carried item.</b> A billet still carries
 * at most one special item, and an integral system never spends it — a heavy
 * suit must not cost a marine their grenades, or the heavy role reads as a
 * downgrade. The two are separately authored and separately issued.
 *
 * <p>The resource, duration, and AI-policy vocabulary is deliberately the one
 * special equipment already uses ({@link SpecialResourceMode},
 * {@link SpecialAiPolicy}), because this is a second carrier for that
 * vocabulary rather than a second vocabulary.
 *
 * <p><b>The trigger is authored, not compiled.</b> A definition declares the
 * moment it should be spent at and the numbers that recognise it, so every
 * system's trigger is readable from its catalog entry. The sweep that spends
 * it dispatches on the declared policy and reads those numbers; it holds no
 * per-system judgement of its own.
 */
public record IntegralSystemDef(
        String id,
        String displayName,
        EquipmentGrade grade,
        String description,
        IntegralSystemEffect effect,
        SpecialResourceMode resourceMode,
        float durationSeconds,
        float cooldownSeconds,
        int startingAmmo,
        BreacherAssistSpec breacherAssist,
        MissilePodSpec missilePod,
        PerceptionSweepSpec perceptionSweep,
        IntegralSystemPolicySpec policy) implements Serializable {

    /**
     * Keys that would express an integral system as durability. Rejected by
     * name so a future author is told the rule rather than quietly widening the
     * thing this concept exists to avoid.
     */
    private static final String[] FORBIDDEN_DURABILITY_KEYS = {
            "armorCapacity", "armorCapacityBonus", "armorRating", "armorRatingBonus",
            "bonusHp", "maxHp", "hpBonus", "extraArmor"
    };

    /** Parses the optional {@code integralSystem} block on an armour catalog entry. */
    public static IntegralSystemDef parse(JSONObject json, String armorId) throws JSONException {
        rejectDurability(json, armorId);
        String id = requireText(json, "id", armorId);
        EquipmentGrade grade = parseGrade(requireText(json, "grade", armorId), id, armorId);
        IntegralSystemEffect effect = IntegralSystemEffect.fromKey(
                requireText(json, "effect", armorId), armorId);
        SpecialResourceMode resourceMode = SpecialResourceMode.fromKey(
                requireText(json, "resource", armorId), armorId);
        float durationSeconds = positive(json, "durationSeconds", armorId);
        float cooldownSeconds = json.has("cooldownSeconds")
                ? positive(json, "cooldownSeconds", armorId) : 0f;
        int startingAmmo = json.optInt("startingAmmo", 0);

        switch (resourceMode) {
            case COOLDOWN -> {
                if (cooldownSeconds <= 0f) {
                    throw new JSONException("Integral system '" + id + "' on armor '" + armorId
                            + "' is cooldown-gated and must declare a positive cooldownSeconds");
                }
                if (startingAmmo != 0) {
                    throw new JSONException("Integral system '" + id + "' on armor '" + armorId
                            + "' is cooldown-gated and cannot declare startingAmmo");
                }
                if (cooldownSeconds <= durationSeconds) {
                    throw new JSONException("Integral system '" + id + "' on armor '" + armorId
                            + "' would be permanently available: cooldownSeconds must exceed"
                            + " durationSeconds, or the effect is not temporary");
                }
            }
            case AMMUNITION -> {
                if (startingAmmo <= 0) {
                    throw new JSONException("Integral system '" + id + "' on armor '" + armorId
                            + "' is ammunition-gated and must declare positive startingAmmo");
                }
            }
        }

        BreacherAssistSpec breacher = null;
        MissilePodSpec missilePod = null;
        PerceptionSweepSpec perceptionSweep = null;
        switch (effect) {
            case BREACHER_ASSIST -> {
                if (resourceMode != SpecialResourceMode.COOLDOWN) {
                    throw new JSONException("Integral system '" + id + "' on armor '" + armorId
                            + "' is a breacher assist and must be cooldown-gated");
                }
                breacher = BreacherAssistSpec.parse(json, armorId, id);
            }
            case MISSILE_POD -> {
                if (resourceMode != SpecialResourceMode.AMMUNITION) {
                    throw new JSONException("Integral system '" + id + "' on armor '" + armorId
                            + "' is a missile pod and must be ammunition-gated");
                }
                missilePod = MissilePodSpec.parse(json, armorId, id);
            }
            case PERCEPTION_SWEEP -> {
                // Ammunition is for a payload that leaves the suit. A scout who
                // runs out of looking is a scout carrying a stat with a counter
                // on it, so a sweep waits instead: the clock is the whole cost.
                if (resourceMode != SpecialResourceMode.COOLDOWN) {
                    throw new JSONException("Integral system '" + id + "' on armor '" + armorId
                            + "' is a sensor sweep and must be cooldown-gated: looking at"
                            + " something is not a payload that runs out.");
                }
                perceptionSweep = PerceptionSweepSpec.parse(json, armorId, id);
            }
        }

        return new IntegralSystemDef(
                id,
                requireText(json, "displayName", armorId),
                grade,
                requireText(json, "description", armorId),
                effect,
                resourceMode,
                durationSeconds,
                cooldownSeconds,
                startingAmmo,
                breacher,
                missilePod,
                perceptionSweep,
                parsePolicy(json, armorId, id, effect));
    }

    /**
     * The policies that can honestly apply to one effect. A policy names the
     * moment worth spending something at, and not every moment fits every
     * capability: charging a doorway is not something a shoulder rack can do,
     * and a rack that waits for standoff has nothing to do with a suit that
     * only moves faster. Keeping the mapping here, next to the effect's own
     * validation, means an author meets the rule at parse time rather than
     * discovering a policy that silently never fires.
     */
    private static SpecialAiPolicy[] applicablePolicies(IntegralSystemEffect effect) {
        return switch (effect) {
            case BREACHER_ASSIST -> new SpecialAiPolicy[] {SpecialAiPolicy.EXPOSED_UNDER_FIRE};
            case MISSILE_POD -> new SpecialAiPolicy[] {SpecialAiPolicy.SIGHTED_STANDOFF_CONTACT};
            case PERCEPTION_SWEEP ->
                    new SpecialAiPolicy[] {SpecialAiPolicy.APPROACHING_DEAD_GROUND};
        };
    }

    private static IntegralSystemPolicySpec parsePolicy(JSONObject json, String armorId,
                                                        String systemId,
                                                        IntegralSystemEffect effect)
            throws JSONException {
        SpecialAiPolicy declared = SpecialAiPolicy.fromKey(
                requireText(json, "policy", armorId), systemId);
        for (SpecialAiPolicy candidate : applicablePolicies(effect)) {
            if (candidate != declared) continue;
            return switch (declared) {
                case EXPOSED_UNDER_FIRE -> ExposedUnderFireSpec.parse(json, armorId, systemId);
                case SIGHTED_STANDOFF_CONTACT -> SightedStandoffSpec.parse(json, armorId, systemId);
                case APPROACHING_DEAD_GROUND ->
                        ApproachingDeadGroundSpec.parse(json, armorId, systemId);
                default -> throw new JSONException("Integral system '" + systemId + "' on armor '"
                        + armorId + "' declares AI policy '" + declared.key
                        + "', which has no authored parameters for an integral system");
            };
        }
        throw new JSONException("Integral system '" + systemId + "' on armor '" + armorId
                + "' declares AI policy '" + declared.key + "', which cannot apply to effect '"
                + effect.key + "'. Policies that apply to that effect: "
                + SpecialAiPolicy.keysOf(applicablePolicies(effect)) + ".");
    }

    /** The moment this system is authored to be spent at. */
    public SpecialAiPolicy aiPolicy() {
        return policy.aiPolicy();
    }

    /** This system's exposure/pressure judgement, or null when it declares another policy. */
    public ExposedUnderFireSpec exposedUnderFire() {
        return policy instanceof ExposedUnderFireSpec spec ? spec : null;
    }

    /** This system's standoff parameters, or null when it declares another policy. */
    public SightedStandoffSpec sightedStandoff() {
        return policy instanceof SightedStandoffSpec spec ? spec : null;
    }

    /** This system's dead-ground parameters, or null when it declares another policy. */
    public ApproachingDeadGroundSpec approachingDeadGround() {
        return policy instanceof ApproachingDeadGroundSpec spec ? spec : null;
    }

    /**
     * The family this system belongs to, and the name a player reads across
     * every pattern that carries one.
     */
    public String familyName() {
        return effect.displayName;
    }

    /**
     * <b>Grade here is description, not arithmetic.</b> It says how well this
     * tradition builds the thing — welded scrap through to artisan restoration —
     * on the same ladder a weapon's manufacture already uses, so a player who
     * reads "Service" on a rifle reads the same word the same way here. Unlike a
     * weapon family, an integral system does <em>not</em> consume
     * {@link EquipmentGrade}'s stat multipliers: a system's numbers are authored
     * outright, and scaling them by grade as well would price the same quality
     * twice. Two systems at the same grade may be nothing alike, and a
     * Masterwork one is not automatically the strongest — the family is
     * side-grades ({@code integral-system-slate.md}).
     */
    private static EquipmentGrade parseGrade(String key, String systemId, String armorId)
            throws JSONException {
        for (EquipmentGrade candidate : EquipmentGrade.values()) {
            if (candidate.name().equalsIgnoreCase(key)
                    || candidate.displayName.equalsIgnoreCase(key)) {
                return candidate;
            }
        }
        StringBuilder known = new StringBuilder();
        for (EquipmentGrade candidate : EquipmentGrade.values()) {
            if (known.length() > 0) known.append(", ");
            known.append(candidate.displayName.toLowerCase());
        }
        throw new JSONException("Integral system '" + systemId + "' on armor '" + armorId
                + "' declares unknown grade '" + key + "'. Known grades: " + known);
    }

    /** Ammunition-gated systems run out; cooldown-gated ones only make you wait. */
    public boolean usesAmmunition() {
        return resourceMode == SpecialResourceMode.AMMUNITION;
    }

    /**
     * Whether running this system raises a screen — bounded directional
     * mitigation in the sense {@code combat-durability-nouns.md} owns. Read at
     * spawn to decide whether the suit needs the live mitigation capability at
     * all; a system that only moves the wearer never gets one.
     */
    public boolean grantsMitigation() {
        return breacherAssist != null && breacherAssist.screenSoak() > 0f;
    }

    private static void rejectDurability(JSONObject json, String armorId) throws JSONException {
        for (String key : FORBIDDEN_DURABILITY_KEYS) {
            if (json.has(key)) {
                throw new JSONException("Armor '" + armorId + "' integral system declares '" + key
                        + "'. An integral system must express itself as behaviour, never as"
                        + " durability — authored protection belongs on the pattern's own"
                        + " armorCapacity/armorRating, and widening those is what this concept"
                        + " exists to avoid (integral-armor-systems.md).");
            }
        }
    }

    static float positive(JSONObject json, String key, String owner) throws JSONException {
        float value = (float) json.getDouble(key);
        if (!Float.isFinite(value) || value <= 0f) {
            throw new JSONException("Integral system on armor '" + owner + "' field '" + key
                    + "' must be finite and positive");
        }
        return value;
    }

    private static String requireText(JSONObject json, String key, String owner)
            throws JSONException {
        String value = json.optString(key, null);
        if (value == null || value.trim().isEmpty() || "null".equals(value.trim())) {
            throw new JSONException("Armor '" + owner
                    + "' integral system is missing required text field '" + key + "'");
        }
        return value.trim();
    }
}

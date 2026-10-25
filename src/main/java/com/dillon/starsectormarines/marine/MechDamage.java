package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechBodyPlan;
import com.dillon.starsectormarines.battle.mech.MechCarriedWear;
import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechSections;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;

/**
 * What a battle left a surviving mech needing: the fraction of each section's structure and
 * armor that is gone. Persisted on {@link CampaignMech} beside its repair clock, which wears it
 * away ({@link #scaled}); a mech with none is whole. Keyed by section name, so a save survives the section list being reordered.
 *
 * <p>A destroyed mount's weapon is not here: that assembly is lost from stores at settlement
 * ({@link MechBay#settleBattleDamage}). The section itself is rebuilt like any other.
 */
public final class MechDamage implements Serializable {

    /** How much of a section's wear its structure carries; the rest is its armor. */
    static final float STRUCTURE_WEIGHT = 0.75f;

    private final EnumMap<MechSection, Float> structureLost = new EnumMap<>(MechSection.class);
    private final EnumMap<MechSection, Float> armorLost = new EnumMap<>(MechSection.class);

    private MechDamage() {
    }

    /**
     * The damage a frozen section record describes, or null when every fitted section came back
     * whole. A section the mech deployed without (an empty mount) has no damage.
     */
    public static MechDamage of(MechSections.Report report) {
        if (report == null) return null;
        MechDamage damage = new MechDamage();
        for (MechSection section : MechSection.values()) {
            int i = section.ordinal();
            if (!report.fitted()[i]) continue;
            float structure = report.destroyed()[i] ? 1f : clamp(1f - report.structureFraction()[i]);
            float armor = clamp(1f - report.armorFraction()[i]);
            if (structure > 0f) damage.structureLost.put(section, structure);
            if (armor > 0f) damage.armorLost.put(section, armor);
        }
        return damage.isEmpty() ? null : damage;
    }

    /** The worse of two damage records, section by section; either may be null. */
    static MechDamage worst(MechDamage a, MechDamage b) {
        if (a == null) return b;
        if (b == null) return a;
        MechDamage out = new MechDamage();
        for (MechSection section : MechSection.values()) {
            float structure = Math.max(a.structureLost(section), b.structureLost(section));
            float armor = Math.max(a.armorLost(section), b.armorLost(section));
            if (structure > 0f) out.structureLost.put(section, structure);
            if (armor > 0f) out.armorLost.put(section, armor);
        }
        return out;
    }

    /**
     * This damage with {@code share} of it still to go, section by section: what a repair clock
     * that has run part way leaves. Null when nothing is left.
     */
    MechDamage scaled(float share) {
        if (!(share > 0f)) return null;
        if (share >= 1f) return this;
        MechDamage out = new MechDamage();
        for (MechSection section : MechSection.values()) {
            float structure = structureLost(section) * share;
            float armor = armorLost(section) * share;
            if (structure > 0f) out.structureLost.put(section, structure);
            if (armor > 0f) out.armorLost.put(section, armor);
        }
        return out.isEmpty() ? null : out;
    }

    /** The frozen battle value of this damage, for a mech dropping before its repair is done. */
    public MechCarriedWear toCarriedWear() {
        int n = MechSection.values().length;
        float[] structure = new float[n];
        float[] armor = new float[n];
        for (MechSection section : MechSection.values()) {
            structure[section.ordinal()] = structureLost(section);
            armor[section.ordinal()] = armorLost(section);
        }
        return new MechCarriedWear(structure, armor);
    }

    public boolean isEmpty() {
        return structureLost.isEmpty() && armorLost.isEmpty();
    }

    /** Fraction of {@code section}'s structure gone; 1 for a section the battle destroyed. */
    public float structureLost(MechSection section) {
        Float value = structureLost.get(section);
        return value != null ? value : 0f;
    }

    /** Fraction of {@code section}'s armor gone. */
    public float armorLost(MechSection section) {
        Float value = armorLost.get(section);
        return value != null ? value : 0f;
    }

    /** How much of {@code section} has to be rebuilt, 0 to 1: structure first, then armor. */
    public float wear(MechSection section) {
        return STRUCTURE_WEIGHT * structureLost(section) + (1f - STRUCTURE_WEIGHT) * armorLost(section);
    }

    /**
     * How much of the whole chassis has to be rebuilt, 0 to 1: each section's wear weighted by
     * its share of the chassis pools, so a stripped head costs less than a stripped torso.
     */
    public float wear(MechBodyPlan plan) {
        float total = 0f;
        for (MechSection section : MechSection.values()) {
            total += plan.poolShare(section) * wear(section);
        }
        return clamp(total);
    }

    /** Sections with any damage, in section order. */
    public Map<MechSection, Float> wearBySection() {
        EnumMap<MechSection, Float> out = new EnumMap<>(MechSection.class);
        for (MechSection section : MechSection.values()) {
            float wear = wear(section);
            if (wear > 0f) out.put(section, wear);
        }
        return out;
    }

    private static float clamp(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}

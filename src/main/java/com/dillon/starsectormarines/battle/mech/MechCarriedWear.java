package com.dillon.starsectormarines.battle.mech;

import java.util.Arrays;

/**
 * The wear a company mech carries into a drop while its repair is still running: the fraction of
 * each section's structure and armor still missing, indexed by {@link MechSection} ordinal. Frozen
 * at launch off the campaign's repair clock; {@link #NONE} for a whole mech and for every mech the
 * company does not own. {@link MechSections#carryWear} lays it on the sections at landing.
 */
public record MechCarriedWear(float[] structureLost, float[] armorLost) {

    public static final MechCarriedWear NONE =
            new MechCarriedWear(new float[MechSection.VALUES.length], new float[MechSection.VALUES.length]);

    public MechCarriedWear {
        int n = MechSection.VALUES.length;
        if (structureLost == null || armorLost == null || structureLost.length != n || armorLost.length != n) {
            throw new IllegalArgumentException("Carried wear needs one structure and one armor value per section");
        }
        structureLost = clamped(structureLost);
        armorLost = clamped(armorLost);
    }

    public float structureLost(MechSection section) {
        return structureLost[section.ordinal()];
    }

    public float armorLost(MechSection section) {
        return armorLost[section.ordinal()];
    }

    public boolean isEmpty() {
        for (int i = 0; i < structureLost.length; i++) {
            if (structureLost[i] > 0f || armorLost[i] > 0f) return false;
        }
        return true;
    }

    private static float[] clamped(float[] values) {
        float[] out = Arrays.copyOf(values, values.length);
        for (int i = 0; i < out.length; i++) {
            out[i] = Float.isFinite(out[i]) ? Math.max(0f, Math.min(1f, out[i])) : 0f;
        }
        return out;
    }
}

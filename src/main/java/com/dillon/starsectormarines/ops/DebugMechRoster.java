package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.ops.detachment.Detachment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Immutable briefing-selected mech family roster used only by debug missions. */
public final class DebugMechRoster {

    public static final int DEFAULT_COUNT = 3;
    public static final int MAX_COUNT = 8;

    private static final MechVariant[] FAMILY = {
            MechVariant.BULWARK, MechVariant.HOUND, MechVariant.SIROCCO
    };

    private final List<MechVariant> variants;

    private DebugMechRoster(List<MechVariant> variants) {
        this.variants = Collections.unmodifiableList(new ArrayList<>(variants));
    }

    public static DebugMechRoster randomized(int count, long seed) {
        int clamped = Math.max(0, Math.min(MAX_COUNT, count));
        Random random = new Random(seed);
        List<MechVariant> variants = new ArrayList<>(clamped);
        for (int i = 0; i < clamped; i++) {
            variants.add(FAMILY[random.nextInt(FAMILY.length)]);
        }
        return new DebugMechRoster(variants);
    }

    public int count() {
        return variants.size();
    }

    public List<MechVariant> variants() {
        return variants;
    }

    /** Replace a sourced/default Mech Support in place, or append the debug grant. */
    public List<CommandPower> applyTo(List<CommandPower> powers) {
        List<CommandPower> configured = new ArrayList<>();
        boolean inserted = false;
        if (powers != null) {
            for (CommandPower power : powers) {
                if (MechSupport.ID.equals(power.id)) {
                    if (!inserted && !variants.isEmpty()) {
                        configured.add(new MechSupport(variants));
                        inserted = true;
                    }
                } else {
                    configured.add(power);
                }
            }
        }
        if (!inserted && !variants.isEmpty()) configured.add(new MechSupport(variants));
        return configured;
    }

    public Detachment applyTo(Detachment detachment) {
        if (detachment == null) return Detachment.EMPTY;
        return new Detachment(detachment.shuttleManifest, detachment.marineWings,
                applyTo(detachment.powers));
    }
}

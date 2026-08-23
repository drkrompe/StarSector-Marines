package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.MechSupportPayload;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechVariant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Calls a shootable heavy transport that physically unloads one marine mech. */
public final class MechSupport extends AirDeliveryPower {

    public static final String ID = "mech_support";

    private final List<MechVariant> variants;

    public MechSupport() {
        this(List.of(MechVariant.BULWARK));
    }

    /** Debug/playtest constructor: one physical support sortie per chassis. */
    public MechSupport(List<MechVariant> variants) {
        super(ID, displayName(variants), 4f, validVariants(variants).size(), 0, 3,
                ShuttleType.VALKYRIE,
                MechSupportPayload.INSTANCE, 5);
        this.variants = Collections.unmodifiableList(new ArrayList<>(validVariants(variants)));
    }

    @Override
    protected void configureMission(ShuttleMission mission, ShuttleType carrier,
                                    CommandPowerService service) {
        int remaining = service != null ? service.getChargesRemaining(ID) : maxCharges - 1;
        mission.mechVariant = variantForRemainingCharges(remaining);
    }

    MechVariant variantForRemainingCharges(int remaining) {
        int activation = Math.max(0, maxCharges - Math.max(0, remaining) - 1);
        return variants.get(Math.min(activation, variants.size() - 1));
    }

    private static List<MechVariant> validVariants(List<MechVariant> variants) {
        if (variants == null || variants.isEmpty()) return List.of(MechVariant.BULWARK);
        List<MechVariant> valid = new ArrayList<>();
        for (MechVariant variant : variants) {
            if (variant != null) valid.add(variant);
        }
        return valid.isEmpty() ? List.of(MechVariant.BULWARK) : valid;
    }

    private static String displayName(List<MechVariant> variants) {
        int count = validVariants(variants).size();
        return count > 1 ? "Mech Support x" + count : "Mech Support";
    }
}

package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.MechSupportPayload;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechVariant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Calls a shootable heavy transport that physically unloads a marine mech lance. */
public final class MechSupport extends AirDeliveryPower {

    public static final String ID = "mech_support";
    public static final int LANCE_SIZE = 4;

    private final List<MechVariant> variants;

    public MechSupport() {
        this(List.of(MechVariant.BULWARK));
    }

    /** Debug/playtest constructor: one physical support sortie per lance. */
    public MechSupport(List<MechVariant> variants) {
        super(ID, displayName(variants), 4f, lanceCount(validVariants(variants).size()), 0, 3,
                ShuttleType.VALKYRIE,
                MechSupportPayload.INSTANCE, 5);
        this.variants = Collections.unmodifiableList(new ArrayList<>(validVariants(variants)));
    }

    @Override
    protected void configureMission(ShuttleMission mission, ShuttleType carrier,
                                    CommandPowerService service) {
        int remaining = service != null ? service.getChargesRemaining(ID) : maxCharges - 1;
        List<MechVariant> lance = lanceForRemainingCharges(remaining);
        mission.mechVariants = lance.toArray(new MechVariant[0]);
        mission.mechVariant = lance.get(0);
        mission.marinesRemaining = lance.size();
    }

    List<MechVariant> lanceForRemainingCharges(int remaining) {
        int activation = Math.max(0, maxCharges - Math.max(0, remaining) - 1);
        int from = Math.min(activation * LANCE_SIZE, variants.size() - 1);
        int to = Math.min(from + LANCE_SIZE, variants.size());
        return variants.subList(from, to);
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
        int mechs = validVariants(variants).size();
        int lances = lanceCount(mechs);
        return mechs > 1 ? "Mech Lance x" + lances : "Mech Support";
    }

    private static int lanceCount(int mechCount) {
        return Math.max(1, (mechCount + LANCE_SIZE - 1) / LANCE_SIZE);
    }
}

package com.dillon.starsectormarines.ops.loot;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.Trait;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

/** Resolves captain expertise and player-fleet salvage hardware. */
public final class LootRecoveryModifiers {

    static final String SALVAGE_RIG_HULL_ID = "crig";
    static final String SALVAGE_GANTRY_HULLMOD_ID = "repair_gantry";
    static final int EXPERT_RECOVERY_BONUS = 25;
    static final int EXPERT_HIGH_VALUE_CHANCE = 10;
    static final int RIG_RECOVERY_BONUS = 10;
    static final int GANTRY_RECOVERY_BONUS = 5;
    static final int FLEET_RECOVERY_CAP = 40;

    private LootRecoveryModifiers() {}

    public static LootRecoveryModifier resolve(MarineCaptain captain) {
        boolean expert = captain != null && captain.traits().contains(Trait.SALVAGE_EXPERT);
        int rigCount = 0;
        int gantryCount = 0;
        if (Global.getSector() != null) {
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            if (fleet != null && fleet.getFleetData() != null) {
                for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
                    if (member == null) continue;
                    if (SALVAGE_RIG_HULL_ID.equals(member.getHullId())) {
                        rigCount++;
                        continue;
                    }
                    ShipVariantAPI variant = member.getVariant();
                    if (variant != null && variant.hasHullMod(SALVAGE_GANTRY_HULLMOD_ID)) {
                        gantryCount++;
                    }
                }
            }
        }
        return compute(expert, rigCount, gantryCount);
    }

    static LootRecoveryModifier compute(boolean expert, int rigCount, int gantryCount) {
        int fleetBonus = Math.max(0, rigCount) * RIG_RECOVERY_BONUS
                + Math.max(0, gantryCount) * GANTRY_RECOVERY_BONUS;
        fleetBonus = Math.min(FLEET_RECOVERY_CAP, fleetBonus);
        int recoveryBonus = fleetBonus + (expert ? EXPERT_RECOVERY_BONUS : 0);
        int highValueChance = expert ? EXPERT_HIGH_VALUE_CHANCE : 0;
        return new LootRecoveryModifier(recoveryBonus, highValueChance);
    }
}

package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;

import java.util.HashSet;
import java.util.Set;

/** Shared player-ownership view used before offering another template card. */
public final class EquipmentTemplateCardInventory {

    private EquipmentTemplateCardInventory() {}

    /**
     * Collected and already-carried template ids, or {@code null} while player
     * cargo is unavailable and a consumer should avoid making a duplicate offer.
     */
    public static Set<String> playerUnavailableTemplateIds() {
        CargoAPI cargo = playerCargo();
        if (cargo == null) return null;
        Set<String> unavailable = new HashSet<>();
        MarineRosterScript roster = MarineRosterScript.getInstance();
        if (roster != null) {
            unavailable.addAll(roster.roster().armory().ownedEquipmentTemplateIds());
        }
        unavailable.addAll(carriedTemplateIds(cargo));
        return Set.copyOf(unavailable);
    }

    static Set<String> carriedTemplateIds(CargoAPI cargo) {
        if (cargo == null) return Set.of();
        Set<String> carried = new HashSet<>();
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData special = stack.getSpecialDataIfSpecial();
            if (special != null
                    && EquipmentTemplateCardItemPlugin.ITEM_ID.equals(special.getId())
                    && special.getData() != null) {
                carried.add(special.getData());
            }
        }
        return Set.copyOf(carried);
    }

    private static CargoAPI playerCargo() {
        if (Global.getSector() == null) return null;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        return fleet != null ? fleet.getCargo() : null;
    }
}

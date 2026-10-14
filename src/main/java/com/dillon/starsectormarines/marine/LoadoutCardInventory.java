package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;

import java.util.HashSet;
import java.util.Set;

/** Which loadout cards no channel should offer again: already learned, or already in cargo. */
public final class LoadoutCardInventory {

    private LoadoutCardInventory() {}

    /** Null when the player's cargo is unavailable and the caller must retry later. */
    public static Set<String> playerUnavailableCardIds() {
        CargoAPI cargo = playerCargo();
        if (cargo == null) return null;
        Set<String> unavailable = new HashSet<>(carriedCardIds(cargo));
        MarineRosterScript roster = MarineRosterScript.getInstance();
        if (roster != null) unavailable.addAll(knownCardIds(roster.roster().armory()));
        return Set.copyOf(unavailable);
    }

    /** Offerable cards that teaching the armory would add nothing for. */
    public static Set<String> knownCardIds(MarineArmory armory) {
        Set<String> known = new HashSet<>();
        for (LoadoutCard card : LoadoutCards.all()) {
            if (LoadoutCardLearning.status(armory, card.id())
                    == LoadoutCardLearning.Result.ALREADY_KNOWN) {
                known.add(card.id());
            }
        }
        return known;
    }

    static Set<String> carriedCardIds(CargoAPI cargo) {
        if (cargo == null) return Set.of();
        Set<String> carried = new HashSet<>();
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData special = stack.getSpecialDataIfSpecial();
            if (special != null && LoadoutCardItemPlugin.ITEM_ID.equals(special.getId())
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

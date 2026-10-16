package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;

import java.util.HashSet;
import java.util.Set;

/** Which mech patterns no channel should offer again: already known by the bay, or in cargo. */
public final class MechPatternInventory {

    private MechPatternInventory() {}

    /** The cargo payload of one mech pattern card. */
    public static SpecialItemData itemData(String patternId) {
        return new SpecialItemData(MechPatternLearning.ITEM_ID, patternId);
    }

    public static boolean isPatternCard(SpecialItemData special) {
        return special != null && MechPatternLearning.ITEM_ID.equals(special.getId());
    }

    /** Null when the player's cargo is unavailable and the caller must retry later. */
    public static Set<String> playerUnavailablePatternIds() {
        CargoAPI cargo = playerCargo();
        if (cargo == null) return null;
        Set<String> unavailable = new HashSet<>(carriedPatternIds(cargo));
        unavailable.addAll(knownPatternIds(playerBay()));
        return Set.copyOf(unavailable);
    }

    /** Patterns the bay already knows; empty when there is no bay. */
    public static Set<String> knownPatternIds(MechBay bay) {
        return bay != null ? Set.copyOf(bay.knownPatternIds()) : Set.of();
    }

    /** Whether the company's bay holds at least one mech. */
    public static boolean fieldsMechs(MechBay bay) {
        if (bay == null) return false;
        for (CampaignMechSquad squad : bay.squads()) {
            if (!squad.mechs().isEmpty()) return true;
        }
        return false;
    }

    static Set<String> carriedPatternIds(CargoAPI cargo) {
        if (cargo == null) return Set.of();
        Set<String> carried = new HashSet<>();
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData special = stack.getSpecialDataIfSpecial();
            if (isPatternCard(special) && special.getData() != null) carried.add(special.getData());
        }
        return Set.copyOf(carried);
    }

    public static MechBay playerBay() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster().mechBay() : null;
    }

    private static CargoAPI playerCargo() {
        if (Global.getSector() == null) return null;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        return fleet != null ? fleet.getCargo() : null;
    }
}

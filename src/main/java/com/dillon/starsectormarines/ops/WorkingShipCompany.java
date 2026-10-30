package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.ShepherdBasin;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.LoadoutCard;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
import java.util.List;

/**
 * The company A Working Ship is balanced against: the one that comes to Asharu having won A Cold Account, five squads
 * and four machines with its first heavies in the bay (the Kit Ladder sells the Bulwark by Blind Watch and the
 * breakthrough tank by Thunder Run, {@code kit-ladder.json}). The recovered Hound leads a Bulwark, a breakthrough tank
 * and an LRM carrier on every visit. Its kit grows with the ladder: Salvage Rights' squads carry Cold Account's cards;
 * winning it sells Fire-Support, which Cold Start's company carries; winning Cold Start sells the Foundry-Breaker rigs,
 * which Working Passage's breaching squad wears. The debug briefing lands it and the probes build it, so a played debug
 * start and a probe meet the same force.
 */
public final class WorkingShipCompany {
    private WorkingShipCompany() {}

    /** Line squads. */
    public static final int SQUADS = 5;
    /** Mechs in the bay beside the recovered Hound. */
    public static final List<MechVariant> MECHS = List.of(MechVariant.BULWARK);
    /** Vehicles in the bay. */
    public static final List<VehicleType> VEHICLES = List.of(VehicleType.BREAKTHROUGH_TANK, VehicleType.LRM_CARRIER);
    /** Every machine the company drops: the Hound, its mechs and its vehicles. */
    public static final int MACHINES = 1 + MECHS.size() + VEHICLES.size();
    /** The bay as the debug briefing names it. */
    static final String BAY = "the Hound (chaingun), a Bulwark, a breakthrough tank and an LRM carrier";

    /** One squad's weapon card each, in squad order, on {@code visit}. */
    public static List<String> weaponCards(ShepherdBasin.Visit visit) {
        if (visit == ShepherdBasin.Visit.SALVAGE_RIGHTS) return ColdAccountCompany.WEAPON_CARDS;
        return List.of(SquadEquipmentDoctrines.TRENCH_CLEARING_WEAPONS, SquadEquipmentDoctrines.CONVOY_ESCORT_WEAPONS,
                SquadEquipmentDoctrines.COUNTER_BATTERY_WEAPONS, SquadEquipmentDoctrines.FIRE_SUPPORT_WEAPONS,
                FirstRefit.Path.STEADY_LINE.weapons);
    }

    /** Squad {@code index}'s armour sheet on {@code visit}: the breaching squad wears the heavy rigs once they sell. */
    public static String armorSheet(ShepherdBasin.Visit visit, int index) {
        return visit == ShepherdBasin.Visit.WORKING_PASSAGE && index == 0
                ? SquadEquipmentDoctrines.OUTLAW_HEAVY_ARMOR : ColdAccountCompany.ARMOR_SHEET;
    }

    /** Teaches {@code roster} {@code visit}'s cards and issues them to its line squads, one card per squad in order. */
    public static void issue(MarineRoster roster, ShepherdBasin.Visit visit) {
        List<String> cards = weaponCards(visit);
        FirstRefit.registerWeaponCards(roster.armory());
        for (String card : cards) roster.armory().learnLoadoutCard(new LoadoutCard(LoadoutCard.Kind.WEAPON, card));
        var squads = roster.squads().stream().filter(squad -> !squad.reserve()).toList();
        for (int i = 0; i < squads.size(); i++) {
            String card = cards.get(i % cards.size()), sheet = armorSheet(visit, i);
            roster.armory().acquireArmorPlan(sheet);
            SquadEquipmentResult result = roster.applySquadEquipment(squads.get(i).id(), card, sheet);
            if (result != SquadEquipmentResult.APPLIED)
                throw new IllegalStateException(card + " / " + sheet + " not issued to " + squads.get(i).id() + ": " + result);
        }
    }

    /** The bay as a debug drop: the recovered Hound first, then the rest. */
    static DebugMechRoster bay() {
        return DebugMechRoster.recoveredHoundWith(MECHS, VEHICLES);
    }
}

package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.LoadoutCard;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
import java.util.List;

/**
 * The company A Cold Account is balanced against: the chapter's five squads and four machines, the recovered Hound
 * beside a Warden, an LRM carrier and a mortar APC, every one of them a machine the company's own boat can lift (a
 * fitted Hermes carries Medium at most, so no tank). The debug briefing lands it and the Corvus evidence musters it, so
 * a played debug start and an evidence run meet the same force.
 *
 * <p>Its kit is what the Kit Ladder lets a company buy by Blind Watch ({@code kit-ladder.json}): one squad on each
 * weapon card it reaches, the Fleet Combat sheet over all of them, so the landing carries its rockets, point defence
 * and anti-materiel rifle rather than rifles alone.
 */
public final class ColdAccountCompany {
    private ColdAccountCompany() {}

    /** One squad's weapon card each, in squad order: the cards the ladder sells by Blind Watch. */
    public static final List<String> WEAPON_CARDS = List.of(SquadEquipmentDoctrines.TRENCH_CLEARING_WEAPONS,
            SquadEquipmentDoctrines.CONVOY_ESCORT_WEAPONS, SquadEquipmentDoctrines.COUNTER_BATTERY_WEAPONS,
            FirstRefit.Path.STEADY_LINE.weapons, FirstRefit.Path.FORWARD_SCREEN.weapons);
    /** Every squad's armour sheet: the heaviest the ladder sells by Blind Watch. */
    public static final String ARMOR_SHEET = SquadEquipmentDoctrines.FLEET_COMBAT_ARMOR;

    /** Mechs in the bay beside the recovered Hound. */
    public static final List<MechVariant> MECHS = List.of(MechVariant.WARDEN);
    /** Vehicles in the bay. */
    public static final List<VehicleType> VEHICLES = List.of(VehicleType.LRM_CARRIER, VehicleType.FIRE_SUPPORT_APC);
    /** Every machine the company drops: the Hound, its mechs and its vehicles. */
    public static final int MACHINES = 1 + MECHS.size() + VEHICLES.size();
    /** The bay as the debug briefing names it. */
    static final String BAY = "the Hound (chaingun), a Warden, an LRM carrier and a mortar APC";

    /** Teaches {@code roster} the company's cards and issues them to its line squads, one card per squad in order. */
    public static void issue(MarineRoster roster) {
        FirstRefit.registerWeaponCards(roster.armory());
        for (String card : WEAPON_CARDS) roster.armory().learnLoadoutCard(new LoadoutCard(LoadoutCard.Kind.WEAPON, card));
        roster.armory().acquireArmorPlan(ARMOR_SHEET);
        var squads = roster.squads().stream().filter(squad -> !squad.reserve()).toList();
        for (int i = 0; i < squads.size(); i++) {
            String card = WEAPON_CARDS.get(i % WEAPON_CARDS.size());
            SquadEquipmentResult result = roster.applySquadEquipment(squads.get(i).id(), card, ARMOR_SHEET);
            if (result != SquadEquipmentResult.APPLIED)
                throw new IllegalStateException(card + " not issued to " + squads.get(i).id() + ": " + result);
        }
    }

    /** The bay as a debug drop: the recovered Hound first, then the rest. */
    static DebugMechRoster bay() {
        return DebugMechRoster.recoveredHoundWith(MECHS, VEHICLES);
    }
}

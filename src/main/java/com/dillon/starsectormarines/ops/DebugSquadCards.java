package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadArmorPlan;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
import com.dillon.starsectormarines.marine.SquadWeaponDoctrine;
import java.util.List;

/**
 * Issues the debug company's squads any weapon or armor card its armory holds. The debug
 * company holds every card ({@code DebugCompany.unlockEveryCard}), so these are the whole
 * catalog; a card the armory still refuses is simply not offered.
 */
final class DebugSquadCards {
    private DebugSquadCards() { }

    /** The squads the debug briefing offers cards for: the line squads, in order. */
    static List<MarineSquad> lineSquads(MarineRoster roster) {
        return roster.squads().stream().filter(s -> !s.reserve()).toList();
    }

    /** Every weapon card a squad may be given. */
    static List<SquadWeaponDoctrine> weaponOptions(MarineRoster roster) {
        return roster.armory().weaponDoctrines().stream()
                .filter(d -> roster.armory().knowsWeaponCard(d.id())).toList();
    }

    /** Every tactic sheet the armory can fill. */
    static List<SquadArmorPlan> armorOptions(MarineRoster roster) {
        return SquadEquipmentDoctrines.armorPlans().stream().filter(roster.armory()::canIssue).toList();
    }

    /** Gives each of {@code squads} the weapon card, keeping its armor; returns how many took it. */
    static int issueWeapons(MarineRoster roster, List<MarineSquad> squads, String doctrineId) {
        int issued = 0;
        for (MarineSquad squad : squads) {
            if (roster.applySquadEquipment(squad.id(), doctrineId, squad.armorDoctrineId())
                    == SquadEquipmentResult.APPLIED) issued++;
        }
        return issued;
    }

    /** Gives each of {@code squads} the tactic sheet, keeping its weapons; returns how many took it. */
    static int issueArmor(MarineRoster roster, List<MarineSquad> squads, String planId) {
        int issued = 0;
        for (MarineSquad squad : squads) {
            if (roster.applySquadEquipment(squad.id(), squad.weaponDoctrineId(), planId)
                    == SquadEquipmentResult.APPLIED) issued++;
        }
        return issued;
    }
}

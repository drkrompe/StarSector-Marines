package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
import java.util.List;
import java.util.function.Function;

/** Steps a debug company squad through the weapon and armor cards its armory holds. */
final class DebugSquadCards {
    private DebugSquadCards() { }

    /** The squads the debug briefing offers cards for: the line squads, in order. */
    static List<MarineSquad> lineSquads(MarineRoster roster) {
        return roster.squads().stream().filter(s -> !s.reserve()).toList();
    }

    /** The squad's weapon card after {@code step} cards, or the same card when none can be issued. */
    static String cycleWeapons(MarineRoster roster, MarineSquad squad, int step) {
        return cycle(roster, squad, step, true);
    }

    static String cycleArmor(MarineRoster roster, MarineSquad squad, int step) {
        return cycle(roster, squad, step, false);
    }

    static String weaponName(MarineRoster roster, MarineSquad squad) {
        return roster.armory().weaponDoctrines().stream().filter(d -> d.id().equals(squad.weaponDoctrineId()))
                .map(d -> d.displayName()).findFirst().orElse("None");
    }

    static String armorName(MarineRoster roster, MarineSquad squad) {
        return roster.armory().armorDoctrines().stream().filter(d -> d.id().equals(squad.armorDoctrineId()))
                .map(d -> d.displayName()).findFirst().orElse("None");
    }

    private static String cycle(MarineRoster roster, MarineSquad squad, int step, boolean weapons) {
        List<String> ids = weapons ? roster.armory().weaponDoctrines().stream().map(d -> d.id()).toList()
                : roster.armory().armorDoctrines().stream().map(d -> d.id()).toList();
        String current = weapons ? squad.weaponDoctrineId() : squad.armorDoctrineId();
        int start = Math.max(0, ids.indexOf(current));
        for (int i = 1; i <= ids.size(); i++) {
            String candidate = ids.get(Math.floorMod(start + step * i, ids.size()));
            var result = weapons ? roster.applySquadEquipment(squad.id(), candidate, squad.armorDoctrineId())
                    : roster.applySquadEquipment(squad.id(), squad.weaponDoctrineId(), candidate);
            if (result == SquadEquipmentResult.APPLIED) return candidate;
        }
        return current;
    }
}

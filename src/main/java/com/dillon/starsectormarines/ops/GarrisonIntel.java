package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.setup.DefenderRoster;
import com.dillon.starsectormarines.battle.setup.GroundRosterProfile;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.i18n.Strings;

/**
 * The garrison an operation will face, as the briefing room's holo-tank shows it. The headcount is the battle's own:
 * the defenders' total is settled by the mission's type, tier and risk and the market's garrison, and nothing the
 * company commits moves it. Only the mechs answer the detachment: a lighter landing faces fewer of them, and the seats
 * they leave are filled with infantry. So this reads the roster with nothing held back ({@code mechs} is the most the
 * operation can field) and the regulars and militia are the least of each.
 *
 * @param defenders every defender on the ground, mechs included
 * @param regulars  stiffening regulars, at the most mechs
 * @param militia   militia filler, at the most mechs
 * @param mechs     the most mechs the operation fields
 */
public record GarrisonIntel(int defenders, int regulars, int militia, int mechs) {

    /** The estimate for {@code mission} over the profile launch builds its battle from ({@link MissionLaunch#targetProfile}). */
    static GarrisonIntel estimate(Mission mission, TargetProfile faced, boolean heavyArmor,
                                  GroundRosterProfile groundRoster) {
        DefenderRoster roster = DefenderRoster.forMission(mission.type, mission.tier, mission.risk, heavyArmor,
                Float.POSITIVE_INFINITY, groundRoster, faced.garrison());
        return new GarrisonIntel(roster.totalCount, roster.eliteCount, roster.militiaCount, roster.mechCount);
    }

    /** The holo-tank's line on the briefing. */
    public String line() {
        return mechs > 0
                ? Strings.format("briefingGarrisonIntelMechs", defenders, regulars, militia, mechs)
                : Strings.format("briefingGarrisonIntel", defenders, regulars, militia);
    }
}

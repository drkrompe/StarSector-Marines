package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.world.gen.FortSitingPolicy;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;

/**
 * Decides which ground defences a mission's battle carries. This is the one place the decision is made: map
 * generation and battle setup place whatever {@link GroundDefences} comes out of here and nothing else, so a
 * designer who wants more, fewer or different defences changes this class (or a mission's explicit statement), not a
 * generator stage.
 *
 * <p>Precedence: a mission's explicit {@link Mission#defences} statement (today the DEBUG ground-defences dial) wins;
 * otherwise the mission type's policy applies; a type with no policy carries none.
 */
public final class MissionDefencePolicy {

    private MissionDefencePolicy() {}

    /**
     * Fortress batteries a Conquest declares when its target market holds Heavy Batteries.
     * UNTUNED first-pass mission-design choice: it equals what the old area rule gave the canonical 560x336 Conquest
     * map, so the numbers can be changed freely once play settles how often and how heavy.
     */
    static final int CONQUEST_BATTERIES_WHEN_MARKET_HAS_THEM = 4;

    /**
     * Whether a Conquest stands its declared batteries as battery forts (a walled platform with fire-control bunker,
     * magazine and power building) rather than as bare structures. UNTUNED first-pass mission-design choice, kept here
     * so a designer can turn the whole conversion off in one line; see {@code forts.md}.
     */
    static final boolean CONQUEST_BATTERIES_AS_FORTS = true;

    /** Whether a Conquest declares a shield generator when its target market holds a Planetary Shield. UNTUNED. */
    static final boolean CONQUEST_SHIELD_WHEN_MARKET_HAS_ONE = true;

    /**
     * Whether a Conquest stands its declared shield generator as a shield fort (the generator inside an inner keep, with
     * a power plant and a control room) rather than as a bare generator beside the keep. UNTUNED first-pass
     * mission-design choice, one line to turn off; see {@code forts.md}.
     */
    static final boolean CONQUEST_SHIELD_AS_FORT = true;

    /**
     * Whether a Conquest sites its declared forts as barriers on the way in (a shield fort over the keep, battery forts
     * spread across the approach between the landing and the keep) rather than on leftover ground. UNTUNED first-pass
     * mission-design choice, one line to turn off; see {@code forts.md}.
     */
    static final boolean CONQUEST_FORTS_COVER_THE_APPROACH = true;

    /**
     * Defence nests a Conquest declares when its target market holds Ground Defenses. UNTUNED first-pass
     * mission-design choice: a dozen over the canonical 560x336 map (roughly one per 15,000 cells), few enough to be
     * a field to cross rather than a wall. Change freely once play settles how thick the network should be.
     */
    static final int CONQUEST_NESTS_WHEN_MARKET_HAS_THEM = 12;

    /**
     * The defences this mission declares.
     *
     * @param market the target world's profile as the market gave it; its signals are an input, not the answer
     */
    public static GroundDefences declare(Mission mission, TargetProfile market) {
        if (mission.defences != null) return mission.defences.declaration();
        if (mission.type == MissionType.CONQUEST) return conquest(market);
        return GroundDefences.NONE;
    }

    /**
     * UNTUNED first-pass mission-design choice: a Conquest reads its market's Heavy Batteries, Planetary Shield and Ground Defenses
     * signals and declares them (and its Ground Defenses as nests) at the fixed amounts above. Change here to make Conquest always, never or sometimes
     * carry them.
     */
    static GroundDefences conquest(TargetProfile market) {
        int batteries = market.heavyBatteries() ? CONQUEST_BATTERIES_WHEN_MARKET_HAS_THEM : 0;
        boolean shield = market.planetaryShield() && CONQUEST_SHIELD_WHEN_MARKET_HAS_ONE;
        GroundDefences declared = new GroundDefences(
                CONQUEST_BATTERIES_AS_FORTS ? 0 : batteries,
                shield && !CONQUEST_SHIELD_AS_FORT,
                market.groundDefences() ? CONQUEST_NESTS_WHEN_MARKET_HAS_THEM : 0);
        if (CONQUEST_BATTERIES_AS_FORTS) declared = declared.withForts(FortKind.BATTERY_FORT, batteries);
        if (CONQUEST_SHIELD_AS_FORT && shield) declared = declared.withForts(FortKind.SHIELD_FORT, 1);
        if (CONQUEST_FORTS_COVER_THE_APPROACH && !declared.forts().isEmpty()) {
            declared = declared.withSiting(FortSitingPolicy.COVER_THE_APPROACH);
        }
        return declared;
    }
}

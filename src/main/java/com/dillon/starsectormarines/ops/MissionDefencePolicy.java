package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.airsupport.AirPoolAllowance;
import com.dillon.starsectormarines.battle.airsupport.AirPoolDeclaration;
import com.dillon.starsectormarines.battle.airsupport.AirPoolStock;
import com.dillon.starsectormarines.battle.infantry.SquadSupportDeclaration;
import com.dillon.starsectormarines.battle.infantry.SquadSupportKit;
import com.dillon.starsectormarines.battle.mech.MechDropAllowance;
import com.dillon.starsectormarines.battle.mech.MechDropDeclaration;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.FortSitingPolicy;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;

import java.util.List;

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
     * Mech lances a Conquest lets its marine commander call to the drop zone. UNTUNED first-pass mission-design
     * choice: two, because one lance is a point to be lost and the phased assault wants a base of fire and a
     * replacement. Change freely once play settles how much armour a muster can ask for; see {@code phased-assault.md}.
     */
    static final int CONQUEST_MARINE_MECH_LANCES = 2;

    /** The composition of each Conquest lance: one anchor, two skirmishers, one support. UNTUNED. */
    static final List<MechVariant> CONQUEST_MECH_LANCE =
            List.of(MechVariant.BULWARK, MechVariant.HOUND, MechVariant.HOUND, MechVariant.SIROCCO);

    /**
     * Every Nth marine squad of a Conquest is a mortar team, the last of each run of this many. UNTUNED first-pass
     * mission-design choice: a Conquest force is tens of squads, so six makes a handful of tubes, enough that a
     * fire mission is a thing that happens without a mortar being most of the force. See {@code phased-assault.md}.
     */
    static final int CONQUEST_MARINE_MORTAR_TEAM_EVERY = 6;

    /** Every Nth marine squad of a Conquest carries a forward observer's comms radio. UNTUNED, as above. */
    static final int CONQUEST_MARINE_OBSERVER_EVERY = 4;

    /** The marine air pool a Conquest declares: bombers and fighters, replaced over time, directed by its commander. A fighter is the cheaper airframe and is replaced faster (25 s) than a bomber (40 s). UNTUNED. */
    static final AirPoolAllowance CONQUEST_MARINE_AIR_POOL = AirPoolAllowance.of(
            new AirPoolStock(3, 3, 40f), new AirPoolStock(2, 2, 25f), true);

    /**
     * The pool of bombers and fighters each side holds, tasked by comms operators. A mission's explicit
     * {@link Mission#airPool} statement (today the DEBUG dial) wins; otherwise a Conquest gives its marines
     * {@code CONQUEST_MARINE_AIR_POOL}, which their commander directs; every other mission type declares none and plays as it
     * did before pools.
     */
    public static AirPoolDeclaration declareAirPool(Mission mission) {
        if (mission.airPool != null) return mission.airPool.declaration();
        if (mission.type == MissionType.CONQUEST) {
            return AirPoolDeclaration.NONE.with(Faction.MARINE, CONQUEST_MARINE_AIR_POOL);
        }
        return AirPoolDeclaration.NONE;
    }

    /**
     * The support kit each side issues its squads: a Conquest's marines carry mortar teams and forward observers, which is
     * what lets the commander's plan and standing fire missions use the field mortar and the air pool declared beside it.
     * Every other mission type declares none.
     */
    public static SquadSupportDeclaration declareSupportKit(Mission mission) {
        if (mission.type == MissionType.CONQUEST) {
            return SquadSupportDeclaration.NONE.with(Faction.MARINE,
                    new SquadSupportKit(CONQUEST_MARINE_MORTAR_TEAM_EVERY, CONQUEST_MARINE_OBSERVER_EVERY));
        }
        return SquadSupportDeclaration.NONE;
    }

    /**
     * The mech lances each side's commander may call down on its own authority. A mission's explicit
     * {@link Mission#mechDrop} statement (today the DEBUG dial) wins; otherwise a Conquest gives its marines
     * {@link #CONQUEST_MARINE_MECH_LANCES}; every other mission type declares none.
     */
    public static MechDropDeclaration declareMechDrops(Mission mission) {
        if (mission.mechDrop != null) return mission.mechDrop.declaration();
        if (mission.type == MissionType.CONQUEST) {
            return MechDropDeclaration.NONE.with(Faction.MARINE,
                    new MechDropAllowance(CONQUEST_MARINE_MECH_LANCES, CONQUEST_MECH_LANCE));
        }
        return MechDropDeclaration.NONE;
    }

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

package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;

/**
 * What the marines come down on.
 *
 * <p>The landing zone used to be whatever ground a terminal scan found first
 * inside the attacker's region — walkable, outside a building, and otherwise
 * unremarkable, which on one 560x336 frame put the beachhead in the middle of a
 * base district. A kind is the first half of making it a <em>place</em>: it says
 * what stands there, and therefore how much ground the precinct that holds it
 * has to claim before the town grows over it.
 *
 * <p>Derived from the world when nobody says, and stated by a mission when it
 * cares — the same authored-wins shape {@link Fortification},
 * {@link PrecinctCharacter} and {@link Standoff} already have.
 */
public enum LandingKind {

    /**
     * A programmed place: a terminal, a hangar, a control office and a fuel
     * yard round an open apron. The civil spaceport a market with a real port
     * already has, which is where a landing force would put itself down.
     */
    SPACEPORT,

    /**
     * A zoned open place: apron and nothing built. What a world with no port
     * offers, and what a remote installation's country offers whatever the
     * market says — a field is a field.
     */
    FIELD,

    /**
     * Between the two: an apron with one hut on it. The shape an off-grid
     * settlement's {@link SettlementLink#LANDING} lifeline already implies —
     * somewhere ships put down, run from a hut, with nothing else round it.
     */
    STRIP;

    /**
     * What a world offers to land on.
     *
     * <p>Deliberately coarse, like the rest of {@link PrecinctPlan#derive}: it
     * is a default that keeps procedural battles varied rather than a model of
     * civil aviation, and the moment a mission cares it should state one.
     *
     * <p><b>A remote world is a field whatever its market reports.</b> Sprawl
     * says the map is an installation in country with no settlement on it, and
     * a civil spaceport campus standing alone in that country is a town the
     * sprawl already said there was not.
     */
    public static LandingKind derive(TargetProfile profile, PrecinctPlan.Sprawl sprawl) {
        if (profile == null) return FIELD;
        if (sprawl == PrecinctPlan.Sprawl.REMOTE) return FIELD;
        if (profile.spaceportTier() > 0) return SPACEPORT;
        if (profile.link() == SettlementLink.LANDING) return STRIP;
        return FIELD;
    }

    /**
     * The buildings and the open ground this kind owes, before the map is asked
     * whether it can afford them.
     *
     * <p>Every kind owes an apron, because the berths have to stand somewhere
     * and the ground under them is the whole reason the landing place is a
     * precinct rather than a scan. A {@link #FIELD} owes only that, which is
     * what "builds nothing" means: it still claims its ground.
     */
    public FortressProgram program() {
        return switch (this) {
            case SPACEPORT -> FortressProgram.spaceport();
            case STRIP -> FortressProgram.landingStrip();
            case FIELD -> FortressProgram.landingField();
        };
    }
}

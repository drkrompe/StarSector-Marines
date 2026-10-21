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
    STRIP,

    /**
     * Fields outside a settlement's edge, chosen for what they let the force
     * see: nothing built, walled or tall within {@link #fieldOfFire()} cells of
     * the berths' centre, so the company lands with long, clear fields of fire
     * on every side and walks into the town across open ground.
     *
     * <p>Never derived. It is what a mission asks for when the town itself is
     * the danger — a colony whose ground opens under it, whose sky the spores
     * deny and whose buildings would give the swarm cover to the hull — and
     * the reason for landing out there rather than beside the people being
     * rescued is the mission's to state, not the world's.
     */
    OPEN_FIELD;

    /**
     * How far from the field's centre nothing may stand, in cells, or zero for
     * a kind that promises no field of fire.
     *
     * <p>Forty: the berths stand within {@link #BERTH_REACH} of the centre, so
     * every berth keeps at least twenty-four cells of open ground on every
     * side, which is a service rifle's reach and most of a marksman's. Less,
     * and a runner is on the berth before a squad has fired twice; much more,
     * and the field stops fitting between a 400x240 colony's places.
     */
    public int fieldOfFire() {
        return this == OPEN_FIELD ? 40 : 0;
    }

    /**
     * How far from a field's centre its berths may stand: the box the arrival
     * areas are scanned in, measured to the far corner of a berth.
     */
    public static final int BERTH_REACH = 16;

    /**
     * How far in from the edge it lands across a field's centre sits.
     *
     * <p>Less than the radius, so a quarter of the disc lies off the map. The
     * edge is not an obstruction, and nothing comes at the company out of the
     * country behind it: the clear ground that matters is the town side and
     * the flanks. Sitting closer in keeps the shuttles' flight to the berths
     * short and leaves the town more of the map, since the country it gives up
     * runs to the disc's far side.
     */
    public int fieldSetback() {
        return fieldOfFire() * 3 / 4;
    }

    /** Whether this kind promises a clear field of fire round its berths. */
    public boolean hasFieldOfFire() {
        return fieldOfFire() > 0;
    }

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
     * How many arrival areas the shipped apron already seats.
     *
     * <p>Measured at 560x336 on both canonical Conquest fixtures: five, against
     * the three the mission asked for. It is the baseline a lift's berth demand
     * is compared against, so a lift wanting four pairs asks the program for
     * nothing at all.
     */
    public static final int AREAS_THE_APRON_SEATS = 5;

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
        return program(AREAS_THE_APRON_SEATS);
    }

    /**
     * The same program with an apron wide enough for {@code arrivalAreas}
     * berthing pairs.
     *
     * <p>The apron is the frontage the arrival areas are stepped along, so a
     * lift sized to land a larger force needs a wider one — the berths are
     * where the lift stops being arithmetic and starts needing ground. It grows
     * in proportion to the demand and <b>never shrinks below what the shipped
     * apron already buys</b>: a smaller lift does not want a smaller beachhead,
     * because the apron is also the ground the beachhead compound is made of.
     */
    public FortressProgram program(int arrivalAreas) {
        FortressProgram base = switch (this) {
            case SPACEPORT -> FortressProgram.spaceport();
            case STRIP -> FortressProgram.landingStrip();
            case FIELD -> FortressProgram.landingField();
            case OPEN_FIELD -> FortressProgram.openField(fieldOfFire());
        };
        if (arrivalAreas <= AREAS_THE_APRON_SEATS) return base;
        // Frontage scales with the areas and the claim is roughly square, so
        // the ground goes as the square of the ratio.
        float ratio = arrivalAreas / (float) AREAS_THE_APRON_SEATS;
        return base.withApron(Math.round(base.apron() * ratio * ratio));
    }
}

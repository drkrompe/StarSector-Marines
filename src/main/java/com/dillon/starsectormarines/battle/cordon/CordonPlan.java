package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.precinct.LandingKind;
import com.dillon.starsectormarines.battle.world.gen.precinct.MapPlacement;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctBrief;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctCharacter;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;

/**
 * The Cordon's map, laid out ({@code cordon-nouns.md}, The map): an open-field
 * landing in surveyed country against the rear edge, hamlets behind the line, a
 * row of field redoubts across the map's long axis, and the colony the bloom
 * came out of in front of them.
 *
 * <p>Every place is stated by its depth from the rear rather than by a compass
 * point, because which edge is the rear is the seed's: a depth of 0 is the
 * rear side of the ground the landing's country leaves, 1 the far edge. The
 * redoubts are programmed places, so they claim their ground first and the
 * hamlets and the colony grow round them; they are listed west to east, so a
 * redoubt's number is its order across the line.
 *
 * <p>The Road keeps the colony's civil-defence compound (its people are in it);
 * in the Line the compound fell with the colony and is not built.
 */
public final class CordonPlan {

    /** Redoubts across the line. */
    public static final int REDOUBTS = 4;
    /** The precinct name of redoubt {@code n} (1-based, west to east) is this plus {@code n}. */
    public static final String REDOUBT_PLACE = "cordon-redoubt-";
    /** Wall left in a field redoubt's panel: field grade, inside what a Brute can break. */
    public static final int REDOUBT_WALL_HP = 120;
    /** Ways in: one toward the front road, one toward the rear. */
    public static final int REDOUBT_GATES = 2;

    /** Depth band of the line, as fractions of the ground the country leaves. */
    static final float LINE_NEAR = 0.36f, LINE_FAR = 0.42f;

    /**
     * A redoubt's own roads: short arms, enough to cross its wall into the
     * roads either side and no more (the civil-defence compound's growth).
     */
    private static final GrownTrunkPlan.Profile REDOUBT_GROWTH =
            new GrownTrunkPlan.Profile(0, 0f, 0.04f, 0.06f, 0f, 4, SettlementLink.NONE);

    /**
     * The colony: a town, but a frontier one, its streets reaching a twentieth
     * to a ninth of the map's short side rather than the third a town's arms
     * run (which on a 560x336 map grew one town across the whole of it).
     */
    private static final GrownTrunkPlan.Profile COLONY_GROWTH =
            new GrownTrunkPlan.Profile(7, 0.7f, 0.05f, 0.11f, 0.4f, 12, SettlementLink.ROAD);
    /** A hamlet in open country: a few short roads. */
    private static final GrownTrunkPlan.Profile HAMLET_GROWTH =
            new GrownTrunkPlan.Profile(3, 0.5f, 0.04f, 0.09f, 0.3f, 8, SettlementLink.ROAD);
    /** The logistics base's place name; not a rear hamlet, so its people do not walk out. */
    public static final String BASE_PLACE = "logistics-base";
    /** Where the base sits: across the middle of the map, between the rear edge and the line. */
    static final float BASE_X0 = 0.38f;
    static final float BASE_X1 = 0.62f;
    static final float BASE_NEAR = 0.04f;
    static final float BASE_FAR = 0.26f;
    /** A fence, not a redoubt's wall: the base is not meant to be fought from. */
    private static final int BASE_GATES = 2;
    private static final int BASE_FENCE_HP = 60;
    private static final long SEED_SALT = 0x434F52444F4E504CL;
    /** The rear's side, its own splittable draw (see {@code BattleSetup.colonyPlanFor}). */
    private static final long REAR_SIDE_SALT = 0x5245415253494445L;

    private CordonPlan() {}

    /** Whether the rear is the south edge for this seed (else the north). */
    public static boolean rearSouth(long seed) {
        return new SplittableRandom(seed ^ REAR_SIDE_SALT).nextBoolean();
    }

    /**
     * How built-up the country the line is drawn across is: the colony's world
     * sets it (a frontier farm world, a settled one, a core world's sprawl).
     * Rural is the open country the Cordon was first laid out in; a town world
     * grows its hamlets into towns and puts a village on the line itself; an
     * urban one is built across the whole width, so the redoubts stand as
     * fortified blocks in streets and the front is suburbs, ground for
     * infantry and walkers more than for wheels.
     */
    public enum Density {
        RURAL(null, COLONY_GROWTH, HAMLET_GROWTH),
        TOWN(new GrownTrunkPlan.Profile(5, 0.6f, 0.04f, 0.1f, 0.4f, 10, SettlementLink.ROAD),
                new GrownTrunkPlan.Profile(10, 0.7f, 0.06f, 0.13f, 0.4f, 14, SettlementLink.ROAD),
                new GrownTrunkPlan.Profile(6, 0.6f, 0.05f, 0.11f, 0.4f, 10, SettlementLink.ROAD)),
        URBAN(GrownTrunkPlan.Profile.city(),
                GrownTrunkPlan.Profile.city(),
                GrownTrunkPlan.Profile.of(0.9f, SettlementLink.ROAD));

        /** Growth of the built ground the line runs through, or null where it runs through fields. */
        final GrownTrunkPlan.Profile lineGrowth;
        final GrownTrunkPlan.Profile colonyGrowth;
        final GrownTrunkPlan.Profile placeGrowth;

        /** Smallest vanilla market size that is a town world, and a city. */
        static final int TOWN_SIZE = 5, URBAN_SIZE = 7;

        /** The density of a bloomed world of this vanilla market size. */
        public static Density of(int marketSize) {
            return marketSize >= URBAN_SIZE ? URBAN : marketSize >= TOWN_SIZE ? TOWN : RURAL;
        }

        Density(GrownTrunkPlan.Profile lineGrowth, GrownTrunkPlan.Profile colonyGrowth,
                GrownTrunkPlan.Profile placeGrowth) {
            this.lineGrowth = lineGrowth;
            this.colonyGrowth = colonyGrowth;
            this.placeGrowth = placeGrowth;
        }
    }

    /** The plan for one Cordon battle in open country. */
    public static PrecinctPlan planFor(CordonVariant variant, int width, int height, long seed) {
        return planFor(variant, Density.RURAL, width, height, seed);
    }

    /** The plan for one Cordon battle in country as built-up as {@code density}. */
    public static PrecinctPlan planFor(CordonVariant variant, Density density, int width, int height, long seed) {
        Random rng = new Random(seed ^ SEED_SALT);
        boolean south = rearSouth(seed);
        boolean installationEast = rng.nextBoolean();
        List<PrecinctBrief> places = new ArrayList<>();
        // The line first: programmed places pool their claims first whatever the order,
        // and listing them first keeps a redoubt's index its number less one.
        Fortification wall = new Fortification(REDOUBT_GATES, REDOUBT_WALL_HP).laidSquare();
        for (int i = 0; i < REDOUBTS; i++) {
            float x0 = (i + 0.3f) / REDOUBTS, x1 = (i + 0.7f) / REDOUBTS;
            places.add(PrecinctBrief.garrison(REDOUBT_PLACE + (i + 1),
                    band(south, x0, x1, LINE_NEAR, LINE_FAR), REDOUBT_GROWTH,
                    FortressProgram.fieldRedoubt(), wall));
        }
        // The front: the colony, overgrown, with its installation and suburb either side.
        places.add(PrecinctBrief.settlement("colony", band(south, 0.38f, 0.62f, 0.72f, 0.92f),
                density.colonyGrowth, PrecinctCharacter.TOWN));
        places.add(PrecinctBrief.settlement("installation",
                installationEast ? band(south, 0.72f, 0.95f, 0.55f, 0.85f) : band(south, 0.05f, 0.28f, 0.55f, 0.85f),
                density.placeGrowth, PrecinctCharacter.DEPOT));
        places.add(PrecinctBrief.settlement("suburb",
                installationEast ? band(south, 0.05f, 0.28f, 0.55f, 0.85f) : band(south, 0.72f, 0.95f, 0.55f, 0.85f),
                density.placeGrowth, PrecinctCharacter.SUBURB));
        // The rear: the hamlets behind the line whose people walk out.
        places.add(PrecinctBrief.settlement("rear-west", band(south, 0.1f, 0.35f, 0.04f, 0.2f),
                density.placeGrowth, PrecinctCharacter.HAMLET));
        places.add(PrecinctBrief.settlement("rear-east", band(south, 0.65f, 0.9f, 0.04f, 0.2f),
                density.placeGrowth, PrecinctCharacter.HAMLET));
        if (density.lineGrowth != null) {
            // Built ground the line was drawn through: a village between the middle redoubts, or,
            // in a city, streets across the whole width either side of the line.
            float x0 = density == Density.URBAN ? 0.05f : 0.4f, x1 = density == Density.URBAN ? 0.95f : 0.6f;
            places.add(PrecinctBrief.settlement("line-ground", band(south, x0, x1, 0.3f, 0.5f),
                    density.lineGrowth, density == Density.URBAN ? PrecinctCharacter.TOWN : PrecinctCharacter.HAMLET));
        }
        // The allies' logistics base, behind the middle of the line: where their machines are built
        // and their squads form up to be carried forward.
        places.add(PrecinctBrief.garrison(BASE_PLACE, band(south, BASE_X0, BASE_X1, BASE_NEAR, BASE_FAR),
                REDOUBT_GROWTH, FortressProgram.logisticsBase(),
                new Fortification(BASE_GATES, BASE_FENCE_HP).laidSquare()));
        if (variant == CordonVariant.ROAD) {
            // The colony's people are still in its compound, on the town's near side where the road comes in.
            places.add(PrecinctBrief.garrison(CivilDefenceCompound.PLACE, band(south, 0.42f, 0.58f, 0.6f, 0.7f),
                    REDOUBT_GROWTH, FortressProgram.civilDefence(),
                    new Fortification(CivilDefenceCompound.GATES, CivilDefenceCompound.WALL_HP).laidSquare()));
        }
        return PrecinctPlan.laidOut(places, south ? MapPlacement.SOUTH : MapPlacement.NORTH,
                LandingKind.OPEN_FIELD, width, height, rng);
    }

    /** The redoubts of a plan laid out by {@link #planFor}, west to east. */
    public static List<Precinct> redoubts(PrecinctPlan plan) {
        List<Precinct> out = new ArrayList<>();
        for (int n = 1; n <= REDOUBTS; n++) {
            for (Precinct precinct : plan.precincts()) {
                if (precinct.name().equals(REDOUBT_PLACE + n)) out.add(precinct);
            }
        }
        return out;
    }

    /**
     * A placement across {@code x0..x1} of the map's width and {@code d0..d1}
     * of its depth from the rear. Y is up, so a southern rear is low y.
     */
    static MapPlacement band(boolean rearSouth, float x0, float x1, float d0, float d1) {
        return rearSouth ? new MapPlacement(x0, d0, x1, d1) : new MapPlacement(x0, 1f - d1, x1, 1f - d0);
    }
}

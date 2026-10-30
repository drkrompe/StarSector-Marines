package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.BasinOccupiers.Hull;
import com.dillon.starsectormarines.battle.setup.BasinOccupiers.Post;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.List;
import java.util.Random;

/**
 * The rival crew holding Basin's yard on Salvage Rights' night, sized for the company that comes to it after A Cold
 * Account: five squads and four machines, one of them heavy. Scrappers stiffened with hired guns hold every building
 * and the cradle; their machines are what they could make run (two technicals, a dozer, a Warden they pulled out of
 * the wreckfall, and two of Morrow's own drones they have woken); point defence cut from the wreck covers the cradle
 * against rockets. The south road's reserve comes up behind a technical and a tank once the company reaches the
 * cradle. The guns are the map's ({@code BasinGunNests}). UNTUNED first pass: unplayed against the company.
 */
final class SalvageRightsForce {
    private SalvageRightsForce() {}

    /** Hired guns and scrappers in the receiving store, nearest the landing. */
    static final int RECEIVING_REGULARS = 3, RECEIVING_MILITIA = 5;
    /** Hired guns and scrappers in the workshop. */
    static final int WORKSHOP_REGULARS = 4, WORKSHOP_MILITIA = 4;
    /** Every other garrison. */
    static final List<Post> POSTS = List.of(
            new Post("north-sheds", 104, 128, 0, 6),
            new Post("long-shed", 113, 92, 2, 4),
            new Post("cradle-guard", 124, 128, 6, 0),
            new Post("scrapper-cutters", 129, 98, 0, 4),
            new Post("pump", 171, 117, 0, 6),
            new Post("south-picket", 176, 38, 0, 4));
    /** The south road's reserve, regulars first so each squad of four is one kind. */
    static final int ROAD_REGULARS = 4, ROAD_MILITIA = 8;

    /** The yard's working machines and Morrow's woken drones, parked where the crew left them. */
    static final List<Hull> YARD_MACHINES = List.of(
            new Hull(VehicleType.MILITIA_TECHNICAL, 102, 96, 180f),
            new Hull(VehicleType.SRM_TECHNICAL, 84, 124, 180f),
            new Hull(VehicleType.DOZER_BREACHER, 72, 104, 180f),
            new Hull(VehicleType.DRONE_SKIMMER, 120, 101, 180f),
            new Hull(VehicleType.DRONE_SKIMMER, 146, 110, 180f));
    /** The salvaged machine standing over the cradle. */
    static final MechVariant CRADLE_MECH = MechVariant.WARDEN;
    static final int CRADLE_MECH_X = 134, CRADLE_MECH_Y = 99;
    /** Hulls driving up the south haul road behind the reserve, entering at its mouth. */
    static final List<Hull> ROAD_MACHINES = List.of(
            new Hull(VehicleType.SRM_TECHNICAL, 150, 92, 0f),
            new Hull(VehicleType.BREAKTHROUGH_TANK, 142, 88, 0f));
    static final int ROAD_ENTRY_X = 194, ROAD_ENTRY_Y = 4;
    /** Point defence cut from the wreck, on the cradle's corners. */
    static final List<int[]> POINT_DEFENCE = List.of(new int[]{124, 104}, new int[]{142, 124});

    /** Every garrison at tick zero: the two contract buildings at their anchors, then {@link #POSTS}. */
    static void garrison(BattleSimulation sim, PointOfInterest receiving, PointOfInterest workshop, Random rng) {
        // The store looks onto the receiving apron, where Vale's party waits: its crew holds fire until the company
        // comes close rather than shooting the party before the boats are down.
        BasinOccupiers.hold(sim, "receiving-store", receiving.interiorAnchorX, receiving.interiorAnchorY,
                RECEIVING_REGULARS, RECEIVING_MILITIA, true, rng);
        BasinOccupiers.hold(sim, "workshop", workshop.interiorAnchorX, workshop.interiorAnchorY,
                WORKSHOP_REGULARS, WORKSHOP_MILITIA, false, rng);
        for (Post post : POSTS) BasinOccupiers.hold(sim, post, rng);
    }

    /** The south road's reserve infantry at its mouth, regulars first. */
    static List<EntitySpec> roadReserve(NavigationGrid grid, int roadX, int roadY, Random rng) {
        return BasinOccupiers.team(grid, "south-haul-scrapper", roadX, roadY, ROAD_REGULARS, ROAD_MILITIA, rng);
    }

    /** The hulls the reserve brings up the road. */
    static List<RoadTechnical> roadMachines() {
        return BasinOccupiers.driveIn(ROAD_MACHINES, ROAD_ENTRY_X, ROAD_ENTRY_Y);
    }

    /** The yard's machines, the cradle's Warden and the wreck's point defence, at tick zero. */
    static void arm(BattleSimulation sim) {
        BasinOccupiers.park(sim, YARD_MACHINES);
        BasinOccupiers.standMech(sim, CRADLE_MECH, CRADLE_MECH_X, CRADLE_MECH_Y, "Salvaged Warden stands over the cradle");
        ColdAccountForce.installPointDefence(sim, POINT_DEFENCE);
    }
}

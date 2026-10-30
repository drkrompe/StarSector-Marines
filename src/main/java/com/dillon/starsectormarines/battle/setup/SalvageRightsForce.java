package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.FactionMechLoadouts;
import com.dillon.starsectormarines.battle.mech.MechSpawnPlacement;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The rival crew holding Basin's yard on Salvage Rights' night, sized for the company that comes to it after A Cold
 * Account: five squads and four machines, one of them heavy. Scrappers stiffened with hired guns hold every building
 * and the cradle; their machines are what they could make run (two technicals, a dozer, a Warden they pulled out of
 * the wreckfall, and two of Morrow's own drones they have woken); point defence cut from the wreck covers the cradle
 * against rockets. The south road's reserve comes up behind a technical and a tank once the company reaches the
 * cradle. The guns are the map's ({@code SalvageRightsGuns}). UNTUNED first pass: unplayed against the company.
 */
final class SalvageRightsForce {
    private SalvageRightsForce() {}

    /** A garrison: hired guns (regulars) and scrappers (militia) holding the ground round {@code (x, y)}. */
    record Post(String id, int x, int y, int regulars, int militia) {}

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

    /** A parked hull: its type, the cell it stands nearest and its facing. */
    record Hull(VehicleType type, int x, int y, float facing) {}
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

    private static final String FACTION = "pirates";
    private static final RiskLevel RISK = RiskLevel.MEDIUM;

    /** Every garrison at tick zero: the two contract buildings at their anchors, then {@link #POSTS}. */
    static void garrison(BattleSimulation sim, PointOfInterest receiving, PointOfInterest workshop, Random rng) {
        GroundRosterProfile roster = GroundRosterRegistry.resolve(FACTION);
        // The store looks onto the receiving apron, where Vale's party waits: its crew holds fire until the company
        // comes close rather than shooting the party before the boats are down.
        hold(sim, roster, "receiving-store", receiving.interiorAnchorX, receiving.interiorAnchorY,
                RECEIVING_REGULARS, RECEIVING_MILITIA, true, rng);
        hold(sim, roster, "workshop", workshop.interiorAnchorX, workshop.interiorAnchorY,
                WORKSHOP_REGULARS, WORKSHOP_MILITIA, false, rng);
        for (Post post : POSTS)
            hold(sim, roster, post.id(), post.x(), post.y(), post.regulars(), post.militia(), false, rng);
    }

    /** The south road's reserve infantry at its mouth, regulars first. */
    static List<EntitySpec> roadReserve(NavigationGrid grid, int roadX, int roadY, Random rng) {
        GroundRosterProfile roster = GroundRosterRegistry.resolve(FACTION);
        List<EntitySpec> reserve = new ArrayList<>();
        List<int[]> cells = BattleSetup.pickSpawnCluster(grid, roadX, roadY, ROAD_REGULARS + ROAD_MILITIA);
        for (int i = 0; i < cells.size(); i++) {
            int[] cell = cells.get(i);
            String id = "south-haul-scrapper-" + i;
            reserve.add((i < ROAD_REGULARS
                    ? BattleSetup.makeDefender(id, Faction.DEFENDER, UnitType.MARINE_RED, cell[0], cell[1], RISK, roster,
                            GroundRosterProfile.ForceTier.ELITE, rng, null)
                    : BattleSetup.makeOpeningMilitia(id, Faction.DEFENDER, cell[0], cell[1], rng)).role(UnitRole.PATROL));
        }
        return reserve;
    }

    /** The hulls the reserve brings up the road. */
    static List<RoadTechnical> roadMachines() {
        List<RoadTechnical> machines = new ArrayList<>();
        for (Hull hull : ROAD_MACHINES)
            machines.add(new RoadTechnical(hull.type(), ROAD_ENTRY_X, ROAD_ENTRY_Y, hull.x(), hull.y()));
        return machines;
    }

    /** The yard's machines, the cradle's Warden and the wreck's point defence, at tick zero. */
    static void arm(BattleSimulation sim) {
        for (Hull hull : YARD_MACHINES) {
            VehicleClearance mask = VehicleClearance.erode(sim.getGrid(),
                    VehicleClearance.radiusForWidth(hull.type().visualWidthCells));
            int[] at = BattleSetup.nearestClear(mask, new int[]{hull.x(), hull.y()});
            long id = sim.convoy().spawn(hull.type(), Faction.DEFENDER, VehicleMission.deployed(at[0] + .5f, at[1] + .5f));
            sim.convoy().body(id).teleport(at[0] + .5f, at[1] + .5f, hull.facing());
        }
        standMech(sim);
        ColdAccountForce.installPointDefence(sim, POINT_DEFENCE);
    }

    private static void standMech(BattleSimulation sim) {
        MechVariant variant = CRADLE_MECH;
        MechSpawnPlacement.Point at = null;
        for (int r = 0; r <= 8 && at == null; r++)
            for (int dy = -r; dy <= r && at == null; dy++)
                for (int dx = -r; dx <= r && at == null; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    at = MechSpawnPlacement.nearCell(sim.getGrid(), variant.radius, CRADLE_MECH_X + dx, CRADLE_MECH_Y + dy,
                            (px, py) -> true,
                            (px, py) -> MechSpawnPlacement.unoccupied(sim.getRoster(), px, py, variant.radius, 0L));
                }
        if (at == null) throw new IllegalStateException("No ground for the cradle's Warden");
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.HEAVY_MECH);
        long id = sim.spawn(variant.applyTo(new EntitySpec("Salvaged " + variant.name().toLowerCase(), Faction.DEFENDER,
                UnitType.HEAVY_MECH, at.cellX(), at.cellY())).atPosition(at.x(), at.y()).squad(squadId));
        sim.world().attachMechLoadout(id, FactionMechLoadouts.create(variant, variant.defaultRole, FACTION));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = id;
        squad.originalSize = 1;
        squad.assignedNode = BattleSetup.openingDefenseNode(CRADLE_MECH_X, CRADLE_MECH_Y, Faction.DEFENDER, sim.getGrid());
        squad.patrolRadius = 6;
        sim.assignSquadCommand(ObjectiveAssignment.holdNode(squadId, squad.assignedNode), CommandAuthority.GARRISON,
                "salvage-cradle-mech", "Salvaged Warden stands over the cradle");
    }

    /**
     * Spawns the regulars and the militia round {@code (x, y)} as one squad of each, holding that ground; an
     * {@code ambush} squad holds its fire until an enemy is close.
     */
    private static void hold(BattleSimulation sim, GroundRosterProfile roster, String id, int x, int y,
                             int regulars, int militia, boolean ambush, Random rng) {
        List<int[]> cells = BattleSetup.pickSpawnCluster(sim.getGrid(), x, y, regulars + militia);
        squad(sim, roster, id + "-hired", UnitType.MARINE_RED, x, y, cells.subList(0, Math.min(regulars, cells.size())),
                ambush, rng);
        squad(sim, roster, id + "-scrappers", UnitType.MILITIA, x, y,
                cells.subList(Math.min(regulars, cells.size()), cells.size()), ambush, rng);
    }

    private static void squad(BattleSimulation sim, GroundRosterProfile roster, String id, UnitType type, int x, int y,
                              List<int[]> cells, boolean ambush, Random rng) {
        if (cells.isEmpty()) return;
        int squadId = sim.mintSquad(Faction.DEFENDER, type);
        Squad squad = sim.getSquad(squadId);
        squad.holdsFireUntilKillZone = ambush;
        squad.assignedNode = BattleSetup.openingDefenseNode(x, y, Faction.DEFENDER, sim.getGrid());
        squad.patrolRadius = 4;
        sim.assignSquadCommand(ObjectiveAssignment.holdNode(squadId, squad.assignedNode), CommandAuthority.GARRISON,
                "salvage-yard-post", "Occupiers hold " + id);
        for (int i = 0; i < cells.size(); i++) {
            int[] cell = cells.get(i);
            EntitySpec unit = type == UnitType.MILITIA
                    ? BattleSetup.makeOpeningMilitia(id + "-" + i, Faction.DEFENDER, cell[0], cell[1], rng)
                    : BattleSetup.makeDefender(id + "-" + i, Faction.DEFENDER, type, cell[0], cell[1], RISK, roster,
                            GroundRosterProfile.ForceTier.ELITE, rng, null);
            sim.spawn(unit.role(UnitRole.GARRISON).home(cell[0], cell[1]).squad(squadId));
        }
        squad.originalSize = cells.size();
    }
}

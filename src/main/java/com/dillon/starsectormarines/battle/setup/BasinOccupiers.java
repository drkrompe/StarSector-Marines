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
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The rival crew A Working Ship's three visits fight, as the pieces each visit's force is laid out from: garrisons of
 * hired guns (regulars) beside scrappers (militia), road teams mixing the two, parked hulls, a salvaged mech, and hulls
 * that drive in from a road mouth. Each visit's force ({@code SalvageRightsForce}, {@code ColdStartForce},
 * {@code WorkingPassageForce}) states where and how many.
 */
final class BasinOccupiers {
    private BasinOccupiers() {}

    /** A garrison: hired guns and scrappers holding the ground round {@code (x, y)}. */
    record Post(String id, int x, int y, int regulars, int militia) {}

    /** A hull: its type, the cell it stands nearest (or parks on, for one that drives in) and its facing. */
    record Hull(VehicleType type, int x, int y, float facing) {}

    static final String FACTION = "pirates";
    static final RiskLevel RISK = RiskLevel.MEDIUM;

    static GroundRosterProfile roster() {
        return GroundRosterRegistry.resolve(FACTION);
    }

    /** Spawns {@code post} as one squad of each kind holding that ground. */
    static void hold(BattleSimulation sim, Post post, Random rng) {
        hold(sim, post.id(), post.x(), post.y(), post.regulars(), post.militia(), false, rng);
    }

    /**
     * Spawns the regulars and the militia round {@code (x, y)} as one squad of each, holding that ground; an
     * {@code ambush} squad holds its fire until an enemy is close.
     */
    static void hold(BattleSimulation sim, String id, int x, int y, int regulars, int militia, boolean ambush,
                     Random rng) {
        GroundRosterProfile roster = roster();
        List<int[]> cells = BattleSetup.pickSpawnCluster(sim.getGrid(), x, y, regulars + militia);
        int split = Math.min(regulars, cells.size());
        squad(sim, roster, id + "-hired", UnitType.MARINE_RED, x, y, cells.subList(0, split), ambush, rng);
        squad(sim, roster, id + "-scrappers", UnitType.MILITIA, x, y, cells.subList(split, cells.size()), ambush, rng);
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
                "basin-occupier-post", "Occupiers hold " + id);
        for (int i = 0; i < cells.size(); i++) {
            int[] cell = cells.get(i);
            sim.spawn(member(id + "-" + i, type, cell, roster, rng).role(UnitRole.GARRISON).home(cell[0], cell[1])
                    .squad(squadId));
        }
        squad.originalSize = cells.size();
    }

    /**
     * A road team at {@code (x, y)}, regulars first, for a sequence to spawn when it enters: the sequence mints each
     * squad of four or six with its first member's type, so a team's regulars should fill whole squads.
     */
    static List<EntitySpec> team(NavigationGrid grid, String id, int x, int y, int regulars, int militia, Random rng) {
        GroundRosterProfile roster = roster();
        List<EntitySpec> team = new ArrayList<>();
        List<int[]> cells = BattleSetup.pickSpawnCluster(grid, x, y, regulars + militia);
        for (int i = 0; i < cells.size(); i++)
            team.add(member(id + "-" + i, i < regulars ? UnitType.MARINE_RED : UnitType.MILITIA, cells.get(i), roster, rng)
                    .role(UnitRole.PATROL));
        return team;
    }

    private static EntitySpec member(String id, UnitType type, int[] cell, GroundRosterProfile roster, Random rng) {
        return type == UnitType.MILITIA
                ? BattleSetup.makeOpeningMilitia(id, Faction.DEFENDER, cell[0], cell[1], rng)
                : BattleSetup.makeDefender(id, Faction.DEFENDER, type, cell[0], cell[1], RISK, roster,
                        GroundRosterProfile.ForceTier.ELITE, rng, null);
    }

    /** Parks every hull on the clear cell nearest its own, with no errand: its guns fight what they see. */
    static void park(BattleSimulation sim, List<Hull> hulls) {
        for (Hull hull : hulls) {
            VehicleClearance mask = VehicleClearance.erode(sim.getGrid(),
                    VehicleClearance.radiusForWidth(hull.type().visualWidthCells));
            int[] at = BattleSetup.nearestClear(mask, new int[]{hull.x(), hull.y()});
            long id = sim.convoy().spawn(hull.type(), Faction.DEFENDER, VehicleMission.deployed(at[0] + .5f, at[1] + .5f));
            sim.convoy().body(id).teleport(at[0] + .5f, at[1] + .5f, hull.facing());
        }
    }

    /** Hulls that drive from the road mouth at {@code (entryX, entryY)} to each one's own cell. */
    static List<RoadTechnical> driveIn(List<Hull> hulls, int entryX, int entryY) {
        List<RoadTechnical> machines = new ArrayList<>();
        for (Hull hull : hulls) machines.add(new RoadTechnical(hull.type(), entryX, entryY, hull.x(), hull.y()));
        return machines;
    }

    /** A salvaged {@code variant} standing over {@code (x, y)}, holding that ground. */
    static void standMech(BattleSimulation sim, MechVariant variant, int x, int y, String why) {
        MechSpawnPlacement.Point at = null;
        for (int r = 0; r <= 8 && at == null; r++)
            for (int dy = -r; dy <= r && at == null; dy++)
                for (int dx = -r; dx <= r && at == null; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    at = MechSpawnPlacement.nearCell(sim.getGrid(), variant.radius, x + dx, y + dy, (px, py) -> true,
                            (px, py) -> MechSpawnPlacement.unoccupied(sim.getRoster(), px, py, variant.radius, 0L));
                }
        if (at == null) throw new IllegalStateException("No ground for the salvaged " + variant + " at " + x + "," + y);
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.HEAVY_MECH);
        long id = sim.spawn(variant.applyTo(new EntitySpec("Salvaged " + variant.name().toLowerCase(), Faction.DEFENDER,
                UnitType.HEAVY_MECH, at.cellX(), at.cellY())).atPosition(at.x(), at.y()).squad(squadId));
        sim.world().attachMechLoadout(id, FactionMechLoadouts.create(variant, variant.defaultRole, FACTION));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = id;
        squad.originalSize = 1;
        squad.assignedNode = BattleSetup.openingDefenseNode(x, y, Faction.DEFENDER, sim.getGrid());
        squad.patrolRadius = 6;
        sim.assignSquadCommand(ObjectiveAssignment.holdNode(squadId, squad.assignedNode), CommandAuthority.GARRISON,
                "basin-occupier-mech", why);
    }
}

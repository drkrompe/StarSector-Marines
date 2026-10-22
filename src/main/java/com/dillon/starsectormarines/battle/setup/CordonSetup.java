package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.command.objective.EliminateFactionObjective;
import com.dillon.starsectormarines.battle.command.allied.AlliedCommand;
import com.dillon.starsectormarines.battle.cordon.AlliedColumns;
import com.dillon.starsectormarines.battle.cordon.CordonArmour;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.cordon.CordonCommand;
import com.dillon.starsectormarines.battle.cordon.CordonRefugees;
import com.dillon.starsectormarines.battle.cordon.CordonCorridor;
import com.dillon.starsectormarines.battle.cordon.CordonGarrisons;
import com.dillon.starsectormarines.battle.cordon.CordonRedoubt;
import com.dillon.starsectormarines.battle.cordon.CordonSequence;
import com.dillon.starsectormarines.battle.cordon.CordonFront;
import com.dillon.starsectormarines.battle.cordon.CordonPlan;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapGenerator;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationWalls;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Stands a Cordon battle up ({@code cordon-nouns.md}): its map, re-rolled
 * until every redoubt of the line stands walled and the landing's field of
 * fire is clear, then the company's landing.
 */
public final class CordonSetup {

    /** Mission-owned, the Conquest size: a front wide enough to hold a line. */
    public static final int WIDTH = 560, HEIGHT = 336;
    /** Maps tried before the seed is given up on. */
    static final int ATTEMPTS = 8;
    /** How far from its plan seed a redoubt's wall is looked for. */
    private static final int WALL_REACH = 30;
    /** Wall cells a built redoubt has at least: a square of any size worth the name. */
    private static final int MIN_WALL_CELLS = 40;

    private static final MapGenerator MAP_GEN = new BspCityGenerator();

    private CordonSetup() {}

    /** A generated Cordon map and the plan it was laid out from. */
    public record CordonMap(MapResult map, PrecinctPlan plan, long battleSeed, List<int[]> corridor) { }

    /**
     * The world the Cordon is fought on: green colony country whatever the
     * event names, as the colony is laid out the same whatever market raised
     * the call. A profile with a world of its own keeps it.
     */
    public static TargetProfile country(TargetProfile profile) {
        if (profile != null && profile.surface() != SurfacePalette.ROCK) return profile;
        return new TargetProfile(0, 0, 0, 0, "", Set.of(), SurfacePalette.VERDANT, SettlementLink.ROAD);
    }

    /** The first map from {@code seed} that carries the whole Cordon. */
    public static CordonMap map(CordonVariant variant, long seed, TargetProfile profile) {
        profile = country(profile);
        List<String> failures = new ArrayList<>();
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            long battleSeed = seed + attempt * 0x9E3779B97F4A7C15L;
            PrecinctPlan plan = CordonPlan.planFor(variant, WIDTH, HEIGHT, battleSeed);
            MapResult map = MAP_GEN.generate(WIDTH, HEIGHT, battleSeed, null, profile, plan);
            String missing = missing(variant, map, plan);
            if (missing == null) {
                List<int[]> corridor = variant == CordonVariant.ROAD
                        ? CordonCorridor.find(map.grid, map.topology, plan) : null;
                return new CordonMap(map, plan, battleSeed, corridor);
            }
            failures.add("attempt " + attempt + ": " + missing);
        }
        throw new IllegalStateException("no Cordon map from seed " + seed + " in " + ATTEMPTS
                + " attempts: " + String.join("; ", failures));
    }

    /** What this map lacks, or {@code null} when it carries the whole Cordon. */
    static String missing(CordonVariant variant, MapResult map, PrecinctPlan plan) {
        if (!MapFeature.OPEN_FIELD_LANDING.presentIn(map)) return "no clear landing field";
        List<Precinct> redoubts = CordonPlan.redoubts(plan);
        for (int i = 0; i < redoubts.size(); i++) {
            if (!walled(map.topology, redoubts.get(i))) return "redoubt " + (i + 1) + " has no wall";
        }
        if (variant == CordonVariant.ROAD) {
            for (Precinct precinct : plan.precincts()) {
                if (precinct.name().equals(CivilDefenceCompound.PLACE) && !walled(map.topology, precinct)) {
                    return "the town's compound has no wall";
                }
            }
            if (CordonCorridor.find(map.grid, map.topology, plan) == null) return "no drivable way from the rear edge to the compound";
        }
        return null;
    }

    private static boolean walled(CellTopology topology, Precinct precinct) {
        int count = 0;
        for (int y = Math.max(0, precinct.seedY() - WALL_REACH);
             y <= Math.min(topology.getHeight() - 1, precinct.seedY() + WALL_REACH); y++) {
            for (int x = Math.max(0, precinct.seedX() - WALL_REACH);
                 x <= Math.min(topology.getWidth() - 1, precinct.seedX() + WALL_REACH); x++) {
                if (topology.isWall(x, y) && InstallationWalls.PERIMETER.equals(topology.getSurfaceId(x, y))) count++;
            }
        }
        return count >= MIN_WALL_CELLS;
    }

    /** Cells behind the line the company's reserve stands. */
    static final int RESERVE_DEPTH = 24;
    /** The garrisons' kit: troops dug in on the line for weeks, the colony's best. */
    static final RiskLevel GARRISON_RISK = RiskLevel.HIGH;
    /** The rear hamlets' names in the plan begin so. */
    private static final String REAR_PLACE = "rear-";
    /** The allies' light armour behind the line, west to east ({@code cordon-nouns.md}, Allied armour). */
    static final List<VehicleType> ARMOUR = List.of(VehicleType.MILITIA_TECHNICAL, VehicleType.SCOUT_CAR,
            VehicleType.SCOUT_CAR, VehicleType.MILITIA_TECHNICAL);
    /** Teams in each allied column. */
    static final int COLUMN_TEAMS = 2;
    /** How far along the rear edge a refugee or column looks for the road before taking open ground. */
    private static final int EDGE_ROAD_REACH = 40;
    private static final long COLUMN_SALT = 0x434F4C554D4E5321L;
    /** Salt for the garrisons' loadout rolls, their own stream so the map's draws are not read again. */
    private static final long GARRISON_SALT = 0x4741525249534F4EL;

    /** The Cordon battle on the briefing's timetable. */
    public static BattleSimulation create(CordonVariant variant, long seed,
                                          List<ShuttleAssignment> manifest, TargetProfile profile) {
        return create(map(variant, seed, profile), variant, manifest, profile, CordonSequence.Timetable.STANDARD);
    }

    /**
     * The Cordon battle on a map already generated: the company's landing, the
     * line manned, the front grown, the director bore-sourced and the cordon's
     * story over it ({@code cordon-nouns.md}).
     */
    public static BattleSimulation create(CordonMap cordon, CordonVariant variant, List<ShuttleAssignment> manifest,
                                          TargetProfile profile, CordonSequence.Timetable timetable) {
        MapResult map = cordon.map();
        Random rng = new Random(cordon.battleSeed());
        List<Doodad> vehicles = BattleSetup.stampVehicles(map, rng);
        int shuttles = manifest == null || manifest.isEmpty() ? 4 : manifest.size();
        List<LandingPad> pads = LandingPadSelector.select(map, shuttles, BattleSetup.LZ_MIN_SEPARATION);
        BattleSimulation sim = BattleSetup.buildMap(map, vehicles, Collections.emptyList(),
                Collections.emptyList(), cordon.battleSeed()).sim();
        sim.setSurface(BattleSetup.surfaceOf(country(profile)));
        BattleSetup.recordLzPads(sim, pads);
        BattleSetup.landCompany(sim, pads, manifest, rng,
                SquadCommandClaim.mission(CordonCommand.ISSUER, "Cordon company lift"));
        List<CordonRedoubt> redoubts = CordonGarrisons.man(sim, cordon.plan(), GARRISON_RISK,
                new Random(cordon.battleSeed() ^ GARRISON_SALT));
        CordonFront.grow(sim, cordon.plan(), cordon.corridor(), CordonFront.DEFAULT_TIERS, cordon.battleSeed());
        if (!sim.configureSwarmFromBores(cordon.battleSeed())) {
            throw new IllegalStateException("Cordon swarm director configuration failed");
        }
        int[][] eruptions = CordonFront.eruptionSites(sim.getGrid(), sim.getTopology(), cordon.plan(),
                cordon.corridor(), timetable.eruptionSeconds().size(), cordon.battleSeed());
        CordonSequence sequence = sim.installCordon(variant, redoubts, gap(variant, redoubts, cordon), eruptions,
                timetable);
        if (variant == CordonVariant.LINE) {
            int rearRow = CordonFront.rearRow(cordon.plan(), HEIGHT);
            sequence.attach(new CordonRefugees(refugeeRoutes(sim, cordon.plan(), timetable, rearRow),
                            timetable.refugeeSeconds(), rearRow),
                    new AlliedColumns(columnEntries(sim, timetable, rearRow), COLUMN_TEAMS, CordonGarrisons.ISSUER,
                            GARRISON_RISK, new Random(cordon.battleSeed() ^ COLUMN_SALT)));
        }
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));
        int reserveY = Math.round(CordonFront.lineY(cordon.plan()) - CordonFront.frontward(cordon.plan()) * RESERVE_DEPTH);
        CordonArmour armour = new CordonArmour(new AlliedCommand(CordonGarrisons.ISSUER),
                CordonFront.frontward(cordon.plan()));
        for (int i = 0; i < ARMOUR.size(); i++) {
            armour.deploy(sim, ARMOUR.get(i), WIDTH * (i + 1) / (ARMOUR.size() + 1), reserveY);
        }
        sequence.attachArmour(armour);
        sim.setAutonomousCommander(Faction.MARINE, new CordonCommand(WIDTH / 2, reserveY),
                CordonCommand.disclosure(sequence));
        return sim;
    }

    /**
     * Each refugee group's way out, in departure order: from the rear hamlets
     * in turn to the rear edge below them, the nearest open cell of it on a
     * road where there is one ({@code cordon-nouns.md}, Refugees).
     */
    static List<CordonRefugees.Route> refugeeRoutes(BattleSimulation sim, PrecinctPlan plan,
                                                    CordonSequence.Timetable timetable, int rearRow) {
        List<Precinct> hamlets = new ArrayList<>();
        for (Precinct p : plan.precincts()) if (p.name().startsWith(REAR_PLACE)) hamlets.add(p);
        List<CordonRefugees.Route> routes = new ArrayList<>();
        if (hamlets.isEmpty()) return routes;
        for (int i = 0; i < timetable.refugeeSeconds().size(); i++) {
            Precinct from = hamlets.get(i % hamlets.size());
            int[] exit = edgeCell(sim, from.seedX(), rearRow);
            if (exit != null) routes.add(new CordonRefugees.Route(from.seedX(), from.seedY(), exit[0], exit[1]));
        }
        return routes;
    }

    /** Where each allied column comes on: the rear edge behind the middle of the line, west and east in turn. */
    static List<AlliedColumns.Departure> columnEntries(BattleSimulation sim, CordonSequence.Timetable timetable,
                                                       int rearRow) {
        List<AlliedColumns.Departure> out = new ArrayList<>();
        for (int i = 0; i < timetable.columnSeconds().size(); i++) {
            int x = i % 2 == 0 ? WIDTH * 2 / 5 : WIDTH * 3 / 5;
            int[] entry = edgeCell(sim, x, rearRow);
            if (entry != null) out.add(new AlliedColumns.Departure(timetable.columnSeconds().get(i), entry[0], entry[1]));
        }
        return out;
    }

    /** The open cell of the rear edge nearest {@code x}, a road cell if one lies within reach. */
    private static int[] edgeCell(BattleSimulation sim, int x, int row) {
        int[] open = null;
        for (int d = 0; d < WIDTH; d++) {
            for (int cx : new int[]{x - d, x + d}) {
                if (!sim.getGrid().inBounds(cx, row) || !sim.getGrid().isWalkable(cx, row)) continue;
                CellTopology.GroundKind kind = sim.getTopology().getGroundKind(cx, row);
                if ((kind == CellTopology.GroundKind.STREET || kind == CellTopology.GroundKind.SIDEWALK)
                        && d <= EDGE_ROAD_REACH) return new int[]{cx, row};
                if (open == null) open = new int[]{cx, row};
            }
            if (open != null && d > EDGE_ROAD_REACH) return open;
        }
        return open;
    }

    /**
     * On the Road, the two redoubts either side of where the corridor crosses
     * the line, by index ({@code cordon-nouns.md}, Gap); empty on the Line.
     */
    static List<Integer> gap(CordonVariant variant, List<CordonRedoubt> redoubts, CordonMap cordon) {
        if (variant != CordonVariant.ROAD || cordon.corridor() == null || redoubts.size() < 2) return List.of();
        float line = CordonFront.lineY(cordon.plan());
        int[] crossing = cordon.corridor().get(0);
        for (int[] cell : cordon.corridor()) {
            if (Math.abs(cell[1] - line) < Math.abs(crossing[1] - line)) crossing = cell;
        }
        int crossX = crossing[0];
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < redoubts.size(); i++) order.add(i);
        order.sort(Comparator.comparingInt((Integer i) -> Math.abs(redoubts.get(i).centreX() - crossX))
                .thenComparingInt(i -> i));
        List<Integer> gap = new ArrayList<>(order.subList(0, 2));
        Collections.sort(gap);
        return List.copyOf(gap);
    }
}

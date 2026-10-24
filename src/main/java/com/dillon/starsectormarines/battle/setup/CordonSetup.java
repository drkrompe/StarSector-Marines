package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.command.objective.EliminateFactionObjective;
import com.dillon.starsectormarines.battle.command.allied.AlliedCommand;
import com.dillon.starsectormarines.battle.cordon.AlliedColumns;
import com.dillon.starsectormarines.battle.cordon.CordonAlliedTable;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.battle.cordon.CordonArmour;
import com.dillon.starsectormarines.battle.cordon.CordonLineCommand;
import com.dillon.starsectormarines.battle.cordon.CordonLogistics;
import com.dillon.starsectormarines.battle.ambient.RoomSite;
import com.dillon.starsectormarines.battle.ambient.WorksCrewService;
import com.dillon.starsectormarines.battle.fabrication.FabricationService;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.cordon.CordonCommand;
import com.dillon.starsectormarines.battle.cordon.CordonObjectives;
import com.dillon.starsectormarines.battle.cordon.CordonRefugees;
import com.dillon.starsectormarines.battle.cordon.CordonConvoys;
import com.dillon.starsectormarines.battle.cordon.CordonCorridor;
import com.dillon.starsectormarines.battle.cordon.CordonGround;
import com.dillon.starsectormarines.battle.cordon.CordonGapLaw;
import com.dillon.starsectormarines.battle.cordon.CordonGarrisons;
import com.dillon.starsectormarines.battle.cordon.CordonRedoubt;
import com.dillon.starsectormarines.battle.cordon.CordonSequence;
import com.dillon.starsectormarines.battle.cordon.CordonFront;
import com.dillon.starsectormarines.battle.cordon.CordonPlan;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapGenerator;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.layout.MissionLayout;
import com.dillon.starsectormarines.battle.world.gen.layout.MissionLayoutCheck;
import com.dillon.starsectormarines.battle.world.gen.layout.Piece;
import com.dillon.starsectormarines.battle.world.gen.layout.Road;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.fortress.InstallationWalls;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.ops.RiskLevel;

import com.dillon.starsectormarines.battle.world.gen.pool.MapPatch;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPool;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
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

    /** A generated Cordon map, the plan it was laid out from, the world biome it was grown on and how built-up it is. */
    public record CordonMap(MapResult map, PrecinctPlan plan, long battleSeed, List<int[]> corridor,
                            SurfacePalette biome, CordonPlan.Density density) { }

    /**
     * The titles of the optional objectives a battle on this map will run, for the briefing: the same roll
     * the battle makes at setup ({@code cordon-nouns.md}, Optional objectives).
     */
    public static List<String> briefingObjectives(CordonMap cordon, CordonVariant variant) {
        return CordonObjectives.titles(variant, cordon.density(), cordon.map().layout, cordon.battleSeed());
    }

    /**
     * The world the Cordon is fought on: the colony's country on a world of
     * this biome, laid out the same whatever market raised the call.
     */
    public static TargetProfile country(SurfacePalette biome) {
        return new TargetProfile(0, 0, 0, 0, "", Set.of(), biome, SettlementLink.ROAD);
    }

    /** The story map a variant plays from in open country ({@code story-map-pools.md}). */
    public static StoryMap storyMap(CordonVariant variant) {
        return storyMap(variant, CordonPlan.Density.RURAL);
    }

    /** The story map a variant plays from in country as built-up as {@code density}: one pool each. */
    public static StoryMap storyMap(CordonVariant variant, CordonPlan.Density density) {
        boolean line = variant == CordonVariant.LINE;
        return switch (density) {
            case RURAL -> line ? StoryMap.CORDON_LINE : StoryMap.CORDON_ROAD;
            case TOWN -> line ? StoryMap.CORDON_LINE_TOWN : StoryMap.CORDON_ROAD_TOWN;
            case URBAN -> line ? StoryMap.CORDON_LINE_URBAN : StoryMap.CORDON_ROAD_URBAN;
        };
    }

    /** The variant a Cordon story map is, or null for another mission's map. */
    public static CordonVariant variantOf(StoryMap map) {
        for (CordonVariant variant : CordonVariant.values()) {
            for (CordonPlan.Density density : CordonPlan.Density.values()) {
                if (storyMap(variant, density) == map) return variant;
            }
        }
        return null;
    }

    /** The density a Cordon story map is laid out at; rural for any other map. */
    public static CordonPlan.Density densityOf(StoryMap map) {
        for (CordonVariant variant : CordonVariant.values()) {
            for (CordonPlan.Density density : CordonPlan.Density.values()) {
                if (storyMap(variant, density) == map) return density;
            }
        }
        return CordonPlan.Density.RURAL;
    }

    /**
     * The map for {@code seed} on a world of this biome: its pooled map, patch
     * and all, when the variant's pool holds it for the biome, else the first
     * map from it that carries the whole Cordon. The second is how a candidate
     * is tried before it is pooled; the campaign reaches only the first
     * ({@link #pooled}).
     */
    public static CordonMap map(CordonVariant variant, SurfacePalette biome, long seed) {
        return map(variant, CordonPlan.Density.RURAL, biome, seed);
    }

    /** As {@link #map(CordonVariant, SurfacePalette, long)}, in country as built-up as {@code density}. */
    public static CordonMap map(CordonVariant variant, CordonPlan.Density density, SurfacePalette biome, long seed) {
        StoryMapPool.Entry entry = StoryMapPools.installed()
                ? StoryMapPools.pool(storyMap(variant, density)).entry(biome, seed) : null;
        if (entry != null) return map(variant, density, biome, entry);
        return rolled(variant, density, biome, seed);
    }

    /**
     * The first map from {@code seed} that carries the whole Cordon in country as built-up as
     * {@code density}: how a density is tried before any of its maps are pooled.
     */
    public static CordonMap rolled(CordonVariant variant, CordonPlan.Density density, SurfacePalette biome,
                                   long seed) {
        List<String> failures = new ArrayList<>();
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            long battleSeed = seed + attempt * 0x9E3779B97F4A7C15L;
            Generated generated = generate(variant, density, biome, battleSeed, MapPatch.NONE);
            if (generated.missing() == null) return generated.cordon();
            failures.add("attempt " + attempt + ": " + generated.missing());
        }
        throw new IllegalStateException("no " + biome + " Cordon map from seed " + seed + " in " + ATTEMPTS
                + " attempts: " + String.join("; ", failures));
    }

    /**
     * The pooled map a campaign battle plays on a world of this biome and market size, for a roll
     * its event carries: a frontier world's farmland, a settled world's towns or a core world's city.
     */
    public static CordonMap pooled(CordonVariant variant, SurfacePalette biome, int marketSize, long roll) {
        CordonPlan.Density density = CordonPlan.Density.of(marketSize);
        return map(variant, density, biome, StoryMapPools.pool(storyMap(variant, density)).pick(biome, roll));
    }

    /**
     * A pooled map: generated from its seed with its places pinned, never
     * re-rolled, and carrying its ruin to battle assembly. A pooled seed that no
     * longer carries the whole Cordon is a broken pool, not a map to roll past.
     */
    public static CordonMap map(CordonVariant variant, SurfacePalette biome, StoryMapPool.Entry entry) {
        return map(variant, CordonPlan.Density.RURAL, biome, entry);
    }

    /** As {@link #map(CordonVariant, SurfacePalette, StoryMapPool.Entry)}, from the pool of a density. */
    public static CordonMap map(CordonVariant variant, CordonPlan.Density density, SurfacePalette biome,
                                StoryMapPool.Entry entry) {
        Generated generated = generate(variant, density, biome, entry.seed(), entry.patch());
        if (generated.missing() != null) {
            throw new IllegalStateException(storyMap(variant, density).id + " " + biome + " seed " + entry.seed()
                    + " does not carry the Cordon: " + generated.missing());
        }
        CordonMap cordon = generated.cordon();
        return new CordonMap(entry.patch().ruined(cordon.map(), entry.seed()), cordon.plan(), cordon.battleSeed(),
                cordon.corridor(), biome, density);
    }

    private record Generated(CordonMap cordon, String missing) { }

    private static Generated generate(CordonVariant variant, CordonPlan.Density density, SurfacePalette biome,
                                      long battleSeed, MapPatch patch) {
        PrecinctPlan plan = patch.pinned(CordonPlan.planFor(variant, density, WIDTH, HEIGHT, battleSeed));
        Generated whole = generate(variant, density, biome, battleSeed, plan, List.of(), null);
        if (whole.missing() == null) return whole;
        // A layout road moves the ground the places grow round, and a map can come out without the Cordon for it
        // (a base whose shed faces a wall). An optional road is not worth the map: try the map without it.
        List<String> roads = new ArrayList<>();
        for (Piece piece : plan.layout().pieces()) if (piece instanceof Road && !piece.required()) roads.add(piece.name());
        List<List<String>> fallbacks = new ArrayList<>();
        // A redoubt's terrace that cannot stand with its own supply road (one crossing its cliff at a corner, where
        // no ramp climbs) is tried without that road first, so the other redoubts keep theirs.
        for (int n = 1; n <= CordonPlan.REDOUBTS; n++) {
            String road = CordonPlan.SUPPLY_ROAD + n;
            if (whole.missing().contains(CordonPlan.CREST + CordonPlan.REDOUBT_PLACE + n + " ") && roads.contains(road)) {
                fallbacks.add(List.of(road));
            }
        }
        if (roads.contains(CordonPlan.SECOND_ROAD)) fallbacks.add(List.of(CordonPlan.SECOND_ROAD));
        if (!roads.isEmpty()) fallbacks.add(roads);
        for (List<String> drop : fallbacks) {
            Generated without = generate(variant, density, biome, battleSeed, plan, drop, whole.missing());
            if (without.missing() == null) return without;
        }
        return whole;
    }

    /** The map from {@code plan} less the layout pieces named in {@code drop}, reported refused for {@code why}. */
    private static Generated generate(CordonVariant variant, CordonPlan.Density density, SurfacePalette biome,
                                      long battleSeed, PrecinctPlan plan, List<String> drop, String why) {
        if (!drop.isEmpty()) {
            List<Piece> kept = new ArrayList<>();
            for (Piece piece : plan.layout().pieces()) if (!drop.contains(piece.name())) kept.add(piece);
            plan = plan.withLayout(new MissionLayout(kept));
        }
        MapResult map = MAP_GEN.generate(WIDTH, HEIGHT, battleSeed, null, country(biome), plan);
        if (!drop.isEmpty()) {
            List<Laid> laid = new ArrayList<>(map.layout.pieces());
            for (String name : drop) {
                laid.add(Laid.refused(name, Laid.Kind.ROAD, false, "left out: with it, " + why));
            }
            map = map.withLayout(new LaidLayout(laid));
        }
        String missing = missing(variant, map, plan);
        if (missing != null) return new Generated(null, missing);
        List<int[]> corridor = variant == CordonVariant.ROAD ? CordonCorridor.find(convoyGrid(map), map.topology, plan) : null;
        return new Generated(new CordonMap(map, plan, battleSeed, corridor, biome, density), null);
    }

    /** What this map lacks, or {@code null} when it carries the whole Cordon. */
    static String missing(CordonVariant variant, MapResult map, PrecinctPlan plan) {
        if (!MapFeature.OPEN_FIELD_LANDING.presentIn(map)) return "no clear landing field";
        List<Precinct> redoubts = CordonPlan.redoubts(plan);
        for (int i = 0; i < redoubts.size(); i++) {
            if (!walled(map.topology, redoubts.get(i))) return "redoubt " + (i + 1) + " has no wall";
        }
        for (Precinct precinct : plan.precincts()) {
            if (precinct.name().equals(CordonPlan.BASE_PLACE) && !shedNear(map, precinct)) {
                return "the logistics base packed no shed";
            }
        }
        List<int[]> lineMiddles = new ArrayList<>();
        for (Precinct r : redoubts) lineMiddles.add(new int[]{r.seedX(), r.seedY()});
        if (!CordonLogistics.apronReaches(map.grid, map.gantries, lineMiddles)) {
            return "the logistics base's apron has no way to the line for a carrier";
        }
        List<String> layout = MissionLayoutCheck.problems(map.grid, map.layout);
        if (!layout.isEmpty()) return "the layout: " + layout.get(0);
        if (variant == CordonVariant.ROAD) {
            for (Precinct precinct : plan.precincts()) {
                if (precinct.name().equals(CivilDefenceCompound.PLACE) && !walled(map.topology, precinct)) {
                    return "the town's compound has no wall";
                }
            }
            List<int[]> corridor = CordonCorridor.find(convoyGrid(map), map.topology, plan);
            if (corridor == null) return "no drivable way from the rear edge to the compound";
            if (!CordonCorridor.truckReaches(convoyGrid(map), corridor)) {
                return "an edge wall cuts a truck's way from the rear edge to the compound";
            }
        }
        return null;
    }

    /**
     * The line's gaps, west to east ({@link CordonGapLaw}): each neighbouring pair of redoubts' gun towers as their
     * walls will be dressed, with the picket course's nests between them. A reading, not part of {@link #missing}:
     * on the maps as laid the law fails on most of them (the courses' nests stand forty cells apart and a road gap
     * or a redoubt's wide lawn leaves a longer step), and closing it moves guns, which is a balance decision.
     */
    public static List<CordonGapLaw.Gap> gaps(MapResult map, PrecinctPlan plan) {
        List<Precinct> redoubts = CordonPlan.redoubts(plan);
        int front = CordonFront.frontward(plan);
        List<List<int[]>> towers = new ArrayList<>();
        for (Precinct r : redoubts) towers.add(CordonGarrisons.towers(map.grid, map.topology, map.doodads, r, front));
        List<CordonGapLaw.Gap> out = new ArrayList<>();
        for (int n = 1; n < redoubts.size(); n++) {
            Laid picket = map.layout.find(CordonPlan.PICKET_LINE + n + "-" + (n + 1));
            List<int[]> nests = picket == null || !picket.isLaid() ? List.of() : picket.nests();
            out.add(CordonGapLaw.measure(n, n + 1, towers.get(n - 1), towers.get(n), nests));
        }
        return out;
    }

    /**
     * The ground a convoy's road is proved on: the map's, with the wreck across the corridor (beat R1) cleared, as
     * the dozer will clear it. The wreck closes the road to trucks until then, so the corridor runs through it.
     */
    static NavigationGrid convoyGrid(MapResult map) {
        Laid wreck = map.layout.find(CordonPlan.CORRIDOR_WRECK);
        if (wreck == null || !wreck.isLaid() || wreck.kind() != Laid.Kind.BLOCK) return map.grid;
        NavigationGrid copy = map.grid.copyRoutingTopology();
        for (int[] c : wreck.cells()) copy.setWalkable(c[0], c[1], true);
        return copy;
    }

    /** Cells from the base's seed a shed's berth must stand to be the base's. */
    private static final int BASE_SHED_REACH = 40;

    /**
     * Whether the base packed a shed: a base without one builds nothing all battle, and its allies
     * fight with what they start with.
     */
    private static boolean shedNear(MapResult map, Precinct base) {
        for (Gantry g : map.gantries) {
            if (g.holds == Gantry.Holds.MACHINE && Math.abs(g.centerX - base.seedX()) <= BASE_SHED_REACH
                    && Math.abs(g.centerY - base.seedY()) <= BASE_SHED_REACH) return true;
        }
        return false;
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
    /**
     * The allies' logistics base ({@code cordon-nouns.md}, Logistics base): a muster yard at the
     * base's seed, with one carrier standing in it where the density's table opens with one, and the
     * base's shed manned by an allied crew and tooled for the table's line, each machine put to work as
     * it rolls out ({@code cordon-allied-forces.md}).
     */
    private static CordonLogistics logisticsBase(BattleSimulation sim, CordonMap cordon, CordonArmour armour,
                                                 List<CordonRedoubt> redoubts, CordonAlliedTable table) {
        Precinct base = null;
        for (Precinct p : cordon.plan().precincts()) if (p.name().equals(CordonPlan.BASE_PLACE)) base = p;
        if (base == null) return null;
        CordonLogistics logistics = CordonLogistics.at(sim, armour, cordon.map().gantries, base.seedX(), base.seedY(),
                redoubts, table, GARRISON_RISK, new Random(cordon.battleSeed() ^ BASE_SALT));
        if (logistics == null) return null;
        int[] first = logistics.slots().get(0);
        if (table.openingCarrier()) {
            logistics.addCarrier(sim, armour.command().deploy(sim, VehicleType.HEAVY_APC, first[0], first[1], 0f));
        }
        MapResult map = cordon.map();
        if (sim.getWorksCrews() == null) sim.setWorksCrews(new WorksCrewService(TraversalAxis.SOUTH_TO_NORTH));
        List<RoomSite> rooms = RoomSite.findAll(map.topology, map.grid.getWidth(), map.grid.getHeight());
        FabricationService works = new FabricationService(map.gantries, map.fixtureTasks, rooms);
        if (works.isEmpty()) return logistics;
        works.toolForLine(table.baseLine());
        works.setPace(table.basePace());
        works.onRollOut(hull -> logistics.rolledOut(sim, hull));
        sim.setFabrication(works);
        StructureWatch.man(sim, Faction.ALLY, rooms, map.fixtureTasks, works.berthed(),
                EnumSet.of(RoomPurpose.VEHICLE_BAY), BASE_CREW, sim.getWorksCrews());
        return logistics;
    }

    private static final long BASE_SALT = 0x4C4F474953544943L;
    /** Technicians posted to the base's shed. */
    static final int BASE_CREW = 3;
    /** Teams in each allied column. */
    static final int COLUMN_TEAMS = 2;
    /** How far along the rear edge a refugee or column looks for the road before taking open ground. */
    private static final int EDGE_ROAD_REACH = 40;
    private static final long COLUMN_SALT = 0x434F4C554D4E5321L;
    /** Salt for the garrisons' loadout rolls, their own stream so the map's draws are not read again. */
    private static final long GARRISON_SALT = 0x4741525249534F4EL;

    /** The Cordon battle on the briefing's timetable. */
    public static BattleSimulation create(CordonVariant variant, SurfacePalette biome, long seed,
                                          List<ShuttleAssignment> manifest) {
        return create(map(variant, biome, seed), variant, manifest, CordonSequence.Timetable.STANDARD);
    }

    /**
     * The Cordon battle on a map already generated: the company's landing, the
     * line manned, the front grown, the director bore-sourced and the cordon's
     * story over it ({@code cordon-nouns.md}).
     */
    public static BattleSimulation create(CordonMap cordon, CordonVariant variant, List<ShuttleAssignment> manifest,
                                          CordonSequence.Timetable timetable) {
        MapResult map = cordon.map();
        Random rng = new Random(cordon.battleSeed());
        List<Doodad> vehicles = BattleSetup.stampVehicles(map, rng, corridorClear(map, cordon.corridor()));
        int shuttles = manifest == null || manifest.isEmpty() ? 4 : manifest.size();
        List<LandingPad> pads = LandingPadSelector.select(map, shuttles, BattleSetup.LZ_MIN_SEPARATION);
        BattleSimulation sim = BattleSetup.buildMap(map, vehicles, Collections.emptyList(),
                Collections.emptyList(), cordon.battleSeed()).sim();
        sim.setSurface(BattleSetup.surfaceOf(country(cordon.biome())));
        BattleSetup.recordLzPads(sim, pads);
        BattleSetup.landCompany(sim, pads, manifest, rng,
                SquadCommandClaim.mission(CordonCommand.ISSUER, "Cordon company lift"));
        List<CordonRedoubt> redoubts = CordonGarrisons.man(sim, cordon.plan(), GARRISON_RISK,
                new Random(cordon.battleSeed() ^ GARRISON_SALT));
        List<Long> pickets = CordonGarrisons.manPickets(sim, map.layout);
        CordonFront.grow(sim, cordon.plan(), cordon.corridor(), CordonFront.DEFAULT_TIERS, cordon.battleSeed());
        if (!sim.configureSwarmFromBores(cordon.battleSeed())) {
            throw new IllegalStateException("Cordon swarm director configuration failed");
        }
        int[][] eruptions = CordonFront.eruptionSites(sim.getGrid(), sim.getTopology(), cordon.plan(),
                cordon.corridor(), timetable.eruptionSeconds().size(), cordon.battleSeed());
        CordonSequence sequence = sim.installCordon(variant, redoubts, gap(variant, redoubts, cordon), eruptions,
                timetable);
        // Both lines are reinforced by columns walking in off the rear edge; only the Line has
        // people of its own on the move behind it (the Road's are in the town).
        int rearRow = CordonFront.rearRow(cordon.plan(), HEIGHT);
        sequence.attach(variant == CordonVariant.LINE
                        ? new CordonRefugees(refugeeRoutes(sim, cordon.plan(), timetable, rearRow),
                                timetable.refugeeSeconds(), rearRow) : null,
                new AlliedColumns(columnEntries(sim, timetable, rearRow), COLUMN_TEAMS, CordonGarrisons.ISSUER,
                        GARRISON_RISK, new Random(cordon.battleSeed() ^ COLUMN_SALT)));
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));
        int reserveY = Math.round(CordonFront.lineY(cordon.plan()) - CordonFront.frontward(cordon.plan()) * RESERVE_DEPTH);
        CordonArmour armour = new CordonArmour(new AlliedCommand(CordonGarrisons.ISSUER),
                CordonFront.frontward(cordon.plan()));
        CordonAlliedTable table = CordonAlliedTable.of(cordon.density());
        List<VehicleType> opening = table.openingArmour();
        for (int i = 0; i < opening.size(); i++) {
            armour.deploy(sim, opening.get(i), WIDTH * (i + 1) / (opening.size() + 1), reserveY);
        }
        standMouths(sim, armour, redoubts, table, new Random(cordon.battleSeed() ^ MOUTH_SALT));
        sequence.attachArmour(armour);
        CordonLogistics logistics = logisticsBase(sim, cordon, armour, redoubts, table);
        if (logistics != null) sequence.attachLogistics(logistics);
        if (variant == CordonVariant.ROAD && cordon.corridor() != null) {
            CordonConvoys convoys = convoys(sim, cordon, timetable, logistics);
            sequence.attachConvoys(convoys);
            armour.escortConvoys(convoys::guardPosts);
        }
        sequence.attachLine(new CordonLineCommand(sim, sequence, armour, logistics, cordon.density(), table, pickets,
                map.layout));
        sim.setAutonomousCommander(Faction.MARINE, new CordonCommand(WIDTH / 2, reserveY, cordon.density()),
                CordonCommand.disclosure(sequence));
        CordonObjectives.install(sim, sequence, variant, cordon.density(), cordon.plan(), map.layout,
                cordon.corridor(), cordon.battleSeed());
        return sim;
    }

    /**
     * Each redoubt's street mouths held as the density's table says ({@code cordon-allied-forces.md}, Urban): its
     * Hounds in the mouths, and a walking rifle team behind them holding there.
     */
    static void standMouths(BattleSimulation sim, CordonArmour armour, List<CordonRedoubt> redoubts,
                            CordonAlliedTable table, Random rng) {
        for (CordonRedoubt r : redoubts) {
            for (int i = 0; i < table.mouthHounds(); i++) {
                armour.deployMech(sim, r, "Redoubt " + r.number() + " Hound " + (i + 1));
            }
            if (table.yardMortar()) yardMortar(sim, armour, r, rng);
            if (!table.mouthRifleTeam()) continue;
            int[] post = armour.teamPost(sim, r);
            List<int[]> cells = CordonGround.clusterNear(sim, post[0], post[1], ColonyMilitia.POST_SIZE);
            if (cells.size() < ColonyMilitia.POST_SIZE) continue;
            String name = "Redoubt " + r.number() + " street team";
            int squad = ColonyMilitia.seatTeam(sim, cells, name, GARRISON_RISK, rng);
            armour.addStreetTeam(squad, r, cells.get(0)[0], cells.get(0)[1], MOUTH_TEAM_HOLD);
            sim.assignSquadCommand(ObjectiveAssignment.defendArea(squad, cells.get(0)[0], cells.get(0)[1], MOUTH_TEAM_HOLD),
                    CommandAuthority.GARRISON, CordonGarrisons.ISSUER, name + " holds the street mouth");
        }
    }

    /**
     * A mortar team in a redoubt's yard, its first man carrying a field mortar, holding the yard with the ground in front
     * of the redoubt as its standing fire mission: it sets its tube down where it stands and fires on what the side has
     * seen there, never a round with one of its own under it (`FieldMortarService.friendlyUnderRound`).
     */
    static void yardMortar(BattleSimulation sim, CordonArmour armour, CordonRedoubt r, Random rng) {
        List<int[]> cells = CordonGround.clusterNear(sim, r.centreX(), r.centreY(), ColonyMilitia.POST_SIZE);
        if (cells.size() < ColonyMilitia.POST_SIZE) return;
        String name = "Redoubt " + r.number() + " mortar team";
        int squad = ColonyMilitia.seatTeam(sim, cells, name, GARRISON_RISK, rng);
        SpecialEquipmentDef tube = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FIELD_MORTAR_ID);
        sim.world().attachSpecialEquipment(sim.getSquad(squad).leaderId, tube, tube.startingAmmo());
        armour.addMortarTeam(squad);
        int[] ground = armour.mortarGround(r);
        sim.assignSquadCommand(ObjectiveAssignment.defendArea(squad, cells.get(0)[0], cells.get(0)[1], MOUTH_TEAM_HOLD)
                        .withFireMission(new ObjectiveAssignment.FireMission(ground[0], ground[1], CordonArmour.MISSION_RADIUS)),
                CommandAuthority.GARRISON, CordonGarrisons.ISSUER, name + " fires on the ground in front");
    }

    /** Cells round its post a mouth's rifle team holds. */
    private static final int MOUTH_TEAM_HOLD = 4;
    private static final long MOUTH_SALT = 0x4D4F555448534C54L;

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
     * The corridor and the ground either side of it, kept clear of parked vehicles: nothing is parked
     * on the relief road ({@code cordon-nouns.md}, Corridor). {@code null} where there is no corridor.
     */
    static boolean[][] corridorClear(MapResult map, List<int[]> corridor) {
        if (corridor == null) return null;
        int w = map.grid.getWidth(), h = map.grid.getHeight();
        boolean[][] clear = new boolean[w][h];
        for (int[] cell : corridor) {
            for (int dx = -CORRIDOR_MARGIN; dx <= CORRIDOR_MARGIN; dx++) {
                for (int dy = -CORRIDOR_MARGIN; dy <= CORRIDOR_MARGIN; dy++) {
                    int x = cell[0] + dx, y = cell[1] + dy;
                    if (x >= 0 && y >= 0 && x < w && y < h) clear[x][y] = true;
                }
            }
        }
        return clear;
    }

    /** Cells either side of the corridor kept clear with it: a truck is three wide and a parked car two. */
    private static final int CORRIDOR_MARGIN = 3;

    /**
     * The Road's relief convoys on the corridor, and the cut-off town's people and militia in its
     * compound at the corridor's far end ({@code cordon-nouns.md}, Convoy, Cut-off town).
     */
    static CordonConvoys convoys(BattleSimulation sim, CordonMap cordon,
                                 CordonSequence.Timetable timetable, CordonLogistics base) {
        List<int[]> corridor = cordon.corridor();
        float line = CordonFront.lineY(cordon.plan());
        int frontward = CordonFront.frontward(cordon.plan());
        int gapIndex = corridor.size() - 1;
        for (int i = 0; i < corridor.size(); i++) {
            if ((corridor.get(i)[1] - line) * frontward >= 0) {
                gapIndex = i;
                break;
            }
        }
        List<Integer> departTicks = new ArrayList<>();
        for (int seconds : timetable.convoySeconds()) departTicks.add(Math.round(seconds / BattleSimulation.TICK_DT));
        int[] gate = corridor.get(corridor.size() - 1);
        Random rng = new Random(cordon.battleSeed() ^ TOWN_SALT);
        List<Integer> militia = new ArrayList<>();
        for (int team = 0; team < TOWN_TEAMS; team++) {
            List<int[]> cells = CordonGround.clusterNear(sim, gate[0], gate[1] + frontward * (TOWN_DEPTH + team * 3), TOWN_TEAM_SIZE);
            if (!cells.isEmpty()) militia.add(ColonyMilitia.seatTeam(sim, cells, "Town militia " + (team + 1), GARRISON_RISK, rng));
        }
        CordonConvoys convoys = new CordonConvoys(corridor, convoyEntry(corridor, gapIndex, base), gapIndex, departTicks,
                timetable.convoySeconds().size() * CordonConvoys.TRUCKS * CordonConvoys.SEATS);
        convoys.setTownMilitia(militia);
        return convoys;
    }

    /** Cells from the base's muster yard the corridor must pass for the convoys to set out from the base. */
    private static final int BASE_ROAD_REACH = 60;
    /** Corridor cells short of the gap a convoy sets out at the nearest, so it has road to form up on. */
    private static final int ENTRY_SHORT_OF_GAP = 60;

    /**
     * The corridor cell the convoys set out from: where it passes nearest the logistics base, or its
     * rear end where it does not pass the base, or there is none.
     */
    static int convoyEntry(List<int[]> corridor, int gapIndex, CordonLogistics base) {
        if (base == null) return CordonConvoys.ENTRY;
        int best = CordonConvoys.ENTRY;
        long bestD = Long.MAX_VALUE;
        for (int i = CordonConvoys.ENTRY; i < Math.max(CordonConvoys.ENTRY, gapIndex - ENTRY_SHORT_OF_GAP); i++) {
            long dx = corridor.get(i)[0] - base.musterX(), dy = corridor.get(i)[1] - base.musterY();
            if (dx * dx + dy * dy < bestD) {
                bestD = dx * dx + dy * dy;
                best = i;
            }
        }
        return bestD <= (long) BASE_ROAD_REACH * BASE_ROAD_REACH ? best : CordonConvoys.ENTRY;
    }

    /** The town militia still holding the compound when the convoys come. */
    private static final int TOWN_TEAMS = 2;
    private static final int TOWN_TEAM_SIZE = 6;
    /** Cells into the compound past the gate the town's militia stand. */
    private static final int TOWN_DEPTH = 6;
    private static final long TOWN_SALT = 0x544F574E4D494CL;

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

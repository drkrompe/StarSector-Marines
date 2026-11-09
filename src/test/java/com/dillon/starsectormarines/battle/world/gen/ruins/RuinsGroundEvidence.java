package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Layer;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ruins ground over every grade and surface ({@code mapgen-nouns.md}, Ruins ground): each map generates, keeps every
 * delve law ({@link DelveCheck}, which generation already enforces), and is a ruin: no road leaves it, its streets
 * end short of every edge, and it has no berth but its own landing's. The vault, every room inside the interior
 * and every chamber is under a roof; every lift point is under open sky. Writes {@code build/reports/ruins-ground/summary.md} with each map's
 * layers, ways, rooms and route length.
 */
@Tag("generated-space-evidence")
class RuinsGroundEvidence {

    static final int SEEDS = Integer.getInteger("ruinsGround.seeds", 6);
    /** Seeds that once broke a ground, kept so they stay covered: 91104 seated no power hall at extensive grade. */
    static final long[] REGRESSION_SEEDS = {91104L};

    @Test
    void everyGradeAndSurfaceKeepsTheDelve() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        DiskRegistries.installMapGeneration(root);
        TileRegistryTestInstaller.install(root);
        List<String> failures = new ArrayList<>();
        StringBuilder out = new StringBuilder("# Ruins ground\n\n| layers | surface | seed | kinds | open ways | rooms | courts | inside | chambers | route | ms |\n|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (int layers = 1; layers <= RuinsGroundPlan.MAX_LAYERS; layers++) {
            RuinsGroundPlan plan = RuinsGroundPlan.forLayers(layers);
            for (SurfacePalette surface : SurfacePalette.values()) {
                for (long seed : seeds()) {
                    long start = System.nanoTime();
                    MapResult map;
                    try {
                        map = new BspCityGenerator().generateRuins(seed, profile(surface), plan);
                    } catch (RuntimeException e) {
                        failures.add(layers + "/" + surface + "/" + seed + ": " + e.getMessage());
                        out.append("| ").append(layers).append(" | ").append(surface).append(" | ").append(seed)
                                .append(" | FAILED: ").append(e.getMessage()).append(" |||||||\n");
                        continue;
                    }
                    long ms = (System.nanoTime() - start) / 1_000_000;
                    RuinsDelve delve = map.ruinsDelve;
                    if (!map.roadGraph.perimeterNodes().isEmpty()) {
                        failures.add(layers + "/" + surface + "/" + seed + ": a road leaves the ruin");
                    }
                    int edgeStreets = 0;
                    for (int y = 0; y < plan.height(); y++) {
                        for (int x = 0; x < plan.width(); x++) {
                            int edge = Math.min(Math.min(x, plan.width() - 1 - x), Math.min(y, plan.height() - 1 - y));
                            if (edge < RuinedLotsStage.STREET_FALLOFF
                                    && map.topology.getGroundKind(x, y) == GroundKind.STREET) edgeStreets++;
                        }
                    }
                    if (edgeStreets > 0) {
                        failures.add(layers + "/" + surface + "/" + seed + ": " + edgeStreets + " street cells by the edge");
                    }
                    if (map.landingPads.size() != 2 * delve.landing().size()) {
                        failures.add(layers + "/" + surface + "/" + seed + ": berths beyond the landing");
                    }
                    StringBuilder kinds = new StringBuilder(), ways = new StringBuilder(), rooms = new StringBuilder();
                    int courts = 0;
                    for (Layer layer : delve.layers()) {
                        kinds.append(layer.kind().name().charAt(0));
                        ways.append(layer.openWays().size());
                        rooms.append(layer.rooms().size());
                        if (layer.court() != null) courts++;
                    }
                    String where = layers + "/" + surface + "/" + seed + ": ";
                    RuinsDelve.Interior interior = delve.interior();
                    if (!roofed(map, delve.vault().source().x(), delve.vault().source().y())) {
                        failures.add(where + "the vault is not under a roof");
                    }
                    for (Layer layer : delve.layers()) {
                        if (!interior.contains(layer.index())) continue;
                        for (RuinsDelve.SideRoom room : layer.rooms()) {
                            if (!roofed(map, room.interior().x(), room.interior().y())) {
                                failures.add(where + room.name() + " is inside but not roofed");
                            }
                        }
                    }
                    for (RuinsDelve.Area chamber : interior.chambers()) {
                        if (!roofed(map, chamber.centreX(), chamber.centreY())) {
                            failures.add(where + "a chamber at " + chamber + " is not roofed");
                        }
                    }
                    for (var area : delve.liftPoints()) {
                        for (var berth : area.berths()) {
                            if (map.topology.getBuildingId(berth.centerX, berth.centerY) != 0) {
                                failures.add(where + area.id + " is under a roof");
                            }
                        }
                    }
                    var start0 = delve.landing().get(0).berth(0);
                    boolean[] reach = DelveCheck.reach(map.grid, start0.centerX, start0.centerY, null);
                    out.append("| ").append(layers).append(" | ").append(surface).append(" | ").append(seed)
                            .append(" | ").append(kinds).append(" | ").append(ways).append(" | ").append(rooms)
                            .append(" | ").append(courts).append(" | ")
                            .append(delve.layers().size() - interior.firstLayer()).append(" | ")
                            .append(interior.chambers().size()).append(" | ")
                            .append(DelveCheck.routeLength(map.grid, start0.centerX, start0.centerY, delve.vault().source()))
                            .append(" | ").append(ms).append(" |\n");
                    assertTrue(reach.length > 0);
                }
            }
        }
        out.append("\n").append(failures.size()).append(" failures\n");
        for (String f : failures) out.append("- ").append(f).append('\n');
        Path report = root.resolve("build/reports/ruins-ground/summary.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, out.toString());
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    /** Whether a cell is a building's floor under an intact roof. */
    static boolean roofed(MapResult map, int x, int y) {
        return map.topology.isRoofIntact(x, y);
    }

    /** One to {@link #SEEDS}, then the regression seeds. */
    static long[] seeds() {
        long[] out = new long[SEEDS + REGRESSION_SEEDS.length];
        for (int k = 0; k < SEEDS; k++) out[k] = k + 1;
        System.arraycopy(REGRESSION_SEEDS, 0, out, SEEDS, REGRESSION_SEEDS.length);
        return out;
    }

    static TargetProfile profile(SurfacePalette surface) {
        return new TargetProfile(0, 0, 0, 0, "", EnumSet.noneOf(EconomicFunction.class), surface,
                SettlementLink.NONE, 0f, 0f, false, false, false, GroundDefences.NONE);
    }
}

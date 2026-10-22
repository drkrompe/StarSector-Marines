package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.cordon.CordonCorridor;
import com.dillon.starsectormarines.battle.cordon.CordonFront;
import com.dillon.starsectormarines.battle.cordon.CordonPlan;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
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

import java.util.ArrayList;
import java.util.Collections;
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

    /**
     * The Cordon battle: its map and the company's landing. The garrisons, the
     * front's bores and the cordon sequence are {@code cordon-battle.md}.
     */
    public static BattleSimulation create(CordonVariant variant, long seed,
                                          List<ShuttleAssignment> manifest, TargetProfile profile) {
        return create(map(variant, seed, profile), manifest, profile);
    }

    /** The Cordon battle on a map already generated. */
    public static BattleSimulation create(CordonMap cordon, List<ShuttleAssignment> manifest, TargetProfile profile) {
        MapResult map = cordon.map();
        Random rng = new Random(cordon.battleSeed());
        List<Doodad> vehicles = BattleSetup.stampVehicles(map, rng);
        int shuttles = manifest == null || manifest.isEmpty() ? 4 : manifest.size();
        List<LandingPad> pads = LandingPadSelector.select(map, shuttles, BattleSetup.LZ_MIN_SEPARATION);
        BattleSimulation sim = BattleSetup.buildMap(map, vehicles, Collections.emptyList(),
                Collections.emptyList(), cordon.battleSeed()).sim();
        sim.setSurface(BattleSetup.surfaceOf(country(profile)));
        BattleSetup.recordLzPads(sim, pads);
        CordonFront.grow(sim, cordon.plan(), cordon.corridor(), CordonFront.DEFAULT_TIERS, cordon.battleSeed());
        return sim;
    }
}

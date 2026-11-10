package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.Climate;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.Habitat;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * A town built for the world it stands on ({@code mapgen-nouns.md}, Habitat), through the look preset: one town
 * market's map on the same seed as the open town, sealed (toxic air) and shielded (hard radiation), each whole, over
 * its town and at play zoom, framed on the same spot. The town plan is the same in all three; only what it is built
 * of and what stands in its open ground differ. Opt-in.
 *
 * <p>The shielded town is also drawn with its walls in the earthwork look and in plain installation concrete, the
 * alternatives to its brutalist walls put to the owner; that resurfacing is this suite's only, nothing generates it.
 * {@code -Dsnapshot.habitat.seeds=7,19} picks the maps.
 */
public final class HabitatLookSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    private static final int MAP_W = 200, MAP_H = 120;
    private static final float PLAY_ZOOM_CELLS = 60f, TOWN_CELLS = 110f;

    @Override public String id() { return "habitat-look"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "Towns built for their world: open, sealed, shielded, same seed"; }

    private record Take(String tag, Climate climate, String shieldWall) { }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        List<Take> takes = List.of(
                new Take("open", Climate.NONE, null),
                new Take("sealed", new Climate(EnumSet.of(Climate.Hazard.TOXIC)), null),
                new Take("shielded", new Climate(EnumSet.of(Climate.Hazard.RADIATION)), null),
                new Take("shielded-earthwork", new Climate(EnumSet.of(Climate.Hazard.RADIATION)),
                        "installation.light-concrete-wall@earthwork"),
                new Take("shielded-plain", new Climate(EnumSet.of(Climate.Hazard.RADIATION)),
                        "installation.light-concrete-wall"));
        List<SnapshotArtifact> out = new ArrayList<>();
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            for (String s : System.getProperty("snapshot.habitat.seeds", "7,19").split(",")) {
                long seed = Long.parseLong(s.trim());
                float[] focus = null;
                for (Take take : takes) {
                    TargetProfile profile = new TargetProfile(5, 5, 1, 1, "independent",
                            EnumSet.of(EconomicFunction.HABITATION), SurfacePalette.ROCK, SettlementLink.ROAD)
                            .withClimate(take.climate());
                    MapResult map = new BspCityGenerator().generate(MAP_W, MAP_H, seed, null, profile);
                    if (take.shieldWall() != null) resurface(map.topology, Habitat.SHIELD_WALL, take.shieldWall());
                    try (BattleSimulation sim = new BattleSimulation(map.grid, map.topology)) {
                        for (Doodad d : map.doodads) sim.addDoodad(d);
                        sim.setSurface(SurfacePalette.ROCK);
                        if (focus == null) focus = busiest(map.topology, (int) PLAY_ZOOM_CELLS);
                        String tag = "habitat-seed" + seed + "-" + take.tag() + "-";
                        int w = map.topology.getWidth(), h = map.topology.getHeight();
                        BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H,
                                w / 2f, h / 2f, w);
                        out.add(new SnapshotArtifact(tag + "1-whole.png", look.render(sim, "")));
                        look.refocus(focus[0], focus[1], TOWN_CELLS);
                        out.add(new SnapshotArtifact(tag + "2-town.png", look.render(sim, "")));
                        look.refocus(focus[0], focus[1], PLAY_ZOOM_CELLS);
                        out.add(new SnapshotArtifact(tag + "3-play-zoom.png", look.render(sim, "")));
                        System.out.printf(Locale.ROOT, "habitat-look seed %d %s: %d doodads (%d court plants), focus %.0f,%.0f%n",
                                seed, take.tag(), map.doodads.size(),
                                map.doodads.stream().filter(d -> d.definitionId() != null && d.definitionId().endsWith("oxygen-sphere")).count(),
                                focus[0], focus[1]);
                    }
                }
            }
        }
        return out;
    }

    /** Every wall of {@code from}, drawn as {@code to}. */
    private static void resurface(CellTopology topology, String from, String to) {
        int target = topology.surfaceIndex(to);
        for (int y = 0; y < topology.getHeight(); y++)
            for (int x = 0; x < topology.getWidth(); x++)
                if (from.equals(topology.getSurfaceId(x, y))) topology.setSurface(x, y, target);
    }

    /** The middle of the {@code size}-cell square holding the most building cells. */
    private static float[] busiest(CellTopology topology, int size) {
        int w = topology.getWidth(), h = topology.getHeight(), step = size / 4, best = -1;
        float[] at = {w / 2f, h / 2f};
        for (int x0 = 0; x0 + size <= w; x0 += step) {
            for (int y0 = 0; y0 + size * 9 / 16 <= h; y0 += step) {
                int count = 0;
                for (int x = x0; x < x0 + size; x++)
                    for (int y = y0; y < y0 + size * 9 / 16; y++) if (topology.getBuildingId(x, y) != 0) count++;
                if (count > best) {
                    best = count;
                    at = new float[]{x0 + size / 2f, y0 + size * 9 / 32f};
                }
            }
        }
        return at;
    }
}

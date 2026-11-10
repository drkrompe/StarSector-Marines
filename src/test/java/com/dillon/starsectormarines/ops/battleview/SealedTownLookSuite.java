package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.Climate;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.SealedTown;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan.Sprawl;
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
import java.util.Map;
import java.util.Random;

/**
 * A hazardous world's town sealed inside one wall ({@code mapgen-nouns.md}, Habitat), through the look preset: one
 * defended market's precinct map on the same seed as an open world, a toxic one and an irradiated one; whole, over the
 * town, and at play zoom on a lock. Opt-in.
 *
 * <p>{@code -Dsnapshot.sealed.seeds=7,19} picks the maps.
 */
public final class SealedTownLookSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    private static final int MAP_W = 240, MAP_H = 144;
    private static final float PLAY_ZOOM_CELLS = 60f, SITE_CELLS = 130f;

    @Override public String id() { return "sealed-town-look"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "Hazardous worlds' towns sealed in one wall, beside the open world"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        Map<String, Climate> takes = Map.of(
                "open", Climate.NONE,
                "toxic", new Climate(EnumSet.of(Climate.Hazard.TOXIC)),
                "irradiated", new Climate(EnumSet.of(Climate.Hazard.RADIATION)));
        List<SnapshotArtifact> out = new ArrayList<>();
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            for (String s : System.getProperty("snapshot.sealed.seeds", "7,19").split(",")) {
                long seed = Long.parseLong(s.trim());
                for (String take : List.of("open", "toxic", "irradiated", "unplanned")) {
                    // "unplanned": a toxic world's town laid with no plan, as an extraction or a rescue lays it.
                    boolean unplanned = take.equals("unplanned");
                    TargetProfile profile = new TargetProfile(5, 5, 2, 1, "independent",
                            EnumSet.of(EconomicFunction.HABITATION), SurfacePalette.ROCK, SettlementLink.ROAD)
                            .withClimate(takes.get(unplanned ? "toxic" : take));
                    MapResult map = unplanned ? new BspCityGenerator().generate(MAP_W, MAP_H, seed, null, profile)
                            : null;
                    PrecinctPlan plan = unplanned ? SealedTown.alone(profile, MAP_W, MAP_H)
                            : PrecinctPlan.derive(profile, Sprawl.BALANCED, MAP_W, MAP_H, new Random(seed * 31 + 5));
                    if (!unplanned) map = new BspCityGenerator().generate(MAP_W, MAP_H, seed, null, profile, plan);
                    Precinct town = plan.precincts().stream()
                            .filter(p -> p.name().equals("settlement")).findFirst().orElse(plan.precincts().get(0));
                    int[] lock = nearestLock(map.topology, map.doodads, town.seedX(), town.seedY());
                    try (BattleSimulation sim = new BattleSimulation(map.grid, map.topology)) {
                        for (Doodad d : map.doodads) sim.addDoodad(d);
                        sim.setSurface(SurfacePalette.ROCK);
                        String tag = "seed" + seed + "-" + take + "-";
                        int w = map.topology.getWidth(), h = map.topology.getHeight();
                        BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W,
                                FRAME_H, w / 2f, h / 2f, w);
                        out.add(new SnapshotArtifact(tag + "1-whole.png", look.render(sim, "")));
                        look.refocus(town.seedX(), town.seedY(), SITE_CELLS);
                        out.add(new SnapshotArtifact(tag + "2-town.png", look.render(sim, "")));
                        int[] at = lock == null ? new int[]{town.seedX(), town.seedY()} : lock;
                        look.refocus(at[0], at[1], PLAY_ZOOM_CELLS);
                        out.add(new SnapshotArtifact(tag + "3-lock.png", look.render(sim, "")));
                        System.out.printf(Locale.ROOT, "sealed-town-look seed %d %s: %d places, town %d,%d, lock %s%n",
                                seed, take, plan.precincts().size(), town.seedX(), town.seedY(),
                                lock == null ? "none" : lock[0] + "," + lock[1]);
                    }
                }
            }
        }
        return out;
    }

    /** The lock nearest the town's seed: a striped floor marking beside the envelope's own wall. */
    private static int[] nearestLock(CellTopology topology, List<Doodad> doodads, int sx, int sy) {
        int[] best = null;
        long bestD = Long.MAX_VALUE;
        for (Doodad d : doodads) {
            if (!"doodad.fl-striped-yellow".equals(d.definitionId())) continue;
            boolean byWall = false;
            for (int dx = -1; dx <= 1 && !byWall; dx++) {
                for (int dy = -1; dy <= 1 && !byWall; dy++) {
                    int x = d.cellX + dx, y = d.cellY + dy;
                    byWall = x >= 0 && y >= 0 && x < topology.getWidth() && y < topology.getHeight()
                            && topology.getSurfaceId(x, y) != null
                            && topology.getSurfaceId(x, y).startsWith(SealedTown.ENVELOPE_WALL);
                }
            }
            if (!byWall) continue;
            long dist = (long) (d.cellX - sx) * (d.cellX - sx) + (long) (d.cellY - sy) * (d.cellY - sy);
            if (dist < bestD) { bestD = dist; best = new int[]{d.cellX, d.cellY}; }
        }
        return best;
    }
}

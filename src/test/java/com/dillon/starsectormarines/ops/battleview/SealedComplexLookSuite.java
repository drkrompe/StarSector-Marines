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
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan.Sprawl;
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
import java.util.Random;

/**
 * An irradiated world's places as sealed complexes ({@code mapgen-nouns.md}, Habitat), through the look preset: one
 * defended market's precinct map on the same seed as an open world and under hard radiation, whole, over the
 * settlement and at play zoom on its face. Opt-in.
 *
 * <p>{@code -Dsnapshot.sealed.seeds=7,19} picks the maps.
 */
public final class SealedComplexLookSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    private static final int MAP_W = 240, MAP_H = 144;
    private static final float PLAY_ZOOM_CELLS = 60f, SITE_CELLS = 110f;

    @Override public String id() { return "sealed-complex-look"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "Irradiated world's places as sealed complexes, beside the open world"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>();
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            for (String s : System.getProperty("snapshot.sealed.seeds", "7,19").split(",")) {
                long seed = Long.parseLong(s.trim());
                for (String take : List.of("open", "sealed")) {
                    Climate climate = take.equals("sealed")
                            ? new Climate(EnumSet.of(Climate.Hazard.RADIATION)) : Climate.NONE;
                    TargetProfile profile = new TargetProfile(5, 5, 2, 1, "independent",
                            EnumSet.of(EconomicFunction.HABITATION), SurfacePalette.ROCK, SettlementLink.ROAD)
                            .withClimate(climate);
                    PrecinctPlan plan = PrecinctPlan.derive(profile, Sprawl.BALANCED, MAP_W, MAP_H,
                            new Random(seed * 31 + 5));
                    MapResult map = new BspCityGenerator().generate(MAP_W, MAP_H, seed, null, profile, plan);
                    Precinct settlement = plan.precincts().stream()
                            .filter(p -> p.name().equals("settlement")).findFirst().orElse(plan.precincts().get(0));
                    try (BattleSimulation sim = new BattleSimulation(map.grid, map.topology)) {
                        for (Doodad d : map.doodads) sim.addDoodad(d);
                        sim.setSurface(SurfacePalette.ROCK);
                        String tag = "sealed-seed" + seed + "-" + take + "-";
                        int w = map.topology.getWidth(), h = map.topology.getHeight();
                        BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H,
                                w / 2f, h / 2f, w);
                        out.add(new SnapshotArtifact(tag + "1-whole.png", look.render(sim, "")));
                        look.refocus(settlement.seedX(), settlement.seedY(), SITE_CELLS);
                        out.add(new SnapshotArtifact(tag + "2-settlement.png", look.render(sim, "")));
                        look.refocus(settlement.seedX(), settlement.seedY(), PLAY_ZOOM_CELLS);
                        out.add(new SnapshotArtifact(tag + "3-play-zoom.png", look.render(sim, "")));
                        System.out.printf(Locale.ROOT, "sealed-complex-look seed %d %s: %d places, settlement %d,%d%n",
                                seed, take, plan.precincts().size(), settlement.seedX(), settlement.seedY());
                    }
                }
            }
        }
        return out;
    }
}

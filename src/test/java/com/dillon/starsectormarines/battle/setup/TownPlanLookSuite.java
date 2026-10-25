package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.MainStreet;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The town plan ({@code town-plan.md}) on fixed seeds, for comparing the planner before and after a change:
 * three stock medium cities (a Sabotage battle's map) and three Colony Breach colonies, each at play zoom and
 * close with the roofs off, centred on the map's main street where it has one and on the map's middle where it
 * has none. Look-preset stills. Opt-in.
 *
 * <p>The map is generated once on its own to find the centre, then the battle is built from the same seed; a
 * battle that laid another map is skipped and says so.
 */
public final class TownPlanLookSuite implements SnapshotSuite {

    static final long[] CITY_SEEDS = {12, 13, 19};
    static final long[] COLONY_SEEDS = {1, 4, 8};
    static final float CLOSE_CELLS = 30f;

    @Override public String id() { return "town-plan-look"; }

    @Override public String label() { return "Town plan on fixed city and colony seeds"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        for (boolean colony : new boolean[]{false, true}) {
            for (long seed : colony ? COLONY_SEEDS : CITY_SEEDS) {
                MapScale scale = colony ? MapScale.COLONY : MapScale.MEDIUM;
                MapResult map = colony
                        ? new BspCityGenerator().generate(scale.width, scale.height, seed, null, TargetProfile.NEUTRAL,
                                BattleSetup.colonyPlanFor(scale, seed))
                        : new BspCityGenerator().generate(scale.width, scale.height, seed);
                float cx = scale.width / 2f, cy = scale.height / 2f;
                if (!map.mainStreets.isEmpty()) {
                    MainStreet street = map.mainStreets.get(0);
                    cx = (street.startX() + street.endX()) / 2f;
                    cy = (street.startY() + street.endY()) / 2f;
                }
                try (BattleSimulation sim = colony
                        ? BattleSetup.createCivilianRescue(seed, Collections.emptyList(), false, RiskLevel.HIGH)
                        : BattleSetup.createSabotage(seed)) {
                    if (!sameMap(sim, map, map.mainStreets.isEmpty() ? null : map.mainStreets.get(0))) {
                        System.out.printf(Locale.ROOT, "[%s] seed %d: the battle laid another map%n", id(), seed);
                        continue;
                    }
                    String stem = (colony ? "colony" : "city") + seed;
                    System.out.printf(Locale.ROOT, "[%s] %s centred on %.0f,%.0f%n", id(), stem, cx, cy);
                    out.add(new SnapshotArtifact(stem + "-play.png", BattleReviewFrameRenderer.look(context.modRoot(),
                            1920, 1080, cx, cy, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).render(sim, stem)));
                    out.add(new SnapshotArtifact(stem + "-close-open.png", BattleReviewFrameRenderer.look(
                            context.modRoot(), 1920, 1080, cx, cy, CLOSE_CELLS).withoutRoofs().render(sim, stem)));
                }
            }
        }
        return out;
    }

    /**
     * Whether the battle's map is the one read: the same size, and then the main street's plaza border is stone at
     * two opposite corners, or with no main street, the same ground on nine in ten of a sample of cells.
     */
    private static boolean sameMap(BattleSimulation sim, MapResult map, MainStreet street) {
        if (sim.getGrid().getWidth() != map.grid.getWidth() || sim.getGrid().getHeight() != map.grid.getHeight()) {
            return false;
        }
        if (street != null) {
            return street.plazaLeft() >= 0
                    && sim.getTopology().getGroundKind(street.plazaLeft(), street.plazaTop()) == GroundKind.STONE
                    && sim.getTopology().getGroundKind(street.plazaRight(), street.plazaBottom()) == GroundKind.STONE;
        }
        int same = 0, total = 0;
        for (int x = 3; x < map.grid.getWidth(); x += 7) {
            for (int y = 3; y < map.grid.getHeight(); y += 7) {
                GroundKind a = sim.getTopology().getGroundKind(x, y), b = map.topology.getGroundKind(x, y);
                total++;
                if (a == b) same++;
            }
        }
        return same * 10 >= total * 9;
    }
}

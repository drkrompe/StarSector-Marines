package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.MainStreet;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.OpenSquare;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
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
 * The main street district ({@code set-piece-districts.md}) on Colony Breach colonies: the whole district at
 * play zoom, a close look along the street from the plaza end, roofed and not, and a close look at the market
 * square where it stands at the plaza end. Look-preset stills. Opt-in.
 *
 * <p>The colony's map is generated once on its own to read its main street, which the battle does not keep,
 * then the battle is built from the same seed; a seed whose battle laid a different map is skipped.
 */
public final class MainStreetLookSuite implements SnapshotSuite {

    static final int SEEDS_SCANNED = 24;
    static final int MAPS = 3;
    static final float CLOSE_CELLS = 30f;

    @Override public String id() { return "main-street-look"; }

    @Override public String label() { return "Main street district on colony maps"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        int maps = 0;
        MapScale scale = MapScale.COLONY;
        for (long seed = 1; seed <= SEEDS_SCANNED && maps < MAPS; seed++) {
            MapResult map = new BspCityGenerator().generate(scale.width, scale.height, seed, null, null,
                    BattleSetup.colonyPlanFor(scale, seed));
            if (map.mainStreets.isEmpty()) {
                System.out.printf(Locale.ROOT, "[%s] seed %d: no main street%n", id(), seed);
                continue;
            }
            MainStreet street = map.mainStreets.get(0);
            try (BattleSimulation sim = BattleSetup.createCivilianRescue(seed, Collections.emptyList(), false,
                    RiskLevel.HIGH)) {
                if (!sameStreet(sim.getTopology(), street)) {
                    System.out.printf(Locale.ROOT, "[%s] seed %d: the battle laid another map%n", id(), seed);
                    continue;
                }
                maps++;
                System.out.printf(Locale.ROOT, "[%s] seed %d: %s%n", id(), seed, street);
                float midX = (street.startX() + street.endX()) / 2f, midY = (street.startY() + street.endY()) / 2f;
                // A third of the way down from the plaza: the square behind, the hall ahead.
                float nearX = street.startX() + (street.endX() - street.startX()) / 3f;
                float nearY = street.startY() + (street.endY() - street.startY()) / 3f;
                String stem = "seed" + seed;
                out.add(new SnapshotArtifact(stem + "-district.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        1920, 1080, midX, midY, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).render(sim, stem)));
                out.add(new SnapshotArtifact(stem + "-street.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        1920, 1080, nearX, nearY, CLOSE_CELLS).render(sim, stem)));
                out.add(new SnapshotArtifact(stem + "-street-open.png", BattleReviewFrameRenderer.look(
                        context.modRoot(), 1920, 1080, nearX, nearY, CLOSE_CELLS).withoutRoofs().render(sim, stem)));
                OpenSquare square = street.square();
                if (square != null) {
                    out.add(new SnapshotArtifact(stem + "-square.png", BattleReviewFrameRenderer.look(
                            context.modRoot(), 1920, 1080, square.centreX(), square.centreY(), CLOSE_CELLS)
                            .render(sim, stem)));
                }
            }
        }
        return out;
    }

    /**
     * Whether the battle's map is the one read: the plaza's border is stone at two opposite corners, or, where
     * the market square stands there, most of its open square is brick.
     */
    static boolean sameStreet(CellTopology battle, MainStreet street) {
        if (street.plazaLeft() < 0) return false;
        if (street.square() == null) {
            return battle.getGroundKind(street.plazaLeft(), street.plazaTop()) == GroundKind.STONE
                    && battle.getGroundKind(street.plazaRight(), street.plazaBottom()) == GroundKind.STONE;
        }
        int brick = 0, cells = 0;
        for (int y = street.plazaTop(); y <= street.plazaBottom(); y++) {
            for (int x = street.plazaLeft(); x <= street.plazaRight(); x++) {
                cells++;
                if (battle.getGroundKind(x, y) == GroundKind.BRICK) brick++;
            }
        }
        return brick * 2 > cells;
    }
}

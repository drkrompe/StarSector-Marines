package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.IndustrialZone;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
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
 * The industrial zone district ({@code set-piece-districts.md}) on Colony Breach colonies: the whole district at
 * play zoom, and a close look down its service street, roofed and not. Look-preset stills. Opt-in.
 *
 * <p>The colony's map is generated once on its own to read its zone, which the battle does not keep, then the
 * battle is built from the same seed; a seed whose battle laid a different map is skipped.
 */
public final class IndustrialZoneLookSuite implements SnapshotSuite {

    static final int SEEDS_SCANNED = 24;
    static final int MAPS = 3;
    static final float CLOSE_CELLS = 30f;

    @Override public String id() { return "industrial-zone-look"; }

    @Override public String label() { return "Industrial zone district on colony maps"; }

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
            if (map.industrialZones.isEmpty()) {
                System.out.printf(Locale.ROOT, "[%s] seed %d: no industrial zone%n", id(), seed);
                continue;
            }
            IndustrialZone zone = map.industrialZones.get(0);
            try (BattleSimulation sim = BattleSetup.createCivilianRescue(seed, Collections.emptyList(), false,
                    RiskLevel.HIGH)) {
                if (!sameZone(sim.getGrid(), zone)) {
                    System.out.printf(Locale.ROOT, "[%s] seed %d: the battle laid another map%n", id(), seed);
                    continue;
                }
                maps++;
                System.out.printf(Locale.ROOT, "[%s] seed %d: %s%n", id(), seed, zone);
                float midX = (zone.startX() + zone.endX()) / 2f, midY = (zone.startY() + zone.endY()) / 2f;
                // A third of the way down the street: the gates and docks ahead on both sides.
                float nearX = zone.startX() + (zone.endX() - zone.startX()) / 3f;
                float nearY = zone.startY() + (zone.endY() - zone.startY()) / 3f;
                String stem = "seed" + seed;
                out.add(new SnapshotArtifact(stem + "-district.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        1920, 1080, midX, midY, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).render(sim, stem)));
                out.add(new SnapshotArtifact(stem + "-street.png", BattleReviewFrameRenderer.look(context.modRoot(),
                        1920, 1080, nearX, nearY, CLOSE_CELLS).render(sim, stem)));
                out.add(new SnapshotArtifact(stem + "-street-open.png", BattleReviewFrameRenderer.look(
                        context.modRoot(), 1920, 1080, nearX, nearY, CLOSE_CELLS).withoutRoofs().render(sim, stem)));
            }
        }
        return out;
    }

    /** Whether the battle's map is the one read: every dock door and every bay's gate cell stands where it was. */
    private static boolean sameZone(NavigationGrid battle, IndustrialZone zone) {
        for (IndustrialZone.Dock dock : zone.docks()) {
            if (!battle.isDoorway(dock.doorX(), dock.doorY())) return false;
        }
        return !zone.docks().isEmpty() || !zone.bays().isEmpty() || !zone.lanes().isEmpty();
    }
}

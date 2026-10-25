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
 * The industrial zone district ({@code set-piece-districts.md}) where it hosts the freight yard or the factory
 * row, on medium city maps (a Sabotage battle) and then Colony Breach colonies: the whole district at play zoom,
 * and a close look down its service street, roofed and not. Look-preset stills. Opt-in.
 *
 * <p>Each map is generated once on its own to read its zone, which the battle does not keep, then the battle is
 * built from the same seed; a seed whose battle laid a different map is skipped.
 */
public final class IndustrialZoneLookSuite implements SnapshotSuite {

    static final int SEEDS_SCANNED = 24;
    static final int MAPS = 3;
    static final float CLOSE_CELLS = 30f;

    @Override public String id() { return "industrial-zone-look"; }

    @Override public String label() { return "Industrial zone district hosting the yard or the row"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        // Every map whose zone hosts a set piece, those hosting both the yard and the row first.
        List<long[]> found = new ArrayList<>();
        for (int colony = 0; colony < 2; colony++) {
            MapScale scale = colony == 1 ? MapScale.COLONY : MapScale.MEDIUM;
            for (long seed = 1; seed <= SEEDS_SCANNED; seed++) {
                IndustrialZone zone = zone(map(colony == 1, scale, seed));
                if (zone == null) continue;
                found.add(new long[]{colony, seed, (zone.bays().isEmpty() ? 0 : 1) + (zone.lanes().isEmpty() ? 0 : 1)});
            }
        }
        found.sort((x, y) -> Long.compare(y[2], x[2]));
        int maps = 0;
        for (long[] f : found) {
            if (maps >= MAPS) break;
            boolean colony = f[0] == 1;
            long seed = f[1];
            MapScale scale = colony ? MapScale.COLONY : MapScale.MEDIUM;
            MapResult map = map(colony, scale, seed);
            IndustrialZone zone = zone(map);
            String stem = (colony ? "colony" : "city") + seed;
            try (BattleSimulation sim = colony
                    ? BattleSetup.createCivilianRescue(seed, Collections.emptyList(), false, RiskLevel.HIGH)
                    : BattleSetup.createSabotage(seed)) {
                if (!sameMap(sim, map, zone)) {
                    System.out.printf(Locale.ROOT, "[%s] %s: the battle laid another map%n", id(), stem);
                    continue;
                }
                maps++;
                System.out.printf(Locale.ROOT, "[%s] %s: %s%n", id(), stem, zone);
                float midX = (zone.startX() + zone.endX()) / 2f, midY = (zone.startY() + zone.endY()) / 2f;
                // A third of the way down the street: the gates and docks ahead on both sides.
                float nearX = zone.startX() + (zone.endX() - zone.startX()) / 3f;
                float nearY = zone.startY() + (zone.endY() - zone.startY()) / 3f;
                out.add(new SnapshotArtifact(stem + "-district.png", BattleReviewFrameRenderer.look(
                        context.modRoot(), 1920, 1080, midX, midY, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS)
                        .render(sim, stem)));
                out.add(new SnapshotArtifact(stem + "-street.png", BattleReviewFrameRenderer.look(
                        context.modRoot(), 1920, 1080, nearX, nearY, CLOSE_CELLS).render(sim, stem)));
                out.add(new SnapshotArtifact(stem + "-street-open.png", BattleReviewFrameRenderer.look(
                        context.modRoot(), 1920, 1080, nearX, nearY, CLOSE_CELLS).withoutRoofs().render(sim, stem)));
            }
        }
        return out;
    }

    private static MapResult map(boolean colony, MapScale scale, long seed) {
        return colony
                ? new BspCityGenerator().generate(scale.width, scale.height, seed, null, null,
                        BattleSetup.colonyPlanFor(scale, seed))
                : new BspCityGenerator().generate(scale.width, scale.height, seed);
    }

    /** The map's industrial zone where it hosts the freight yard or the factory row, or null. */
    private static IndustrialZone zone(MapResult map) {
        if (map.industrialZones.isEmpty()) return null;
        IndustrialZone zone = map.industrialZones.get(0);
        return zone.bays().isEmpty() && zone.lanes().isEmpty() ? null : zone;
    }

    /**
     * Whether the battle's map is the one read: the same size, every dock door where it was, and the same ground on
     * nine in ten of a sample of cells.
     */
    private static boolean sameMap(BattleSimulation sim, MapResult map, IndustrialZone zone) {
        NavigationGrid battle = sim.getGrid();
        if (battle.getWidth() != map.grid.getWidth() || battle.getHeight() != map.grid.getHeight()) return false;
        for (IndustrialZone.Dock dock : zone.docks()) {
            if (!battle.isDoorway(dock.doorX(), dock.doorY())) return false;
        }
        int same = 0, total = 0;
        for (int x = 3; x < map.grid.getWidth(); x += 7) {
            for (int y = 3; y < map.grid.getHeight(); y += 7) {
                total++;
                if (sim.getTopology().getGroundKind(x, y) == map.topology.getGroundKind(x, y)) same++;
            }
        }
        return same * 10 >= total * 9;
    }
}

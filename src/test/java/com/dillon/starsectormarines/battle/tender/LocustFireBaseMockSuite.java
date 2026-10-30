package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock-ups for the staged Locust build (`tender-ship.md`, Staged builds): the shapes a Locust site could take, made from the
 * pieces a fort's prepared positions already use (concrete paving, the tight emplacement parapet, sandbag runs, crate
 * stacks, the single-cell Locust and the raised Locust Hardpoint) on real arid ground, with a four-marine fire team
 * beside each for scale. Forward is north. One still per shot, 18 cells across, for the owner's picks; nothing here
 * is built by a tender yet.
 *
 * <ul>
 *   <li>{@code nest-1-pad}, {@code nest-2-parapet}, {@code nest-3-gun}: the 3x3 nest's three stages.</li>
 *   <li>{@code hardpoint}: the 3x3 Locust Hardpoint, its raised plinth filling the plot.</li>
 *   <li>{@code base-mount}: 5x5, the hardpoint in the middle with the parapet rim round it (a fort's missile mount).</li>
 *   <li>{@code base-forward}: 5x5, the Locust a cell forward of centre, the rim open at the rear, ammo and sandbags
 *   behind the gun.</li>
 *   <li>{@code base-corner}: 5x5, the Locust in the forward-left corner, a sandbag wing along the front right, ammo at
 *   the rear right.</li>
 * </ul>
 */
public class LocustFireBaseMockSuite implements SnapshotSuite {

    private static final int W = 40, H = 30;
    private static final float CELLS_ACROSS = 18f;
    private static final String LOCUST = TurretCatalogRegistry.LOCUST_STRUCTURE_ID;
    private static final String HARDPOINT = "structure.hardpoint-locust";

    /** One layout: what is paved, where the rim stands, where the gun and the stores go, all relative to the plot's centre. */
    private record Layout(String name, int padReach, boolean parapet, int rimBodyHalf, List<int[]> rimGaps,
                          String gun, int gunDx, int gunDy, List<Object[]> props) {}

    @Override public String id() { return "locust-fire-base-mock"; }

    @Override public String label() { return "Mock-ups: the staged 3x3 Locust nest and three 5x5 Locust fire bases"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<int[]> rear = List.of(new int[]{-1, -2}, new int[]{0, -2}, new int[]{1, -2});
        List<Layout> layouts = List.of(
                new Layout("nest-1-pad", 1, false, 0, List.of(), null, 0, 0, List.of()),
                new Layout("nest-2-parapet", 1, true, 0, List.of(), null, 0, 0, List.of()),
                new Layout("nest-3-gun", 1, true, 0, List.of(), LOCUST, 0, 0, List.of()),
                new Layout("hardpoint", 1, false, 0, List.of(), HARDPOINT, 0, 0, List.of()),
                new Layout("base-mount", 2, true, 1, List.of(), HARDPOINT, 0, 0, List.of()),
                new Layout("base-forward", 2, true, 1, rear, LOCUST, 0, 1, List.of(
                        new Object[]{"doodad.industrial-crate-stack", -1, -1},
                        new Object[]{"doodad.industrial-crate-stack", 1, -1},
                        new Object[]{"doodad.sandbag-straight-n", -1, 0},
                        new Object[]{"doodad.sandbag-straight-n", 1, 0})),
                new Layout("base-corner", 2, true, 1, List.of(new int[]{0, -2}), LOCUST, -1, 1, List.of(
                        new Object[]{"doodad.sandbag-straight-n", 0, 1},
                        new Object[]{"doodad.sandbag-straight-n", 1, 1},
                        new Object[]{"doodad.industrial-crate-stack", 1, -1},
                        new Object[]{"doodad.industrial-crate-stack", 1, 0})));
        List<SnapshotArtifact> out = new ArrayList<>();
        for (Layout layout : layouts) out.addAll(LookReview.record(context.modRoot(), shot(layout)));
        return out;
    }

    private static LookReview.Shot shot(Layout layout) {
        float[] centre = new float[2];
        return new LookReview.Shot(layout.name(), LookGround.of(SurfacePalette.ARID, 7L), W, H, placement -> {
            float[] at = placement.openNear(W / 2f, H / 2f);
            int cx = (int) at[0], cy = (int) at[1];
            centre[0] = cx + 0.5f;
            centre[1] = cy - 1.5f;
            CellTopology topology = placement.map().topology;
            List<Doodad> laid = new ArrayList<>();
            int reach = layout.padReach();
            // The builders clear the plot: no boulder or scrub stays on a poured pad.
            placement.map().doodads.removeIf(d -> Math.abs(d.cellX - cx) <= reach && Math.abs(d.cellY - cy) <= reach);
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dy = -reach; dy <= reach; dy++) {
                    int x = cx + dx, y = cy + dy;
                    topology.setGroundKind(x, y, GroundKind.COURTYARD);
                    topology.setNatureOverlayIndex(x, y, -1);
                    topology.setSurface(x, y, topology.surfaceIndex(
                            Material.CONCRETE.block(RoadRasterizer.age(.55, 7L, x, y))));
                    if (!layout.parapet() || Math.max(Math.abs(dx), Math.abs(dy)) != reach) continue;
                    if (gap(layout.rimGaps(), dx, dy)) continue;
                    Doodad piece = TileManifest.emplacementTightParapet(x, y, dx, dy, layout.rimBodyHalf());
                    if (piece != null) laid.add(piece);
                }
            }
            for (Object[] prop : layout.props()) {
                var def = TileRegistry.installed().doodad((String) prop[0]);
                if (def != null) laid.add(new Doodad(cx + (int) prop[1], cy + (int) prop[2], def));
            }
            int x0 = placement.site().x0(), y0 = placement.site().y0();
            SceneBuilder builder = placement.holdouts(placement.builder()).doodads(laid);
            builder.squad("fire-team").faction(Faction.MARINE).size(4)
                    .at(cx - x0 + 4, cy - y0 - 4).primary("weapon.field-rifle").stationary().done();
            BattleSimulation sim = builder.build().sim();
            if (layout.gun() != null) {
                sim.spawn(MapTurret.create("locust-mock", Faction.MARINE, layout.gun(),
                        cx + layout.gunDx(), cy + layout.gunDy()));
            }
            return sim;
        }, placement -> List.of(new LookReview.View("close", s -> new float[]{centre[0], centre[1], CELLS_ACROSS})),
                30, 15, 1, new int[]{1});
    }

    private static boolean gap(List<int[]> gaps, int dx, int dy) {
        for (int[] g : gaps) if (g[0] == dx && g[1] == dy) return true;
        return false;
    }
}

package com.dillon.starsectormarines.tools.terrainauthoring;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.ShepherdBasin;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.BattleSceneHostPass;
import com.dillon.starsectormarines.ops.battleview.BattleSceneFrame;
import com.dillon.starsectormarines.ops.battleview.RenderLayer;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Opt-in map authoring acceptance. No battle, repair timer or campaign reward is simulated here. */
public final class ShepherdBasinSnapshotSuite implements SnapshotSuite {
    public String id() { return "shepherd-basin"; }
    public String label() { return "Shepherd recovery basin: three authored visits"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        var renderer = new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(context.modRoot()), context.modRoot());
        List<SnapshotArtifact> frames = new ArrayList<>();
        int checked = 0;
        for (SurfacePalette surface : SurfacePalette.values()) for (int seed = 14101; seed <= 14102; seed++) {
            var profile = new TargetProfile(0,0,0,0,"",Set.of(),surface,SettlementLink.ROAD);
            for (ShepherdBasin.Visit visit : ShepherdBasin.Visit.values()) {
                MapResult map = ShepherdBasin.generate(seed, profile, visit);
                verify(map, visit);
                checked++;
                if (surface != SurfacePalette.ARID || seed != 14101) continue;
                String name = visit.name().toLowerCase(Locale.ROOT).replace('_','-');
                try (var scene = new ShipDeckBattleScene(map, 0)) {
                    frames.add(new SnapshotArtifact(name + ".png", renderer.renderHostPass(daylight(scene.pass(
                            ShipDeckBattleScene.DeckView.fitting(0,0,304,176,1920,1080))),1920,1080)));
                    frames.add(new SnapshotArtifact(name + "-cradle.png", renderer.renderHostPass(daylight(scene.pass(
                            ShipDeckBattleScene.DeckView.fitting(57,75,112,63,1920,1080))),1920,1080)));
                    if (visit == ShepherdBasin.Visit.COLD_START)
                        frames.add(new SnapshotArtifact("cold-start-hull.png", renderer.renderHostPass(daylight(scene.pass(
                                ShipDeckBattleScene.DeckView.fitting(104,101,48,27,1920,1080))),1920,1080)));
                    if (visit == ShepherdBasin.Visit.WORKING_PASSAGE)
                        frames.add(new SnapshotArtifact("working-passage-narrows.png", renderer.renderHostPass(daylight(scene.pass(
                                ShipDeckBattleScene.DeckView.fitting(166,68,128,72,1920,1080))),1920,1080)));
                }
            }
        }
        System.out.println("[shepherd-basin] " + checked + " maps: connected outdoor ground, doors, berths, build plots and freight clearance; grounded hull removed only for Working Passage.");
        return frames;
    }

    private static void verify(MapResult map, ShepherdBasin.Visit visit) {
        var grid = map.grid;
        int w = grid.getWidth(), h = grid.getHeight();
        boolean[] reached = new boolean[w*h];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int start = grid.index(map.marineSpawnX,map.marineSpawnY);
        reached[start] = true; queue.add(start);
        while (!queue.isEmpty()) {
            int at = queue.remove(), x = at%w, y = at/w;
            for (Direction dir : Direction.ALL) if (grid.canTraverseCellStep(x,y,dir)) {
                int to = grid.index(x+dir.dx,y+dir.dy);
                if (!reached[to]) { reached[to] = true; queue.add(to); }
            }
        }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            if (grid.isWalkable(x,y) && map.topology.getBuildingId(x,y)==0 && !reached[grid.index(x,y)])
                throw new IllegalStateException("Isolated basin outdoor cell " + x + "," + y);
        for (var edge : map.roadGraph.edges()) for (int i = 0; i < edge.cellsX.length; i++)
            if (!grid.isWalkable(edge.cellsX[i],edge.cellsY[i]))
                throw new IllegalStateException("Blocked authored road " + edge.cellsX[i] + "," + edge.cellsY[i]);
        var clearance = VehicleClearance.erode(grid, VehicleClearance.radiusForWidth(VehicleType.CARGO_CARRIER.visualWidthCells));
        boolean[] mask = new boolean[w*h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) mask[grid.index(x,y)] = clearance.isPassable(x,y);
        for (int[] route : new int[][]{{8,68,296,77},{8,68,82,119},{194,8,151,76}})
            if (Paths.isEmpty(GridPathfinder.findPath(grid,route[0],route[1],route[2],route[3],null,mask)))
                throw new IllegalStateException("Recovery cargo cannot traverse basin route");
        for (int[] plot : new int[][]{{204,119,214,129},{244,105,258,115}})
            for (int y=plot[1];y<=plot[3];y++)for(int x=plot[0];x<=plot[2];x++)
                if (!grid.isWalkable(x,y) || map.topology.isFixture(x,y)
                        || map.topology.getGroundKind(x,y)==GroundKind.STREET)
                    throw new IllegalStateException("Construction plot obstructed at " + x + "," + y);
        long hulls = map.doodads.stream().filter(d -> "doodad.shepherd-recovery-hull".equals(d.definitionId())).count();
        if (hulls != (visit == ShepherdBasin.Visit.WORKING_PASSAGE ? 0 : 1))
            throw new IllegalStateException("Grounded hull disagrees with visit");
        for(int y=105;y<=124;y++)for(int x=119;x<=142;x++)
            if(grid.isWalkable(x,y)!=(visit==ShepherdBasin.Visit.WORKING_PASSAGE))
                throw new IllegalStateException("Large recovery hull left the wrong navigation footprint");
        if (map.landingPads.size()!=4 || map.topology.terrainSurface()==null || map.topology.roadSurface()==null)
            throw new IllegalStateException("Basin lost authored landing or landscape");
    }

    /** Outdoor evidence uses ordinary daylight, never the ship compartment lighting layer. */
    private static BattleSceneHostPass daylight(BattleSceneHostPass source) {
        return new BattleSceneHostPass() {
            public BattleSceneFrame prepare(CanvasHostViewport viewport, float alpha) {
                BattleSceneFrame frame = source.prepare(viewport, alpha);
                var layers = frame.layers();
                layers.remove(RenderLayer.DECK_LIGHTING);
                return new BattleSceneFrame(frame.context(), layers);
            }
            public void draw(CanvasHostViewport viewport, float alpha) {
                throw new UnsupportedOperationException("Snapshot collection only");
            }
        };
    }
}

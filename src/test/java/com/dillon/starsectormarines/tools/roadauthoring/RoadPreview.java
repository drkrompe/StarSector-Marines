package com.dillon.starsectormarines.tools.roadauthoring;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;

/** Production topology, tile selection and draw commands, with an editor camera. */
public final class RoadPreview {
    private final Path root;
    private HeadlessUiRenderer renderer;
    public RoadPreview(Path root) { this.root=root; }
    public BufferedImage render(RoadPlan plan) {
        var grid=new NavigationGrid(plan.width(),plan.height()); var topology=new CellTopology(plan.width(),plan.height());
        for(int y=0;y<plan.height();y++) for(int x=0;x<plan.width();x++) {
            grid.setWalkableFloor(x,y);topology.setGroundKind(x,y,GroundKind.GRASS);
        }
        RoadRasterizer.stamp(plan,RoadRasterizer.rasterize(plan),grid,topology);
        var map=new MapResult(grid,topology,0,0,0,0,new ArrayList<>(),new ArrayList<>());
        if(renderer==null) renderer=new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(root.resolve("mod")),root.resolve("mod"));
        try(var scene=new ShipDeckBattleScene(map,0)) {
            float scale=Math.min(1920f/plan.width(),1080f/plan.height());
            return renderer.renderHostPass(scene.pass(ShipDeckBattleScene.DeckView.over(0,0,plan.width(),plan.height(),scale)),1920,1080);
        }
    }
}

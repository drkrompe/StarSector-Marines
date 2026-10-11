package com.dillon.starsectormarines.tools.terrainauthoring;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainPlan;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainField;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainBrush;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;

/** Real map construction and battle draw commands; the workbench is only a host. */
public final class TerrainPreview {
    public record Prepared(MapResult map,TerrainField field) {}
    public record Frame(BufferedImage image,TerrainField field) {}
    private final Path root;
    private HeadlessUiRenderer renderer;
    public TerrainPreview(Path root) { this.root=root; }
    public static Prepared prepare(TerrainPlan plan,RoadPlan roads) {
        var grid=new NavigationGrid(plan.width(),plan.height());var topology=new CellTopology(plan.width(),plan.height());
        for(int y=0;y<plan.height();y++)for(int x=0;x<plan.width();x++) {
            grid.setWalkableFloor(x,y);topology.setGroundKind(x,y,plan.finish()==TerrainPlan.Finish.ICE?GroundKind.SNOW:GroundKind.GRASS);
        }
        if(roads!=null)RoadRasterizer.stamp(roads,RoadRasterizer.rasterize(roads),grid,topology);
        TerrainField field=TerrainBrush.stamp(plan,grid,topology);
        return new Prepared(new MapResult(grid,topology,0,0,0,0,new ArrayList<>(),new ArrayList<>()),field);
    }
    public Frame render(TerrainPlan plan,RoadPlan roads) {
        Prepared prepared=prepare(plan,roads);
        if(renderer==null)renderer=new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(root.resolve("mod")),root.resolve("mod"));
        try(var scene=new ShipDeckBattleScene(prepared.map(),0)) {
            float scale=Math.min(1920f/plan.width(),1080f/plan.height());
            return new Frame(renderer.renderHostPass(scene.pass(ShipDeckBattleScene.DeckView.over(0,0,plan.width(),plan.height(),scale)),1920,1080),prepared.field());
        }
    }
}

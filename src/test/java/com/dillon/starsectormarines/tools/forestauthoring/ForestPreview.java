package com.dillon.starsectormarines.tools.forestauthoring;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestPlan;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestField;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import com.dillon.starsectormarines.battle.world.model.Doodad;

/** Real map construction and battle draw commands; the workbench is only a host. */
public final class ForestPreview {
    public record Prepared(MapResult map,ForestField field) {}
    public record Frame(BufferedImage image,ForestField field) {}
    private final Path root;
    private HeadlessUiRenderer renderer;
    public ForestPreview(Path root) { this.root=root; }
    public static Prepared prepare(ForestPlan plan,RoadPlan roads) {
        var grid=new NavigationGrid(plan.width(),plan.height());var topology=new CellTopology(plan.width(),plan.height());
        for(int y=0;y<plan.height();y++)for(int x=0;x<plan.width();x++) {
            grid.setWalkableFloor(x,y);topology.setGroundKind(x,y,GroundKind.GRASS);
        }
        if(roads!=null)RoadRasterizer.stamp(roads,RoadRasterizer.rasterize(roads),grid,topology);
        ArrayList<Doodad> trees=new ArrayList<>();
        ForestField field=ForestBrush.stamp(plan,grid,topology,trees,null);
        return new Prepared(new MapResult(grid,topology,0,0,0,0,new ArrayList<>(),trees),field);
    }
    public Frame render(ForestPlan plan,RoadPlan roads) {
        Prepared prepared=prepare(plan,roads);
        if(renderer==null)renderer=new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(root.resolve("mod")),root.resolve("mod"));
        try(var scene=new ShipDeckBattleScene(prepared.map(),0)) {
            float scale=Math.min(1920f/plan.width(),1080f/plan.height());
            return new Frame(renderer.renderHostPass(scene.pass(ShipDeckBattleScene.DeckView.over(0,0,plan.width(),plan.height(),scale)),1920,1080),prepared.field());
        }
    }
}

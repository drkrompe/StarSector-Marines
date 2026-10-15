package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Production shell recipes at three sizes, actual cities and all exported damage states. */
public final class CompactBuildingSnapshotSuite implements SnapshotSuite {
    public String id() { return "compact-buildings"; }
    public String label() { return "Themed small buildings and colony furniture"; }
    static final String[] POOLS = {"RESIDENTIAL", "COMMERCIAL", "WAREHOUSE", "MIXED"};
    static final BuildingLayouts.LayoutRecipe[] RECIPES = {BuildingLayouts.LayoutRecipe.HOME,
            BuildingLayouts.LayoutRecipe.SHOP, BuildingLayouts.LayoutRecipe.WAREHOUSE, BuildingLayouts.LayoutRecipe.SHED};
    static final BuildingKind[] KINDS = {BuildingKind.RESIDENTIAL, BuildingKind.COMMERCIAL,
            BuildingKind.INDUSTRIAL, BuildingKind.CIVIC};
    static BuildingShellCore.BuildingConfig config(int theme) {
        return new BuildingShellCore.BuildingConfig(CellTopology.GroundKind.INDOOR, POOLS[theme],
                PointOfInterest.Kind.ADMINISTRATIVE, RECIPES[theme], KINDS[theme]);
    }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        var renderer = new HeadlessBattleMapRenderer(context.modRoot());
        var grid = open(80, 45); var topology = new CellTopology(80, 45);
        List<Doodad> props = new ArrayList<>();
        for (int y=0;y<45;y++) for (int x=0;x<80;x++) topology.setGroundKind(x,y,CellTopology.GroundKind.STONE);
        for (int row=0;row<3;row++) for (int theme=0;theme<4;theme++) {
            int width = new int[]{5,9,15}[row], height = new int[]{5,8,11}[row];
            int left=theme*19+3, top=row*12+4;
            BuildingShellCore.carve(new BlockLeaf(left,top,left+width-1,top+height-1,false),
                    grid,topology,props,new Random(42+row),config(theme),
                    new BuildingPlacement(BuildingPlacement.Side.BOTTOM,true));
        }
        List<SnapshotArtifact> out = new ArrayList<>();
        var map = new MapResult(grid,topology,0,0,0,0,List.of(),props);
        out.add(new SnapshotArtifact("themed-buildings.png", caption(renderer.renderView(map,42,40,22,80,45,24),
                "HOMES / SHOPS / STORAGE / UTILITY", "Three building sizes / production renderer / 1920 x 1080 / no live capture")));
        for (long seed:new long[]{0,2}) {
            var city = new BspCityGenerator().generate(160,120,seed);
            out.add(new SnapshotArtifact("city-seed-"+seed+".png",caption(renderer.renderView(city,seed,80,60,80,45,24),
                    "COLONY NEIGHBORHOOD / SEED "+seed,"Production BSP city / ordinary buildings and public seating")));
        }
        props.clear(); grid=open(80,45); topology=new CellTopology(80,45);
        for (int y=0;y<45;y++) for (int x=0;x<80;x++) topology.setGroundKind(x,y,CellTopology.GroundKind.INDOOR);
        String[] names={"case","locker","desk","utility","galley","shelf"};
        for (int state=0;state<3;state++) for (int col=0;col<6;col++) for(int turn=0;turn<4;turn++)
            props.add(new Doodad(8+col*11+turn*2,8+state*12,
                    TileRegistry.installed().doodad("colony-furnishings."+names[col]+new String[]{"","-damaged","-destroyed"}[state]),turn));
        out.add(new SnapshotArtifact("furniture-states.png",caption(renderer.renderView(
                new MapResult(grid,topology,0,0,0,0,List.of(),props),42,40,22,80,45,24),
                "CASE / LOCKER / DESK / UTILITY / GALLEY / SHELF", "Bottom to top: intact, damaged, destroyed / four rotations / actual one-cell size")));
        return out;
    }
    static NavigationGrid open(int width,int height) {
        var grid=new NavigationGrid(width,height);
        for(int y=0;y<height;y++) for(int x=0;x<width;x++) grid.setWalkableFloor(x,y);
        return grid;
    }
    private static BufferedImage caption(BufferedImage image,String title,String note) {
        Graphics2D g=image.createGraphics();g.setColor(new Color(0x101b23));g.fillRect(0,0,1920,60);g.fillRect(0,1030,1920,50);
        g.setColor(new Color(0xe0ece9));g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,24));g.drawString(title,28,40);
        g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,18));g.drawString(note,28,1062);g.dispose();return image;
    }
}

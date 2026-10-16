package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill;
import com.dillon.starsectormarines.battle.world.gen.bsp.FortStage;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressInterior;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Actual fort stamping and shared utility fittings, kept out of the unit-test run. */
public final class PowerHallSnapshotSuite implements SnapshotSuite {
    public String id() { return "power-halls"; }
    public String label() { return "Fort power halls and reusable compact utility equipment"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        var renderer = new HeadlessBattleMapRenderer(context.modRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        for (FortKind kind : List.of(FortKind.BATTERY_FORT,FortKind.SHIELD_FORT))
        for (int turn = 0; turn < 4; turn++) {
            GenContext ctx = field(240,160);
            ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(ctx.grid,ctx.topology,null));
            ctx.put(BspKeys.BUILDINGS, BuildingFloodFill.populate(ctx.topology,42));
            ctx.put(BspKeys.MARKET_PROFILE, TargetProfile.NEUTRAL.withDeclaredDefences(
                    new GroundDefences(0,false).withForts(kind,1)));
            int[][] facing = {{0,80},{120,0},{239,80},{120,159}};
            var stage = FortStage.pinned(List.of(new FortStage.Pin(kind,120,80,
                    facing[turn][0],facing[turn][1],false)));
            stage.run(ctx);
            var fort = stage.placed().get(0);
            var at = fort.placement();
            for (var room : fort.plan().rooms()) if (room.aspect() == FortAspect.POWER) {
                int count = 0;
                for (var prop : ctx.doodads) {
                    for (int y=0;y<room.height();y++) for (int x=0;x<room.width();x++) {
                        int[] cell=at.cell(room.x()+x,room.y()+y);
                        if (prop.cellX==cell[0] && prop.cellY==cell[1]) count++;
                    }
                }
                if (count != 7)
                    throw new IllegalStateException("Power hall furnishing was refused: " + count);
                // All authored approach lanes must survive the actual core and shell placement.
                for (var op : room.ops()) if (op instanceof LayoutOp.Lane lane)
                    for (int y=lane.y();y<lane.y()+lane.spanY();y++)
                        for (int x=lane.x();x<lane.x()+lane.spanX();x++) {
                            int[] cell=at.cell(room.x()+x,room.y()+y);
                            if (!ctx.grid.isWalkable(cell[0],cell[1])) throw new IllegalStateException("Blocked power service aisle");
                        }
            }
            int[] core = fort.plan().coreCentre(FortAspect.POWER);
            int[] centre = at.cell(core[0],core[1]);
            out.add(new SnapshotArtifact(kind.name().toLowerCase(Locale.ROOT)+"-turn-"+turn+".png",
                    FortInteriorReview.render(context,ctx,centre[0],centre[1],80,
                            kind.name()+" / FULL-SIZE POWER AND COOLING / "+at.pose().quarterTurns()*90+" DEGREES")));
            if (turn==0)
                out.add(new SnapshotArtifact(kind.name().toLowerCase(Locale.ROOT)+"-detail.png",
                        FortInteriorReview.render(context,ctx,centre[0],centre[1],24,
                                kind.name()+" / power hall / machinery and reactor structure")));
        }
        GenContext ctx=field(80,45);
        List<RoomPacker.Placed> rooms=new ArrayList<>();
        for (int col=0;col<4;col++) {
            int x=6+col*18,y=18;
            int w=col==3?8:7,h=col==3?8:6;
            RoomPose pose=new RoomPose(col,false);
            var shape=RoomShape.rectangle(w,h).posed(pose);
            int[] door=pose.map(3,h,w,h);
            rooms.add(new RoomPacker.Placed(shape,pose,x,y,RoomPurpose.ENGINE_ROOM,
                    List.of(new Doorway(x+door[0],y+door[1]))));
        }
        FortressInterior.author(ctx,rooms);
        out.add(new SnapshotArtifact("utility-kit.png",caption(
                renderer.renderView(map(ctx),42,40,22,80,45,24),
                "REUSABLE UTILITY BUILDINGS / COMPACT POWER PAIRS AND FULL PLANT")));
        return out;
    }

    private static GenContext field(int w,int h) {
        var grid=new NavigationGrid(w,h);var topology=new CellTopology(w,h);
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            grid.setWalkableFloor(x,y);topology.setGroundKind(x,y,CellTopology.GroundKind.DIRT);
        }
        return new GenContext(grid,topology,new Random(42),w,h,42);
    }
    private static MapResult map(GenContext c) {
        return new MapResult(c.grid,c.topology,0,0,0,0,c.pois,c.doodads,
                null,null,c.defensePosts);
    }
    private static BufferedImage caption(BufferedImage image,String title) {
        var g=image.createGraphics();g.setColor(new Color(0x111d26));
        g.fillRect(0,0,1920,48);g.fillRect(0,1042,1920,38);
        g.setColor(new Color(0xddebe6));g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,24));
        g.drawString(title,24,33);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,17));
        g.drawString("Production Java2D / 1920 x 1080 / 24 pixels per cell / no live OpenGL capture",24,1068);
        g.dispose();return image;
    }
}

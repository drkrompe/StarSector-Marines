package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.tools.tilesetauthoring.OpeningReskinTemplate;
import com.dillon.starsectormarines.tools.tilesetauthoring.WallReskinTemplate;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Actual ground/prop rendering of every authored stamp and all four placements. */
public final class BuildingStampSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "building-stamps"; }
    @Override public String label() { return "Building styles and cardinal placements"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        artifacts.add(new SnapshotArtifact("wall-reskin-template.png",
                WallReskinTemplate.render()));
        artifacts.add(new SnapshotArtifact("opening-reskin-template.png", OpeningReskinTemplate.render()));
        int extent = 24;
        int panel = extent * 24, header = 38;
        BufferedImage overview = new BufferedImage(panel * 2, (panel + header) * ((BuildingStamp.values().length + 1) / 2),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D gallery = overview.createGraphics();
        int index = 0;
        for (BuildingStamp stamp : BuildingStamp.values()) {
            BufferedImage rotations = new BufferedImage(panel * 2, (panel + header) * 2,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rotations.createGraphics();
            for (int turn = 0; turn < 4; turn++) {
                GenContext ctx = open(extent);
                stamp.place(ctx, (extent - stamp.width(turn)) / 2,
                        (extent - stamp.height(turn)) / 2, turn);
                MapResult map = new MapResult(ctx.grid, ctx.topology, 0, 0, extent - 1, extent - 1,
                        ctx.pois, ctx.doodads);
                BufferedImage frame = renderer.render(map, 42, 24);
                int x = (turn % 2) * panel, y = (turn / 2) * (panel + header);
                String facing = new String[]{"N", "W", "S", "E"}[turn];
                panel(g, frame, x, y, stamp.label + " / " + facing, panel, header);
                if (turn == 0) panel(gallery, frame, (index % 2) * panel,
                        (index / 2) * (panel + header), stamp.label + " / " + stamp.style.name(),
                        panel, header);
            }
            g.dispose();
            artifacts.add(new SnapshotArtifact(stamp.name().toLowerCase() + "-rotations.png", rotations));
            index++;
        }
        gallery.dispose();
        artifacts.add(new SnapshotArtifact("building-variety.png", overview));
        return artifacts;
    }

    private static GenContext open(int extent) {
        NavigationGrid grid = new NavigationGrid(extent, extent);
        CellTopology topology = new CellTopology(extent, extent);
        for (int y = 0; y < extent; y++) for (int x = 0; x < extent; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, CellTopology.GroundKind.STREET);
        }
        return new GenContext(grid, topology, new Random(42), extent, extent, 42);
    }

    private static void panel(Graphics2D g, BufferedImage frame, int x, int y,
                              String title, int width, int header) {
        g.setColor(new Color(0x191D20));
        g.fillRect(x, y, width, header);
        g.setColor(new Color(0xF0E6D2));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g.drawString(title, x + 14, y + 26);
        g.drawImage(frame, x, y + header, null);
    }
}


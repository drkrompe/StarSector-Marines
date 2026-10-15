package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.CounterattackDepot;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
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

/** Parked scenery through production collectors, with all four placement turns. */
public final class ParkedVehiclesSnapshotSuite implements SnapshotSuite {
    private static final String[] IDS = {"utility-truck", "flatbed-truck", "tanker-truck",
            "box-van", "cargo-truck", "armored-apc"};

    @Override public String id() { return "parked-vehicles"; }
    @Override public String label() { return "Industrial haulers and colonial parked vehicles"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        MapResult depot = CounterattackDepot.generate(42, TargetProfile.NEUTRAL).map();
        return List.of(
                new SnapshotArtifact("rotations-game-scale.png", caption(renderer.render(gallery(false), 42, 32),
                        "PARKED VEHICLES / six types, four rotations / 32 px per cell / 100% UI")),
                new SnapshotArtifact("vehicles-detail.png", caption(renderer.render(gallery(true), 42, 128),
                        "INDUSTRIAL + COLONIAL / service, flatbed, tanker, rover, freight, transport / closer camera / 100% UI")),
                new SnapshotArtifact("counterattack-yard.png", caption(renderer.renderView(depot, 42,
                        125, 25, 60, 32, 32),
                        "COUNTERATTACK / production freight yard / 32 px per cell / 100% UI / pre-combat map evidence")));
    }

    private static MapResult gallery(boolean close) {
        int width = close ? 15 : 60, height = close ? 8 : 32;
        NavigationGrid grid = new NavigationGrid(width, height);
        CellTopology topology = new CellTopology(width, height);
        GroundKind[] surfaces = {GroundKind.STREET, GroundKind.DIRT, GroundKind.GRASS, GroundKind.SAND};
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, close ? GroundKind.STREET : surfaces[y / 8]);
        }
        List<Doodad> props = new ArrayList<>();
        for (int i = 0; i < IDS.length; i++) {
            for (int turn = 0; turn < (close ? 1 : 4); turn++) {
                int x = close ? 1 + (i % 3) * 5 : 4 + i * 9;
                int y = close ? 5 - (i / 3) * 4 : 2 + turn * 8;
                Doodad prop = new Doodad(x, y, TileRegistry.installed().doodad("doodad.parked-" + IDS[i]), turn);
                props.add(prop);
                for (int dy = 0; dy < prop.footprintCellsY; dy++) for (int dx = 0; dx < prop.footprintCellsX; dx++) {
                    grid.setWalkable(x + dx, y + dy, false);
                    grid.setSeeThrough(x + dx, y + dy, true);
                    topology.setFixture(x + dx, y + dy, true);
                }
            }
        }
        return new MapResult(grid, topology, 0, 0, width - 1, height - 1, List.of(), props);
    }

    /** Render at authored scale; add a 56px caption without resizing the map. */
    private static BufferedImage caption(BufferedImage map, String text) {
        if (map.getWidth() != 1920 || map.getHeight() != 1024)
            throw new IllegalArgumentException("Review map must be 1920x1024 before caption");
        BufferedImage out = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(0x182128)); g.fillRect(0, 0, 1920, 1080);
        g.drawImage(map, 0, 56, null);
        g.setColor(new Color(0xD4DDD8)); g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        g.drawString(text, 24, 35); g.dispose();
        return out;
    }
}

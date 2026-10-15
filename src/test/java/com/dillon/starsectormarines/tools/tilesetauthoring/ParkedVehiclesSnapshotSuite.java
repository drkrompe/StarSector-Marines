package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.CounterattackDepot;
import com.dillon.starsectormarines.battle.world.gen.ReliefCheckpoint;
import com.dillon.starsectormarines.battle.world.gen.JunctionCompound;
import com.dillon.starsectormarines.battle.world.gen.RelayNineCompound;
import com.dillon.starsectormarines.battle.world.gen.ThunderRunCompound;
import com.dillon.starsectormarines.battle.world.gen.ShepherdBasin;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
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
import java.util.Locale;

/** Parked scenery through production collectors, with all four placement turns. */
public final class ParkedVehiclesSnapshotSuite implements SnapshotSuite {
    private static final String[] IDS = {"utility-truck", "flatbed-truck", "tanker-truck",
            "box-van", "cargo-truck", "armored-apc"};

    @Override public String id() { return "parked-vehicles"; }
    @Override public String label() { return "Industrial haulers and colonial parked vehicles"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        checkCampaignPlacements();
        HeadlessBattleMapRenderer renderer = new HeadlessBattleMapRenderer(context.modRoot());
        MapResult depot = CounterattackDepot.generate(42, TargetProfile.NEUTRAL).map();
        List<SnapshotArtifact> frames = new ArrayList<>(List.of(
                new SnapshotArtifact("rotations-game-scale.png", caption(renderer.render(gallery(false), 42, 32),
                        "PARKED VEHICLES / six types, four rotations / 32 px per cell / 100% UI")),
                new SnapshotArtifact("vehicles-detail.png", caption(renderer.render(gallery(true), 42, 128),
                        "INDUSTRIAL + COLONIAL / service, flatbed, tanker, rover, freight, transport / closer camera / 100% UI")),
                new SnapshotArtifact("counterattack-yard.png", caption(renderer.renderView(depot, 42,
                        125, 25, 60, 32, 32),
                        "COUNTERATTACK / production freight yard / 32 px per cell / 100% UI / pre-combat map evidence"))));
        yardFrame(frames, renderer, "relief", ReliefCheckpoint.generate(144, 64, 42, TargetProfile.NEUTRAL).map(), 54, 20);
        yardFrame(frames, renderer, "counterattack-dispatch", depot, 51, 15);
        yardFrame(frames, renderer, "junction", JunctionCompound.generate(42, TargetProfile.NEUTRAL).map(), 76, 79);
        yardFrame(frames, renderer, "relay-nine", RelayNineCompound.generate(42, TargetProfile.NEUTRAL).map(), 166, 88);
        yardFrame(frames, renderer, "relay-nine-freight", RelayNineCompound.generate(42, TargetProfile.NEUTRAL).map(), 86, 86);
        yardFrame(frames, renderer, "thunder-run", ThunderRunCompound.generate(42, TargetProfile.NEUTRAL).map(), 210, 56);
        for (ShepherdBasin.Visit visit : ShepherdBasin.Visit.values()) {
            MapResult basin = ShepherdBasin.generate(42, TargetProfile.NEUTRAL, visit);
            yardFrame(frames, renderer, "basin-" + visit.name().toLowerCase(Locale.ROOT), basin, 111, 117);
            if (visit == ShepherdBasin.Visit.COLD_START)
                yardFrame(frames, renderer, "basin-crew-parking", basin, 105, 96);
        }
        return frames;
    }

    /** Bounded production-generation evidence; each generator also checks its mission routes. */
    private static void checkCampaignPlacements() {
        for (SurfacePalette surface : SurfacePalette.values()) for (long seed : new long[]{7, 42, 14110}) {
            TargetProfile neutral = TargetProfile.NEUTRAL;
            TargetProfile profile = new TargetProfile(neutral.marketSize(), neutral.stability(),
                    neutral.defenseLevel(), neutral.spaceportTier(), neutral.factionId(),
                    neutral.functions(), surface, neutral.link());
            try {
                checkVehicles(ReliefCheckpoint.generate(144, 64, seed, profile).map());
                checkVehicles(CounterattackDepot.generate(seed, profile).map());
                checkVehicles(JunctionCompound.generate(seed, profile).map());
                checkVehicles(RelayNineCompound.generate(seed, profile).map());
                checkVehicles(ThunderRunCompound.generate(seed, profile).map());
                for (ShepherdBasin.Visit visit : ShepherdBasin.Visit.values())
                    checkVehicles(ShepherdBasin.generate(seed, profile, visit));
            } catch (RuntimeException e) {
                throw new IllegalStateException("Campaign vehicle evidence: " + surface + " seed=" + seed, e);
            }
        }
        System.out.println("Campaign vehicle placement evidence: 96 maps across 3 seeds and 4 surface palettes passed");
    }

    private static void checkVehicles(MapResult map) {
        int found = 0;
        for (Doodad vehicle : map.doodads) {
            if (!vehicle.sheetPath.endsWith("/parked-vehicles.png")) continue;
            found++;
            if (vehicle.mirrored || vehicle.cover != Doodad.COVER_HEAVY)
                throw new IllegalStateException("Parked fleet must retain handedness and heavy cover");
            for (int y = vehicle.cellY; y < vehicle.cellY + vehicle.footprintCellsY; y++)
                for (int x = vehicle.cellX; x < vehicle.cellX + vehicle.footprintCellsX; x++) {
                    if (map.grid.isWalkable(x, y) || map.topology.getGroundKind(x, y) == GroundKind.STREET)
                        throw new IllegalStateException("Vehicle footprint must block yard ground at " + x + "," + y);
                }
        }
        if (found == 0) throw new IllegalStateException("Campaign yard has no parked fleet to review");
    }

    private static void yardFrame(List<SnapshotArtifact> frames, HeadlessBattleMapRenderer renderer,
                                  String name, MapResult map, int x, int y) throws Exception {
        frames.add(new SnapshotArtifact(name + "-yard.png", caption(renderer.renderView(map, 42, x, y, 60, 32, 32),
                name.toUpperCase(Locale.ROOT) + " / production vehicle staging / 32 px per cell / 100% UI / pre-combat evidence")));
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

package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DenseQuarterFillerTest {

    private static final int W = 34;
    private static final int H = 34;

    private static final BlockLeaf ANCHOR = new BlockLeaf(2, 2, 13, 14, false);
    private static final BlockLeaf STOREFRONT = new BlockLeaf(17, 2, 28, 14, false);
    private static final BlockLeaf SERVICE = new BlockLeaf(2, 18, 13, 30, false);

    @Test
    void buildingsFaceSharedConcourseAndLeaveRoadGraphUntouched() {
        Fixture fixture = generate(73L);

        assertDoorwaysOnOpposedSides(fixture.grid, ANCHOR,
                BuildingPlacement.Side.RIGHT, BuildingPlacement.Side.LEFT);
        assertDoorwaysOnOpposedSides(fixture.grid, STOREFRONT,
                BuildingPlacement.Side.LEFT, BuildingPlacement.Side.RIGHT);
        assertDoorwaysOnOpposedSides(fixture.grid, SERVICE,
                BuildingPlacement.Side.TOP, BuildingPlacement.Side.BOTTOM);

        assertPurposeDominates(fixture.topology, ANCHOR, BuildingPlacement.Side.RIGHT,
                RoomPurpose.SHOP_FLOOR);
        assertPurposeDominates(fixture.topology, ANCHOR, BuildingPlacement.Side.LEFT,
                RoomPurpose.STOCKROOM);
        assertPurposeDominates(fixture.topology, STOREFRONT, BuildingPlacement.Side.LEFT,
                RoomPurpose.SHOP_FLOOR);
        assertPurposeDominates(fixture.topology, STOREFRONT, BuildingPlacement.Side.RIGHT,
                RoomPurpose.STOCKROOM);

        int concourseProps = 0;
        for (Doodad doodad : fixture.ctx.doodads) {
            assertFalse(fixture.reserved[doodad.cellX][doodad.cellY],
                    "prop on reserved road cell " + doodad.cellX + "," + doodad.cellY);
            if (fixture.road[doodad.cellX][doodad.cellY]) {
                concourseProps++;
                assertFalse(nearDoorway(fixture.grid, doodad.cellX, doodad.cellY),
                        "concourse prop crowds doorway " + doodad.cellX + "," + doodad.cellY);
            }
        }
        assertTrue(concourseProps >= 3, "each frontage should contribute sparse edge cover");

        for (int y = 2; y <= 14; y++) {
            assertReservedStreet(fixture, 15, y);
            assertEquals(CellTopology.GroundKind.BRICK,
                    fixture.topology.getGroundKind(14, y), "west pedestrian strip");
            assertEquals(CellTopology.GroundKind.BRICK,
                    fixture.topology.getGroundKind(16, y), "east pedestrian strip");
        }
        for (int x = 2; x <= 13; x++) {
            assertReservedStreet(fixture, x, 16);
            assertEquals(CellTopology.GroundKind.BRICK,
                    fixture.topology.getGroundKind(x, 15), "north pedestrian strip");
            assertEquals(CellTopology.GroundKind.BRICK,
                    fixture.topology.getGroundKind(x, 17), "south pedestrian strip");
        }
    }

    @Test
    void compoundLayoutIsDeterministic() {
        assertEquals(digest(generate(991L)), digest(generate(991L)));
    }

    @Test
    void representativeCityBatchStillClaimsCommercialCompounds() {
        BspCityGenerator generator = new BspCityGenerator();
        int denseQuarters = 0;
        int tacticalAnchors = 0;
        for (long seed = 0; seed < 20; seed++) {
            MapResult map = generator.generate(80, 80, seed);
            for (Compound compound : generator.getLastCompounds()) {
                if (compound.kind != BlockKind.DENSE_QUARTER) continue;
                denseQuarters++;
                BlockLeaf largest = compound.members.get(0);
                for (BlockLeaf member : compound.members) {
                    if (member.area() > largest.area()) largest = member;
                }
                boolean hasFixture = false;
                for (int y = largest.top; y <= largest.bottom && !hasFixture; y++) {
                    for (int x = largest.left; x <= largest.right; x++) {
                        if (map.topology.isFixture(x, y)) {
                            hasFixture = true;
                            break;
                        }
                    }
                }
                if (hasFixture) tacticalAnchors++;
            }
        }
        assertTrue(denseQuarters >= 2,
                "commercial compounds should remain visible in representative cities; count="
                        + denseQuarters);
        assertTrue(tacticalAnchors >= 1,
                "choosing the largest member should yield tactical anchor stores; count="
                        + tacticalAnchors);
    }

    @Test
    void renderCommercialCompoundPreview() throws Exception {
        Fixture fixture = generate(73L);
        int scale = 12;
        BufferedImage image = new BufferedImage(W * scale, H * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                graphics.setColor(colorAt(fixture, x, y));
                graphics.fillRect(x * scale, y * scale, scale - 1, scale - 1);
            }
        }
        graphics.setColor(Color.WHITE);
        for (Doodad doodad : fixture.ctx.doodads) {
            graphics.drawOval(doodad.cellX * scale + 3, doodad.cellY * scale + 3,
                    scale - 7, scale - 7);
        }
        graphics.dispose();

        Path directory = Path.of("build", "zone-previews");
        Files.createDirectories(directory);
        Path output = directory.resolve("dense-quarter-commercial-compound.png");
        ImageIO.write(image, "PNG", output.toFile());
        System.out.println("  wrote " + output.toAbsolutePath());
    }

    private static Fixture generate(long seed) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.STREET);
            }
        }

        boolean[][] road = new boolean[W][H];
        boolean[][] reserved = new boolean[W][H];
        for (int y = 2; y <= 14; y++) {
            for (int x = 14; x <= 16; x++) road[x][y] = true;
            reserved[15][y] = true;
        }
        for (int x = 2; x <= 13; x++) {
            for (int y = 15; y <= 17; y++) road[x][y] = true;
            reserved[x][16] = true;
        }

        List<BlockLeaf> members = new ArrayList<>(List.of(ANCHOR, STOREFRONT, SERVICE));
        Map<BlockLeaf, Compound.Role> roles = new IdentityHashMap<>();
        roles.put(ANCHOR, Compound.Role.COMMAND);
        roles.put(STOREFRONT, Compound.Role.BARRACKS);
        roles.put(SERVICE, Compound.Role.ARMORY);
        Compound compound = new Compound(
                BlockKind.DENSE_QUARTER, ANCHOR, members, roles, null);

        GenContext ctx = new GenContext(grid, topology, new Random(seed), W, H, seed);
        ctx.put(BspKeys.ROAD_CELLS, road);
        ctx.put(BspKeys.ROAD_RESERVATION, reserved);
        new DenseQuarterFiller().fill(compound, ctx);
        return new Fixture(grid, topology, road, reserved, ctx);
    }

    private static void assertDoorwaysOnOpposedSides(NavigationGrid grid, BlockLeaf leaf,
                                                      BuildingPlacement.Side publicSide,
                                                      BuildingPlacement.Side serviceSide) {
        assertTrue(hasDoorway(grid, leaf, publicSide), "missing public doorway on " + publicSide);
        assertTrue(hasDoorway(grid, leaf, serviceSide), "missing service doorway on " + serviceSide);
        assertEquals(2, perimeterDoorwayCount(grid, leaf), "exactly one public + one service doorway");
    }

    private static boolean hasDoorway(NavigationGrid grid, BlockLeaf leaf, BuildingPlacement.Side side) {
        int min = side == BuildingPlacement.Side.TOP || side == BuildingPlacement.Side.BOTTOM
                ? leaf.left + 1 : leaf.top + 1;
        int max = side == BuildingPlacement.Side.TOP || side == BuildingPlacement.Side.BOTTOM
                ? leaf.right - 1 : leaf.bottom - 1;
        for (int along = min; along <= max; along++) {
            int x = side == BuildingPlacement.Side.LEFT ? leaf.left
                    : side == BuildingPlacement.Side.RIGHT ? leaf.right : along;
            int y = side == BuildingPlacement.Side.TOP ? leaf.top
                    : side == BuildingPlacement.Side.BOTTOM ? leaf.bottom : along;
            if (grid.isDoorway(x, y)) return true;
        }
        return false;
    }

    private static int perimeterDoorwayCount(NavigationGrid grid, BlockLeaf leaf) {
        int count = 0;
        for (int x = leaf.left; x <= leaf.right; x++) {
            if (grid.isDoorway(x, leaf.top)) count++;
            if (grid.isDoorway(x, leaf.bottom)) count++;
        }
        for (int y = leaf.top + 1; y < leaf.bottom; y++) {
            if (grid.isDoorway(leaf.left, y)) count++;
            if (grid.isDoorway(leaf.right, y)) count++;
        }
        return count;
    }

    private static void assertPurposeDominates(CellTopology topology, BlockLeaf leaf,
                                               BuildingPlacement.Side side, RoomPurpose expected) {
        int expectedCount = 0;
        int otherCount = 0;
        int min = side == BuildingPlacement.Side.TOP || side == BuildingPlacement.Side.BOTTOM
                ? leaf.left + 1 : leaf.top + 1;
        int max = side == BuildingPlacement.Side.TOP || side == BuildingPlacement.Side.BOTTOM
                ? leaf.right - 1 : leaf.bottom - 1;
        for (int along = min; along <= max; along++) {
            int x = side == BuildingPlacement.Side.LEFT ? leaf.left + 1
                    : side == BuildingPlacement.Side.RIGHT ? leaf.right - 1 : along;
            int y = side == BuildingPlacement.Side.TOP ? leaf.top + 1
                    : side == BuildingPlacement.Side.BOTTOM ? leaf.bottom - 1 : along;
            RoomPurpose actual = topology.getRoomPurpose(x, y);
            if (actual == expected) expectedCount++;
            else if (actual != null) otherCount++;
        }
        assertTrue(expectedCount > otherCount,
                expected + " should dominate the interior edge behind " + side);
    }

    private static boolean nearDoorway(NavigationGrid grid, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (grid.inBounds(x + dx, y + dy) && grid.isDoorway(x + dx, y + dy)) return true;
            }
        }
        return false;
    }

    private static void assertReservedStreet(Fixture fixture, int x, int y) {
        assertTrue(fixture.grid.isWalkable(x, y), "reserved road remains walkable");
        assertFalse(fixture.topology.isFixture(x, y), "reserved road cannot become a fixture");
        assertEquals(CellTopology.GroundKind.STREET,
                fixture.topology.getGroundKind(x, y), "reserved road keeps street paving");
    }

    private static String digest(Fixture fixture) {
        StringBuilder out = new StringBuilder();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                out.append(fixture.grid.isWalkable(x, y) ? '1' : '0');
                out.append(fixture.grid.isDoorway(x, y) ? 'D' : '-');
                out.append(fixture.topology.getGroundKind(x, y).ordinal()).append(',');
                RoomPurpose purpose = fixture.topology.getRoomPurpose(x, y);
                out.append(purpose == null ? -1 : purpose.ordinal()).append(';');
            }
        }
        for (Doodad doodad : fixture.ctx.doodads) {
            out.append(doodad.cellX).append(':').append(doodad.cellY).append(':')
                    .append(doodad.tile.col).append(':').append(doodad.tile.row).append('|');
        }
        return out.toString();
    }

    private static Color colorAt(Fixture fixture, int x, int y) {
        if (fixture.reserved[x][y]) return new Color(50, 90, 130);
        if (fixture.grid.isDoorway(x, y)) return new Color(235, 190, 75);
        if (!fixture.grid.isWalkable(x, y)) {
            return fixture.topology.isFixture(x, y)
                    ? new Color(150, 65, 55) : new Color(35, 38, 43);
        }
        RoomPurpose purpose = fixture.topology.getRoomPurpose(x, y);
        if (purpose == RoomPurpose.SHOP_FLOOR) return new Color(75, 150, 155);
        if (purpose == RoomPurpose.STOCKROOM) return new Color(145, 105, 70);
        if (fixture.topology.getGroundKind(x, y) == CellTopology.GroundKind.BRICK) {
            return new Color(135, 120, 115);
        }
        if (fixture.topology.getGroundKind(x, y) == CellTopology.GroundKind.INDOOR) {
            return new Color(110, 105, 95);
        }
        return new Color(78, 82, 88);
    }

    private static final class Fixture {
        final NavigationGrid grid;
        final CellTopology topology;
        final boolean[][] road;
        final boolean[][] reserved;
        final GenContext ctx;

        Fixture(NavigationGrid grid, CellTopology topology,
                boolean[][] road, boolean[][] reserved, GenContext ctx) {
            this.grid = grid;
            this.topology = topology;
            this.road = road;
            this.reserved = reserved;
            this.ctx = ctx;
        }
    }
}

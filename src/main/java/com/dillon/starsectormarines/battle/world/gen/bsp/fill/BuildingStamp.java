package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Small, authored city buildings. Rows read north to south; a space is unowned
 * ground, # structure, + a threshold, comma accent floor, and n/e/s/w a firing
 * recess with its outside edge closed. Furniture letters name one-cell fixtures.
 * One local pose transforms every cell, aperture and prop, including concave cuts.
 */
enum BuildingStamp {
    CORNER_HOME("Corner home", BuildingKind.RESIDENTIAL, BuildingStyle.SANDSTONE,
            "###n###    ",
            "#h....#    ",
            "#.....e    ",
            "#b....#    ",
            "#..####    ",
            "#..#       ",
            "#..####n###",
            "#..+,,,,h.#",
            "w..#,,,,..e",
            "#..#b,,,..#",
            "##+####s###"),
    COURT_HOUSE("Court house", BuildingKind.RESIDENTIAL, BuildingStyle.TENEMENT,
            "##n######n###",
            "#h..#...#..h#",
            "#...+...+...#",
            "#b..#...#..b#",
            "#...##+##...#",
            "#...#   #...#",
            "w...#   #...e",
            "#...+   +...#",
            "#...#   #...#",
            "##+##   ##+##"),
    CORNER_SHOP("Corner shop", BuildingKind.COMMERCIAL, BuildingStyle.ARCADE,
            "###n###    ",
            "#kk...#    ",
            "#.....e    ",
            "#.....#    ",
            "#..####    ",
            "#..####n###",
            "#..+,,,,cc#",
            "w..#,,,,..e",
            "#d.#,,,,..#",
            "##+####+###"),
    MARKET_HALL("Market hall", BuildingKind.COMMERCIAL, BuildingStyle.SANDSTONE,
            "###n####n####",
            "#kk..#cc..cc#",
            "#....+......#",
            "#....#......e",
            "+....##+#####",
            "+....,,,,,,d#",
            "#kk..,,,,,,.#",
            "###s#####+###"),
    T_WORKSHOP("T workshop", BuildingKind.INDUSTRIAL, BuildingStyle.WORKSHOP,
            "##n#######n##",
            "#c.c.#..d..c#",
            "+....+......+",
            "+....#......+",
            "#....#......#",
            "#####+.+#####",
            "    #,,,#    ",
            "    #,,,e    ",
            "    w,,,#    ",
            "    #c.c#    ",
            "    ##+##    "),
    OFFSET_DEPOT("Offset depot", BuildingKind.INDUSTRIAL, BuildingStyle.DEPOT,
            "    ###n###",
            "    #cc.cc#",
            "    #.....+",
            "    #.....+",
            "#####..####",
            "#cc,,,,#   ",
            "+..,,,,#   ",
            "+..,,,,e   ",
            "#d.,,cc#   ",
            "###s####   ");

    final String label;
    final BuildingKind kind;
    final BuildingStyle style;
    private final List<String> rows;
    final int width;
    final int height;

    BuildingStamp(String label, BuildingKind kind, BuildingStyle style, String... rows) {
        this.label = label;
        this.kind = kind;
        this.style = style;
        this.rows = List.of(rows);
        width = rows[0].length();
        height = rows.length;
        for (String row : rows) {
            if (row.length() != width) throw new IllegalArgumentException(label + ": ragged rows");
        }
    }

    int width(int turns) { return (turns & 1) == 0 ? width : height; }
    int height(int turns) { return (turns & 1) == 0 ? height : width; }

    /** Canonical y-up coordinate into the north-first authored rows. */
    char cell(int x, int y) {
        return x < 0 || y < 0 || x >= width || y >= height ? ' ' : rows.get(height - 1 - y).charAt(x);
    }

    /** Counterclockwise quarter turns, matching Doodad's placement convention. */
    int[] posed(int x, int y, int turns) {
        switch (Math.floorMod(turns, 4)) {
            case 1: return new int[]{height - 1 - y, x};
            case 2: return new int[]{width - 1 - x, height - 1 - y};
            case 3: return new int[]{y, width - 1 - x};
            default: return new int[]{x, y};
        }
    }

    private static Direction posed(Direction side, int turns) {
        return Direction.CARDINALS[Math.floorMod(side.bit() - turns, 4)];
    }

    /**
     * Keep the procedural plans in the mix. Enumerate fitting poses before
     * drawing, so tall lots can use turned wide plans and refused lots spend no RNG.
     */
    static boolean tryPlace(BlockLeaf leaf, GenContext ctx, BuildingKind kind) {
        if (leaf.left < 1 || leaf.top < 1 || leaf.right >= ctx.width - 1
                || leaf.bottom >= ctx.height - 1) return false;
        List<Choice> choices = new ArrayList<>();
        for (BuildingStamp stamp : values()) {
            if (stamp.kind != kind) continue;
            for (int turn = 0; turn < 4; turn++) {
                if (stamp.width(turn) <= leaf.width() && stamp.height(turn) <= leaf.height()
                        && stamp.width(turn) * stamp.height(turn) * 2 >= leaf.area()) {
                    choices.add(new Choice(stamp, turn));
                }
            }
        }
        if (choices.isEmpty() || ctx.rng.nextInt(3) == 0) return false;
        Choice choice = choices.get(ctx.rng.nextInt(choices.size()));
        int left = leaf.left + (leaf.width() - choice.stamp.width(choice.turns)) / 2;
        int bottom = leaf.top + (leaf.height() - choice.stamp.height(choice.turns)) / 2;
        choice.stamp.place(ctx, left, bottom, choice.turns);
        return true;
    }

    private record Choice(BuildingStamp stamp, int turns) {}

    void place(GenContext ctx, int left, int bottom, int turns) {
        if (left < 1 || bottom < 1 || left + width(turns) >= ctx.width
                || bottom + height(turns) >= ctx.height) {
            throw new IllegalArgumentException("building stamp needs an exterior approach");
        }
        int walls = ctx.topology.surfaceIndex(style.wall);
        int floor = ctx.topology.surfaceIndex(style.floor);
        int accent = ctx.topology.surfaceIndex(style.accent);
        int[] interior = null;
        int[] entrance = null;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                char cell = cell(x, y);
                if (cell == ' ') continue;
                int[] position = posed(x, y, turns);
                int wx = left + position[0], wy = bottom + position[1];
                boolean wall = cell == '#';
                ctx.grid.setWalkable(wx, wy, !wall);
                ctx.grid.setSeeThrough(wx, wy, !wall);
                ctx.grid.setDoorway(wx, wy, cell == '+');
                ctx.topology.setWall(wx, wy, wall);
                ctx.topology.setGroundKind(wx, wy, kind == BuildingKind.INDUSTRIAL
                        ? GroundKind.STRIPED : kind == BuildingKind.COMMERCIAL
                        ? GroundKind.TILE : GroundKind.INDOOR);
                ctx.topology.setBuildingKindHint(wx, wy, kind);
                ctx.topology.setSurface(wx, wy, wall ? walls : cell == ',' ? accent : floor);
                ctx.markMadeGround(wx, wy);
                if (wall) {
                    int mask = 0;
                    for (Direction side : Direction.CARDINALS) {
                        if (cell(x + side.dx, y + side.dy) != '#') {
                            mask |= wallBit(posed(side, turns));
                        }
                    }
                    ctx.topology.setWallDirMask(wx, wy, mask);
                } else {
                    ctx.topology.setWallDirMask(wx, wy, 0);
                }
                if (cell == '.' && interior == null) interior = new int[]{wx, wy};
                if (cell == '+' && entrance == null) {
                    for (Direction side : Direction.CARDINALS) {
                        if (cell(x + side.dx, y + side.dy) == ' ') {
                            Direction out = posed(side, turns);
                            entrance = new int[]{wx + out.dx, wy + out.dy};
                            break;
                        }
                    }
                }
                String propId = prop(cell);
                if (propId != null) {
                    DoodadDef def = TileRegistry.installed().doodad(propId);
                    if (def == null || def.footprintCellsX != 1 || def.footprintCellsY != 1) {
                        throw new IllegalStateException("stamp fixture must resolve to one cell: " + propId);
                    }
                    ctx.grid.setWalkable(wx, wy, false);
                    ctx.topology.setFixture(wx, wy, true);
                    ctx.doodads.add(new Doodad(wx, wy, def, turns));
                }
            }
        }
        // Both sides must be carved before installing an edge feature.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Direction side = window(cell(x, y));
                if (side == null) continue;
                int[] position = posed(x, y, turns);
                try {
                    ctx.grid.placeEdgeBarrier(left + position[0], bottom + position[1],
                            posed(side, turns), SharedEdgeBarrier.Kind.WINDOW);
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException(name() + " turn " + turns
                            + " window " + x + "," + y, ex);
                }
            }
        }
        if (interior == null || entrance == null) throw new IllegalStateException("stamp has no entrance");
        PointOfInterest.Kind poiKind = kind == BuildingKind.INDUSTRIAL
                ? PointOfInterest.Kind.DEPOT : PointOfInterest.Kind.RESIDENTIAL;
        ctx.pois.add(new PointOfInterest(poiKind, left, bottom,
                left + width(turns) - 1, bottom + height(turns) - 1,
                entrance[0], entrance[1], interior[0], interior[1]));
    }

    private static int wallBit(Direction side) {
        switch (side) {
            case N: return CellTopology.WALL_DIR_N;
            case E: return CellTopology.WALL_DIR_E;
            case S: return CellTopology.WALL_DIR_S;
            default: return CellTopology.WALL_DIR_W;
        }
    }

    private static Direction window(char cell) {
        switch (cell) {
            case 'n': return Direction.N;
            case 'e': return Direction.E;
            case 's': return Direction.S;
            case 'w': return Direction.W;
            default: return null;
        }
    }

    private static String prop(char cell) {
        switch (cell) {
            case 'b': return "doodad.chair-south-green";
            case 'h': return "doodad.chest-1";
            case 'c': return "doodad.crate";
            case 'k': return "doodad.shelf-1";
            case 'd': return "doodad.desk-1";
            default: return null;
        }
    }
}


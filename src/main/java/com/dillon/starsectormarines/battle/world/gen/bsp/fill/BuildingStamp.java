package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import java.util.Random;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.ConsoleBankLayout.Cell;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
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
public enum BuildingStamp {
    CORNER_HOME("Corner home", BuildingKind.RESIDENTIAL, BuildingStyle.SANDSTONE,
            "##nnn##    ",
            "#h....#    ",
            "#.....e    ",
            "#b....#    ",
            "#..####    ",
            "#..#       ",
            "#..###nn###",
            "#..+,,,,h.#",
            "w..#,,,,..e",
            "#..#b,,,..#",
            "##+####s###"),
    COURT_HOUSE("Court house", BuildingKind.RESIDENTIAL, BuildingStyle.TENEMENT,
            "##nn#####nn##",
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
            "##nnn##    ",
            "#kk...#    ",
            "#.....e    ",
            "#.....#    ",
            "#..####    ",
            "#..###nn###",
            "#..+,,,,cc#",
            "w..#,,,,..e",
            "#d.#,,,,..#",
            "##+####+###"),
    MARKET_HALL("Market hall", BuildingKind.COMMERCIAL, BuildingStyle.SANDSTONE,
            "##nn###nn####",
            "#kk..#cc..cc#",
            "#....+......#",
            "#....#......e",
            "+....##+#####",
            "+....,,,,,,d#",
            "#kk..,,,,,,.#",
            "###s#####+###"),
    T_WORKSHOP("T workshop", BuildingKind.INDUSTRIAL, BuildingStyle.WORKSHOP,
            "##n#######n##",
            "#c.c.#.....c#",
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
            "###s####   "),
    /** Freight facilities shared by authored missions and sufficiently large city parcels. */
    FREIGHT_L("Freight depot with office wing", BuildingKind.INDUSTRIAL, BuildingStyle.DEPOT,
            "###n####            ",
            "#......#            ",
            "#cc..cc#            ",
            "#......#            ",
            "#......#            ",
            "+......#            ",
            "#......#            ",
            "#......#            ",
            "#......#            ",
            "#......#            ",
            "#.......############",
            "#......#.,,,,,,,,cc#",
            "w......#.,,,,,,,,,.#",
            "#......+.,,,,,,,,,.e",
            "#......#.,,,,,,,,,.#",
            "#......#.,,,,,,,,,c#",
            "############++######"),
    FREIGHT_T("Freight transfer workshop", BuildingKind.INDUSTRIAL, BuildingStyle.WORKSHOP,
            "      ###n###      ",
            "      #,,,,,#      ",
            "      #,,,,c#      ",
            "      #,,,,,#      ",
            "      #,,,,,e      ",
            "      #,,,,,#      ",
            "      w,,,,,#      ",
            "      #,,,,,#      ",
            "      #,,,,,#      ",
            "      #,,,,,#      ",
            "      #,,,,,#      ",
            "###n##.#++##.##n###",
            "#.................#",
            "#.c.............c.#",
            "+.................+",
            "#.................#",
            "#.c.c.........c.c.#",
            "#.................#",
            "#########++########"),
    /** A farm's house, set back on a country track; rural only (`mapgen-nouns.md`, Rural country). */
    FARMHOUSE("Farmhouse", BuildingKind.RESIDENTIAL, BuildingStyle.TENEMENT,
            "##nn#nn##",
            "#...#...#",
            "w...+...e",
            "#...#...#",
            "#...#...#",
            "#.......#",
            "###+#s###"),
    /** A farm's barn beside its house; rural only. */
    BARN("Barn", BuildingKind.INDUSTRIAL, BuildingStyle.DEPOT,
            "######n######",
            "#c.........c#",
            "#...........#",
            "w...........e",
            "#...........#",
            "#c.........c#",
            "#...........#",
            "#####+++#####"),
    /**
     * Saltwell Cross's old terraforming pump house (`dust-ledger-crossing.md`): the pump hall on the west, and
     * off a one-cell corridor the broker's office and the radio room to the north, the counting room and the ledger
     * room to the south. Every office also opens on the yard, and the ledger room has four ways in (the corridor, the
     * counting room, and yard doors east and south), so taking it is not one doorway's fight. Story map only.
     */
    PUMP_HOUSE("Pump house", BuildingKind.CIVIC, BuildingStyle.WORKSHOP,
            "##nn#nn###n+n#####n+n##",
            "#k.....k#d....k#c....c#",
            "w.......#......#......e",
            "#.......#d.....#......#",
            "#.......#......#......#",
            "#.......###+#######+###",
            "#.......+.............+",
            "#.......###+#######+###",
            "#.......#......#......#",
            "w.......#......#......e",
            "#.......#......+......+",
            "#.......#......#......#",
            "#k.....k#d.d.d.#k.....#",
            "##s++s#####s+#####s+###"),
    /**
     * A hardened pillbox (`dust-ledger-crossing.md`, Defences): one room behind firing slits on three faces, its door
     * on the fourth, so its crew covers an entrance from under cover. Pose it with the door toward the yard. Story map only.
     */
    PILLBOX("Pillbox", BuildingKind.FORTIFIED, BuildingStyle.WORKSHOP,
            "#n#n#n#",
            "w.....e",
            "#.....#",
            "w.....e",
            "###+###");

    /**
     * The plans a city lot may draw. The farm buildings stand only where the
     * country survey puts them; drawn here they would also change every
     * city's draws.
     */
    private static final List<BuildingStamp> CITY = List.of(CORNER_HOME, COURT_HOUSE, CORNER_SHOP,
            MARKET_HALL, T_WORKSHOP, OFFSET_DEPOT, FREIGHT_L, FREIGHT_T);

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
    /** Cells across the footprint at {@code turns} quarter turns. */
    public int footprintWidth(int turns) { return width(turns); }
    /** Cells along the footprint at {@code turns} quarter turns. */
    public int footprintHeight(int turns) { return height(turns); }
    int height(int turns) { return (turns & 1) == 0 ? height : width; }

    /** Canonical y-up coordinate into the north-first authored rows. */
    char cell(int x, int y) {
        return x < 0 || y < 0 || x >= width || y >= height ? ' ' : rows.get(height - 1 - y).charAt(x);
    }

    /** Counterclockwise quarter turns, matching Doodad's placement convention. */
    public int[] posed(int x, int y, int turns) {
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
        return tryPlace(leaf, ctx, kind, CITY);
    }

    /** A compound warehouse can reuse a complete freight building without becoming a second factory. */
    static boolean tryPlaceFreight(BlockLeaf leaf, GenContext ctx) {
        return tryPlace(leaf, ctx, BuildingKind.INDUSTRIAL, List.of(FREIGHT_L, FREIGHT_T));
    }

    public static boolean fitsFreightParcel(int width, int height) {
        for (BuildingStamp stamp : List.of(FREIGHT_L,FREIGHT_T)) for (int turn=0;turn<2;turn++)
            if (stamp.width(turn)<=width && stamp.height(turn)<=height && stamp.width*stamp.height*2>=width*height) return true;
        return false;
    }

    private static boolean tryPlace(BlockLeaf leaf, GenContext ctx, BuildingKind kind, List<BuildingStamp> templates) {
        if (leaf.left < 1 || leaf.top < 1 || leaf.right >= ctx.width - 1
                || leaf.bottom >= ctx.height - 1) return false;
        List<Choice> choices = new ArrayList<>();
        for (BuildingStamp stamp : templates) {
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

    /** Place an authored plan without stretching; caller owns the surrounding lot and approaches. */
    public void place(GenContext ctx, int left, int bottom, int turns) {
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
                    int mask = 0;
                    if (cell == '+') {
                        int across = (cell(x - 1, y) == '#' ? 1 : 0) + (cell(x + 1, y) == '#' ? 1 : 0);
                        int upright = (cell(x, y - 1) == '#' ? 1 : 0) + (cell(x, y + 1) == '#' ? 1 : 0);
                        mask = wallBit(posed(upright > across ? Direction.E : Direction.N, turns));
                        for (Direction side : Direction.CARDINALS) {
                            if (cell(x + side.dx, y + side.dy) == ' ') {
                                mask = wallBit(posed(side, turns));
                                break;
                            }
                        }
                    }
                    ctx.topology.setWallDirMask(wx, wy, mask);
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
                if (kind == BuildingKind.RESIDENTIAL && cell != '#') {
                    RoomPurpose purpose = this == CORNER_HOME
                            ? (x < 3 && y < 6 ? RoomPurpose.RESIDENTIAL_HALL : y >= 5 ? RoomPurpose.BEDROOM
                                : x < 7 ? RoomPurpose.HOME_KITCHEN : RoomPurpose.APARTMENT_LIVING)
                            : x < 4 ? RoomPurpose.BEDROOM : x > 8 ? RoomPurpose.APARTMENT_LIVING : RoomPurpose.HOME_KITCHEN;
                    if (cell != '+') ctx.topology.setRoomPurpose(wx,wy,purpose);
                }
                String propId = kind == BuildingKind.RESIDENTIAL ? null : prop(cell);
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
                Direction outward = posed(side, turns);
                // A procedurally placed lot can back onto a neighbour's wall or the map edge;
                // a window there looks onto nothing, so the plan simply goes without it. The same
                // holds where the neighbour's own window already glazes this edge from its recess.
                int outsideX = left + position[0] + outward.dx;
                int outsideY = bottom + position[1] + outward.dy;
                if (!ctx.grid.inBounds(outsideX, outsideY) || !ctx.grid.isWalkable(outsideX, outsideY)
                        || ctx.grid.getEdgeBarrier(left + position[0], bottom + position[1], outward) != null) {
                    continue;
                }
                try {
                    ctx.grid.placeEdgeBarrier(left + position[0], bottom + position[1],
                            outward, SharedEdgeBarrier.Kind.WINDOW);
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException(name() + " turn " + turns
                            + " window " + x + "," + y, ex);
                }
            }
        }
        if (kind == BuildingKind.RESIDENTIAL || kind == BuildingKind.COMMERCIAL) {
            if (turns == 0) {
                if (kind == BuildingKind.RESIDENTIAL)
                    NeighborhoodFurnishing.furnish(ctx.grid,ctx.topology,left,bottom,left+width-1,bottom+height-1,ctx.doodads);
                else {
                    var groups = new BuildingWorkGroups(ctx.grid,ctx.topology,ctx.doodads);
                    groups.reserveDoorSurrounds(left,bottom,left+width-1,bottom+height-1);
                    groups.fit(left,bottom,left+width-1,bottom+height-1,null,CompactBuildingFurnishing.pair("shelf","shelf"),1);
                }
            } else {
                // Fit in the authored frame once, then rotate whole groups with the building.
                NavigationGrid grid=new NavigationGrid(width+4,height+4);
                for(int y=0;y<height+4;y++)for(int x=0;x<width+4;x++)grid.setWalkableFloor(x,y);
                GenContext local=new GenContext(grid,new CellTopology(width+4,height+4),new Random(0),width+4,height+4,0);
                place(local,2,2,0);
                for(Doodad d:local.doodads) {
                    int lx=d.cellX-2,ly=d.cellY-2;
                    // One-cell authored commercial fixtures have already been stamped above.
                    if(kind==BuildingKind.COMMERCIAL && prop(cell(lx,ly))!=null)continue;
                    int[] a=posed(lx,ly,turns),b=posed(lx+d.footprintCellsX-1,ly+d.footprintCellsY-1,turns);
                    Doodad placed=new Doodad(left+Math.min(a[0],b[0]),bottom+Math.min(a[1],b[1]),
                            TileRegistry.installed().doodad(d.definitionId()),(d.quarterTurns+turns)%4);
                    ctx.doodads.add(placed);
                    if(!grid.isWalkable(d.cellX,d.cellY)) for(int y=placed.cellY;y<placed.cellY+placed.footprintCellsY;y++)
                        for(int x=placed.cellX;x<placed.cellX+placed.footprintCellsX;x++) {
                            ctx.grid.setWalkable(x,y,false);ctx.grid.setSeeThrough(x,y,true);ctx.topology.setFixture(x,y,true);
                        }
                }
            }
        }
        placeConsoleBank(ctx, left, bottom, turns);
        if (this == FREIGHT_L) {
            var rack = BuildingWorkGroups.stockedRack("cartons");
            int[] a = posed(3, 6, turns), b = posed(2 + rack.width(), 5 + rack.height(), turns);
            new BuildingWorkGroups(ctx.grid, ctx.topology, ctx.doodads).place(rack,
                    left + Math.min(a[0], b[0]), bottom + Math.min(a[1], b[1]), turns, null);
        }
        if (interior == null || entrance == null) throw new IllegalStateException("stamp has no entrance");
        // Furnishing may occupy the first authored floor cell; retain a real interior anchor.
        if (!ctx.grid.isWalkable(interior[0], interior[1])) {
            outer: for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                if (cell(x, y) != '.' && cell(x, y) != ',') continue;
                int[] p = posed(x, y, turns);
                if (ctx.grid.isWalkable(left + p[0], bottom + p[1])) {
                    interior = new int[]{left + p[0], bottom + p[1]};
                    break outer;
                }
            }
        }
        PointOfInterest.Kind poiKind = kind == BuildingKind.INDUSTRIAL || kind == BuildingKind.FORTIFIED ? PointOfInterest.Kind.DEPOT
                : kind == BuildingKind.CIVIC ? PointOfInterest.Kind.ADMINISTRATIVE : PointOfInterest.Kind.RESIDENTIAL;
        ctx.pois.add(new PointOfInterest(poiKind, left, bottom,
                left + width(turns) - 1, bottom + height(turns) - 1,
                entrance[0], entrance[1], interior[0], interior[1]));
    }

    /** Authored operations desks turn with their whole building, including the operator side. */
    private void placeConsoleBank(GenContext ctx, int left, int bottom, int turns) {
        List<Cell> path = switch (this) {
            case T_WORKSHOP -> List.of(new Cell(7, 8), new Cell(7, 9), new Cell(8, 9), new Cell(9, 9));
            case FREIGHT_L -> List.of(new Cell(1, 3), new Cell(1, 2), new Cell(1, 1),
                    new Cell(2, 1), new Cell(3, 1), new Cell(4, 1));
            case FREIGHT_T -> List.of(new Cell(7, 14), new Cell(7, 15), new Cell(7, 16),
                    new Cell(8, 16), new Cell(9, 16));
            default -> List.of();
        };
        if (path.isEmpty()) return;
        List<Cell> world = path.stream().map(cell -> {
            int[] p = posed(cell.x(), cell.y(), turns);
            return new Cell(left + p[0], bottom + p[1]);
        }).toList();
        BuildingConsoleBanks.place(ctx.grid, ctx.topology, world, this != FREIGHT_L, ctx.doodads);
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
            case 'b': return "mess-galley.stool";
            case 'h': return "colony-furnishings.locker";
            case 'c': return "colony-furnishings.case";
            case 'k': return "colony-furnishings.shelf";
            case 'd': return "colony-furnishings.desk";
            default: return null;
        }
    }
}


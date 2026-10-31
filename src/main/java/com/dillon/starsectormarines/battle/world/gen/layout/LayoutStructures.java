package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.world.gen.AirbaseLot;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.PreparedGround;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.List;
import java.util.Random;

/**
 * Raises a layout's {@link Structure}s in the structures phase, before the map
 * is finished: buildings through their authored plans, rings and stores from
 * the fort's own props. A structure whose footprint is not all ground it may
 * stand on is refused, never squeezed.
 */
final class LayoutStructures {

    /** The square's marker: there is no bell art yet, so a mast stands for it (placeholder). */
    static final String MARKER = "doodad.military-radar-dish";
    private static final String CRATES = "doodad.industrial-crate-stack";
    private static final String PAVING = "pavement.concrete.new";

    /** A laid structure's reason when it is paving itself (a square), which may stand on road. */
    static final String PAVING_REASON = "paving";

    private LayoutStructures() {}

    static Laid raise(GenContext ctx, LayoutGround ground, Structure s, boolean[][] held) {
        int w = s.width(), h = s.height();
        if (s.x() < 1 || s.y() < 1 || s.x() + w >= ctx.width || s.y() + h >= ctx.height) {
            return Laid.refused(s.name(), Laid.Kind.STRUCTURE, s.required(), "off the map");
        }
        // Nothing but paving stands on a way, the farmland's and landscape's tracks as well as roads; what keeps off
        // ways keeps off the column clearance too.
        LineCourse.CellTest standable = (x, y) -> (held == null || !held[x][y])
                && (s.kind().takesRoad() || !ground.isWay(x, y))
                && !(s.kind().keepsOffWays() && ground.inColumnClearance(x, y))
                && (ground.buildable(x, y)
                || s.kind().takesRoad() && ground.isRoad(x, y) && PreparedGround.open(ctx, x, y));
        if (!StructureFit.fits(standable, s.x(), s.y(), w, h)) {
            return Laid.refused(s.name(), Laid.Kind.STRUCTURE, s.required(), "its ground is not open" + firstRefused(standable, s, ground));
        }
        int x0 = s.x(), y0 = s.y(), x1 = x0 + w - 1, y1 = y0 + h - 1;
        ctx.doodads.removeIf(d -> d.cellX >= x0 && d.cellX <= x1 && d.cellY >= y0 && d.cellY <= y1);
        switch (s.kind()) {
            case FARMSTEAD -> {
                yard(ctx, x0, y0, x1, y1);
                int x = x0 + StructureFit.YARD, y = y0 + StructureFit.YARD;
                BuildingStamp.FARMHOUSE.place(ctx, x, y, s.turns());
                if ((s.turns() & 1) == 0) {
                    BuildingStamp.BARN.place(ctx, x + StructureFit.HOUSE_ALONG + StructureFit.BETWEEN, y, s.turns());
                } else {
                    BuildingStamp.BARN.place(ctx, x, y + StructureFit.HOUSE_ALONG + StructureFit.BETWEEN, s.turns());
                }
            }
            case GRANARY -> {
                yard(ctx, x0, y0, x1, y1);
                BuildingStamp.BARN.place(ctx, x0 + StructureFit.YARD, y0 + StructureFit.YARD, s.turns());
            }
            case AMMO_DUMP -> {
                ring(ctx, x0, y0, x1, y1, s.turns());
                // Stacks in rows with a walk between them, so the crew can reach every one.
                for (int x = x0 + 2; x <= x1 - 2; x += 2) {
                    for (int y = y0 + 2; y <= y1 - 2; y++) PreparedGround.obstacle(ctx, x, y, CRATES);
                }
            }
            case OBSERVATION_POST -> ring(ctx, x0, y0, x1, y1, s.turns());
            case BELL_SQUARE -> square(ctx, x0, y0, x1, y1);
            case AIRFIELD -> airfield(ctx, ground, s, x0, y0, x1, y1);
            case CREW_QUARTERS -> hamletBuilding(ctx, BuildingStamp.PREFAB_BUNKHOUSE, s, x0, y0, x1, y1);
            case STORE_SHED -> hamletBuilding(ctx, BuildingStamp.PREFAB_SHORT_MODULE, s, x0, y0, x1, y1);
            case PUMP_HUT -> hamletBuilding(ctx, BuildingStamp.CONTROL_HUT, s, x0, y0, x1, y1);
            case MESS_HALL -> hamletBuilding(ctx, BuildingStamp.PREFAB_MODULE, s, x0, y0, x1, y1);
            case RADIO_HUT -> hamletBuilding(ctx, BuildingStamp.CONTROL_HUT, s, x0, y0, x1, y1);
            case HOMESTEAD -> hamletBuilding(ctx, BuildingStamp.FARMHOUSE, s, x0, y0, x1, y1);
        }
        return new Laid(s.name(), Laid.Kind.STRUCTURE, Laid.Status.LAID, s.kind().takesRoad() ? PAVING_REASON : "",
                s.required(),
                List.of(new int[]{x0, y0}, new int[]{x1, y1}), List.of(), List.of());
    }

    /**
     * An allied airbase over the footprint: the lot inside its verge, its strip on the side {@code turns} names
     * (0 north, 1 east, 2 south, 3 west), its hardstands published as {@link LandingPad.Purpose#ALLIED_AIRFIELD}, so
     * it is no garrison's field and puts up no node to take. Laid from its own draw, so the map's shared stream reads
     * the same whether or not a base was laid.
     */
    private static void airfield(GenContext ctx, LayoutGround ground, Structure s, int x0, int y0, int x1, int y1) {
        int verge = StructureFit.AIRFIELD_VERGE;
        AirbaseLot.Facing facing = switch (s.turns()) {
            case 0 -> AirbaseLot.Facing.NORTH;
            case 1 -> AirbaseLot.Facing.EAST;
            case 2 -> AirbaseLot.Facing.SOUTH;
            default -> AirbaseLot.Facing.WEST;
        };
        new AirbaseLot(x0 + verge, y0 + verge, x1 - verge, y1 - verge, facing, StructureFit.AIRFIELD_SIZE,
                LandingPad.Purpose.ALLIED_AIRFIELD, verge).author(ctx, new Random(ground.seed(s.name())));
        // The track from its gate to the way it stands beside (AirfieldSiting.access).
        for (int[] c : AirfieldSiting.access(ground, s).track()) {
            if (PreparedGround.open(ctx, c[0], c[1])) PreparedGround.workedGround(ctx, c[0], c[1]);
        }
    }

    /** Where and why the footprint first fails, for the refusal's reason: " at x,y (way)" and the like. */
    private static String firstRefused(LineCourse.CellTest standable, Structure s, LayoutGround ground) {
        for (int y = s.y(); y < s.y() + s.height(); y++) {
            for (int x = s.x(); x < s.x() + s.width(); x++) {
                if (standable.test(x, y)) continue;
                String why = ground.isHeld(x, y) ? "held" : ground.isWay(x, y) ? "way"
                        : ground.inColumnClearance(x, y) ? "column clearance" : ground.isClaimed(x, y) ? "claimed" : "built";
                return " at " + x + "," + y + " (" + why + ")";
            }
        }
        return "";
    }

    /** The props a hamlet's yards are dressed with; each is skipped quietly where its art is missing. */
    static final String DRUMS = "doodad.industrial-drum-cluster", TANK = "doodad.industrial-fluid-tank",
            UTILITY_TRUCK = "doodad.parked-utility-truck", HEDGE = "nature.shrub-1";
    /** Cells of a hamlet yard's gate, in the middle of its door side. */
    static final int GATE = 3;

    /**
     * A hamlet's building ({@link HamletGrowth}) on its worked yard, its door on the side {@code turns} names (0 south,
     * 1 west, 2 north, 3 east), the yard closed by a fence (a homestead's by a hedge) with a gate before the door, and
     * dressed by what the building is: drums and crates at the stores, a tank at the pump house, a utility truck at
     * the quarters, a dish at the radio hut.
     */
    private static void hamletBuilding(GenContext ctx, BuildingStamp stamp, Structure s, int x0, int y0, int x1, int y1) {
        yard(ctx, x0, y0, x1, y1);
        stamp.place(ctx, x0 + StructureFit.HAMLET_YARD, y0 + StructureFit.HAMLET_YARD, s.turns());
        Yard yard = new Yard(s.turns(), x0, y0, x1, y1);
        int a = yard.along(), d = yard.deep();
        TileRegistry tiles = TileRegistry.installed();
        int hedge = tiles == null || !tiles.has(HEDGE) ? -1 : tiles.indexOf(HEDGE);
        for (int u = 0; u < a; u++) {
            for (int v = 0; v < d; v++) {
                boolean ring = u == 0 || v == 0 || u == a - 1 || v == d - 1;
                if (!ring || v == 0 && Math.abs(u - a / 2) <= GATE / 2) continue;
                int[] c = yard.cell(u, v);
                if (s.kind() == StructureKind.HOMESTEAD) {
                    if (hedge >= 0 && PreparedGround.open(ctx, c[0], c[1])) {
                        ctx.topology.setGroundKind(c[0], c[1], GroundKind.GRASS);
                        ctx.topology.setSurface(c[0], c[1], 0);
                        ctx.topology.setNatureOverlayIndex(c[0], c[1], hedge);
                    }
                } else {
                    // Along the door side's own direction the fence runs as that side does.
                    boolean alongSide = v == 0 || v == d - 1;
                    PreparedGround.fence(ctx, c[0], c[1], alongSide == ((s.turns() & 1) == 0));
                }
            }
        }
        int back = d - 2;
        switch (s.kind()) {
            case STORE_SHED -> {
                for (int u = 2; u <= a - 3; u += 2) dress(ctx, yard.cell(u, back), u % 4 == 2 ? DRUMS : CRATES);
                for (int v = 2; v < back; v += 2) dress(ctx, yard.cell(1, v), DRUMS);
            }
            case PUMP_HUT -> {
                dress(ctx, yard.cell(1, back), TANK);
                dress(ctx, yard.cell(a - 2, back), TANK);
            }
            case MESS_HALL -> {
                dress(ctx, yard.cell(1, back), CRATES);
                dress(ctx, yard.cell(a - 2, back), DRUMS);
            }
            case RADIO_HUT -> dress(ctx, yard.cell(a - 2, back), MARKER);
            case HOMESTEAD -> dress(ctx, yard.cell(a - 2, back), CRATES);
            case CREW_QUARTERS -> {
                // The truck in the end margin, two cells across and three deep inside the fence.
                int[] p = yard.cell(1, 3), q = yard.cell(2, 5);
                int bx = Math.min(p[0], q[0]), by = Math.min(p[1], q[1]);
                boolean wide = Math.abs(p[0] - q[0]) + 1 == 3;
                DoodadDef truck = tiles == null ? null : tiles.doodad(UTILITY_TRUCK);
                if (truck != null) PreparedGround.prop(ctx, bx, by, truck, truck.footprintCellsX == 3 == wide ? 0 : 1);
            }
            default -> { }
        }
    }

    private static void dress(GenContext ctx, int[] c, String doodad) {
        TileRegistry tiles = TileRegistry.installed();
        DoodadDef def = tiles == null ? null : tiles.doodad(doodad);
        if (def != null && def.footprintCellsX == 1 && def.footprintCellsY == 1) PreparedGround.prop(ctx, c[0], c[1], def, 0);
    }

    /**
     * A footprint read from its door side: {@code u} along the door side, {@code v} inward from it, for the side
     * {@code turns} names.
     */
    record Yard(int turns, int x0, int y0, int x1, int y1) {
        int along() { return (turns & 1) == 0 ? x1 - x0 + 1 : y1 - y0 + 1; }

        int deep() { return (turns & 1) == 0 ? y1 - y0 + 1 : x1 - x0 + 1; }

        int[] cell(int u, int v) {
            return switch (turns) {
                case 0 -> new int[]{x0 + u, y0 + v};
                case 2 -> new int[]{x0 + u, y1 - v};
                case 1 -> new int[]{x0 + v, y0 + u};
                default -> new int[]{x1 - v, y0 + u};
            };
        }
    }

    /** Worked earth over the footprint: a farm's or a store's yard. */
    private static void yard(GenContext ctx, int x0, int y0, int x1, int y1) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) PreparedGround.workedGround(ctx, x, y);
    }

    /** Paving over the footprint and a marker in its middle. */
    private static void square(GenContext ctx, int x0, int y0, int x1, int y1) {
        int paving = ctx.topology.surfaceIndex(PAVING);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                ctx.topology.setGroundKind(x, y, GroundKind.SIDEWALK);
                if (paving >= 0) ctx.topology.setSurface(x, y, paving);
                ctx.topology.setNatureOverlayIndex(x, y, -1);
                ctx.markMadeGround(x, y);
            }
        }
        int cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
        DoodadDef marker = TileRegistry.installed().doodad(MARKER);
        if (marker != null && marker.footprintCellsX == 1 && marker.footprintCellsY == 1) {
            ctx.grid.setWalkable(cx, cy, false);
            ctx.topology.setFixture(cx, cy, true);
            ctx.doodads.add(new Doodad(cx, cy, marker));
        } else {
            PreparedGround.obstacle(ctx, cx, cy, CRATES);
        }
    }

    /**
     * A sandbag ring on the footprint's edge, bags facing out and corners turned, open in the middle of one side
     * (the side {@code turns} names: 0 south, 1 west, 2 north, 3 east) so it can be walked into.
     */
    private static void ring(GenContext ctx, int x0, int y0, int x1, int y1, int turns) {
        int mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
        for (int x = x0 + 1; x <= x1 - 1; x++) {
            for (int y = y0 + 1; y <= y1 - 1; y++) PreparedGround.workedGround(ctx, x, y);
        }
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                boolean west = x == x0, east = x == x1, south = y == y0, north = y == y1;
                if (!west && !east && !south && !north) continue;
                boolean entrance = switch (turns) {
                    case 0 -> south && x == mx;
                    case 1 -> west && y == my;
                    case 2 -> north && x == mx;
                    default -> east && y == my;
                };
                if (entrance) continue;
                int[] ns = north ? new int[]{0, 1} : south ? new int[]{0, -1} : null;
                int[] ew = east ? new int[]{1, 0} : west ? new int[]{-1, 0} : null;
                if (ns != null && ew != null) PreparedGround.sandbag(ctx, x, y, ns, ew);
                else PreparedGround.sandbag(ctx, x, y, ns != null ? ns : ew, null);
            }
        }
    }
}

package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenKey;
import com.dillon.starsectormarines.battle.world.gen.PreparedGround;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * A military site's large installations as set pieces built from the map's own kit, not single props: each has its
 * ground (a concrete pad), its enclosure (a fence with a gate, or an earth bund), and where it has one a real roofed
 * building, so it stands, blocks and shades like the buildings round it. Its art only dresses it: the dishes, the
 * radar tower, the stacks and the tanks stand inside on their pads (owner, 2026-11-11: the large doodads wanted a
 * physical presence).
 *
 * <p>Each set piece is laid out in its own frame, read from its gate: {@code u} along the gate side, {@code v} inward
 * from it. Its gate faces the building it serves; {@link #site} finds the nearest open ground for it.
 */
final class MilitarySetPieces {

    private MilitarySetPieces() {}

    /** What closes a set piece's edge. */
    enum Enclosure { NONE, FENCE, BUND }

    /** The set pieces: cells along the gate side, cells deep, and what closes them. */
    enum Kind {
        /** Sat comms: two dishes on a fenced concrete pad, a cable reel between them. */
        DISH_COMPOUND(13, 8, Enclosure.FENCE),
        /** The radar tower on its fenced hardstand, its control hut at the back and its switch gear by the gate. */
        RADAR_HARDSTAND(13, 10, Enclosure.FENCE),
        /** The generator plant: a roofed shed over its sets, the exhaust stacks behind it, its day tank and reel. */
        GENERATOR_PLANT(13, 9, Enclosure.NONE),
        /** The tank farm: vertical tanks inside an earth bund, its pump by the gap. */
        TANK_FARM(14, 10, Enclosure.BUND);

        final int along, deep;
        final Enclosure enclosure;

        Kind(int along, int deep, Enclosure enclosure) {
            this.along = along;
            this.deep = deep;
            this.enclosure = enclosure;
        }
    }

    /** Cells of a set piece's gate, in the middle of its gate side. */
    static final int GATE = 3;
    /** The pad's surface. */
    static final String PAD = "pavement.concrete.new";

    /**
     * A prop's slot, in the set piece's frame: its low corner and its extent. The first of {@code ids} the art has and
     * that fits the slot stands there, centred; none stands where none does. An item {@code indoors} stands on the
     * floor of the set piece's building.
     */
    record Item(List<String> ids, int u, int v, int along, int deep, boolean indoors) {
        Item(List<String> ids, int u, int v, int along, int deep) {
            this(ids, u, v, along, deep, false);
        }
    }

    /** A building's slot: its plan, its low corner in the set piece's frame; its door faces the gate. */
    record Hut(BuildingStamp stamp, int u, int v) {
        int along() { return stamp.footprintWidth(0); }

        int deep() { return stamp.footprintHeight(0); }
    }

    /** The radar's tower: the lattice tower, or the round-2 radar art meanwhile. */
    static final List<String> RADAR_TOWER = List.of("doodad.radar-tower-lattice", "doodad.radar-tower");
    /** A sat comms dish on its pedestal. */
    static final List<String> DISH = List.of("doodad.listening-dish");
    /** A storage tank: the vertical fuel tank, or a storage sphere meanwhile. */
    static final List<String> TANK = List.of("doodad.fuel-tank-vertical", "doodad.oxygen-sphere");
    /** An exhaust stack against the shed's back wall; a fluid tank stands for it meanwhile. */
    static final List<String> STACK = List.of("doodad.exhaust-stack", "doodad.industrial-fluid-tank");
    /** A generator set inside the plant's shed. */
    static final List<String> GENERATOR = List.of("ship-engineering.generator", "doodad.industrial-generator");

    /** The props of {@code kind}, in its frame. */
    static List<Item> items(Kind kind) {
        return switch (kind) {
            case DISH_COMPOUND -> List.of(
                    new Item(DISH, 1, 2, 4, 4), new Item(DISH, 8, 2, 4, 4),
                    new Item(List.of("doodad.industrial-cable-reel"), 6, 5, 1, 1));
            case RADAR_HARDSTAND -> List.of(
                    new Item(RADAR_TOWER, 9, 5, 3, 3),
                    new Item(List.of("doodad.industrial-switchboard"), 9, 2, 3, 1));
            case GENERATOR_PLANT -> List.of(
                    new Item(GENERATOR, 3, 2, 3, 3, true), new Item(GENERATOR, 7, 2, 3, 3, true),
                    new Item(STACK, 3, 6, 1, 1), new Item(STACK, 6, 6, 1, 1), new Item(STACK, 9, 6, 1, 1),
                    new Item(List.of("doodad.radiator-skid", "doodad.industrial-cable-reel"), 4, 7, 2, 1),
                    new Item(List.of("doodad.industrial-fluid-tank"), 11, 2, 1, 1),
                    new Item(List.of("doodad.industrial-cable-reel"), 11, 4, 1, 1));
            case TANK_FARM -> List.of(
                    new Item(TANK, 1, 4, 3, 3), new Item(TANK, 5, 4, 3, 3), new Item(TANK, 9, 4, 3, 3),
                    new Item(List.of("refinery.pump-skid", "doodad.industrial-drum-cluster"), 3, 2, 1, 1),
                    new Item(List.of("doodad.industrial-drum-cluster"), 10, 2, 1, 1));
        };
    }

    /** The building of {@code kind}, or null for one with none. */
    static Hut hut(Kind kind) {
        return switch (kind) {
            case RADAR_HARDSTAND -> new Hut(BuildingStamp.CONTROL_HUT, 1, 4);
            case GENERATOR_PLANT -> new Hut(BuildingStamp.PREFAB_SHORT_MODULE, 2, 1);
            default -> null;
        };
    }

    /**
     * A set piece laid at {@code (x0, y0)}, its gate on the side {@code gate} names (0 south, 1 east, 2 north,
     * 3 west: the quarter turns a building's south door is turned by to face it).
     */
    record Plan(Kind kind, int x0, int y0, int gate) {
        int width() { return (gate & 1) == 0 ? kind.along : kind.deep; }

        int height() { return (gate & 1) == 0 ? kind.deep : kind.along; }

        int x1() { return x0 + width() - 1; }

        int y1() { return y0 + height() - 1; }

        /** The world cell at {@code (u, v)} of the frame. */
        int[] cell(int u, int v) {
            return switch (gate) {
                case 0 -> new int[]{x0 + u, y0 + v};
                case 1 -> new int[]{x1() - v, y0 + u};
                case 2 -> new int[]{x1() - u, y1() - v};
                default -> new int[]{x0 + v, y1() - u};
            };
        }

        /** The world rectangle {x, y, width, height} of the frame's {@code along} by {@code deep} slot at {@code (u, v)}. */
        int[] rect(int u, int v, int along, int deep) {
            int[] a = cell(u, v), b = cell(u + along - 1, v + deep - 1);
            return new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]),
                    Math.abs(a[0] - b[0]) + 1, Math.abs(a[1] - b[1]) + 1};
        }

        /** Whether {@code (u, v)} is on the set piece's edge. */
        boolean edge(int u, int v) {
            return u == 0 || v == 0 || u == kind.along - 1 || v == kind.deep - 1;
        }

        /** Whether {@code (u, v)} is in the gate. */
        boolean gateway(int u, int v) {
            return v == 0 && Math.abs(u - kind.along / 2) <= GATE / 2;
        }

        /** Whether {@code (u, v)} is closed by the enclosure: on the edge and not in the gate. */
        boolean enclosed(int u, int v) {
            return kind.enclosure != Enclosure.NONE && edge(u, v) && !gateway(u, v);
        }

        /** The world step out of the set piece through its gate. */
        int[] out() {
            return switch (gate) {
                case 0 -> new int[]{0, -1};
                case 1 -> new int[]{1, 0};
                case 2 -> new int[]{0, 1};
                default -> new int[]{-1, 0};
            };
        }
    }

    /** Farthest a set piece stands off the building it serves, cells. UNTUNED. */
    static final int SITE_MOST = 30;

    /** The set pieces raised on a map, each {x0, y0, x1, y1}: their ground is theirs alone. */
    static final GenKey<List<int[]>> RAISED = GenKey.of("militarySetPieces");

    /** Whether a {@code w} by {@code h} footprint at {@code (x, y)} overlaps a set piece already raised. */
    static boolean raised(GenContext c, int x, int y, int w, int h) {
        List<int[]> raised = c.get(RAISED);
        if (raised == null) return false;
        for (int[] r : raised) {
            if (x <= r[2] && x + w - 1 >= r[0] && y <= r[3] && y + h - 1 >= r[1]) return true;
        }
        return false;
    }

    /**
     * The nearest open ground beside building {@code b} that takes a {@code kind} with its gate toward {@code b}, from
     * {@code gap} cells off its walls; null where none does. Tried on each side, centred on it first, then slid along.
     */
    static Plan site(GenContext c, PointOfInterest b, Kind kind, int gap) {
        for (int r = gap + 1; r <= SITE_MOST; r++) {
            // East and south first: the building's walls lean over its west and north sides.
            for (int gate : new int[]{3, 2, 0, 1}) {
                boolean across = (gate & 1) == 1;
                int w = across ? kind.deep : kind.along, h = across ? kind.along : kind.deep;
                int span = across ? b.bottom - b.top + 1 : b.right - b.left + 1;
                int length = across ? h : w;
                int centred = (across ? b.top : b.left) + (span - length) / 2;
                for (int slide = 0; slide <= length; slide++) {
                    for (int sign : slide == 0 ? new int[]{1} : new int[]{1, -1}) {
                        int along = centred + sign * slide;
                        int x0 = switch (gate) {
                            case 3 -> b.right + r;
                            case 1 -> b.left - r - w + 1;
                            default -> along;
                        };
                        int y0 = switch (gate) {
                            case 0 -> b.bottom + r;
                            case 2 -> b.top - r - h + 1;
                            default -> along;
                        };
                        Plan p = new Plan(kind, x0, y0, gate);
                        if (fits(c, b, p)) return p;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Whether {@code p} stands on open ground: every cell open and unbuilt, off the base's avenues, out from under
     * {@code b}'s leaning walls and on {@code b}'s side of any wall, a cell of clear ground round it, and its gate
     * opening onto ground that can be walked.
     */
    static boolean fits(GenContext c, PointOfInterest b, Plan p) {
        if (p.x0() < 2 || p.y0() < 2 || p.x1() >= c.width - 2 || p.y1() >= c.height - 2) return false;
        if (raised(c, p.x0() - 1, p.y0() - 1, p.width() + 2, p.height() + 2)) return false;
        for (int x = p.x0() - 1; x <= p.x1() + 1; x++) {
            for (int y = p.y0() - 1; y <= p.y1() + 1; y++) {
                boolean inside = x >= p.x0() && x <= p.x1() && y >= p.y0() && y <= p.y1();
                if (c.topology.hasBuildingKindHint(x, y) || c.topology.isWall(x, y) || c.grid.isDoorway(x, y)) {
                    return false;
                }
                if (inside && !PreparedGround.open(c, x, y)) return false;
            }
        }
        if (MilitaryWorks.onRoad(c, p.x0(), p.y0(), p.width(), p.height())
                || MilitaryWorks.hidden(b, p.x0(), p.y0(), p.width(), p.height())
                || MilitaryWorks.pastWall(c, b, p.x0(), p.y0(), Math.max(p.width(), p.height()))) {
            return false;
        }
        int[] out = p.out();
        for (int u = 0; u < p.kind().along; u++) {
            if (!p.gateway(u, 0)) continue;
            int[] g = p.cell(u, 0);
            if (!c.grid.isWalkable(g[0] + out[0], g[1] + out[1])) return false;
        }
        return true;
    }

    /** Raises {@code p}: its pad, its enclosure, its building and its props. */
    static void raise(GenContext c, Plan p) {
        List<int[]> raised = c.get(RAISED);
        if (raised == null) c.put(RAISED, raised = new ArrayList<>());
        raised.add(new int[]{p.x0(), p.y0(), p.x1(), p.y1()});
        int pad = c.topology.surfaceIndex(PAD);
        c.doodads.removeIf(d -> d.cellX >= p.x0() && d.cellX <= p.x1() && d.cellY >= p.y0() && d.cellY <= p.y1());
        Kind kind = p.kind();
        for (int u = 0; u < kind.along; u++) {
            for (int v = 0; v < kind.deep; v++) {
                int[] at = p.cell(u, v);
                if (kind.enclosure == Enclosure.BUND && p.edge(u, v)) {
                    PreparedGround.workedGround(c, at[0], at[1]);
                    continue;
                }
                c.topology.setGroundKind(at[0], at[1], GroundKind.SIDEWALK);
                if (pad >= 0) c.topology.setSurface(at[0], at[1], pad);
                c.topology.setNatureOverlayIndex(at[0], at[1], -1);
                c.markMadeGround(at[0], at[1]);
            }
        }
        for (int u = 0; u < kind.along; u++) {
            for (int v = 0; v < kind.deep; v++) {
                if (!p.enclosed(u, v)) continue;
                int[] at = p.cell(u, v);
                if (kind.enclosure == Enclosure.FENCE) {
                    boolean alongSide = v == 0 || v == kind.deep - 1;
                    PreparedGround.fence(c, at[0], at[1], alongSide == ((p.gate() & 1) == 0));
                } else {
                    bund(c, p, u, v);
                }
            }
        }
        Hut hut = hut(kind);
        if (hut != null) {
            int[] r = p.rect(hut.u(), hut.v(), hut.along(), hut.deep());
            int before = c.pois.size();
            hut.stamp().place(c, r[0], r[1], p.gate());
            for (int i = before; i < c.pois.size(); i++) {
                PointOfInterest building = c.pois.get(i);
                MilitaryWorks.oliveWalls(c, building);
                MilitaryWorks.roof(c, building, kind == Kind.GENERATOR_PLANT
                        ? CellTopology.RoofKind.HANGAR : CellTopology.RoofKind.HUT);
            }
        }
        for (Item item : items(kind)) stand(c, p, item);
    }

    /** One piece of the bund at {@code (u, v)}: the earth bank where the art has it, sandbags meanwhile. */
    private static void bund(GenContext c, Plan p, int u, int v) {
        int[] at = p.cell(u, v);
        boolean west = at[0] == p.x0(), east = at[0] == p.x1(), south = at[1] == p.y0(), north = at[1] == p.y1();
        int[] ns = north ? new int[]{0, 1} : south ? new int[]{0, -1} : null;
        int[] ew = east ? new int[]{1, 0} : west ? new int[]{-1, 0} : null;
        TileRegistry tiles = TileRegistry.installed();
        String earth = ns != null && ew != null ? "doodad.earth-bund-corner-" + (north ? "n" : "s") + (east ? "e" : "w")
                : "doodad.earth-bund-" + (ns != null ? "h" : "v");
        DoodadDef def = tiles == null ? null : tiles.doodad(earth);
        if (def != null) {
            if (!PreparedGround.open(c, at[0], at[1])) return;
            c.grid.setSeeThrough(at[0], at[1], true);
            c.topology.setFixture(at[0], at[1], true);
            c.doodads.add(new Doodad(at[0], at[1], def));
            c.markMadeGround(at[0], at[1]);
        } else if (ns != null && ew != null) {
            PreparedGround.sandbag(c, at[0], at[1], ns, ew);
        } else {
            PreparedGround.sandbag(c, at[0], at[1], ns != null ? ns : ew, null);
        }
    }

    /** The first of {@code item}'s props that the art has and that fits its slot, centred in it. */
    private static boolean stand(GenContext c, Plan p, Item item) {
        TileRegistry tiles = TileRegistry.installed();
        if (tiles == null) return false;
        int[] r = p.rect(item.u(), item.v(), item.along(), item.deep());
        for (String id : item.ids()) {
            DoodadDef def = tiles.doodad(id);
            if (def == null) continue;
            for (int turns = 0; turns < 2; turns++) {
                Doodad d = new Doodad(0, 0, def, turns);
                if (d.footprintCellsX > r[2] || d.footprintCellsY > r[3]) continue;
                int x = r[0] + (r[2] - d.footprintCellsX) / 2, y = r[1] + (r[3] - d.footprintCellsY) / 2;
                if (PreparedGround.prop(c, x, y, def, turns)) return true;
            }
        }
        return false;
    }
}

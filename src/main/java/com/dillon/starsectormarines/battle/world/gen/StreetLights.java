package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.door.WallGate;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.StreetDressing;
import com.dillon.starsectormarines.battle.world.gen.precinct.SealedTown;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.NightLighting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The light a town gives a dark map, so a night town reads as a living city ({@link #light}): street lamps along the
 * kerbs and at every junction ({@link #kerbLamps}), floodlit works docks and gates ({@link #yardLamps}), lit landing
 * pads ({@link #padLamps}) and a sealed town's envelope lights ({@link #envelopeLamps}), with the wall lamps below.
 * <p>The wall lamps: a warm pool on the pavement in front of a building's street wall,
 * one every {@link #EVERY} cells along it ({@link #townLamps}), and under each lamp bracket a main street hangs on its
 * shopfronts ({@link StreetDressing#WALL_LAMP}, {@link StreetDressing#WALL_LAMP_INDUSTRIAL}). No fixture is drawn
 * ({@link NightLighting.Fixture#GLOW}): the light is the lamp. Pure: read from the town as built, so a night laid
 * after the town lights the same streets.
 */
public final class StreetLights {

    /** The circuit the wall lamps hang on, powered wherever they are lit. */
    public static final String CIRCUIT = "street";
    /** How far a wall lamp's pool reaches, cells. UNTUNED. */
    static final float REACH = 3.4f;
    /** How far a street lamp's pool reaches, cells. UNTUNED. */
    static final float KERB_REACH = 4.2f;
    /** Cells between the street lamps along a kerb. UNTUNED. */
    static final int KERB_EVERY = 7;
    /** How far a works floodlight's pool reaches, cells. UNTUNED. */
    static final float FLOOD_REACH = 5.5f;
    /** Cells between the lights along a sealed town's envelope. UNTUNED. */
    static final int ENVELOPE_EVERY = 8;
    /** How far past the pavement cell's middle a wall lamp's pool is centred, away from its wall, cells. */
    private static final float OUT = .5f;
    /** Cells between the lamps along a building's street wall. UNTUNED. */
    static final int EVERY = 6;
    /** A town lamp no nearer a bracket's than this, cells, so a shopfront is not lit twice. */
    private static final float APART = 2.5f;
    /** A wall lamp's warm sodium light. */
    private static final float R = 1f, G = .78f, B = .5f;
    /** A street lamp's light, a paler sodium. */
    private static final float KR = 1f, KG = .85f, KB = .62f;
    /** A floodlight's and a pad's cold white. */
    private static final float FR = .86f, FG = .93f, FB = 1f;
    /** A pad's edge lights, amber. */
    private static final float PR = 1f, PG = .62f, PB = .22f;

    /**
     * Every light the town as built gives a night: its wall lamps and brackets ({@link #townLamps}), its street lamps
     * ({@link #kerbLamps}), its works ({@link #yardLamps}), its pads ({@link #padLamps}) and its envelope
     * ({@link #envelopeLamps}).
     */
    public static List<NightLighting.Lamp> light(MapResult map) {
        List<NightLighting.Lamp> out = new ArrayList<>(townLamps(map.topology, wallLamps(map.doodads)));
        out.addAll(kerbLamps(map.topology, out));
        out.addAll(yardLamps(map.industrialZones, map.wallGates));
        out.addAll(padLamps(map.landingPads));
        out.addAll(envelopeLamps(map.topology));
        return out;
    }

    /**
     * A street lamp on the pavement at the kerb every {@link #KERB_EVERY} cells along it, by its place on the map, and
     * one on every pavement corner where streets meet, none within {@link #APART} of a lamp in {@code near}. Where a
     * street has no pavement its own edge cells stand in for it.
     */
    public static List<NightLighting.Lamp> kerbLamps(CellTopology town, List<NightLighting.Lamp> near) {
        List<NightLighting.Lamp> out = new ArrayList<>();
        int w = town.getWidth(), h = town.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (town.isWall(x, y) || town.isRoofIntact(x, y)) continue;
                GroundKind k = town.getGroundKind(x, y);
                boolean pavement = k == GroundKind.SIDEWALK;
                if (!pavement && k != GroundKind.STREET) continue;
                // Which sides the carriageway lies on, from a pavement; which sides the verge does, from a street.
                int sx = 0, sy = 0, sides = 0;
                for (int d = 0; d < 4; d++) {
                    int dx = d == 0 ? 1 : d == 1 ? -1 : 0, dy = d == 2 ? 1 : d == 3 ? -1 : 0;
                    if (!town.inBounds(x + dx, y + dy)) continue;
                    GroundKind n = town.getGroundKind(x + dx, y + dy);
                    boolean kerb = pavement ? n == GroundKind.STREET
                            : n != GroundKind.STREET && n != GroundKind.SIDEWALK && !town.isWall(x + dx, y + dy)
                            && n != GroundKind.INDOOR && n != GroundKind.VOID && n != GroundKind.WATER;
                    if (!kerb) continue;
                    sides |= dx != 0 ? 1 : 2;
                    sx += dx;
                    sy += dy;
                }
                if (sides == 0) continue;
                boolean corner = sides == 3;
                int along = (sides & 1) != 0 ? y : x;
                if (!corner && Math.floorMod(along, KERB_EVERY) != KERB_EVERY / 2) continue;
                // At the kerb's edge, toward the carriageway (from a pavement) or away from the verge (on a street).
                float toward = pavement ? .35f : -.35f;
                float lx = x + .5f + Math.signum(sx) * toward, ly = y + .5f + Math.signum(sy) * toward;
                if (near(near, lx, ly) || near(out, lx, ly)) continue;
                out.add(sodium(lx, ly, KERB_REACH, KR, KG, KB));
            }
        }
        return out;
    }

    /**
     * The works lit for the night shift: a floodlight over each dock's apron, between its loading door and the
     * street, and one either side of every gate in a wall, inside and out.
     */
    public static List<NightLighting.Lamp> yardLamps(List<IndustrialZone> zones, List<WallGate> gates) {
        List<NightLighting.Lamp> out = new ArrayList<>();
        for (IndustrialZone zone : zones) {
            for (IndustrialZone.Dock dock : zone.docks()) {
                float lx = (dock.doorX() + dock.streetX()) / 2f + .5f, ly = (dock.doorY() + dock.streetY()) / 2f + .5f;
                if (!near(out, lx, ly)) out.add(flood(lx, ly, FLOOD_REACH));
            }
        }
        for (WallGate gate : gates) {
            for (int[] jamb : gate.jambs()) {
                for (int side = -1; side <= 1; side += 2) {
                    float lx = jamb[0] + .5f + gate.outX() * side * 1.2f, ly = jamb[1] + .5f + gate.outY() * side * 1.2f;
                    out.add(flood(lx, ly, FLOOD_REACH * .8f));
                }
            }
        }
        return out;
    }

    /** Each landing pad lit: a white wash over it and an amber light at each of its corners. */
    public static List<NightLighting.Lamp> padLamps(List<LandingPad> pads) {
        List<NightLighting.Lamp> out = new ArrayList<>();
        for (LandingPad pad : pads) {
            float cx = pad.centerX + .5f, cy = pad.centerY + .5f;
            out.add(flood(cx, cy, Math.max(3f, Math.max(pad.halfWidth, pad.halfHeight) * 1.3f)));
            for (int c = 0; c < 4; c++) {
                float lx = cx + (c % 2 == 0 ? -1 : 1) * (pad.halfWidth + .3f);
                float ly = cy + (c < 2 ? -1 : 1) * (pad.halfHeight + .3f);
                out.add(new NightLighting.Lamp(lx, ly, 2.2f, PR, PG, PB, CIRCUIT, NightLighting.Fixture.GLOW, 0f, 1f,
                        2f));
            }
        }
        return out;
    }

    /**
     * A sealed town's envelope lit along its length, every {@link #ENVELOPE_EVERY} cells by its place on the map, a
     * light on the open ground either side of its wall.
     */
    public static List<NightLighting.Lamp> envelopeLamps(CellTopology town) {
        List<NightLighting.Lamp> out = new ArrayList<>();
        int w = town.getWidth(), h = town.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                String surface = town.isWall(x, y) ? town.getSurfaceId(x, y) : null;
                if (surface == null || !surface.startsWith(SealedTown.ENVELOPE_WALL)) continue;
                for (int d = 0; d < 4; d++) {
                    int dx = d == 0 ? 1 : d == 1 ? -1 : 0, dy = d == 2 ? 1 : d == 3 ? -1 : 0;
                    int fx = x + dx, fy = y + dy;
                    if (!town.inBounds(fx, fy) || town.isWall(fx, fy) || town.isRoofIntact(fx, fy)) continue;
                    if (Math.floorMod(dx != 0 ? y : x, ENVELOPE_EVERY) != ENVELOPE_EVERY / 2) continue;
                    float lx = fx + .5f, ly = fy + .5f;
                    if (!near(out, lx, ly))
                        out.add(new NightLighting.Lamp(lx, ly, REACH, FR, FG, FB, CIRCUIT, NightLighting.Fixture.GLOW,
                                0f));
                }
            }
        }
        return out;
    }

    /** How hard a sodium lamp stains what it lights, so a street reads warm rather than as grey daylight. UNTUNED. */
    private static final float SODIUM_TINT = 1f;

    private static NightLighting.Lamp sodium(float x, float y, float reach, float r, float g, float b) {
        return new NightLighting.Lamp(x, y, reach, r, g, b, CIRCUIT, NightLighting.Fixture.GLOW, 0f, 1f, SODIUM_TINT);
    }

    private static NightLighting.Lamp flood(float x, float y, float reach) {
        return new NightLighting.Lamp(x, y, reach, FR, FG, FB, CIRCUIT, NightLighting.Fixture.GLOW, 0f);
    }

    private StreetLights() { }

    /** A pool under every wall lamp among {@code doodads}. */
    public static List<NightLighting.Lamp> wallLamps(List<Doodad> doodads) {
        List<NightLighting.Lamp> out = new ArrayList<>();
        for (Doodad d : doodads) {
            String id = d.definitionId();
            if (!StreetDressing.WALL_LAMP.equals(id) && !StreetDressing.WALL_LAMP_INDUSTRIAL.equals(id)) continue;
            out.add(sodium(d.cellX + .5f, d.cellY + .5f, REACH, R, G, B));
        }
        return out;
    }

    /**
     * A pool on the street, pavement or paved forecourt in front of every building wall that faces one (a room behind it), one every {@link #EVERY} cells
     * along the wall by its place on the map (so neighbouring buildings keep the rhythm), none in front of a window, and
     * none within {@link #APART} of a lamp in {@code brackets}. With the brackets' own pools after them.
     */
    public static List<NightLighting.Lamp> townLamps(CellTopology town, List<NightLighting.Lamp> brackets) {
        List<NightLighting.Lamp> out = new ArrayList<>();
        int w = town.getWidth(), h = town.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!town.isWall(x, y) || town.isWindow(x, y)) continue;
                for (int d = 0; d < 4; d++) {
                    int dx = d == 0 ? 1 : d == 1 ? -1 : 0, dy = d == 2 ? 1 : d == 3 ? -1 : 0;
                    int fx = x + dx, fy = y + dy;
                    if (!street(town, fx, fy) || town.isWall(fx, fy) || !indoor(town, x - dx, y - dy)) continue;
                    int along = dx != 0 ? y : x;
                    if (Math.floorMod(along, EVERY) != EVERY / 2) continue;
                    // Out over the pavement, a cell from the wall, so the pool falls on the street and not the roof.
                    float lx = fx + .5f + dx * OUT, ly = fy + .5f + dy * OUT;
                    if (near(brackets, lx, ly) || near(out, lx, ly)) continue;
                    out.add(sodium(lx, ly, REACH, R, G, B));
                }
            }
        }
        out.addAll(brackets);
        return out;
    }

    /** Whether the town paved this ground for people to walk or drive, open to the sky: a street, pavement or forecourt. */
    private static boolean street(CellTopology town, int x, int y) {
        if (!town.inBounds(x, y) || town.isRoofIntact(x, y)) return false;
        GroundKind k = town.getGroundKind(x, y);
        return k == GroundKind.STREET || k == GroundKind.SIDEWALK || k == GroundKind.TILE || k == GroundKind.BRICK
                || k == GroundKind.STRIPED;
    }

    /** Whether a room lies behind the wall, so it is a building's and not a garden's or a yard's. */
    private static boolean indoor(CellTopology town, int x, int y) {
        return town.inBounds(x, y) && town.getGroundKind(x, y) == GroundKind.INDOOR;
    }

    private static boolean near(List<NightLighting.Lamp> lamps, float x, float y) {
        for (NightLighting.Lamp l : lamps) {
            float dx = l.x() - x, dy = l.y() - y;
            if (dx * dx + dy * dy < APART * APART) return true;
        }
        return false;
    }

    /** {@code night} with {@code lamps} added and the street circuit powered. */
    public static NightLighting lit(NightLighting night, List<NightLighting.Lamp> lamps) {
        List<NightLighting.Lamp> all = new ArrayList<>(night.lamps());
        all.addAll(lamps);
        Set<String> powered = new HashSet<>(night.poweredCircuits());
        powered.add(CIRCUIT);
        return new NightLighting(night.darkness(), night.roofedDarkness(), all, powered);
    }
}

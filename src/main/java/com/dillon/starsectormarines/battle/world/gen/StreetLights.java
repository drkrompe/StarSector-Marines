package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.bsp.fill.StreetDressing;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.NightLighting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The light a town's wall lamps give a dark map: a warm pool on the pavement in front of a building's street wall,
 * one every {@link #EVERY} cells along it ({@link #townLamps}), and under each lamp bracket a main street hangs on its
 * shopfronts ({@link StreetDressing#WALL_LAMP}, {@link StreetDressing#WALL_LAMP_INDUSTRIAL}). No fixture is drawn
 * ({@link NightLighting.Fixture#GLOW}): the light is the lamp. Pure: read from the town as built, so a night laid
 * after the town lights the same streets.
 */
public final class StreetLights {

    /** The circuit the wall lamps hang on, powered wherever they are lit. */
    public static final String CIRCUIT = "street";
    /** How far a wall lamp's pool reaches, cells. UNTUNED. */
    static final float REACH = 2.8f;
    /** How far past the pavement cell's middle a wall lamp's pool is centred, away from its wall, cells. */
    private static final float OUT = .5f;
    /** Cells between the lamps along a building's street wall. UNTUNED. */
    static final int EVERY = 6;
    /** A town lamp no nearer a bracket's than this, cells, so a shopfront is not lit twice. */
    private static final float APART = 2.5f;
    /** A wall lamp's warm sodium light. */
    private static final float R = 1f, G = .78f, B = .5f;

    private StreetLights() { }

    /** A pool under every wall lamp among {@code doodads}. */
    public static List<NightLighting.Lamp> wallLamps(List<Doodad> doodads) {
        List<NightLighting.Lamp> out = new ArrayList<>();
        for (Doodad d : doodads) {
            String id = d.definitionId();
            if (!StreetDressing.WALL_LAMP.equals(id) && !StreetDressing.WALL_LAMP_INDUSTRIAL.equals(id)) continue;
            out.add(new NightLighting.Lamp(d.cellX + .5f, d.cellY + .5f, REACH, R, G, B, CIRCUIT,
                    NightLighting.Fixture.GLOW, 0f));
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
                    out.add(new NightLighting.Lamp(lx, ly, REACH, R, G, B, CIRCUIT, NightLighting.Fixture.GLOW, 0f));
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

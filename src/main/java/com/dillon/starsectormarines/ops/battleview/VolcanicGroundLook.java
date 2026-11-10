package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainSurface;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a volcanic world's ground glows and where its ash lies ({@code world-surface-palette.md}, Volcanic), GL-free:
 * the rules {@link VolcanicGroundRenderSystem} draws. Presentation only: nothing here changes a cell.
 *
 * <p>Marks stand only on <b>open wild ground</b>: stone or sand nobody built on, paved, roofed, routed a road over,
 * raised or set a fixture or surface on, with the same around it, so a crack never runs under a wall or a road.
 * They sit on a jittered lattice, and a slow noise gathers them: fissures and vents into active fields, ash into
 * drifts across the hollows between. Coordinate-hashed, so a map draws the same marks every time.
 */
final class VolcanicGroundLook {

    /** What a mark is: a glowing crack, a vent, a scatter of embers, an ash drift. */
    enum Kind { FISSURE, VENT, EMBERS, ASH }

    /** One mark: its kind, which of its sprites, its centre in cells, its side in cells and its turn in degrees. */
    record Mark(Kind kind, int sprite, float x, float y, float cells, float angle) { }

    /** Sprites per kind, as shipped under {@code graphics/battle/volcanic/}. */
    static final int FISSURES = 4, VENTS = 2, EMBERS = 2, ASH_DRIFTS = 4;
    /** Lattice pitch of the glowing marks and of the drifts, cells. */
    static final int GLOW_PITCH = 6, ASH_PITCH = 9;
    /** How strong the activity noise must be for a glowing mark, and for a drift. */
    static final float GLOW_FROM = .56f, ASH_FROM = .5f;
    /** Noise scales, cells: how wide an active field and a drifted hollow run. */
    static final float GLOW_SCALE = 15f, ASH_SCALE = 19f;
    /** How many cells about a mark's centre must be open wild ground: half its footing. */
    static final int GLOW_FOOTING = 1, ASH_FOOTING = 2;

    private VolcanicGroundLook() { }

    /** Every mark on this map, ash first so the glow draws over it. */
    static List<Mark> marks(CellTopology topology) {
        List<Mark> out = new ArrayList<>();
        int w = topology.getWidth(), h = topology.getHeight();
        for (int gy = 0; gy * ASH_PITCH < h; gy++) {
            for (int gx = 0; gx * ASH_PITCH < w; gx++) {
                int hsh = hash(gx, gy, 0x41534800);
                int x = gx * ASH_PITCH + Math.floorMod(hsh, ASH_PITCH);
                int y = gy * ASH_PITCH + Math.floorMod(hsh >>> 8, ASH_PITCH);
                float n = noise(x / ASH_SCALE, y / ASH_SCALE, 0x41534801);
                if (n < ASH_FROM || !footing(topology, x, y, ASH_FOOTING)) continue;
                out.add(new Mark(Kind.ASH, Math.floorMod(hsh >>> 16, ASH_DRIFTS), x + .5f, y + .5f,
                        8f + 6f * (n - ASH_FROM) / (1f - ASH_FROM), Math.floorMod(hsh >>> 21, 360)));
            }
        }
        for (int gy = 0; gy * GLOW_PITCH < h; gy++) {
            for (int gx = 0; gx * GLOW_PITCH < w; gx++) {
                int hsh = hash(gx, gy, 0x4c415641);
                int x = gx * GLOW_PITCH + Math.floorMod(hsh, GLOW_PITCH);
                int y = gy * GLOW_PITCH + Math.floorMod(hsh >>> 8, GLOW_PITCH);
                float n = noise(x / GLOW_SCALE, y / GLOW_SCALE, 0x4c415642);
                if (n < GLOW_FROM || !footing(topology, x, y, GLOW_FOOTING)) continue;
                int roll = Math.floorMod(hsh >>> 16, 10);
                // Mostly cracks; a vent where the field runs hottest; embers between.
                Kind kind = roll < 6 ? Kind.FISSURE : roll < 8 || n < .7f ? Kind.EMBERS : Kind.VENT;
                int sprites = kind == Kind.FISSURE ? FISSURES : kind == Kind.VENT ? VENTS : EMBERS;
                float cells = kind == Kind.FISSURE ? 3f : kind == Kind.VENT ? 1.6f : 2.4f;
                out.add(new Mark(kind, Math.floorMod(hsh >>> 20, sprites), x + .5f, y + .5f, cells,
                        Math.floorMod(hsh >>> 5, 360)));
            }
        }
        return out;
    }

    /** Whether every cell within {@code r} of this one is open wild ground. */
    static boolean footing(CellTopology topology, int x, int y, int r) {
        for (int yy = y - r; yy <= y + r; yy++) {
            for (int xx = x - r; xx <= x + r; xx++) if (!open(topology, xx, yy)) return false;
        }
        return true;
    }

    /** Open wild ground: on the map, stone or sand, and nothing built, laid, raised or set on it. */
    static boolean open(CellTopology topology, int x, int y) {
        if (x < 0 || y < 0 || x >= topology.getWidth() || y >= topology.getHeight()) return false;
        CellTopology.GroundKind kind = topology.getGroundKind(x, y);
        if (kind != CellTopology.GroundKind.STONE && kind != CellTopology.GroundKind.SAND) return false;
        if (topology.isWall(x, y) || topology.isFixture(x, y) || topology.getBuildingId(x, y) != 0
                || topology.getSurface(x, y) != 0) return false;
        if (topology.roadSurface() != null && topology.roadSurface().underlay(x, y) != null) return false;
        TerrainSurface terrain = topology.terrainSurface();
        if (terrain != null) {
            for (int layer = 0; layer < TerrainSurface.LAYERS; layer++) if (terrain.patch(x, y, layer) != null) return false;
        }
        return true;
    }

    /** A smooth value noise in [0, 1], bilinear over hashed lattice points. */
    static float noise(float x, float y, int salt) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float a = unit(x0, y0, salt), b = unit(x0 + 1, y0, salt);
        float c = unit(x0, y0 + 1, salt), d = unit(x0 + 1, y0 + 1, salt);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    private static float unit(int x, int y, int salt) {
        return (hash(x, y, salt) >>> 8) / (float) (1 << 24);
    }

    static int hash(int x, int y, int salt) {
        int h = x * 0x27d4eb2d ^ y * 0x165667b1 ^ salt;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        return h ^ h >>> 16;
    }
}

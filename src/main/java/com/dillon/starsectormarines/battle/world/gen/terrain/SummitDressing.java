package com.dillon.starsectormarines.battle.world.gen.terrain;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.PatchField;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.nature.MineralFormation;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.Random;

/** Rocks and unattended equipment on permanent summits; no new walkable upper level. */
public final class SummitDressing {
    private final GenContext context;
    private final boolean[] occupied;

    public SummitDressing(GenContext context) {
        this.context = context;
        occupied = new boolean[context.width * context.height];
        for (var prop : context.doodads) reserve(prop);
    }

    /** A small remote relay cluster. The complete site must stand back from the cliff rim. */
    public void relay(int x, int y) {
        if (!fits(x - 1, y - 1, 7, 7)) throw new IllegalArgumentException("Relay needs a clear summit shelf");
        place(x, y, "doodad.military-radar-dish", 0);
        place(x + 3, y + 3, "doodad.military-radar-dish", 1);
        place(x + 3, y, "doodad.industrial-generator", 0);
        place(x + 1, y + 3, "doodad.industrial-cable-reel", 0);
        for (int yy = y - 1; yy <= y + 5; yy++) for (int xx = x - 1; xx <= x + 5; xx++)
            occupied[yy * context.width + xx] = true;
    }

    public void scatter(SurfacePalette palette) {
        var random = new Random(context.seed ^ 0x53554d4d4954L);
        var clusters = new PatchField(context.seed ^ 0x4352414753L, 11);
        var pool = palette == SurfacePalette.FROZEN
                ? new MineralFormation[]{MineralFormation.BOULDERS, MineralFormation.SPIRES,
                    MineralFormation.BOULDERS, MineralFormation.ICE_PINNACLES}
                : new MineralFormation[]{MineralFormation.BOULDERS, MineralFormation.SPIRES};
        for (int y = 2; y < context.height - 5; y += 3) for (int x = 2; x < context.width - 5; x += 3) {
            if (clusters.sample(x, y) > .57f || random.nextFloat() > .32f) continue;
            var shape = pool[random.nextInt(pool.length)];
            int turns = random.nextInt(4);
            if (fits(x - 1, y - 1, shape.width(turns) + 2, shape.height(turns) + 2))
                place(x, y, shape.id(), turns);
        }
        var registry = TileRegistry.installed();
        String[] stones = {"nature.rock-small-1", "nature.rock-small-2", "nature.rock-medium-1"};
        for (int y = 0; y < context.height; y++) for (int x = 0; x < context.width; x++) {
            if (!fits(x, y, 1, 1)) continue;
            float chance = clusters.sample(x, y) < .65f ? .22f : .035f;
            if (random.nextFloat() < chance)
                context.topology.setNatureOverlayIndex(x, y, registry.tile(stones[random.nextInt(stones.length)]).index);
        }
    }

    private boolean fits(int x, int y, int width, int height) {
        var terrain = context.topology.terrainSurface();
        if (terrain == null || x < 0 || y < 0 || x + width > context.width || y + height > context.height) return false;
        for (int yy = y; yy < y + height; yy++) for (int xx = x; xx < x + width; xx++)
            if (!terrain.isFullSummit(xx, yy) || occupied[yy * context.width + xx]) return false;
        return true;
    }

    private void place(int x, int y, String id, int turns) {
        var def = TileRegistry.installed().doodad(id);
        if (def == null) throw new IllegalStateException("Missing summit prop: " + id);
        var prop = new Doodad(x, y, def, turns);
        if (!fits(x, y, prop.footprintCellsX, prop.footprintCellsY))
            throw new IllegalArgumentException("Summit prop overhangs its shelf: " + id);
        context.doodads.add(prop);
        reserve(prop);
    }

    private void reserve(Doodad prop) {
        for (int y = Math.max(0, prop.cellY - 1); y < Math.min(context.height, prop.cellY + prop.footprintCellsY + 1); y++)
            for (int x = Math.max(0, prop.cellX - 1); x < Math.min(context.width, prop.cellX + prop.footprintCellsX + 1); x++)
                occupied[y * context.width + x] = true;
    }
}

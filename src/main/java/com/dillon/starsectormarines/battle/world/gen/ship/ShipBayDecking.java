package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Replaces complete legacy 5x7 bay paving with the accepted open-mouth service pad. */
public final class ShipBayDecking {
    private ShipBayDecking() {}

    public static void apply(GenContext ctx) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return;
        DoodadDef art = registry.doodad("auto-shop.bay");
        if (art == null) return;
        List<DoodadDef> old = Stream.of(registry.doodad("doodad.fl-striped-yellow"),
                registry.doodad("doodad.fl-grate-1"), registry.doodad("doodad.fl-grate-2"))
                .filter(Objects::nonNull).toList();
        List<Doodad> pads = new ArrayList<>();
        for (Gantry berth : ctx.gantries) {
            Doodad pad = pad(berth, art);
            if (pad == null) continue;
            List<Doodad> replaced = ctx.doodads.stream()
                    .filter(d -> pad.occupiesCell(d.cellX, d.cellY) && legacy(d, old)).toList();
            // A custom or shorter bay keeps its own paving. Never cover an authored partial layout.
            if (replaced.size() != 35) continue;
            ctx.doodads.removeAll(replaced);
            pads.add(pad);
        }
        ctx.doodads.addAll(0, pads);
    }

    static Doodad pad(Gantry berth, DoodadDef art) {
        if (berth.holds != Gantry.Holds.MACHINE) return null;
        boolean vertical = berth.facing.dy != 0;
        if (berth.width() != (vertical ? 3 : 6) || berth.height() != (vertical ? 6 : 3)) return null;
        int x = berth.left() - (berth.facing == Gantry.Facing.WEST ? 0 : 1);
        int y = berth.bottom() - (berth.facing == Gantry.Facing.SOUTH ? 0 : 1);
        int turn = switch (berth.facing) {
            case SOUTH -> 0;
            case EAST -> 1;
            case NORTH -> 2;
            case WEST -> 3;
        };
        return new Doodad(x, y, art, turn);
    }

    private static boolean legacy(Doodad d, List<DoodadDef> old) {
        if (d.cover != 0 || d.footprintCellsX != 1 || d.footprintCellsY != 1) return false;
        return old.stream().anyMatch(def -> def.sheetPath.equals(d.sheetPath)
                && def.col == d.tile.col && def.row == d.tile.row);
    }
}

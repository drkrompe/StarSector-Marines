package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartLayout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The cordon's guns on its forward line (`heart-nouns.md`, Cordon's works): the line along the cordon ridge's crest
 * has held the bloom for weeks, so it is armed. An allied gun stands in every nest the line laid, a heavy machine gun
 * with every third a Hephaestus, and a flak emplacement a few cells behind every other nest, so the ridge reads as a
 * front line and its guns shoot what comes up to it. The guns are the military's ({@code ALLY}): they never end the
 * battle, and the flak meets the flier law as the base's does.
 */
public final class HeartRidgeGuns {

    private HeartRidgeGuns() {}

    /** Every this many nests along the front, counted west to east (then south to north), is a Hephaestus. */
    static final int HEAVY_EVERY = 3;
    /** Every this many nests has a flak emplacement behind it. */
    static final int FLAK_EVERY = 2;
    /** Cells behind the line, toward the start, that a nest's flak is dug. */
    static final int FLAK_BEHIND = 3;
    /** Cells round its place a flak emplacement may move to find open ground. */
    static final int FLAK_SEARCH = 4;

    /** Seats the forward line's guns and flak; returns every gun seated, west to east. Call once at setup. */
    public static List<Long> man(BattleSimulation sim, LaidLayout layout, Form form) {
        List<int[]> nests = nests(layout, form);
        List<Long> guns = new ArrayList<>();
        NavigationGrid grid = sim.getGrid();
        int back = form.advancesAlongY() ? -1 : 0, backX = form.advancesAlongY() ? 0 : -1;
        for (int i = 0; i < nests.size(); i++) {
            int[] nest = nests.get(i);
            if (grid.isWalkable(nest[0], nest[1])) {
                guns.add(ColonyMilitia.seatEmplacement(sim, nest, "Ridge gun " + (i + 1),
                        i % HEAVY_EVERY == HEAVY_EVERY - 1 ? TurretCatalogRegistry.HEPHAESTUS_STRUCTURE_ID
                                : TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID));
            }
            if (i % FLAK_EVERY != 0) continue;
            int[] pit = openPit(grid, nest[0] + backX * FLAK_BEHIND, nest[1] + back * FLAK_BEHIND);
            if (pit != null) {
                guns.add(ColonyMilitia.seatEmplacement(sim, pit, "Ridge flak " + (i / FLAK_EVERY + 1),
                        TurretCatalogRegistry.ANTI_AIR_STRUCTURE_ID));
            }
        }
        return guns;
    }

    /** The forward line's nests, every run of it, ordered across the front. */
    static List<int[]> nests(LaidLayout layout, Form form) {
        List<int[]> out = new ArrayList<>();
        for (Laid laid : layout.pieces()) {
            if (laid.kind() == Laid.Kind.LINE && laid.isLaid() && laid.name().startsWith(HeartLayout.FORWARD_LINE)) {
                out.addAll(laid.nests());
            }
        }
        out.sort(Comparator.comparingInt(c -> form.advancesAlongY() ? c[0] : c[1]));
        return out;
    }

    /** The open cell nearest {@code (x0, y0)} within {@link #FLAK_SEARCH} with all four sides walkable, or null. */
    private static int[] openPit(NavigationGrid grid, int x0, int y0) {
        for (int r = 0; r <= FLAK_SEARCH; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = x0 + dx, y = y0 + dy;
                    if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y)) continue;
                    int open = 0;
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        if (grid.inBounds(x + d[0], y + d[1]) && grid.isWalkable(x + d[0], y + d[1])) open++;
                    }
                    if (open == 4) return new int[]{x, y};
                }
            }
        }
        return null;
    }
}

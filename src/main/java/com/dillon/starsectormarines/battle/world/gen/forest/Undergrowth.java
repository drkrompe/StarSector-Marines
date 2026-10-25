package com.dillon.starsectormarines.battle.world.gen.forest;

import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy.Lobe;
import com.dillon.starsectormarines.battle.world.gen.nature.NaturalPropShape;
import java.util.List;

/**
 * Forest-floor growth between the trees: closed to bodies, flattened by a heavy-lift hull (`convoy-nouns.md`, heavy
 * tracks). A dense one also stops sight; a see-through one is a tangle a rifleman looks and shoots across but cannot
 * walk through, and it gives no edge cover. The lobes own the overhead silhouette and the closed cells alike, so the
 * art cannot change the footprint.
 */
public enum Undergrowth implements NaturalPropShape {
    BRAMBLE("bramble", 3, 2, true, 0x5B5A34, List.of(
            new Lobe(.75, 1.05, .78, .72), new Lobe(1.55, .85, .80, .78), new Lobe(2.30, 1.10, .72, .74),
            new Lobe(1.20, 1.35, .62, .52))),
    BRAMBLE_SMALL("bramble-small", 2, 1, true, 0x5F5C36, List.of(
            new Lobe(.62, .50, .60, .46), new Lobe(1.36, .52, .62, .44))),
    THICKET("thicket", 3, 3, false, 0x4B6238, List.of(
            new Lobe(1.50, 1.50, 1.05, 1.05), new Lobe(.85, .95, .70, .66), new Lobe(2.15, .90, .68, .70),
            new Lobe(2.25, 2.05, .70, .68), new Lobe(.90, 2.15, .66, .70), new Lobe(1.55, .55, .55, .50),
            new Lobe(1.50, 2.50, .55, .48))),
    THICKET_SMALL("thicket-small", 2, 2, false, 0x52673A, List.of(
            new Lobe(1.00, 1.00, .80, .80), new Lobe(.62, .66, .52, .50), new Lobe(1.40, 1.36, .52, .52),
            new Lobe(1.36, .64, .50, .50), new Lobe(.64, 1.38, .50, .50))),
    BRACKEN("bracken", 4, 2, false, 0x5E7A3C, List.of(
            new Lobe(.80, 1.00, .80, .88), new Lobe(1.65, .95, .72, .84), new Lobe(2.45, 1.05, .74, .86),
            new Lobe(3.25, .98, .74, .84))),
    SAPLINGS("saplings", 2, 2, true, 0x6C8442, List.of(
            new Lobe(.62, .66, .52, .52), new Lobe(1.40, .74, .50, .50), new Lobe(.98, 1.40, .54, .52)));

    public final String id;
    public final int width, height, color;
    /** Whether sight and direct fire pass the closed cells. */
    public final boolean seeThrough;
    private final List<Lobe> lobes;

    Undergrowth(String name, int width, int height, boolean seeThrough, int color, List<Lobe> lobes) {
        this.id = "forest." + name; this.width = width; this.height = height;
        this.seeThrough = seeThrough; this.color = color; this.lobes = lobes;
    }

    public List<Lobe> lobes() { return lobes; }
    public String id() { return id; }
    public int width() { return width; }
    public int height() { return height; }
    /** Light: a few bursts or one grenade clears it, where a tree takes 200. */
    public int hitPoints() { return 60; }
    @Override public boolean undergrowth() { return true; }

    public boolean blocks(int x, int y, int turns) {
        if (x < 0 || y < 0 || x >= width(turns) || y >= height(turns)) return false;
        int sx, sy;
        switch (Math.floorMod(turns, 4)) {
            case 1 -> { sx = y; sy = height - 1 - x; }
            case 2 -> { sx = width - 1 - x; sy = height - 1 - y; }
            case 3 -> { sx = width - 1 - y; sy = x; }
            default -> { sx = x; sy = y; }
        }
        for (Lobe lobe : lobes) if (lobe.intersectsCell(sx, sy)) return true;
        return false;
    }

    public static Undergrowth find(String id) {
        for (Undergrowth u : values()) if (u.id.equals(id)) return u;
        return null;
    }
}

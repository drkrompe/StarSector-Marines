package com.dillon.starsectormarines.battle.world.gen.forest;

import com.dillon.starsectormarines.battle.world.gen.nature.NaturalPropShape;
import java.util.ArrayList;
import java.util.List;

/** The authoring silhouette and its conservative whole-cell obstruction share these lobes. */
public enum TreeCanopy implements NaturalPropShape {
    OAK("oak", 5, 5, 8, 0x526B39),
    OAK_SMALL("oak-small", 4, 4, 7, 0x64783D),
    BIRCH("birch", 5, 4, 9, 0x7A8748),
    PINE("pine", 3, 3, 9, 0x365E46),
    PINE_LARGE("pine-large", 4, 4, 11, 0x3F6750),
    CEDAR("cedar", 4, 6, 10, 0x536C45);

    public record Lobe(double x, double y, double rx, double ry) {
        public boolean intersectsCell(int x, int y) {
            double dx = Math.max(x - this.x, Math.max(0, this.x - (x + 1))) / rx;
            double dy = Math.max(y - this.y, Math.max(0, this.y - (y + 1))) / ry;
            return dx * dx + dy * dy <= 1;
        }
    }
    public final String id;
    public final int width, height, color;
    private final List<Lobe> lobes;

    TreeCanopy(String name, int width, int height, int count, int color) {
        this.id = "forest." + name; this.width = width; this.height = height; this.color = color;
        double rx = width * .41, ry = height * .41;
        List<Lobe> shapes = new ArrayList<>();
        shapes.add(new Lobe(width * .5, height * .5, rx * .84, ry * .84));
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2 / count + .19;
            double size = .30 + (i % 3) * .025;
            shapes.add(new Lobe(width * .5 + Math.cos(angle) * rx * .63,
                    height * .5 + Math.sin(angle) * ry * .63, rx * size, ry * size));
        }
        lobes = List.copyOf(shapes);
    }

    public List<Lobe> lobes() { return lobes; }
    public String id() { return id; }
    public int width() { return width; }
    public int height() { return height; }
    public int hitPoints() { return 200; }
    public int width(int turns) { return (turns & 1) == 0 ? width : height; }
    public int height(int turns) { return (turns & 1) == 0 ? height : width; }

    /** Any canopy intersection closes the cell, including the thin tips of branches/leaves. */
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
}

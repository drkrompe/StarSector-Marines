package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How the surfaces are grouped for browsing: where a thing is, then what kind
 * of thing it is.
 *
 * <p>Eighteen surfaces in one alphabetical grid is a list to be scanned rather
 * than a place to look something up. Somebody who wants a wall is not choosing
 * between a wall and sand — they are in the structure half of the problem, and
 * within it they want the walls. Two levels answer that in two glances.
 *
 * <p><b>This is a browsing aid, not an authority.</b> The game's own vocabulary
 * is {@code GroundKind} and {@code SurfaceRole}, and it draws no such
 * distinction: nothing resolves differently because a surface is filed under
 * Outdoors. Keeping the grouping here rather than on those enums is deliberate,
 * so that a second taxonomy cannot be mistaken for one the generator consults.
 */
public final class SurfaceCategory {

    private SurfaceCategory() {}

    /** Where the surface is: inside the built thing, or out on the ground. */
    public enum Setting {
        STRUCTURE("Structure"),
        OUTDOORS("Outdoors");

        private final String label;

        Setting(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** What kind of surface it is, within its setting. */
    public enum Kind {
        WALL("Walls"),
        FLOOR("Floors"),
        OTHER("Other");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** One heading in the browser: a setting and a kind within it. */
    public record Section(Setting setting, Kind kind) {

        public String label() {
            return setting.label() + " · " + kind.label();
        }
    }

    /**
     * Where each surface is filed.
     *
     * <p>Written out rather than derived. Whether {@code RUBBLE} is a floor is a
     * judgement about what it is — a floor that used to be a wall — and no rule
     * over the name or the enum would reach it.
     *
     * <p>Every surface must be here. A missing one still browses, filed under
     * Outdoors / Other by {@link #of}, but that fallback exists so the panel
     * cannot break rather than as somewhere for new surfaces to accumulate
     * unnoticed — {@code SurfaceCategoryTest} fails the build for one that is
     * not filed. Adding a {@code GroundKind} is a decision about what the thing
     * is, and this is where that decision is written down.
     */
    private static final Map<String, Section> FILED = filed();

    private static Map<String, Section> filed() {
        Map<String, Section> filed = new LinkedHashMap<>();
        // Inside the built thing.
        filed.put("WALL", new Section(Setting.STRUCTURE, Kind.WALL));
        filed.put("INDOOR", new Section(Setting.STRUCTURE, Kind.FLOOR));
        filed.put("RUBBLE", new Section(Setting.STRUCTURE, Kind.FLOOR));
        filed.put("TILE", new Section(Setting.STRUCTURE, Kind.FLOOR));
        filed.put("DOOR_OPEN", new Section(Setting.STRUCTURE, Kind.OTHER));
        filed.put("ROOF", new Section(Setting.STRUCTURE, Kind.OTHER));
        // Out on the ground: made surfaces first, then natural ones.
        filed.put("STREET", new Section(Setting.OUTDOORS, Kind.FLOOR));
        filed.put("SIDEWALK", new Section(Setting.OUTDOORS, Kind.FLOOR));
        filed.put("COURTYARD", new Section(Setting.OUTDOORS, Kind.FLOOR));
        filed.put("STRIPED", new Section(Setting.OUTDOORS, Kind.FLOOR));
        filed.put("BRICK", new Section(Setting.OUTDOORS, Kind.FLOOR));
        filed.put("GRASS", new Section(Setting.OUTDOORS, Kind.FLOOR));
        filed.put("DIRT", new Section(Setting.OUTDOORS, Kind.FLOOR));
        filed.put("STONE", new Section(Setting.OUTDOORS, Kind.FLOOR));
        filed.put("SAND", new Section(Setting.OUTDOORS, Kind.FLOOR));
        filed.put("SNOW", new Section(Setting.OUTDOORS, Kind.FLOOR));
        // Neither a wall nor something to stand on.
        filed.put("LZ_MARKER", new Section(Setting.OUTDOORS, Kind.OTHER));
        filed.put("WATER", new Section(Setting.OUTDOORS, Kind.OTHER));
        return Map.copyOf(filed);
    }

    /** Every surface this table files, for a test that nothing is missing. */
    public static Set<String> filedNames() {
        return FILED.keySet();
    }

    /** Which section {@code surfaceName} is filed under. */
    public static Section of(String surfaceName) {
        return FILED.getOrDefault(surfaceName, new Section(Setting.OUTDOORS, Kind.OTHER));
    }

    /**
     * The sections these surfaces occupy, in reading order, skipping any that
     * nothing falls into.
     */
    public static List<Section> sectionsOf(List<SurfaceCatalog.Purpose> purposes) {
        Set<Section> present = new LinkedHashSet<>();
        for (Setting setting : Setting.values()) {
            for (Kind kind : Kind.values()) {
                Section section = new Section(setting, kind);
                for (SurfaceCatalog.Purpose purpose : purposes) {
                    if (of(purpose.name()).equals(section)) {
                        present.add(section);
                        break;
                    }
                }
            }
        }
        return List.copyOf(present);
    }
}

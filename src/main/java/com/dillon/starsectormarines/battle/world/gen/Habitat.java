package com.dillon.starsectormarines.battle.world.gen;

import java.util.Map;

/**
 * How a settlement is built to live where it stands ({@code mapgen-nouns.md}, Habitat): the built counterpart to
 * {@link SurfacePalette} and {@link Climate}, which say what the ground is made of and what the air does. A town on an
 * irradiated world is not the open town of a garden world with grey ground under it; it is shielded, and a town that
 * cannot breathe its air is sealed. {@link HabitatZoning} owns which world builds which.
 *
 * <p>Presentation only. A habitat changes what a building's walls are made of and what stands in its open ground,
 * never where a wall, door, window or road is: walkability, cover, sight and every rule that reads a wall are the
 * town's own, so a seed lays the same town plan in every habitat.
 */
public enum Habitat {

    /** The open town: masonry, clay roofs, lawns and benches. A world the colonists can live on in the open. */
    OPEN(Map.of()),

    /**
     * Pressurised blocks in white ceramic panels for air that kills or is not there: masonry houses are rebuilt as
     * panel blocks, the olive prefabs stay as they are, and every outside door is a lock with its threshold marked.
     * Parks are paved over as service courts holding the life support kept outside in case of a leak.
     */
    SEALED(Map.of("colony.masonry-wall", "colony.ceramic-wall")),

    /**
     * Compact heated blocks for a frozen world: brick and steel that hold heat, no white ceramic panel blocks. Its
     * people still walk between buildings, so parks and doors are the open town's.
     */
    INSULATED(Map.of("colony.ceramic-wall", "colony.masonry-wall")),

    /**
     * Shielded works for hard radiation at the surface: every building's shell is thick poured concrete under a flat
     * slab, outside doors are decontamination locks with their thresholds marked, and parks are paved over as service
     * courts. The walls are the installation's light concrete in its brutalist works look ({@link #SHIELD_WALL}), a
     * block of its own, so the town is never marked as a fort's lot. Never the dark concrete: that is a compound's
     * perimeter, which the Cordon's rules read by its surface.
     */
    SHIELDED(Map.of("colony.masonry-wall", Habitat.SHIELD_WALL,
            "colony.ceramic-wall", Habitat.SHIELD_WALL,
            "colony.olive-wall", Habitat.SHIELD_WALL));

    /** What a shielded town's walls are poured as. */
    public static final String SHIELD_WALL = "installation.light-concrete-wall@brutalist";

    private final Map<String, String> walls;

    Habitat(Map<String, String> walls) {
        this.walls = walls;
    }

    /**
     * The wall surface a town wall of {@code surfaceId} is built of in this habitat: the same id where the habitat
     * keeps it, including every wall that is no town building's (a fort's works, an installation, a story map's).
     */
    public String wallFor(String surfaceId) {
        if (surfaceId == null) return null;
        return walls.getOrDefault(surfaceId, surfaceId);
    }

    /** Whether nobody lives in the open: no lawns or benches, outside doors are locks. */
    public boolean enclosed() {
        return this == SEALED || this == SHIELDED;
    }
}

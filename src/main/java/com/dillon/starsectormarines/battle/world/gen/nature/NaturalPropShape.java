package com.dillon.starsectormarines.battle.world.gen.nature;

import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.gen.forest.Undergrowth;

/** Code-owned silhouette shared by art, placement, observation and whole-object destruction. */
public interface NaturalPropShape {
    String id();
    int width();
    int height();
    int hitPoints();
    boolean blocks(int x, int y, int turns);
    /** Whether a heavy-lift hull flattens this object by driving into it while it stands. */
    default boolean undergrowth() { return false; }
    default int width(int turns) { return (turns & 1) == 0 ? width() : height(); }
    default int height(int turns) { return (turns & 1) == 0 ? height() : width(); }

    /**
     * The id suffix of a shape's dry-country art (the grey scrub a desert basin's rim grows): the same silhouette,
     * footprint and states, drawn from the {@code dry-scrub-*} sheets. {@code forest.oak-dry}, and its states
     * {@code forest.oak-dry-damaged}, {@code -destroyed} and {@code -blight-k}.
     */
    String DRY = Flora.DRY.suffix;

    /** The id a shape's art is placed under: its own, or its dry-country twin's. */
    static String placedId(NaturalPropShape shape, boolean dry) {
        return (dry ? Flora.DRY : Flora.TEMPERATE).placedId(shape);
    }

    /** The shape an intact placement draws, its own art or any flora's twin ({@link Flora}); null for anything else. */
    static NaturalPropShape find(String id) {
        id = Flora.baseId(id);
        for (var tree : TreeCanopy.values()) if (tree.id.equals(id)) return tree;
        for (var growth : Undergrowth.values()) if (growth.id.equals(id)) return growth;
        for (var mineral : MineralFormation.values()) if (mineral.id().equals(id)) return mineral;
        return null;
    }
}

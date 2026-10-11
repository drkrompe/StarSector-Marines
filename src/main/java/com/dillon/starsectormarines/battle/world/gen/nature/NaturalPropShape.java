package com.dillon.starsectormarines.battle.world.gen.nature;

import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;

/** Code-owned silhouette shared by art, placement, observation and whole-object destruction. */
public interface NaturalPropShape {
    String id();
    int width();
    int height();
    int hitPoints();
    boolean blocks(int x, int y, int turns);
    default int width(int turns) { return (turns & 1) == 0 ? width() : height(); }
    default int height(int turns) { return (turns & 1) == 0 ? height() : width(); }

    static NaturalPropShape find(String id) {
        for (var tree : TreeCanopy.values()) if (tree.id.equals(id)) return tree;
        for (var mineral : MineralFormation.values()) if (mineral.id().equals(id)) return mineral;
        return null;
    }
}

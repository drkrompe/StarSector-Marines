package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.Random;

/** Authored material pairings; surfaces change appearance, never ground semantics. */
enum BuildingStyle {
    SANDSTONE("masonry-wall", "limestone", "terracotta"),
    TENEMENT("masonry-wall", "timber", "limestone"),
    ARCADE("ceramic-wall", "terracotta", "basalt"),
    CIVIC("ceramic-wall", "sage", "terrazzo"),
    WORKSHOP("olive-wall", "concrete", "ochre"),
    DEPOT("olive-wall", "tread", "concrete");

    final String wall, floor, accent;

    BuildingStyle(String wall, String floor, String accent) {
        this.wall = "colony." + wall;
        this.floor = "colony." + floor;
        this.accent = "colony." + accent;
    }

    static BuildingStyle pick(BuildingKind kind, Random rng) {
        switch (kind) {
            case RESIDENTIAL: return rng.nextBoolean() ? SANDSTONE : TENEMENT;
            case COMMERCIAL: return rng.nextBoolean() ? ARCADE : SANDSTONE;
            case INDUSTRIAL: return rng.nextBoolean() ? WORKSHOP : DEPOT;
            case CIVIC:
            case MEDICAL: return CIVIC;
            default: return null;
        }
    }

    void paintShell(NavigationGrid grid, CellTopology topology,
                    int left, int bottom, int right, int top) {
        int walls = topology.surfaceIndex(wall);
        int floors = topology.surfaceIndex(floor);
        for (int y = bottom; y <= top; y++) {
            for (int x = left; x <= right; x++) {
                topology.setSurface(x, y, !grid.isWalkable(x, y) && !topology.isFixture(x, y)
                        ? walls : floors);
            }
        }
    }
}

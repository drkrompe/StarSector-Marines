package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

/**
 * Per-carve placement hints that cannot live on a reusable
 * {@link BuildingShellCore.BuildingConfig}. Compound fillers use these hints
 * to orient a building toward shared circulation while ordinary single-leaf
 * buildings retain the unconstrained default.
 */
final class BuildingPlacement {

    enum Side {
        TOP(0), BOTTOM(1), LEFT(2), RIGHT(3);

        final int doorwayCode;

        Side(int doorwayCode) {
            this.doorwayCode = doorwayCode;
        }

        Side opposite() {
            switch (this) {
                case TOP: return BOTTOM;
                case BOTTOM: return TOP;
                case LEFT: return RIGHT;
                default: return LEFT;
            }
        }
    }

    static final BuildingPlacement DEFAULT = new BuildingPlacement(null, false);

    final Side frontage;
    final boolean forceOpposedDoorways;
    /** Only the frontage carries doors and windows; every other wall is blank. Needs a frontage. */
    final boolean frontOnly;

    BuildingPlacement(Side frontage, boolean forceOpposedDoorways) {
        this(frontage, forceOpposedDoorways, false);
    }

    private BuildingPlacement(Side frontage, boolean forceOpposedDoorways, boolean frontOnly) {
        this.frontage = frontage;
        this.forceOpposedDoorways = forceOpposedDoorways;
        this.frontOnly = frontOnly;
    }

    /** Doors and windows on {@code frontage} alone, the other walls blank. */
    static BuildingPlacement frontOnly(Side frontage) {
        return new BuildingPlacement(frontage, false, true);
    }
}

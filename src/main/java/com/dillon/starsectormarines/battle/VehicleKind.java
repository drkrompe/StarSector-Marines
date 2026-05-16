package com.dillon.starsectormarines.battle;

/**
 * Catalog of large vehicle props that can be parked on the battle map. Each
 * variant bundles its source sheet, the frame index inside that sheet (sliced
 * with {@link SpriteSheetSlicer}), and the cell footprint it occupies.
 *
 * <p>Vehicles are mechanically wall-like: their footprint cells are flagged
 * non-walkable at map setup, so they block pathfinding, line of sight, and
 * grant cover to adjacent units automatically through the existing nav-grid
 * machinery. The renderer skips wall art on these cells and draws the truck
 * sprite scaled to fit the footprint instead.
 */
public enum VehicleKind {
    UTILITY_TRUCK (VehicleSheet.TRUCKS,   0, 3, 2),
    FLATBED_TRUCK (VehicleSheet.TRUCKS,   1, 3, 2),
    TANKER_TRUCK  (VehicleSheet.TRUCKS,   2, 3, 2),
    BOX_VAN       (VehicleSheet.TRUCKS,   3, 3, 2),
    CARGO_TRUCK   (VehicleSheet.TRUCKS_2, 0, 3, 2),
    ARMORED_APC   (VehicleSheet.TRUCKS_2, 1, 3, 2);

    public final VehicleSheet sheet;
    public final int frameIndex;
    /** Cells the vehicle occupies along the x axis (width). */
    public final int footprintCellsX;
    /** Cells the vehicle occupies along the y axis (height). */
    public final int footprintCellsY;

    VehicleKind(VehicleSheet sheet, int frameIndex, int footprintCellsX, int footprintCellsY) {
        this.sheet = sheet;
        this.frameIndex = frameIndex;
        this.footprintCellsX = footprintCellsX;
        this.footprintCellsY = footprintCellsY;
    }

    /** Source sheets the vehicle frames are sliced from. Two for now; add more enum members when new sheets land. */
    public enum VehicleSheet {
        TRUCKS  ("graphics/battle/trucks.png"),
        TRUCKS_2("graphics/battle/trucks_2.png");

        public final String path;

        VehicleSheet(String path) {
            this.path = path;
        }
    }
}

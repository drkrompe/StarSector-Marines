package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.sim.BattleSetup;

/**
 * One placed vehicle on the battle map — a {@link VehicleKind} anchored to a
 * cell. The anchor is the south-west cell of the footprint; the vehicle
 * extends {@code kind.footprintCellsX} cells east and {@code kind.footprintCellsY}
 * cells north from there.
 *
 * <p>Strictly visual + an obstacle to pathing/LOS — the gameplay effect comes
 * from {@link BattleSetup} flagging the footprint cells non-walkable on the
 * {@link com.dillon.starsectormarines.battle.nav.NavigationGrid} before the
 * sim is constructed. This POJO just remembers where to draw the sprite.
 */
public final class MapVehicle {

    public final VehicleKind kind;
    /** South-west cell of the footprint. */
    public final int cellX;
    public final int cellY;

    public MapVehicle(VehicleKind kind, int cellX, int cellY) {
        this.kind = kind;
        this.cellX = cellX;
        this.cellY = cellY;
    }
}

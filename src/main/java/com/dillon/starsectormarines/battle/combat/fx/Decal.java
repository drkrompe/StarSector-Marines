package com.dillon.starsectormarines.battle.combat.fx;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Unit;

/**
 * One persistent visual mark left on the battlefield — a bullet hole on a
 * wall, a crater on a street, a pile of rubble after a mortar hit. Lives on
 * {@link BattleSimulation} alongside other transient combat state so a new
 * battle starts clean and decals don't bleed between missions.
 *
 * <p>Strictly visual — no gameplay effect. Pathfinding, LOS, and cover all
 * ignore decals. Rendered by {@code BattleScreen} between the floor pass and
 * the vehicle pass so parked trucks (and units) draw on top of decals.
 *
 * <p>Position is in fractional cell coords (same convention as {@code Unit.getRenderX()/getRenderY()}),
 * so a decal can sit at a cell center, on a cell boundary near a wall, or
 * anywhere a hit lands.
 */
public final class Decal {

    public final float x;
    public final float y;
    public final int decalIndex;
    /** Render rotation in degrees, randomized at spawn so bullet-hole clusters don't all line up the same way. */
    public final float rotationDeg;
    /** Long-axis visual size in cells. Per-decal so a small bullet hole and a medium crater can share the same sheet. */
    public final float scaleCells;

    public Decal(float x, float y, int decalIndex, float rotationDeg, float scaleCells) {
        this.x = x;
        this.y = y;
        this.decalIndex = decalIndex;
        this.rotationDeg = rotationDeg;
        this.scaleCells = scaleCells;
    }
}

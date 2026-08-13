package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Telegraphed heavy orbital shell with structural damage and friendly fire. */
public final class OrbitalBarrage extends CommandPower {

    public static final String ID = "orbital_barrage";
    public static final float WARNING_SECONDS = 3f;
    public static final float BLAST_RADIUS_CELLS = 4f;
    public static final int SUPPLY_COST = 5;

    public OrbitalBarrage() {
        super(ID, "Orbital Barrage", 4f, 0f, 1, SUPPLY_COST, 3);
    }

    @Override
    public float previewRadiusCells() { return BLAST_RADIUS_CELLS; }

    @Override
    public void resolve(int cellX, int cellY, CommandPowerService service,
                        BattleControl battle) {
        service.addFireMission(new PendingDetonation(
                cellX + 0.5f, cellY + 0.5f, WARNING_SECONDS,
                BLAST_RADIUS_CELLS, 80f, 1.5f,
                120, Faction.MARINE, true,
                3f, true, false));
    }
}

package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

/**
 * An easter egg for the debug launcher: drops a stock Hegemony Onslaught on the targeted cell for the company.
 * Never part of a campaign detachment; only a debug mission's briefing offers it.
 */
public final class SpawnInOnslaught extends CommandPower {
    private static final Logger LOG = Global.getLogger(SpawnInOnslaught.class);

    public static final String ID = "spawn_in_onslaught";

    public SpawnInOnslaught() {
        super(ID, "Spawn in Onslaught", 0f, 5f, 0, 0, 1);
    }

    @Override public float previewRadiusCells() { return ShipOnStation.AREA_RADIUS_CELLS; }

    @Override
    public void resolve(int cellX, int cellY, CommandPowerService service, BattleControl battle) {
        try {
            ShipOnStation.call(ShipSupportSource.stockFor("onslaught"), cellX + 0.5f, cellY + 0.5f, battle);
        } catch (Exception e) {
            LOG.warn("SpawnInOnslaught: the Onslaught could not be fitted", e);
        }
    }
}

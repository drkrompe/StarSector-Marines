package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

/**
 * Drops a stock Hegemony Onslaught on the targeted cell for the company. A committed Onslaught of any mark is the
 * source in the campaign, and a debug mission's briefing offers it whatever its roster. The ship called is always the
 * stock one at the reduced body ({@link #PRESENCE}), not the member's fit, and nothing is written back to the fleet.
 */
public final class SpawnInOnslaught extends CommandPower {
    private static final Logger LOG = Global.getLogger(SpawnInOnslaught.class);

    public static final String ID = "spawn_in_onslaught";

    /**
     * The body this Onslaught flies at, in shared-density multiples: a third of the standard hull presence, so it is
     * about 35 cells long rather than over a hundred. At the standard presence its mounts sit further from the ground
     * in front of it than its guns reach, and a pilot laying fire ahead of the bow saw nothing fire.
     */
    public static final float PRESENCE = 2f;

    /** Draft tuning: a capital's single call, paid like Ship on Station. */
    private static final float CP_COST = 4f;
    private static final float COOLDOWN_SECONDS = 5f;
    private static final int MAX_CHARGES = 1;
    private static final int SUPPLY_COST = 3;
    private static final int SLOT_WEIGHT = 1;

    public SpawnInOnslaught() {
        super(ID, "@battlePowerSpawnInOnslaught", CP_COST, COOLDOWN_SECONDS, MAX_CHARGES, SUPPLY_COST, SLOT_WEIGHT);
    }

    @Override public float previewRadiusCells() { return ShipOnStation.AREA_RADIUS_CELLS; }

    @Override
    public void resolve(int cellX, int cellY, CommandPowerService service, BattleControl battle) {
        try {
            ShipOnStation.call(ShipSupportSource.stockFor("onslaught"), Faction.MARINE,
                    cellX + 0.5f, cellY + 0.5f, PRESENCE, battle);
        } catch (Exception e) {
            LOG.warn("SpawnInOnslaught: the Onslaught could not be fitted", e);
        }
    }
}

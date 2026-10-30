package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipAtmosphere;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.Random;

/**
 * Calls a Wolf frigate over the targeted ground for a short strike: it drops through the cloud deck in reach of the
 * cell and holds there, its turrets work whatever they find on their own, and ten to thirty seconds after the descent
 * it departs. The same call path as {@link ShipOnStation}, at the Onslaught's reduced body
 * ({@link SpawnInOnslaught#PRESENCE}).
 *
 * <p>A committed Wolf of any mark is the <em>source</em> (it makes the card exist), but the ship flown is always the
 * stock Wolf (H) Assault, so the guns over the battle are the ones this power chooses, not whatever the member was
 * fitted with, and nothing is written back to the fleet.
 */
public final class WolfStrike extends CommandPower {

    private static final Logger LOG = Global.getLogger(WolfStrike.class);

    public static final String ID = "wolf_strike";
    /** The hull and stock variant every strike flies, whichever Wolf brought the card. */
    public static final String HULL_ID = "wolf_hegemony";
    public static final String VARIANT_ID = "wolf_hegemony_Assault";

    /** Time with its guns live, from the end of the descent; drawn evenly between the two. */
    public static final float MIN_STATION_SECONDS = 10f;
    public static final float MAX_STATION_SECONDS = 30f;

    /** Draft tuning: a light hull's short visit, available twice. */
    private static final float CP_COST = 3f;
    private static final float COOLDOWN_SECONDS = 20f;
    private static final int MAX_CHARGES = 2;
    private static final int SUPPLY_COST = 2;
    private static final int SLOT_WEIGHT = 2;

    public WolfStrike() {
        super(ID, "@battlePowerWolfStrike", CP_COST, COOLDOWN_SECONDS, MAX_CHARGES, SUPPLY_COST, SLOT_WEIGHT);
    }

    /** The one ship every strike fits: the stock Wolf (H) Assault. */
    public static ShipSupportSource source() {
        return ShipSupportSource.stock(HULL_ID, VARIANT_ID, "Wolf (H)", false);
    }

    /** One strike's time on station, drawn from the battle's stream. */
    public static float stationSeconds(Random random) {
        return MIN_STATION_SECONDS + random.nextFloat() * (MAX_STATION_SECONDS - MIN_STATION_SECONDS);
    }

    @Override public float previewRadiusCells() { return ShipOnStation.AREA_RADIUS_CELLS; }

    @Override
    public boolean canTarget(int cellX, int cellY, BattleView battle) {
        return super.canTarget(cellX, cellY, battle) && battle.playerSeesCell(cellX, cellY);
    }

    @Override
    public void resolve(int cellX, int cellY, CommandPowerService service, BattleControl battle) {
        try {
            Warship wolf = ShipOnStation.call(source(), Faction.MARINE, cellX + 0.5f, cellY + 0.5f,
                    SpawnInOnslaught.PRESENCE, battle);
            // It holds where it drops, already in reach of the cell, rather than gliding in to it; it is on station
            // from the start, so the stay is counted through the descent, when it fires nothing.
            wolf.stationTo(wolf.x, wolf.y);
            wolf.stayFor(WarshipAtmosphere.DESCENT_SECONDS + stationSeconds(battle.random()));
        } catch (Exception e) {
            LOG.warn("WolfStrike: the Wolf (H) could not be fitted", e);
        }
    }
}

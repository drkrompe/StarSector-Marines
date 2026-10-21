package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Directs the marines' air pool onto a point through a living comms operator.
 *
 * <p><b>It is paid in airframes, not command points.</b> The power costs nothing and has no charges: what it spends is
 * an airframe from the side's regenerating {@code AirPoolService} stock, and what it needs is a living, unsuppressed
 * forward observer with sight of the ground or fair reason to believe in it. It appears in the roster only for a side
 * the mission gave a pool and left to the player; the card is gated at targeting by exactly the predicate the sim
 * commits on, so an unanswerable click stays armed and spends nothing.
 *
 * <p>What flies, how it bombs and how it is shot down is the aircraft's and the pool's; this class only validates the
 * target and asks.
 */
public final class AirStrikePower extends CommandPower {

    public static final String ID = "air_strike";

    /** Seconds before another call may be requested; the operator's own cooldown is the real pacing. UNTUNED. */
    private static final float COOLDOWN_SECONDS = 3f;
    /** The ground a bomb run covers, drawn at targeting. */
    private static final float PREVIEW_RADIUS_CELLS = 5f;

    public AirStrikePower() {
        super(ID, "@battlePowerAirStrike", 0f, COOLDOWN_SECONDS, 0, 0, 1);
    }

    @Override
    public float previewRadiusCells() {
        return PREVIEW_RADIUS_CELLS;
    }

    /** Ground the player can see (or an operator can see), with an operator free to call it and an airframe in stock. */
    @Override
    public boolean canTarget(int cellX, int cellY, BattleView battle) {
        return super.canTarget(cellX, cellY, battle)
                && battle.canDirectAirStrike(Faction.MARINE, cellX + 0.5f, cellY + 0.5f, battle.playerSeesCell(cellX, cellY));
    }

    @Override
    public void resolve(int cellX, int cellY, CommandPowerService service, BattleControl battle) {
        battle.requestAirStrike(Faction.MARINE, cellX + 0.5f, cellY + 0.5f, battle.playerSeesCell(cellX, cellY));
    }
}

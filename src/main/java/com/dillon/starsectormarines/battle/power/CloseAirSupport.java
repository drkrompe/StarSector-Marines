package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.GunshipOrbit;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Calls a Mudskipper gunship over the targeted ground: it comes in from the nearest map edge,
 * circles overhead of the cell while its turret works the ground beneath it,
 * and leaves when its time is up.
 *
 * <p>The power conjures a fresh craft the way Marine Insertion conjures a Valkyrie. A committed
 * Mudskipper is the <em>source</em> (its presence in the detachment is what makes the card exist)
 * and the sortie flown is a battle-native one, so nothing here reads or changes the campaign
 * fleet and the craft is as expendable as any other in the sim. How the craft flies the ring is
 * {@link GunshipOrbit}'s and what it carries is {@code GunshipArmament}'s; this class only
 * validates the target and puts the craft in the air.
 */
public final class CloseAirSupport extends CommandPower {

    public static final String ID = "close_air_support";

    /** Draft tuning — a cheap light-hull sortie, available twice; persistent balance remains open. */
    private static final float CP_COST = 3f;
    private static final float COOLDOWN_SECONDS = 25f;
    private static final int MAX_CHARGES = 2;
    private static final int SUPPLY_COST = 2;
    private static final int SLOT_WEIGHT = 2;

    public CloseAirSupport() {
        super(ID, "Close Air Support", CP_COST, COOLDOWN_SECONDS, MAX_CHARGES, SUPPLY_COST, SLOT_WEIGHT);
    }

    /** The ring's radius, so the targeting preview draws the ground the gunship circles. */
    @Override
    public float previewRadiusCells() {
        return GunshipOrbit.RADIUS_CELLS;
    }

    /** Ground the player can see: the guns hunt for themselves, but the player should know what they are sending it over. */
    @Override
    public boolean canTarget(int cellX, int cellY, BattleView battle) {
        return super.canTarget(cellX, cellY, battle) && battle.playerSeesCell(cellX, cellY);
    }

    @Override
    public void resolve(int cellX, int cellY, CommandPowerService service, BattleControl battle) {
        float centreX = cellX + 0.5f;
        float centreY = cellY + 0.5f;
        float[] route = AirDeliveryPower.routeFromNearestEdge(centreX, centreY, battle.getGrid());
        battle.spawnGunship(ShuttleType.MUDSKIPPER, Faction.MARINE, centreX, centreY,
                route[0], route[1], route[2], route[3]);
    }
}

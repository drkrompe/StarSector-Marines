package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.deployable.PointDefenseService;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

/**
 * How a missile rack spends its load (`mechs-nouns.md`, Flux): a click is one salvo, and a held trigger, or another
 * click while the rack's cycle still runs from the last salvo, chains the next loaded salvo as soon as the last one
 * has gone, until the rack is empty. The cycle is no wait between a rack's own groups: what bounds a deep rack is its
 * load, its flux and its loader. A mech's rack and a vehicle's launcher obey the same rule, and each salvo either
 * spends is replenished on its own afterwards.
 *
 * <p>An autonomous carrier chooses how many salvos to chain instead of holding a button
 * ({@link #autonomousSalvos}): one at a soft target, more against armor or point defence.
 */
public final class SalvoChain {

    /**
     * The share of a salvo's flux a chained salvo pays: the tubes are loaded already, so a follow-up salvo costs
     * the reservoir half of what a fresh trigger does. At the full share an LRM-15 could never empty its fifteen
     * tubes inside the stock reservoir; at half its whole load costs 80 of 100.
     */
    public static final float CHAINED_FLUX_SHARE = 0.5f;

    /**
     * The most salvos a vehicle's crew chains on one target. A hull's loader is slow beside its ready magazine, so a
     * crew keeps something back for the next machine rather than emptying a deep rack at the first it sees.
     */
    public static final int VEHICLE_AUTONOMOUS_SALVOS = 3;

    private SalvoChain() { }

    /** A turret's missile launcher whose trigger is a salvo of several: the vehicle racks that chain. */
    public static boolean chains(WeaponDef weapon) {
        return weapon != null && weapon.boostRamp && weapon.burstCount > 1;
    }

    /** The flux a salvo reserves: in full on a fresh trigger, at {@link #CHAINED_FLUX_SHARE} when chained. */
    public static float fluxCost(float perTrigger, boolean chained) {
        return chained ? perTrigger * CHAINED_FLUX_SHARE : perTrigger;
    }

    /**
     * The salvos an autonomous carrier commits to one trigger, {@code loadedSalvos} being what the rack holds
     * now (at most {@code cap}). One at a soft target, since a second only adds to the overkill. Against a mech,
     * or a target point defence is screening, the whole load: the spike is what takes plate off before it is
     * repaired, and what saturates the interceptors so some get through. Against other hardened targets (a
     * vehicle, a turret) half the load, rounded up.
     */
    public static int autonomousSalvos(int loadedSalvos, int cap, boolean hardened, boolean mech, boolean screened) {
        int load = Math.min(Math.max(0, loadedSalvos), Math.max(1, cap));
        if (load <= 1) return load;
        if (mech || screened) return load;
        if (hardened) return (load + 1) / 2;
        return 1;
    }

    /** Whether point defence of the target's own side covers where it stands. */
    public static boolean screened(BattleView sim, long target) {
        PointDefenseService pd = sim.pointDefense();
        Faction side = sim.identity().faction(target);
        return pd != null && side != null && pd.isCovered(side, sim.world().x(target), sim.world().y(target));
    }
}

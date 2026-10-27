package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.panel.DirectControlReticle.Load;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

/**
 * The controlled Marine's left trigger as the reticle carries it: the same clocks and magazine the panel's
 * ammunition strip shows, read at the cursor so the player need not look away to see why the gun went quiet.
 */
public final class DirectControlLoad {
    private DirectControlLoad() {}

    /**
     * The left button's load: the primary's cycle, or its reload with its magazine; or, on a special, that special's
     * readying or cooldown, whichever has longer to run. Ready for a vehicle, a mech, a warship or nothing.
     */
    public static Load read(BattleSimulation sim) {
        if (sim == null || !sim.directControl().active() || sim.directControl().warshipControl()) return Load.READY;
        long id = sim.directControl().activeUnitId();
        var world = sim.world();
        if (!world.isAlive(id) || sim.directControl().controlledVehicleId() == id || world.hasMechLoadout(id)) {
            return Load.READY;
        }
        SpecialEquipmentDef special = sim.directControl().leftSpecial();
        if (special != null) {
            float windup = (1f - sim.directControl().marineLeft().readiness()) * special.aimDuration();
            float cooldown = world.secondaryCooldownTimer(id);
            float readiness = windup > cooldown ? Load.clock(windup, special.aimDuration())
                    : Load.clock(cooldown, special.cooldown());
            return new Load(readiness, 0, 0, false);
        }
        var combat = sim.combat();
        if (!combat.has(id) || combat.primaryWeaponDef(id) == null) return Load.READY;
        int capacity = combat.magazineSize(id);
        float reload = combat.reloadTimer(id);
        if (capacity > 0 && reload > 0f) {
            return new Load(Load.clock(reload, combat.reloadSeconds(id)), combat.roundsLoaded(id), capacity, true);
        }
        return new Load(Load.clock(combat.cooldownTimer(id), combat.attackCooldown(id)),
                combat.roundsLoaded(id), capacity, false);
    }
}

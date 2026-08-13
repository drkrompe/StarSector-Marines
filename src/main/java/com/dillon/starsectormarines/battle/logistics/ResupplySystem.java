package com.dillon.starsectormarines.battle.logistics;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

/**
 * Transfers one finite munition unit at a time to a friendly unit physically
 * holding near a cache. Nearby enemies contest the cache and pause service.
 */
public final class ResupplySystem {

    public static final float TRANSFER_INTERVAL_SECONDS = 0.75f;

    private final ResupplyService service;
    private final UnitRosterService roster;

    public ResupplySystem(ResupplyService service, UnitRosterService roster) {
        this.service = service;
        this.roster = roster;
    }

    public void tick(float dt) {
        for (ResupplyCache cache : service.caches()) {
            if (cache.depleted()) continue;
            cache.contested = enemyWithin(cache);
            if (cache.contested) continue;
            cache.transferCooldown -= dt;
            if (cache.transferCooldown > 0f) continue;
            long recipient = nearestEligible(cache);
            if (recipient == 0L) continue;
            if (transferOne(recipient)) {
                cache.stock--;
                cache.transferCooldown = TRANSFER_INTERVAL_SECONDS;
            }
        }
    }

    private boolean enemyWithin(ResupplyCache cache) {
        float r2 = ResupplyCache.CONTEST_RADIUS_CELLS * ResupplyCache.CONTEST_RADIUS_CELLS;
        World world = roster.world();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long unit = roster.get(i);
            Faction faction = roster.identity().faction(unit);
            if (faction == cache.faction || faction == Faction.CIVILIAN) continue;
            float dx = world.x(unit) - (cache.cellX + 0.5f);
            float dy = world.y(unit) - (cache.cellY + 0.5f);
            if (dx * dx + dy * dy <= r2) return true;
        }
        return false;
    }

    private long nearestEligible(ResupplyCache cache) {
        float r2 = ResupplyCache.SERVICE_RADIUS_CELLS * ResupplyCache.SERVICE_RADIUS_CELLS;
        float bestDist2 = Float.MAX_VALUE;
        long best = 0L;
        World world = roster.world();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long unit = roster.get(i);
            if (roster.identity().faction(unit) != cache.faction || !needsSupply(unit)) continue;
            float dx = world.x(unit) - (cache.cellX + 0.5f);
            float dy = world.y(unit) - (cache.cellY + 0.5f);
            float dist2 = dx * dx + dy * dy;
            if (dist2 <= r2 && dist2 < bestDist2) {
                bestDist2 = dist2;
                best = unit;
            }
        }
        return best;
    }

    private boolean needsSupply(long unit) {
        World world = roster.world();
        if (world.hasSecondaryWeapon(unit)
                && world.secondaryAmmo(unit) < world.secondaryWeapon(unit).startingAmmo) return true;
        MechLoadoutComponent mech = world.mechLoadout(unit);
        return mech != null && (mech.srmAmmoSalvos < MechLoadoutComponent.DEFAULT_SRM_AMMO_SALVOS
                || mech.lrmAmmoSalvos < MechLoadoutComponent.DEFAULT_LRM_AMMO_SALVOS);
    }

    private boolean transferOne(long unit) {
        World world = roster.world();
        if (world.hasSecondaryWeapon(unit)) {
            int current = world.secondaryAmmo(unit);
            int maximum = world.secondaryWeapon(unit).startingAmmo;
            if (current < maximum) {
                world.setSecondaryAmmo(unit, current + 1);
                return true;
            }
        }
        MechLoadoutComponent mech = world.mechLoadout(unit);
        if (mech == null) return false;
        if (mech.lrmAmmoSalvos < MechLoadoutComponent.DEFAULT_LRM_AMMO_SALVOS) {
            mech.lrmAmmoSalvos++;
            return true;
        }
        if (mech.srmAmmoSalvos < MechLoadoutComponent.DEFAULT_SRM_AMMO_SALVOS) {
            mech.srmAmmoSalvos++;
            return true;
        }
        return false;
    }
}

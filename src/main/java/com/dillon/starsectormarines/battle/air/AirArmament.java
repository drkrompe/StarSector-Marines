package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.turret.TurretRole;

/**
 * Puts guns on an air craft that has just been spawned.
 *
 * <p>Two things are separate here on purpose. <b>What</b> the craft carries
 * comes from its role and its mount count, through
 * {@link ShuttleType#kitFor} — a role-to-loadout function that never depended
 * on the carrier being a transport and now says so by living out here.
 * <b>Where</b> each gun sits comes from the hull's real {@code weaponSlots}
 * via {@link TurretSlotResolver}, so an armed craft fires from the places its
 * hull actually has, and a two-gun fighter's guns are where a Broadsword's
 * guns are.
 *
 * <p>Deliberately not a second weapon model for fighters. A
 * {@link com.dillon.starsectormarines.battle.flyby.FighterProfile} carries
 * tracer and projectile numbers as well, but those belong to the scripted
 * flyby pass that draws and resolves its own fire; an air entity's mounted
 * weapons are {@link AirTurrets}, and this is how any air craft gets them.
 * Unifying the two is {@code fighter-air-entities.md}.
 */
public final class AirArmament {

    private AirArmament() {}

    /**
     * Arms {@code craftId} for {@code role}, or leaves it unarmed when the
     * airframe has no mounts or the hull publishes no weapon slots.
     *
     * <p>Silent on failure by design: an aircraft that cannot be armed is a
     * worse aircraft rather than a broken battle, and the hull-slot lookup
     * needs the game loaded.
     *
     * @return how many mounts were actually fitted
     */
    public static int equip(BattleSimulation sim, long craftId, TurretRole role) {
        World world = sim.world();
        Airframe airframe = world.airframe(craftId);
        if (airframe == null || airframe.hardpoints() <= 0) return 0;
        ShuttleMission mission = world.mission(craftId);
        if (mission == null) return 0;
        if (mission.assignedRole == null) {
            mission.assignedRole = role != null ? role : TurretRole.A2G;
        }
        // Zip kind[i] with slot[i]; clamp to whichever runs out first.
        String[] kit = ShuttleType.kitFor(mission.assignedRole, airframe.hardpoints());
        float[][] slots = TurretSlotResolver.resolve(airframe.renderHullId());
        int fitted = Math.min(kit.length, slots.length);
        if (fitted <= 0) return 0;
        // Mounts start aligned to the nose so the first tick on station does
        // not snap every turret through a ninety-degree swing.
        float noseFacing = world.kinematics(craftId).facingDegrees;
        MountedTurret[] turrets = new MountedTurret[fitted];
        for (int i = 0; i < fitted; i++) {
            turrets[i] = new MountedTurret(new TurretMount(kit[i], slots[i][0], slots[i][1]));
            turrets[i].facingDegrees = noseFacing;
        }
        // A presence component, so the entity id must already be minted.
        sim.attachAirTurrets(craftId, turrets);
        return fitted;
    }
}

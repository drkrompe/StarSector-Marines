package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.mech.MechWeapon;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.combat.fx.ImpactProfile;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

import java.util.concurrent.ThreadLocalRandom;
import it.unimi.dsi.fastutil.longs.LongArrayList;

/**
 * Chassis-mounted weapons on motorized / heavy units. Modular mech hardpoints
 * use it today; future tanks and hovercraft can hook in through the same
 * {@link MechLoadoutComponent} state bag.
 *
 * <p>The split from {@link com.dillon.starsectormarines.battle.infantry.InfantryWeapons} is along the unit's character —
 * handheld squad weapons vs vehicle-mounted hardpoints — not along weapon
 * class. A future "infantry rocket launcher" would still live in infantry;
 * a hypothetical mech-mounted rifle would live here.
 *
 * <p>Every installed mount owns an independent firing track. Direct weapons
 * and SRMs resolve as modeled ground-level rounds, while LRM artillery retains
 * its indirect scatter/projectile procedure. Continuation pumps every queued
 * burst or salvo at per-component spacing in {@link #tick}.
 *
 * <p>Smoking-wreck spawn for dead mechs is no longer here — it moved to the
 * {@code MechWreckSystem} death-event handler, so it reacts to the one death
 * seam instead of re-scanning the unit list each tick.
 */
public class HeavyWeapons {

    private static final float SHOT_LIFETIME = 0.15f;

    private final UnitRosterService roster;
    private final NavigationGrid grid;
    private final BallisticResolver resolver;
    private final ShotService shots;
    private final Detonations detonations;

    /**
     * Reused per-tick gather of the live mechs before the continuation pass.
     * The pass fires weapons, which can kill a target and release it from the
     * registry mid-pass (swap-and-pop); gathering first makes the iteration a
     * snapshot, so a release doesn't reshuffle the slots out from under it.
     * Only mechs are gathered (a handful per battle), so the copy is cheap.
     */
    private final LongArrayList mechScratch = new LongArrayList();

    public HeavyWeapons(UnitRosterService roster, NavigationGrid grid,
                        BallisticResolver resolver,
                        ShotService shots, Detonations detonations) {
        this.roster = roster;
        this.grid = grid;
        this.resolver = resolver;
        this.shots = shots;
        this.detonations = detonations;
    }

    /** Per-tick pass: drains queued rounds from every installed mech mount. */
    public void tick() {
        advanceMechWeapons();
    }

    /**
     * Convenience overload — full accuracy. Used by all the precision-fire
     * code paths (chaingun, SRM, line-of-sight LRMs).
     */
    public void fireMechWeapon(long shooter, long target, MechWeapon weapon) {
        fireMechWeapon(shooter, target, weapon, 1.0f);
    }

    /**
     * Fires one round of a mech chassis weapon. Damage / accuracy / vsTurret
     * pull from the {@link MechWeapon} parameter rather than the shooter's
     * baked Entity stats — concurrent mounts can carry very different numbers,
     * so the weapon's profile drives the math.
     * Caller is responsible for cooldown / ammo / range gating before calling.
     *
     * <p>{@code accuracyMult} scales the weapon's base accuracy at the hit
     * roll. Set to 1.0 for line-of-sight fire; the LRM indirect-fire path
     * passes {@link MechWeapon#LRM_NO_LOS_ACC_MULT}.
     */
    public void fireMechWeapon(long shooter, long target, MechWeapon weapon, float accuracyMult) {
        if (weapon.arcHeight <= 0f) {
            fireDirectRound(shooter, target, weapon, accuracyMult);
            return;
        }

        fireIndirectRound(shooter, target, weapon, accuracyMult);
    }

    /** Modeled ground-level round for chaingun and SRM tracks. */
    private void fireDirectRound(long shooter, long target, MechWeapon weapon,
                                 float accuracyMult) {
        World world = roster.world();
        float effectiveAccuracy = weapon.accuracy * accuracyMult;
        Faction shooterFaction = roster.identity().faction(shooter);
        float moraleImpact = roster.moraleImpact(shooter);
        float fromX = world.renderX(shooter);
        float fromY = world.renderY(shooter);
        float distToTarget = RangeFalloff.dist(world.x(shooter), world.y(shooter),
                world.x(target), world.y(target));
        float effectiveSpread = RangeFalloff.spread(weapon.hitSpread, distToTarget, weapon.range);
        BallisticResolver.Resolution res = resolver.resolve(shooter, target,
                effectiveAccuracy, effectiveSpread, weapon.roundVelocity(),
                ThreadLocalRandom.current());

        if (weapon.aoeRadius <= 0f && res.victimId() != 0L) {
            float appliedDamage = res.friendlyHit()
                    ? weapon.damage * BallisticResolver.FRIENDLY_FIRE_DAMAGE_MULT
                    : weapon.damage;
            shots.queueImpact(new ShotService.PendingImpact(
                    res.victimId(), shooter, res.flightTime(), appliedDamage,
                    weapon.vsTurretMult, moraleImpact, res.friendlyHit()));
        }

        if (weapon.aoeRadius > 0f) {
            PendingDetonation onArrival = res.impacts()
                    ? new PendingDetonation(
                            res.endX(), res.endY(), res.flightTime(),
                            weapon.aoeRadius, weapon.damage, weapon.vsTurretMult,
                            weapon.wallDamage, shooterFaction, /*aerialDelivery*/ false,
                            weapon.wallDamageRadius, /*spawnDustOnWallBreak*/ true,
                            /*friendlyFireImmune*/ false)
                    : null;
            if (weapon.impactProfile == ImpactProfile.HE) {
                shots.queueProjectile(new Projectile(
                        fromX, fromY, res.endX(), res.endY(),
                        /*hasBoostRamp*/ true, /*arcHeight*/ 0f,
                        shooterFaction, /*aerialDelivery*/ false,
                        res.flightTime(), onArrival));
            } else if (onArrival != null) {
                detonations.queue(onArrival);
            }
        }

        shots.postShot(new ShotEvent(fromX, fromY, 0f,
                res.endX(), res.endY(), res.endZ(),
                res.hitIntended(), shooterFaction, Math.max(res.flightTime(), 0.05f),
                null, null, null, weapon, moraleImpact,
                res.victimId() != 0L, res.kind()));
    }

    /** Legacy indirect scatter/projectile procedure retained for LRM artillery. */
    private void fireIndirectRound(long shooter, long target, MechWeapon weapon,
                                   float accuracyMult) {
        World world = roster.world();
        Faction shooterFaction = roster.identity().faction(shooter);
        float moraleImpact = roster.moraleImpact(shooter);
        float fromX = world.renderX(shooter);
        float fromY = world.renderY(shooter);
        float distToTarget = RangeFalloff.dist(world.x(shooter), world.y(shooter),
                world.x(target), world.y(target));
        float effectiveSpread = RangeFalloff.spread(weapon.hitSpread, distToTarget, weapon.range);
        boolean hit = ThreadLocalRandom.current().nextFloat() < weapon.accuracy * accuracyMult;
        ShotEndpoint.Endpoint ep = ShotEndpoint.resolve(
                world.renderX(target), world.renderY(target),
                hit, effectiveSpread, ThreadLocalRandom.current());

        PendingDetonation onArrival = new PendingDetonation(
                ep.x(), ep.y(), weapon.flightSec,
                weapon.aoeRadius, weapon.damage, weapon.vsTurretMult,
                weapon.wallDamage, shooterFaction, /*aerialDelivery*/ true,
                weapon.wallDamageRadius, /*spawnDustOnWallBreak*/ true,
                /*friendlyFireImmune*/ false);
        shots.queueProjectile(new Projectile(
                fromX, fromY, ep.x(), ep.y(),
                /*hasBoostRamp*/ true, weapon.arcHeight,
                shooterFaction, /*aerialDelivery*/ true,
                weapon.flightSec, onArrival));
        float lifetime = weapon.flightSec > 0f ? weapon.flightSec : SHOT_LIFETIME;
        shots.postShot(new ShotEvent(fromX, fromY, ep.x(), ep.y(), hit,
                shooterFaction, lifetime, null, null, null, weapon, moraleImpact));
    }

    /**
     * Per-tick mech-weapon continuation — runs the three chassis tracks
     * (chaingun burst, SRM salvo, LRM salvo) for every unit with a
     * {@link MechLoadoutComponent}. Mirrors {@link com.dillon.starsectormarines.battle.infantry.InfantryWeapons#tick} for the
     * marine primary side; lives separate because the mech burst state is on
     * the loadout, not the unit.
     *
     * <p>The trigger decisions (start a burst / launch a salvo / lob an LRM)
     * happen inside {@code MechCombatantBehavior.tryFireMechWeapons}. This pass
     * only handles continuation — emitting queued rounds at their proper
     * spacing — and ticks down the per-weapon cooldowns so the next trigger
     * decision sees the right gating.
     */
    private void advanceMechWeapons() {
        // Gather the live mechs first (walking the MECH_LOADOUT query — only mech
        // entities match it, so no scan over the whole registry), then run the
        // continuation pass over the snapshot. Other arrivals in this phase
        // can release a target and swap-and-pop the registry; iterating a
        // snapshot keeps that from corrupting the pass. The query excludes
        // CORPSE (live mechs only), and isLive still guards an entity released
        // earlier this same drain.
        mechScratch.clear();
        EntityWorld entityWorld = roster.entityWorld();
        BattleComponents components = roster.components();
        for (ArchetypeTable t : entityWorld.matched(components.mechLoadouts)) {
            for (int r = 0, n = t.rowCount(); r < n; r++) {
                long uid = t.entityAt(r);
                if (roster.isLive(uid)) mechScratch.add(uid);
            }
        }
        World world = roster.world();
        for (int i = 0, n = mechScratch.size(); i < n; i++) {
            long u = mechScratch.getLong(i);
            if (!roster.isAliveById(u)) continue; // killed earlier in this same pass
            MechLoadoutComponent m = world.mechLoadout(u);

            for (MechWeaponMount mount : m.mounts()) {
                if (mount == null) continue;
                if (mount.cooldown > 0f) mount.cooldown -= BattleSimulation.TICK_DT;
                if (mount.burstRemaining <= 0) continue;
                mount.burstTimer -= BattleSimulation.TICK_DT;
                if (mount.burstTimer > 0f) continue;

                long target = mount.burstTargetId;
                if (!roster.isLive(target)) {
                    mount.burstRemaining = 0;
                    mount.burstTargetId = 0L;
                    continue;
                }
                if (!m.isAimedAt(target)) continue;

                MechWeapon weapon = mount.weapon();
                float accuracyMult = 1f;
                if (weapon == MechWeapon.LRM_ARTILLERY) {
                    boolean hasLos = grid.hasLineOfSight(
                            world.cellX(u), world.cellY(u),
                            world.cellX(target), world.cellY(target));
                    accuracyMult = hasLos ? 1f : MechWeapon.LRM_NO_LOS_ACC_MULT;
                }
                fireMechWeapon(u, target, weapon, accuracyMult);
                mount.burstRemaining--;
                mount.burstTimer = weapon.burstSpacing;
                if (mount.burstRemaining == 0) mount.burstTargetId = 0L;
            }
        }
    }
}

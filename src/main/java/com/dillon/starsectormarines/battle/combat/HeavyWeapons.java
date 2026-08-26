package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.Random;

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

    /** The battle's seeded stream — see {@code BattleSimulation.random()}. */
    private final Random rng;

    public HeavyWeapons(UnitRosterService roster, NavigationGrid grid,
                        BallisticResolver resolver,
                        ShotService shots, Detonations detonations, Random rng) {
        this.roster = roster;
        this.grid = grid;
        this.resolver = resolver;
        this.shots = shots;
        this.detonations = detonations;
        this.rng = rng;
    }

    /**
     * Per-tick pass: replenishes finite missile racks and drains queued rounds
     * from every installed mech mount.
     */
    public void tick() {
        advanceMechWeapons();
    }

    /**
     * Convenience overload — full accuracy. Used by all the precision-fire
     * code paths (chaingun, SRM, line-of-sight LRMs).
     */
    public void fireMechWeapon(long shooter, long target, WeaponDef weapon) {
        fireMechWeapon(shooter, target, weapon, 1.0f);
    }

    /**
     * Fires one round of a mech chassis weapon. Damage / accuracy / vsTurret
     * pull from the {@link WeaponDef} parameter rather than the shooter's
     * baked Entity stats — concurrent mounts can carry very different numbers,
     * so the weapon's profile drives the math.
     * Caller is responsible for cooldown / ammo / range gating before calling.
     *
     * <p>{@code accuracyMult} scales the weapon's base accuracy at the hit
     * roll. Set to 1.0 for line-of-sight fire; the LRM indirect-fire path
     * passes the installed definition's no-LOS accuracy multiplier.
     */
    public void fireMechWeapon(long shooter, long target, WeaponDef weapon, float accuracyMult) {
        roster.telemetry().recordRoundFired(shooter);
        if (weapon.arcHeight <= 0f) {
            fireDirectRound(shooter, target, weapon, accuracyMult);
            return;
        }

        fireIndirectRound(shooter, target, weapon, accuracyMult);
    }

    /** Modeled ground-level round for chaingun, cannon, and SRM tracks. */
    private void fireDirectRound(long shooter, long target, WeaponDef weapon,
                                 float accuracyMult) {
        World world = roster.world();
        float effectiveAccuracy = weapon.accuracy * accuracyMult;
        Faction shooterFaction = roster.identity().faction(shooter);
        float moraleImpact = roster.moraleImpact(shooter);
        float fromX = world.renderX(shooter);
        float fromY = world.renderY(shooter);
        float distToTarget = RangeFalloff.dist(world.x(shooter), world.y(shooter),
                world.x(target), world.y(target));
        float effectiveSpread = RangeFalloff.spread(
                weapon.hitSpread, distToTarget, weapon.range);
        BallisticResolver.Resolution res = resolver.resolve(shooter, target,
                effectiveAccuracy, effectiveSpread, weapon.roundVelocity,
                rng);

        if (weapon.aoeRadius <= 0f && res.victimId() != 0L) {
            float appliedDamage = res.friendlyHit()
                    ? weapon.damage * BallisticResolver.FRIENDLY_FIRE_DAMAGE_MULT
                    : weapon.damage;
            shots.queueImpact(new ShotService.PendingImpact(
                    res.victimId(), shooter, res.flightTime(), appliedDamage,
                    weapon.penetration, moraleImpact, res.friendlyHit()));
        }

        if (weapon.aoeRadius > 0f) {
            PendingDetonation onArrival = res.impacts()
                    ? new PendingDetonation(
                            shooter,
                            res.endX(), res.endY(), res.flightTime(),
                            weapon.aoeRadius, weapon.damage, weapon.penetration,
                            weapon.wallDamage, shooterFaction, /*aerialDelivery*/ false,
                            weapon.wallDamageRadius, /*spawnDustOnWallBreak*/ true,
                            /*friendlyFireImmune*/ false)
                    : null;
            // Rocket-class rounds own a Projectile so future point defense can
            // intercept the payload. Gun-launched HE remains a ballistic
            // ShotEvent paired with a timed detonation; it must not inherit a
            // rocket's boost curve merely because both are explosive.
            if (weapon.interceptableProjectile) {
                shots.queueProjectile(new Projectile(
                        fromX, fromY, res.endX(), res.endY(),
                        weapon.boostRamp, /*arcHeight*/ 0f,
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
                res.victimId() != 0L, res.kind(), shooter));
    }

    /** Legacy indirect scatter/projectile procedure retained for LRM artillery. */
    private void fireIndirectRound(long shooter, long target, WeaponDef weapon,
                                   float accuracyMult) {
        World world = roster.world();
        Faction shooterFaction = roster.identity().faction(shooter);
        float moraleImpact = roster.moraleImpact(shooter);
        float fromX = world.renderX(shooter);
        float fromY = world.renderY(shooter);
        float distToTarget = RangeFalloff.dist(world.x(shooter), world.y(shooter),
                world.x(target), world.y(target));
        float effectiveSpread = RangeFalloff.spread(
                weapon.hitSpread, distToTarget, weapon.range);
        boolean hit = rng.nextFloat() < weapon.accuracy * accuracyMult;
        ShotEndpoint.Endpoint ep = ShotEndpoint.resolve(
                world.renderX(target), world.renderY(target),
                hit, effectiveSpread, rng);

        PendingDetonation onArrival = new PendingDetonation(
                shooter,
                ep.x(), ep.y(), weapon.flightSec,
                weapon.aoeRadius, weapon.damage, weapon.penetration,
                weapon.wallDamage, shooterFaction, /*aerialDelivery*/ true,
                weapon.wallDamageRadius, /*spawnDustOnWallBreak*/ true,
                /*friendlyFireImmune*/ false);
        shots.queueProjectile(new Projectile(
                fromX, fromY, ep.x(), ep.y(),
                weapon.boostRamp, weapon.arcHeight,
                shooterFaction, /*aerialDelivery*/ true,
                weapon.flightSec, onArrival));
        float lifetime = weapon.flightSec > 0f ? weapon.flightSec : SHOT_LIFETIME;
        shots.postShot(new ShotEvent(shooter, fromX, fromY, ep.x(), ep.y(), hit,
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
     * handles continuation — emitting queued rounds at their proper spacing —
     * ticks down per-weapon cooldowns, and advances each finite missile rack's
     * installed replenisher cadence.
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
                mount.advanceReplenishment(BattleSimulation.TICK_DT,
                        m.missileReplenisher());
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

                WeaponDef weapon = mount.weaponDef();
                float accuracyMult = 1f;
                if (WeaponRegistry.MECH_LRM_ARTILLERY_ID.equals(weapon.id)) {
                    boolean hasLos = grid.hasLineOfFire(
                            world.x(u), world.y(u),
                            world.x(target), world.y(target));
                    accuracyMult = hasLos ? 1f : weapon.noLosAccuracyMult;
                }
                fireMechWeapon(u, target, weapon, accuracyMult);
                mount.burstRemaining--;
                mount.burstTimer = weapon.burstSpacing;
                if (mount.burstRemaining == 0) mount.burstTargetId = 0L;
            }
        }
    }
}

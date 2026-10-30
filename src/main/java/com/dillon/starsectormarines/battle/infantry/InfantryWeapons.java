package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.combat.PointAimDispersion;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.combat.RangeFalloff;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vision.FiringExposure;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.Random;

/**
 * Handheld squad weapons — rifles, SMGs, DMRs (primary line tracers / kinetic
 * bullets) and rocket launchers (secondary, AoE). Owns the firing math + the
 * per-tick burst continuation pass for every infantry-class unit:
 * marines, militia, aliens, and any future squaddie with a primary
 * {@link WeaponDef}.
 *
 * <p>Burst continuation state lives on each {@code Entity}
 * ({@code burstRemaining} / {@code burstTimer} / {@code burstTargetId}) so a
 * shared subsystem instance can serve every unit without per-shooter scratch
 * space. Services are constructor-injected; the subsystem pushes events
 * (shots, projectiles, resolved-round impacts) through them without ever
 * holding a {@code BattleSimulation} reference.
 *
 * <p>Parallel structure to {@link com.dillon.starsectormarines.battle.air.AirSystem}:
 * the sim owns one instance and pumps it once per tick via {@link #tick}.
 */
public class InfantryWeapons {

    private final UnitRosterService roster;
    private final BallisticResolver resolver;
    private final ShotService shots;
    private final NavigationGrid grid;

    /**
     * Reused per-tick gather of the units with an active burst before the
     * continuation pass. Damage from {@code fireShot} now applies on a
     * delayed flight clock (see {@link ShotService.PendingImpact}), but a
     * unit can still die mid-loop from another source (detonation arrival,
     * a concurrently-resolving impact) and swap-and-pop the registry;
     * gathering first makes the iteration a snapshot so that release can't
     * reshuffle the slots out from under it. Only mid-burst units are gathered
     * (a small fraction), so the copy is cheap.
     */
    private final LongArrayList burstScratch = new LongArrayList();
    private boolean laneDiscipline;

    /** The battle's seeded stream — see {@code BattleSimulation.random()}. */
    private final Random rng;

    /** Where a shot fired down off higher ground exposes its shooter. */
    private final FiringExposure exposure;

    public InfantryWeapons(UnitRosterService roster, BallisticResolver resolver,
                           ShotService shots, NavigationGrid grid, Random rng) {
        this(roster, resolver, shots, grid, rng, new FiringExposure());
    }

    public InfantryWeapons(UnitRosterService roster, BallisticResolver resolver,
                           ShotService shots, NavigationGrid grid, Random rng, FiringExposure exposure) {
        this.exposure = exposure;
        this.roster = roster;
        this.resolver = resolver;
        this.shots = shots;
        this.grid = grid;
        this.rng = rng;
    }

    /**
     * Whether a trained shooter checks the lane before each follow-up round of a burst
     * ({@link BallisticResolver#friendInLane}); the first round's check is {@code FiringSystem}'s.
     */
    public void setLaneDiscipline(boolean laneDiscipline) {
        this.laneDiscipline = laneDiscipline;
    }

    /**
     * Per-tick burst-fire pass: every unit with {@code burstRemaining > 0}
     * decrements its {@code burstTimer}; on expiry, fires another shot at the
     * locked burst target (if still alive and not friendly) and decrements the count.
     */
    public void tick() {
        // Gather the mid-burst units first (read-only over the dense registry),
        // then run the continuation pass over the snapshot — see burstScratch.
        burstScratch.clear();
        World world = roster.world();
        long[] dense = roster.denseArray();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            if (!roster.identity().type(dense[i]).combatant) continue; // non-combatants carry no COMBAT (no burst)
            if (world.burstRemaining(dense[i]) > 0) burstScratch.add(dense[i]);
        }
        for (int i = 0, n = burstScratch.size(); i < n; i++) {
            long u = burstScratch.getLong(i);
            if (!roster.isAliveById(u)) continue; // killed earlier this pass
            long id = u;
            // Alive + in burstScratch ⟹ combatant (gather gated on type.combatant),
            // so the COMBAT primary-weapon read is safe by id.
            WeaponDef weapon = roster.combat().primaryWeaponDef(id);
            if (world.burstRemaining(id) <= 0) continue; // cleared earlier this pass
            float timer = world.burstTimer(id) - BattleSimulation.TICK_DT;
            world.setBurstTimer(id, timer);
            if (timer > 0f) continue;
            long burstTargetId = world.burstTargetId(id);
            PointFireAim pointAim = roster.combat().burstPointAim(id);
            if (pointAim != null) {
                if (!PointFireAim.supports(weapon)
                        || !pointAim.validFrom(world.renderX(id), world.renderY(id))) {
                    roster.combat().clearPrimaryFire(id);
                    continue;
                }
                boolean moving = world.hasMovement(id)
                        && (roster.movement().velX(id) != 0f || roster.movement().velY(id) != 0f);
                firePointShot(id, pointAim, FireStance.stanceFor(moving));
                int remaining = world.burstRemaining(id) - 1;
                world.setBurstRemaining(id, remaining);
                world.setBurstTimer(id, weapon.burstSpacing);
                if (remaining == 0) roster.combat().setBurstPointAim(id, null);
                continue;
            }
            if (!roster.isAliveById(burstTargetId) || weapon == null
                    || roster.identity().faction(id).friendlyTo(roster.identity().faction(burstTargetId))) {
                world.setBurstRemaining(id, 0);
                world.setBurstTargetId(id, 0L);
                continue;
            }
            // Burst follow-up: use the unit's current motion state. If they
            // walked off the firing position mid-burst, the remaining rounds
            // get the MOVING accuracy penalty — same rule a hand-rolled
            // moving-fire callsite gets.
            //
            // Invariant: this read is unguarded but safe under MOVEMENT
            // membership-narrowing because only a mover reaches this pass. A
            // static emplacement (turret/hub) has no MOVEMENT, but it also never
            // writes the COMBAT burst columns — CombatService.beginBurst is infantry/
            // mech/drone-only, and a turret tracks its burst on its own
            // TURRET_STATE component (battle.sim.TurretStateService) — so its
            // burstRemaining stays 0 and it never enters burstScratch above. If
            // turrets are ever rewired to burst via the COMBAT columns, gate
            // this on world.hasMovement(id) (a non-mover is always STANCED).
            // The lane is checked again for every round of the burst: a friend who stepped into it since the
            // first round ends the burst rather than taking the rest of it.
            if (laneDiscipline && roster.identity().type(id).usesInfantryTraining()
                    && resolver.friendInLane(id, burstTargetId)) {
                world.setBurstRemaining(id, 0);
                world.setBurstTargetId(id, 0L);
                continue;
            }
            fireShot(id, burstTargetId, FireStance.stanceFor(!roster.movement().settled(id)));
            // Combat state is keyed by entity id, so a killing round that
            // swap-and-pops the dense registry (relocating u's slot) can't
            // invalidate these post-fire writes — no slot re-resolve needed.
            int remaining = world.burstRemaining(id) - 1;
            world.setBurstRemaining(id, remaining);
            world.setBurstTimer(id, weapon.burstSpacing);
            if (remaining == 0) world.setBurstTargetId(id, 0L);
        }
        burstScratch.clear();
    }

    /**
     * Fires the shooter's primary at the target. Per-shot accuracy / damage /
     * vsTurret pull from the marine's {@link WeaponDef} when assigned;
     * otherwise from the {@code Entity}'s baked-in stats (militia, aliens,
     * turrets — all callers without a per-primary definition). Accuracy is multiplied
     * by {@code stance.accuracyMult} — STANCED preserves the base roll,
     * MOVING applies the on-the-move suppression penalty. Cover is NOT part
     * of this accuracy stack — {@link BallisticResolver} re-expresses it as
     * physical interception along the round's flight (see
     * {@code ballistics-nouns.md}).
     *
     * <p>The round's full flight is resolved once, here, at fire time; damage
     * and hit-response are NOT applied inline. A victim contact queues a
     * {@link ShotService.PendingImpact} that {@code BattleSimulation} drains
     * on the round's flight clock, in the serial SHOTS phase.
     *
     * <p>Public because behaviors call this when firing.
     */
    public void fireShot(long shooter, long target, FireStance stance) {
        fireShot(shooter, target, stance, rng, false);
    }

    /** Same payload and presentation pipeline, with no target-conditioned hit or lead. */
    public void firePointShot(long shooter, PointFireAim aim, FireStance stance) {
        if (!PointFireAim.supports(roster.combat().primaryWeaponDef(shooter))
                || aim == null || !aim.validFrom(roster.world().renderX(shooter), roster.world().renderY(shooter))) return;
        firePrimary(shooter, 0L, aim, stance, rng, false);
    }

    /**
     * The same shot, fired on a range, where nothing but the butts may be hurt.
     *
     * <p>A drill is a real shot in every respect a viewer can see — the round
     * leaves the muzzle, flies, and strikes the target frame through the
     * ordinary pipeline, which is the whole point of resolving practice rather
     * than posing it. What it may not do is put the ship's company in the sick
     * bay. Ordinary fire has friendly contacts, damped by a damage multiplier
     * and a discipline roll but real; on a firing line whose lanes are a couple
     * of cells apart, fired every few seconds for as long as the ship is under
     * way, damped and real converges on certain. A seeded capital ran four
     * minutes and buried a marine.
     *
     * <p>Suppressed at the impact rather than at the trajectory, so the round
     * still passes down the lane it was aimed along and the shot looks like a
     * shot. It is the deck's own equipment being fired at the deck's own paper;
     * the one thing a drill must not do is cost the ship a hand.
     */
    public void fireDrillShot(long shooter, long target, FireStance stance) {
        fireShot(shooter, target, stance, rng, true);
    }

    /**
     * Deterministic seam for the committed ballistic outcome and the
     * experience-scaled trigger-discipline roll. Package-visible for tests;
     * production callers share the battle's seeded stream across both rolls,
     * preserving replay determinism while the hold decision occurs only after
     * the trajectory is already resolved.
     */
    void fireShot(long shooter, long target, FireStance stance, Random rng) {
        fireShot(shooter, target, stance, rng, false);
    }

    /**
     * @param drill whether this is practice, in which case nothing but
     *     {@code target} may take damage; see {@link #fireDrillShot}
     */
    void fireShot(long shooter, long target, FireStance stance, Random rng, boolean drill) {
        firePrimary(shooter, target, null, stance, rng, drill);
    }

    private void firePrimary(long shooter, long target, PointFireAim aim,
                             FireStance stance, Random rng, boolean drill) {
        World world = roster.world();
        Faction shooterFaction = roster.identity().faction(shooter);
        UnitType shooterType = roster.identity().type(shooter);
        // A shooter firing its primary is a combatant — the COMBAT primary-weapon
        // read is safe by id (null = militia/alien/turret, fall back to baked stats).
        WeaponDef weapon = roster.combat().primaryWeaponDef(shooter);
        float accuracy = world.accuracy(shooter);
        float damage   = world.attackDamage(shooter);
        float penetration = 0f;
        // Distance-scaled accuracy + spread only apply when the shooter has
        // a per-weapon profile (marines). Militia / aliens / turrets fall
        // through to their baked Entity stats with flat accuracy and no
        // lateral spread.
        float maximumTargetingRange = world.attackRange(shooter);
        // Point aim defines only a bearing. Calibrate angular dispersion at weapon
        // range so moving the cursor closer cannot tighten distant impacts.
        float dist = aim != null ? maximumTargetingRange
                : RangeFalloff.dist(world.x(shooter), world.y(shooter), world.x(target), world.y(target));
        float effectiveSpread = 0f;
        if (weapon != null) {
            accuracy = RangeFalloff.accuracy(world.accuracy(shooter),
                    weapon.accuracyFalloff, dist, maximumTargetingRange);
            damage   = world.attackDamage(shooter);
            penetration = weapon.penetration;
            effectiveSpread = RangeFalloff.spread(
                    InfantryCombatStats.spread(weapon,
                            roster.combat().equipmentGrade(shooter),
                            roster.combat().soldierProfile(shooter)),
                    dist, maximumTargetingRange);
        }
        // A planted suit's steadier aim is already in world.accuracy, through its modifier.
        accuracy = Math.min(1f, accuracy * stance.accuracyMult);
        // Recoil from the rounds just fired, recovering between them; zero for a weapon without a magazine.
        if (weapon != null && weapon.magazine() != null) accuracy *= 1f - roster.combat().recoil(shooter);

        StructureDef turretStructure = shooterType.isTurret()
                ? roster.turretState().structure(shooter) : null;
        // Round velocity: weapon-owned where available; null-weapon militia /
        // alien callers use the shared default, while static turrets resolve
        // through their kind's direct-fire timing.
        float roundVelocity = weapon != null && weapon.roundVelocity > 0f
                ? weapon.roundVelocity
                : turretStructure != null
                        ? turretStructure.mount.weapon.directRoundVelocity()
                        : BallisticResolver.DEFAULT_ROUND_VELOCITY;

        int projectileCount = weapon != null ? weapon.projectilesPerShot : 1;
        BallisticResolver.Resolution[] resolutions =
                new BallisticResolver.Resolution[projectileCount];
        boolean friendlyThreat = false;
        for (int i = 0; i < projectileCount; i++) {
            BallisticResolver.Resolution resolution = aim != null
                    ? resolver.resolvePoint(shooter, aim.x(), aim.y(), accuracy, effectiveSpread,
                            roundVelocity, maximumTargetingRange, PointAimDispersion.CONTROLLED, rng)
                    : resolver.resolve(shooter, target, accuracy, effectiveSpread, roundVelocity,
                            maximumTargetingRange, rng);
            resolutions[i] = resolution;
            friendlyThreat |= resolution.friendlyHit();
        }

        // Resolve the whole trigger pull first, then decide whether this
        // soldier recognizes a bad sight picture in time to hold it. A
        // multi-projectile release is one trigger decision: any committed friendly
        // contact makes the entire release eligible for one discipline roll.
        // The caller still consumes the firing opportunity and burst slot.
        if (friendlyThreat && shooterType.usesInfantryTraining()) {
            ExperienceTier experience = roster.combat().soldierProfile(shooter)
                    .experienceTier();
            if (rng.nextFloat() < experience.friendlyFireHoldChance) return;
        }
        // The release leaves the muzzle: it costs a round of the load (one per trigger, however many projectiles).
        if (weapon != null) roster.combat().spendPrimaryRound(shooter);

        float moraleImpact = shooterType != null ? shooterType.moraleImpact : 1.0f;
        float fromX = world.renderX(shooter);
        float fromY = world.renderY(shooter);
        // A shot down off higher ground gives the shooter away to the ground it fired on.
        float toX = aim != null ? aim.x() : world.x(target), toY = aim != null ? aim.y() : world.y(target);
        exposure.noteShot(grid, shooter, world.cellX(shooter), world.cellY(shooter),
                (int) Math.floor(toX), (int) Math.floor(toY));
        for (BallisticResolver.Resolution res : resolutions) {
            roster.telemetry().recordRoundFired(shooter);
            if (res.victimId() != 0L && (!drill || res.victimId() == target)) {
                // Friendly-fire damage is pre-multiplied at queue time (see
                // PendingImpact's javadoc) — the sink applies it as-is.
                float appliedDamage = res.friendlyHit()
                        ? damage * BallisticResolver.FRIENDLY_FIRE_DAMAGE_MULT
                        : damage;
                shots.queueImpact(new ShotService.PendingImpact(res.victimId(), shooter,
                        res.flightTime(), appliedDamage, penetration, moraleImpact,
                        res.friendlyHit(), res.victimPart()).at(res));
            }
            // A round that stopped in cover or a wall wears it; drill rounds harm nothing but their target.
            if (!drill) shots.queueStrike(res, shooter, damage, penetration);

            // One visible traveling body per projectile. Simultaneous
            // projectiles share the muzzle timestamp but keep independent
            // physical endpoints and interception outcomes.
            float lifetime = Math.max(res.flightTime(), 0.05f);
            boolean struckUnit = res.victimId() != 0L;
            if (turretStructure != null) {
                shots.postShot(new ShotEvent(fromX, fromY, 0f,
                        res.endX(), res.endY(), res.endZ(),
                        res.hitIntended(), shooterFaction, lifetime,
                        turretStructure, null, null, null, moraleImpact, struckUnit,
                        res.kind(), shooter));
            } else {
                shots.postShot(ShotEvent.primary(fromX, fromY, 0f,
                        res.endX(), res.endY(), res.endZ(),
                        res.hitIntended(), shooterFaction, lifetime, weapon,
                        moraleImpact, struckUnit, res.kind(), shooter));
            }
        }
    }

    /**
     * Fires the shooter's secondary (rocket launcher today). Resolves a
     * modeled direct round, decrements ammo, and spawns a simulated-flight
     * {@link Projectile}. Physical contacts carry the AoE
     * {@link PendingDetonation}; an overshoot carries no payload. A marine who
     * moves between launch and impact escapes the splash.
     *
     * <p>Same Projectile shape that locust turrets use — the rocket is a
     * real in-flight entity, queryable by squad-coordination scorers
     * ({@link com.dillon.starsectormarines.battle.decision.TacticalScoring#shouldCommitRocket})
     * via {@code sim.getActiveProjectiles()} and (eventually) interceptable
     * by point defense. {@link SpecialEquipmentDef#roundVelocity()} derives a
     * cells/sec speed from the former maximum-range timing, so closer physical
     * stops arrive sooner.
     *
     * <p>The paired {@link ShotEvent} stays — it's what the renderer reads
     * for sprite + contrail + audio + impact-FX dispatch (unchanged from
     * the legacy queueDetonation path). The Projectile is the sim-side
     * source of truth; the ShotEvent is the visual-side mirror.
     *
     * <p>Caller is responsible for verifying ammo &gt; 0 and within-range
     * before calling.
     */
    public void fireSecondary(long shooter, long target) {
        World world = roster.world();
        Faction shooterFaction = roster.identity().faction(shooter);
        long shooterId = shooter;
        if (!world.hasSecondaryWeapon(shooterId)) return;
        SpecialEquipmentDef sec = world.specialEquipment(shooterId);
        if (!sec.isDirectFireWeapon()) return;
        int ammo = world.secondaryAmmo(shooterId);
        if (ammo <= 0) return;
        world.setSecondaryAmmo(shooterId, ammo - 1);
        roster.telemetry().recordSecondaryUsed(shooterId);
        float secondaryAccuracy = Math.min(1f, sec.accuracy()
                * InfantryCombatStats.shooterAccuracyMult(
                        roster.combat().soldierProfile(shooter)));
        // Rocket launches from the marine's current sprite position so the
        // launch FX glue to the sprite if the marine is mid-step. The same
        // modeled round as primaries now authors its endpoint, Z, obstacle
        // contacts, interveners, and real flight time. Secondaries carry no
        // additional miss-spread tuning today.
        float fromX = world.renderX(shooter);
        float fromY = world.renderY(shooter);
        BallisticResolver.Resolution res = resolver.resolve(shooter, target,
                secondaryAccuracy, 0f, sec.roundVelocity(), sec.range(), rng);
        deliverSecondary(shooter, shooterFaction, sec, res, fromX, fromY);
    }

    /**
     * Fires a carried direct-fire special at a world point. That is how a company opens a wall with rockets: the
     * round flies to the point or stops at the first wall in its way, and its detonation does the structural damage the
     * weapon's data says. A precision rifle's round flies the same lane and strikes the first body on it. It is also
     * a controlled Marine's left button on a special. Spends a round of the carrier's ammunition; the caller owns
     * range, line and cooldown. Returns whether a round left.
     */
    public boolean fireSecondaryAtPoint(long shooter, float aimX, float aimY) {
        World world = roster.world();
        if (!world.hasSecondaryWeapon(shooter)) return false;
        SpecialEquipmentDef sec = world.specialEquipment(shooter);
        if (!sec.isDirectFireWeapon()) return false;
        int ammo = world.secondaryAmmo(shooter);
        if (ammo <= 0) return false;
        float fromX = world.renderX(shooter);
        float fromY = world.renderY(shooter);
        PointFireAim aim = new PointFireAim(aimX, aimY);
        if (!aim.validFrom(fromX, fromY)) return false;
        world.setSecondaryAmmo(shooter, ammo - 1);
        roster.telemetry().recordSecondaryUsed(shooter);
        Faction shooterFaction = roster.identity().faction(shooter);
        float accuracy = Math.min(1f, sec.accuracy()
                * InfantryCombatStats.shooterAccuracyMult(roster.combat().soldierProfile(shooter)));
        BallisticResolver.Resolution res = resolver.resolvePoint(
                new BallisticResolver.Source(shooter, fromX, fromY, 0f, shooterFaction), aimX, aimY, accuracy, 0f,
                sec.roundVelocity(), sec.range(), 0, rng);
        deliverSecondary(shooter, shooterFaction, sec, res, fromX, fromY);
        return true;
    }

    private void deliverSecondary(long shooter, Faction shooterFaction, SpecialEquipmentDef sec,
                                  BallisticResolver.Resolution res, float fromX, float fromY) {
        World world = roster.world();
        roster.telemetry().recordRoundFired(shooter);
        if (sec.activation() == SpecialActivation.DIRECT_PRECISION) {
            if (res.victimId() != 0L) {
                float damage = res.friendlyHit()
                        ? sec.damage() * BallisticResolver.FRIENDLY_FIRE_DAMAGE_MULT
                        : sec.damage();
                shots.queueImpact(new ShotService.PendingImpact(
                        res.victimId(), shooter, res.flightTime(), damage,
                        sec.penetration(), roster.identity().type(shooter).moraleImpact,
                        res.friendlyHit(), sec, res.victimPart()).at(res));
            }
            shots.queueStrike(res, shooter, sec.damage(), sec.penetration());
            shots.postShot(ShotEvent.special(fromX, fromY, 0f,
                    res.endX(), res.endY(), res.endZ(),
                    res.hitIntended(), shooterFaction, Math.max(res.flightTime(), 0.05f),
                    sec, 1f, res.victimId() != 0L, res.kind(), shooter));
            return;
        }

        // Marine handheld rocket is direct-fire (no arc) — explodes wherever
        // it physically contacts. A free-flight overshoot keeps its visible
        // projectile entity but carries no phantom ground detonation.
        PendingDetonation onArrival = res.impacts()
                ? new PendingDetonation(
                        shooter,
                        res.endX(), res.endY(), res.flightTime(),
                        sec.aoeRadius(), sec.damage(), sec.penetration(),
                        sec.wallDamage(), shooterFaction, /*aerialDelivery*/ false,
                        sec.wallDamageRadius(), /*spawnDustOnWallBreak*/ true,
                        /*friendlyFireImmune*/ false).incendiary(sec.weaponDef().incendiaryHeat())
                : null;
        // hasBoostRamp=true: marine rocket is a launched missile with a
        // booster, matches locust's accelerate-from-rest visual curve.
        // arcHeight=0: direct-fire, no parabolic lob.
        shots.queueProjectile(new Projectile(fromX, fromY, res.endX(), res.endY(),
                /*hasBoostRamp*/ true, /*arcHeight*/ 0f,
                shooterFaction, /*aerialDelivery*/ false,
                res.flightTime(), onArrival, sec.weaponDef().id,
                sec.weaponDef().pointDefenseTarget));
        shots.postShot(ShotEvent.special(fromX, fromY, 0f,
                res.endX(), res.endY(), res.endZ(),
                res.hitIntended(), shooterFaction, Math.max(res.flightTime(), 0.05f),
                sec, 1f, res.victimId() != 0L, res.kind(), shooter));
    }

    /** Releases a short-arc, ground-targeted fragmentation grenade. */
    public void throwFragmentationGrenade(long carrier, float targetX, float targetY) {
        World world = roster.world();
        if (!world.hasSecondaryWeapon(carrier)) return;
        SpecialEquipmentDef grenade = world.specialEquipment(carrier);
        if (grenade.activation() != SpecialActivation.ARC_EXPLOSIVE) return;
        int ammo = world.secondaryAmmo(carrier);
        if (ammo <= 0) return;

        float fromX = world.renderX(carrier);
        float fromY = world.renderY(carrier);
        if (grenade.isHeldStream()) {
            // A flamer's jet leaves the wand's nozzle, ahead of the Marine, unless a wall stands there.
            float[] nozzle = FlameJet.nozzle(fromX, fromY, targetX, targetY);
            if (grid.hasLineOfFire(fromX, fromY, nozzle[0], nozzle[1])) {
                fromX = nozzle[0];
                fromY = nozzle[1];
            }
        }
        float dx = targetX - fromX;
        float dy = targetY - fromY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance > grenade.range() && distance > 0f) {
            targetX = fromX + dx / distance * grenade.range();
            targetY = fromY + dy / distance * grenade.range();
        }

        float handlingAccuracy = Math.min(1f, grenade.accuracy()
                * InfantryCombatStats.shooterAccuracyMult(
                roster.combat().soldierProfile(carrier)));
        if (rng.nextFloat() > handlingAccuracy) {
            float angle = rng.nextFloat() * (float) (Math.PI * 2.0);
            float radius = (float) Math.sqrt(rng.nextFloat()) * grenade.hitSpread();
            targetX += (float) Math.cos(angle) * radius;
            targetY += (float) Math.sin(angle) * radius;
        }
        targetX = Math.max(0.5f, Math.min(grid.getWidth() - 0.5f, targetX));
        targetY = Math.max(0.5f, Math.min(grid.getHeight() - 0.5f, targetY));
        float landingDx = targetX - fromX;
        float landingDy = targetY - fromY;
        float landingDistance = (float) Math.sqrt(landingDx * landingDx + landingDy * landingDy);
        float flightTime = Math.max(0.15f, landingDistance / grenade.roundVelocity());
        Faction faction = roster.identity().faction(carrier);

        world.setSecondaryAmmo(carrier, ammo - 1);
        roster.telemetry().recordSecondaryUsed(carrier);
        roster.telemetry().recordRoundFired(carrier);
        PendingDetonation payload = new PendingDetonation(carrier,
                targetX, targetY, flightTime, grenade.aoeRadius(),
                grenade.damage(), grenade.penetration(), grenade.wallDamage(),
                faction, /*aerialDelivery*/ false, grenade.wallDamageRadius(),
                /*spawnDustOnWallBreak*/ false, /*friendlyFireImmune*/ false)
                .incendiary(grenade.weaponDef().incendiaryHeat());
        shots.queueProjectile(new Projectile(fromX, fromY, targetX, targetY,
                /*hasBoostRamp*/ false, grenade.arcHeight(), faction,
                /*aerialDelivery*/ false, flightTime, payload, grenade.weaponDef().id,
                grenade.weaponDef().pointDefenseTarget));
        shots.postShot(ShotEvent.special(fromX, fromY, 0f,
                targetX, targetY, 0f, false, faction, flightTime,
                grenade, roster.identity().type(carrier).moraleImpact,
                false, null, carrier));
    }
}

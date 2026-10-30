package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.control.ManualIntent;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.MovementService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.MissilePodMountGeometry;
import com.dillon.starsectormarines.marine.MissilePodSpec;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

import java.io.Serializable;
import java.util.Random;
import java.util.TreeMap;

/**
 * The shoulder pod's sweep: every carrier of a {@code shoulder-pod} special in the hand slot is
 * offered its moment, {@code sighted-standoff-contact}, once per tick
 * ({@code progression-nouns.md}, Shoulder pod).
 *
 * <p><b>It never takes the hands.</b> A salvo is resolved and committed here, outside the hand's
 * aim-then-fire channel: nothing writes the slot's action timer, so the carrier keeps holding and
 * firing the primary through every launch. What it does spend is the slot's own ammunition and
 * cooldown (the referenced weapon's {@link WeaponDef#cooldown}), exactly as any other carried
 * special does.
 *
 * <p><b>The pod picks its own target.</b> It does not read the carrier's engaged target
 * ({@code World#targetId}); it scores the best visible hostile within the weapon's range and no
 * closer than the authored standoff, so it never spends a finite salvo on something the rifle
 * already has in hand.
 *
 * <p><b>Squad scope.</b> Carriers in one squad share a {@link SquadPodCadence}: all of this tick's
 * eligible carriers are evaluated before one successor is chosen, so dense-roster order cannot
 * monopolize the pod, and committed salvos are spaced by the item's authored interval
 * ({@code ai-nouns.md}).
 *
 * <p>It runs ahead of the movement pass, where applied velocity is still the zero
 * {@code MovementService.beginTick} wrote, so body bearing reads intent: direct-control aim, the
 * engaged target, a manual step's legal proposal, or the route's next waypoint.
 */
public final class MissilePodSystem {

    /** How long a committed launch drives the mount's recoil and muzzle flash. */
    public static final float LAUNCH_SECONDS = 0.3f;

    /** The actual committed source and bearings, retained only for the short launch pulse. */
    public record Launch(float originX, float originY, float bodyBearingDegrees,
                         float bearingDegrees, float remainingSeconds) implements Serializable {
        public float phase() { return Math.max(0f, Math.min(1f, 1f - remainingSeconds / LAUNCH_SECONDS)); }
    }

    /** Below this, the carrier is standing still. */
    private static final float MOVING_EPSILON = 1e-3f;

    private final UnitRosterService rosterService;
    private final BallisticResolver resolver;
    private final ShotService shots;
    private final Random rng;
    private final TreeMap<Integer, Candidate> candidates = new TreeMap<>();
    private final IntegralSystemSystem.PolicyMotion motionScratch =
            new IntegralSystemSystem.PolicyMotion(false, 0f, 0f);
    private boolean laneDiscipline;

    private record Candidate(long shooter, long target, float bodyBearing,
                             MissilePodMountGeometry.Pose mount, SpecialEquipmentDef pod, Squad squad) {}

    public MissilePodSystem(UnitRosterService rosterService, BallisticResolver resolver,
                            ShotService shots, Random rng) {
        this.rosterService = rosterService;
        this.resolver = resolver;
        this.shots = shots;
        this.rng = rng;
    }

    /**
     * Whether a pod holds a salvo with a friend in its lane, or one whose resolved burst would reach a friend or
     * the carrier ({@link BallisticResolver#friendInLane}, {@link BallisticResolver#friendInBlast}). A held salvo
     * spends nothing and is offered again on the next tick.
     */
    public void setLaneDiscipline(boolean laneDiscipline) {
        this.laneDiscipline = laneDiscipline;
    }

    /** Drains launch pulses and offers every ready carrier a reason to fire. */
    public void tick(float dt, BattleSimulation sim) {
        World world = rosterService.world();
        MovementService movement = rosterService.movement();
        long[] live = rosterService.denseArray();
        int count = rosterService.liveCount();
        int tick = sim.getSimTickIndex();
        candidates.clear();
        for (int i = 0; i < count; i++) {
            long id = live[i];
            if (!world.hasSecondaryWeapon(id)) continue;
            drainLaunch(world, id, dt);
            SpecialEquipmentDef pod = world.specialEquipment(id);
            if (!ready(world, id, pod)) continue;
            Squad squad = rosterService.squad().hasSquad(id)
                    ? sim.getSquad(rosterService.squad().squadId(id)) : null;
            if (squad != null && !squad.podCadence.ready(tick)) continue;
            long target = standoffTarget(id, pod, sim);
            if (target == 0L) continue;
            ManualTerrainMotion.Result manualStep = null;
            if (sim.directControl().isControlling(id)) {
                ManualIntent intent = sim.directControl().intent();
                manualStep = movement.previewDirect(id, sim.getGrid(), intent.moveX(),
                        intent.moveY(), sim.physicalRadius(id), dt);
            }
            IntegralSystemSystem.PolicyMotion motion =
                    IntegralSystemSystem.policyMotion(motionScratch, id, movement, manualStep, dt);
            float bodyBearing = bodyBearing(id, sim, motion);
            MissilePodSpec spec = pod.missilePodSpec();
            MissilePodMountGeometry.Pose mount = pose(id, target, bodyBearing, spec);
            // A protruding mount must never teleport a round across a wall.
            // Prove the body-to-mouth segment before spending ammunition.
            boolean legalOrigin = spec.mount() == null || legalPodOrigin(sim.getGrid(),
                    world.renderX(id), world.renderY(id), mount);
            // A squadmate between the rail and the target is where the round goes first.
            boolean clearLane = !laneDiscipline || !resolver.friendInLane(id, mount.muzzleX(),
                    mount.muzzleY(), rosterService.identity().faction(id),
                    world.renderX(target), world.renderY(target), target);
            if (!legalOrigin || !clearLane) continue;
            Candidate candidate = new Candidate(id, target, bodyBearing, mount, pod, squad);
            if (squad == null) {
                launch(candidate, tick);
            } else {
                Candidate incumbent = candidates.get(squad.id);
                if (squad.podCadence.prefers(id, incumbent == null ? 0L : incumbent.shooter())) {
                    candidates.put(squad.id, candidate);
                }
            }
        }
        // Every carrier's timers and live legality are evaluated before a successor is chosen.
        for (Candidate candidate : candidates.values()) launch(candidate, tick);
        candidates.clear();
    }

    /** A pod in the hand with a salvo left and its cooldown run down. The hand's channel is never consulted. */
    static boolean ready(World world, long id, SpecialEquipmentDef pod) {
        return pod != null && pod.isShoulderPod() && world.secondaryAmmo(id) > 0
                && world.secondaryCooldownTimer(id) <= 0f;
    }

    private static void drainLaunch(World world, long id, float dt) {
        Launch launch = world.podLaunch(id);
        if (launch == null) return;
        float remaining = launch.remainingSeconds() - dt;
        world.setPodLaunch(id, remaining > 0f ? new Launch(launch.originX(), launch.originY(),
                launch.bodyBearingDegrees(), launch.bearingDegrees(), remaining) : null);
    }

    private void launch(Candidate candidate, int tick) {
        long shooter = candidate.shooter();
        WeaponDef weapon = candidate.pod().weaponDef();
        BallisticResolver.Resolution[] salvo = resolveSalvo(shooter, candidate.target(), weapon,
                candidate.mount());
        // Where each round will actually stop, cover clipped near the rail included: a salvo with any
        // burst reaching a friend or the carrier stays on the rail.
        if (laneDiscipline && salvoEndangersFriends(shooter, weapon, salvo)) return;
        World world = rosterService.world();
        world.setSecondaryAmmo(shooter, Math.max(0, world.secondaryAmmo(shooter) - 1));
        world.setSecondaryCooldownTimer(shooter, weapon.cooldown);
        fire(shooter, weapon, candidate.mount(), salvo);
        world.setPodLaunch(shooter, new Launch(candidate.mount().muzzleX(), candidate.mount().muzzleY(),
                candidate.bodyBearing(), candidate.mount().bearingDegrees(), LAUNCH_SECONDS));
        if (candidate.squad() != null) {
            candidate.squad().podCadence.committed(shooter, tick,
                    candidate.pod().missilePodSpec().squadSalvoIntervalSeconds(), BattleSimulation.TICK_DT);
        }
    }

    /**
     * The pod's own pick, independent of whatever the primary is engaging: the best visible hostile within the
     * weapon's range, scored by {@link TacticalScoring} as any other mount acquires a target, and no closer than the
     * authored standoff. {@code 0L} when nothing qualifies; the pod never fires blind.
     */
    private long standoffTarget(long id, SpecialEquipmentDef pod, BattleSimulation sim) {
        World world = rosterService.world();
        Faction faction = rosterService.identity().faction(id);
        if (faction == null) return 0L;
        int squadId = rosterService.squad().hasSquad(id)
                ? rosterService.squad().squadId(id) : Squad.NO_SQUAD;
        // The authored standoff is the scorer's own minimum range, so a closer
        // contact is never a candidate rather than being picked and discarded.
        return sim.getTacticalScoring().findBestTargetWithinRange(
                world.x(id), world.y(id), faction, squadId, id,
                rosterService.vision().airLosRadius(id), /*allowNoLos*/ false,
                pod.missilePodSpec().minimumStandoffCells(), pod.weaponDef().range());
    }

    /**
     * Launches the whole salvo at once: {@link WeaponDef#projectilesPerShot()} independently resolved missiles,
     * each a real traveling {@link Projectile} carrying the weapon's own {@link PendingDetonation}. The same
     * splash and friendly-fire pipeline as every other explosive round ({@code moddable-weapons-nouns.md}).
     */
    private void fire(long shooter, WeaponDef weapon, MissilePodMountGeometry.Pose mount,
                      BallisticResolver.Resolution[] salvo) {
        Faction shooterFaction = rosterService.identity().faction(shooter);
        float fromX = mount.muzzleX();
        float fromY = mount.muzzleY();
        for (BallisticResolver.Resolution res : salvo) {
            rosterService.telemetry().recordRoundFired(shooter);
            PendingDetonation onArrival = res.impacts()
                    ? new PendingDetonation(shooter, res.endX(), res.endY(), res.flightTime(),
                            weapon.aoeRadius, weapon.damage(), weapon.penetration(),
                            weapon.wallDamage, shooterFaction, /*aerialDelivery*/ false,
                            weapon.wallDamageRadius, /*spawnDustOnWallBreak*/ false,
                            /*friendlyFireImmune*/ false).withBlast(weapon.blast())
                    : null;
            shots.queueProjectile(new Projectile(fromX, fromY, res.endX(), res.endY(),
                    /*hasBoostRamp*/ true, /*arcHeight*/ 0f, shooterFaction,
                    /*aerialDelivery*/ false, res.flightTime(), onArrival, weapon.id,
                    weapon.pointDefenseTarget));
            shots.postShot(ShotEvent.primary(fromX, fromY, 0f,
                    res.endX(), res.endY(), res.endZ(), res.hitIntended(), shooterFaction,
                    Math.max(res.flightTime(), 0.05f), weapon, 1f,
                    res.victimId() != 0L, res.kind(), shooter));
        }
        rosterService.telemetry().recordSecondaryUsed(shooter);
    }

    /** Every missile of one salvo, each independently resolved from the muzzle, before any of it is committed. */
    private BallisticResolver.Resolution[] resolveSalvo(long shooter, long target, WeaponDef weapon,
                                                        MissilePodMountGeometry.Pose mount) {
        BallisticResolver.Source source = new BallisticResolver.Source(shooter, mount.muzzleX(),
                mount.muzzleY(), 0f, rosterService.identity().faction(shooter));
        BallisticResolver.Resolution[] salvo =
                new BallisticResolver.Resolution[Math.max(1, weapon.projectilesPerShot())];
        for (int i = 0; i < salvo.length; i++) {
            salvo[i] = resolver.resolve(source, target, weapon.accuracy(), weapon.hitSpread(),
                    weapon.roundVelocity(), weapon.range(), rng);
        }
        return salvo;
    }

    /** Whether any round of {@code salvo} would burst where a friend's footprint, or the carrier's, reaches. */
    private boolean salvoEndangersFriends(long shooter, WeaponDef weapon,
                                          BallisticResolver.Resolution[] salvo) {
        if (weapon.aoeRadius <= 0f) return false;
        Faction side = rosterService.identity().faction(shooter);
        for (BallisticResolver.Resolution res : salvo) {
            if (res.impacts() && resolver.friendInBlast(shooter, side, res.endX(), res.endY(),
                    weapon.aoeRadius)) return true;
        }
        return false;
    }

    static boolean legalPodOrigin(NavigationGrid grid, float bodyX, float bodyY,
                                  MissilePodMountGeometry.Pose mount) {
        int x = (int) Math.floor(mount.muzzleX());
        int y = (int) Math.floor(mount.muzzleY());
        if (x < 0 || y < 0 || x >= grid.getWidth() || y >= grid.getHeight()
                || grid.blocksStructuralLineOfSightAt(grid.index(x, y))) return false;
        return grid.firstWallOnLine(bodyX, bodyY, mount.muzzleX(), mount.muzzleY()) == -1L
                && grid.firstProjectileBlockingEdgeBarrierOnLine(bodyX, bodyY,
                        mount.muzzleX(), mount.muzzleY()) == null;
    }

    private MissilePodMountGeometry.Pose pose(long id, long target, float bodyBearing, MissilePodSpec spec) {
        World world = rosterService.world();
        float x = world.renderX(id);
        float y = world.renderY(id);
        MissilePodMountGeometry.Pose initial = MissilePodMountGeometry.resolve(x, y, bodyBearing,
                bodyBearing, spec.mount());
        float bearing = MissilePodMountGeometry.bearing(world.renderX(target) - initial.mountX(),
                world.renderY(target) - initial.mountY());
        return MissilePodMountGeometry.resolve(x, y, bodyBearing, bearing, spec.mount());
    }

    /** Reads simulation intent; a mount never depends on an animation pose or a loaded texture. */
    private float bodyBearing(long id, BattleSimulation sim, IntegralSystemSystem.PolicyMotion motion) {
        World world = rosterService.world();
        if (sim.directControl().isControlling(id)) {
            ManualIntent intent = sim.directControl().intent();
            if (Float.isFinite(intent.aimX()) && Float.isFinite(intent.aimY())) {
                return MissilePodMountGeometry.bearing(intent.aimX() - world.renderX(id),
                        intent.aimY() - world.renderY(id));
            }
        }
        long target = world.hasCombat(id) ? world.targetId(id) : 0L;
        if (target != 0L && rosterService.isAliveById(target)) {
            return MissilePodMountGeometry.bearing(world.renderX(target) - world.renderX(id),
                    world.renderY(target) - world.renderY(id));
        }
        if (Math.abs(motion.vx()) > MOVING_EPSILON || Math.abs(motion.vy()) > MOVING_EPSILON) {
            return MissilePodMountGeometry.bearing(motion.vx(), motion.vy());
        }
        // Applied velocity is zero before this sweep. A live AI route still
        // owns its intended heading, including continuous waypoint coordinates.
        if (!sim.directControl().isControlling(id)) {
            float travel = travelBearing(id, rosterService.movement(), world.renderX(id), world.renderY(id));
            if (Float.isFinite(travel)) return travel;
        }
        return 0f;
    }

    static float travelBearing(long id, MovementService movement, float x, float y) {
        if (!movement.has(id) || movement.settled(id)) return Float.NaN;
        int next = movement.nextWaypointIndex(id);
        int count = movement.waypointCount(id);
        while (next < count) {
            float dx = movement.waypointX(id, next) - x;
            float dy = movement.waypointY(id, next) - y;
            if (Math.abs(dx) > MOVING_EPSILON || Math.abs(dy) > MOVING_EPSILON) {
                return MissilePodMountGeometry.bearing(dx, dy);
            }
            next++;
        }
        return Float.NaN;
    }
}

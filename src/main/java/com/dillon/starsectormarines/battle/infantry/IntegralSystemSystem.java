package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.MovementService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.IntegralSystemEffect;
import com.dillon.starsectormarines.marine.MissilePodSpec;

import java.util.Random;

/**
 * Stateless per-tick sweep over the units whose armour pattern carries a
 * capability: drains their clocks, drops an expired effect, and decides when a
 * system is worth spending ({@code integral-armor-systems.md}).
 *
 * <p>A <b>System</b> (processor) — the live state lives on
 * {@link IntegralSystemService}, which this drives. It runs as its own sweep
 * rather than inside a behaviour's prep so a unit's clocks drain no matter what
 * that unit is doing; the cooldown-drain-during-a-long-approach lesson from
 * {@code InfantryUnitPrep.tickCooldowns} applies here for the same reason.
 *
 * <p><b>The use policy here is deliberately the crude one.</b> A breacher
 * spends its assist when it is actually moving and hostiles are close enough to
 * make that movement expensive; a missile pod spends a salvo the instant it can
 * see something worth spending it on. The authored AI-policy vocabulary that
 * special equipment uses is the intended home for both decisions
 * ({@code integral-system-use-policy.md}); until an integral system declares
 * one, this sweep is the whole policy and says so.
 *
 * <p><b>The pod picks its own target.</b> It does not read the wearer's
 * engaged target ({@code World#targetId}) — that would make the suit a second
 * trigger on the marine's own fight. A capability that starts choosing its own
 * fights is a deliberate step past "the suit changes how its wearer moves"
 * ({@code integral-armor-systems.md}'s open question, resolved this way
 * because it is the more interesting half of the model to prove out.
 */
public final class IntegralSystemSystem {

    /**
     * How close a hostile must be for crossing open ground to be worth a
     * charge. Deliberately wider than a marine's own reach — the assist is for
     * getting somewhere under fire, so the threat that justifies it is one that
     * can already shoot at the crossing.
     */
    static final float THREAT_RADIUS_CELLS = 12f;

    /** Below this, the unit is standing still and has nothing to charge through. */
    private static final float MOVING_EPSILON = 1e-3f;

    private final UnitRosterService rosterService;
    private final BallisticResolver resolver;
    private final ShotService shots;
    private final Random rng;
    private final LongBucket nearbyHostiles = new LongBucket();

    public IntegralSystemSystem(UnitRosterService rosterService, BallisticResolver resolver,
                                ShotService shots, Random rng) {
        this.rosterService = rosterService;
        this.resolver = resolver;
        this.shots = shots;
        this.rng = rng;
    }

    /** Drains every carrier's clocks, then offers the ready ones a reason to fire. */
    public void tick(float dt, BattleSimulation sim) {
        IntegralSystemService systems = rosterService.integralSystems();
        MovementService movement = rosterService.movement();
        long[] live = rosterService.denseArray();
        int count = rosterService.liveCount();
        for (int i = 0; i < count; i++) {
            long id = live[i];
            if (!systems.has(id)) continue;
            systems.tick(id, dt);
            if (!systems.canActivate(id)) continue;
            IntegralSystemDef def = systems.spec(id);
            if (def == null) continue;
            switch (def.effect()) {
                case BREACHER_ASSIST -> {
                    if (isMoving(id, movement) && hostileWithin(id, sim, THREAT_RADIUS_CELLS)) {
                        systems.activate(id);
                    }
                }
                case MISSILE_POD -> {
                    long target = missilePodTarget(id, def, sim);
                    if (target != 0L && systems.activate(id)) {
                        fireMissilePod(id, target, def.missilePod());
                    }
                }
            }
        }
    }

    private static boolean isMoving(long id, MovementService movement) {
        float vx = movement.velX(id);
        float vy = movement.velY(id);
        return Math.abs(vx) > MOVING_EPSILON || Math.abs(vy) > MOVING_EPSILON;
    }

    private boolean hostileWithin(long id, BattleSimulation sim, float radius) {
        UnitSpatialIndex index = sim.getUnitIndex();
        if (index == null) return false;
        Faction faction = rosterService.identity().faction(id);
        if (faction == null) return false;
        nearbyHostiles.clear();
        index.gatherOtherFactionCombatants(
                sim.world().x(id), sim.world().y(id), radius, faction, nearbyHostiles);
        return nearbyHostiles.size > 0;
    }

    /**
     * The pod's own pick, independent of whatever the wearer's primary weapon
     * is engaging: the best visible hostile within the referenced weapon's
     * range, scored by {@link TacticalScoring} the same way any other mount
     * acquires a target it can actually reach. Returns {@code 0L} when nothing
     * qualifies, in which case the pod simply waits — it never fires blind.
     */
    private long missilePodTarget(long id, IntegralSystemDef def, BattleSimulation sim) {
        MissilePodSpec pod = def.missilePod();
        if (pod == null) return 0L;
        World world = rosterService.world();
        Faction faction = rosterService.identity().faction(id);
        if (faction == null) return 0L;
        int squadId = rosterService.squad().hasSquad(id)
                ? rosterService.squad().squadId(id) : Squad.NO_SQUAD;
        WeaponDef weapon = pod.weaponDef();
        return sim.getTacticalScoring().findBestTargetWithinRange(
                world.x(id), world.y(id), faction, squadId, id,
                rosterService.vision().airLosRadius(id), /*allowNoLos*/ false,
                0f, weapon.range());
    }

    /**
     * Launches the pod's whole salvo at once: {@link WeaponDef#projectilesPerShot()}
     * independently resolved missiles, each a real traveling {@link Projectile}
     * carrying the weapon's own {@link PendingDetonation} payload. Reuses the
     * same ballistic-resolution, splash, and friendly-fire pipeline every other
     * explosive round in the catalog goes through
     * ({@code moddable-weapons-nouns.md}) — a pod gets no exemption from the
     * collateral discipline the shipped specials already carry.
     */
    private void fireMissilePod(long shooter, long target, MissilePodSpec pod) {
        World world = rosterService.world();
        WeaponDef weapon = pod.weaponDef();
        Faction shooterFaction = rosterService.identity().faction(shooter);
        float fromX = world.renderX(shooter);
        float fromY = world.renderY(shooter);
        int missiles = Math.max(1, weapon.projectilesPerShot());
        for (int i = 0; i < missiles; i++) {
            BallisticResolver.Resolution res = resolver.resolve(shooter, target,
                    weapon.accuracy(), weapon.hitSpread(), weapon.roundVelocity(), rng);
            rosterService.telemetry().recordRoundFired(shooter);
            PendingDetonation onArrival = res.impacts()
                    ? new PendingDetonation(shooter, res.endX(), res.endY(), res.flightTime(),
                            weapon.aoeRadius, weapon.damage(), weapon.penetration(),
                            weapon.wallDamage, shooterFaction, /*aerialDelivery*/ false,
                            weapon.wallDamageRadius, /*spawnDustOnWallBreak*/ false,
                            /*friendlyFireImmune*/ false)
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
}

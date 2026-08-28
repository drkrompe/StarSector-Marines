package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.MovementService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.ApproachingDeadGroundSpec;
import com.dillon.starsectormarines.marine.CrossingUnderFireSpec;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.MissilePodSpec;
import com.dillon.starsectormarines.marine.PerceptionSweepSpec;
import com.dillon.starsectormarines.marine.SightedStandoffSpec;

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
 * <p><b>This sweep holds no judgement about any suit.</b> It dispatches on the
 * {@link com.dillon.starsectormarines.marine.SpecialAiPolicy} each system
 * declares and reads that system's own authored numbers to decide whether the
 * policy's moment has arrived. There is deliberately no threat radius, no range
 * band, and no per-effect special case left in this class: a constant here
 * would be one author's judgement about one suit imposed on every system that
 * will ever exist, which is exactly what the authored policy replaced
 * ({@code progression-nouns.md}).
 *
 * <p><b>Nothing here reads faction.</b> A defender in a system-carrying pattern
 * reaches this sweep through the same component the player's marines do and is
 * offered the same reason to spend it; there is no defender branch and no
 * defender-only tuning field to add one with.
 *
 * <p><b>A sweep reveals to the player and to nobody else.</b> Its whole effect
 * is a temporary observer on {@link FogOfWarService}, rebuilt from live state
 * every tick and gone the tick the window closes. No decision layer reads it,
 * because fog is presentation authority and must not become a simulation input
 * ({@code fog-of-war-nouns.md}) — which is also what makes "information, not
 * authority" true by construction here rather than by discipline: there is no
 * path from a sweep to a shot.
 *
 * <p><b>The pod picks its own target.</b> It does not read the wearer's
 * engaged target ({@code World#targetId}) — that would make the suit a second
 * trigger on the marine's own fight. A capability that starts choosing its own
 * fights is a deliberate step past "the suit changes how its wearer moves"
 * ({@code integral-armor-systems.md}'s open question, resolved this way
 * because it is the more interesting half of the model to prove out.
 */
public final class IntegralSystemSystem {

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

    /**
     * Drains every carrier's clocks, offers the ready ones a reason to fire,
     * and republishes what the running sweeps are currently letting the player
     * see.
     *
     * <p>The sweep set is <em>replaced</em>, never appended to: a temporary
     * source lives for exactly as long as the system projecting it is still
     * running, which is the fog service's standing rule for its own channel
     * ({@code fog-of-war-nouns.md} law 5) and is what makes an expiry — or a
     * wearer's death, which drops the whole component — release its reveal with
     * nothing left over.
     */
    public void tick(float dt, BattleSimulation sim) {
        IntegralSystemService systems = rosterService.integralSystems();
        MovementService movement = rosterService.movement();
        FogOfWarService fog = sim.getFogOfWar();
        fog.clearCarriedSweepSources();
        long[] live = rosterService.denseArray();
        int count = rosterService.liveCount();
        for (int i = 0; i < count; i++) {
            long id = live[i];
            if (!systems.has(id)) continue;
            systems.tick(id, dt);
            projectSweep(id, systems, fog);
            if (!systems.canActivate(id)) continue;
            IntegralSystemDef def = systems.spec(id);
            if (def == null) continue;
            switch (def.aiPolicy()) {
                case CROSSING_UNDER_FIRE -> {
                    CrossingUnderFireSpec crossing = def.crossingUnderFire();
                    if (isMoving(id, movement)
                            && hostileWithin(id, sim, crossing.threatRadiusCells())) {
                        systems.activate(id);
                    }
                }
                case SIGHTED_STANDOFF_CONTACT -> {
                    long target = standoffTarget(id, def, sim);
                    if (target != 0L && systems.activate(id)) {
                        fireMissilePod(id, target, def.missilePod());
                    }
                }
                case APPROACHING_DEAD_GROUND -> {
                    ApproachingDeadGroundSpec ahead = def.approachingDeadGround();
                    if (isMoving(id, movement)
                            && deadGroundAhead(id, sim, movement, ahead.lookaheadCells())) {
                        systems.activate(id);
                    }
                }
                default -> { /* No integral system declares the carried-item policies. */ }
            }
        }
    }

    /**
     * Publishes one running sweep as a temporary observer for this tick.
     *
     * <p>Called for every carrier before its activation is considered, so a
     * sweep that started on the previous tick is republished and one that has
     * just expired simply is not. The wall-read radius rides the shadowcast's
     * existing air-clearance parameter, which is the same bounded
     * "walls near the source are transparent" rule a flier already uses — the
     * sweep is a client of that, not a second visibility algorithm.
     */
    private void projectSweep(long id, IntegralSystemService systems, FogOfWarService fog) {
        PerceptionSweepSpec sweep = systems.activeSweep(id);
        if (sweep == null) return;
        World world = rosterService.world();
        fog.addCarriedSweepSource(world.cellX(id), world.cellY(id),
                Math.round(sweep.revealRangeCells()), sweep.wallReadRadiusCells());
    }

    /**
     * Whether the ground this carrier is heading into is ground they cannot see
     * into: the cell {@code lookaheadCells} along their current heading is
     * behind something their own line of sight does not reach past.
     *
     * <p>Deliberately the wearer's own sight rather than the player's reveal
     * bitmap. Fog is presentation, and a policy that read it would let what the
     * player has already been shown decide what a marine does.
     */
    private boolean deadGroundAhead(long id, BattleSimulation sim, MovementService movement,
                                    float lookaheadCells) {
        float vx = movement.velX(id);
        float vy = movement.velY(id);
        float speed = (float) Math.sqrt(vx * vx + vy * vy);
        if (speed <= MOVING_EPSILON) return false;
        World world = rosterService.world();
        int fromX = world.cellX(id);
        int fromY = world.cellY(id);
        int aheadX = Math.round(fromX + vx / speed * lookaheadCells);
        int aheadY = Math.round(fromY + vy / speed * lookaheadCells);
        NavigationGrid grid = sim.getGrid();
        // Walking off the edge of the map is not dead ground; there is nothing
        // out there for a sweep to read.
        if (aheadX < 0 || aheadX >= grid.getWidth() || aheadY < 0 || aheadY >= grid.getHeight()) {
            return false;
        }
        return !grid.hasLineOfSight(fromX, fromY, aheadX, aheadY);
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
     * acquires a target it can actually reach, and no closer than the system's
     * authored standoff. Returns {@code 0L} when nothing qualifies, in which
     * case the pod simply waits — it never fires blind, and it never spends a
     * finite salvo on something the rifle already has in hand.
     */
    private long standoffTarget(long id, IntegralSystemDef def, BattleSimulation sim) {
        MissilePodSpec pod = def.missilePod();
        SightedStandoffSpec standoff = def.sightedStandoff();
        if (pod == null || standoff == null) return 0L;
        World world = rosterService.world();
        Faction faction = rosterService.identity().faction(id);
        if (faction == null) return 0L;
        int squadId = rosterService.squad().hasSquad(id)
                ? rosterService.squad().squadId(id) : Squad.NO_SQUAD;
        WeaponDef weapon = pod.weaponDef();
        // The authored standoff is the scorer's own minimum range, so a closer
        // contact is never a candidate rather than being picked and discarded.
        return sim.getTacticalScoring().findBestTargetWithinRange(
                world.x(id), world.y(id), faction, squadId, id,
                rosterService.vision().airLosRadius(id), /*allowNoLos*/ false,
                standoff.minimumStandoffCells(), weapon.range());
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

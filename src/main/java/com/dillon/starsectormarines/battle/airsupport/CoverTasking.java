package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirTargetService;
import com.dillon.starsectormarines.battle.air.CoverDuty;
import com.dillon.starsectormarines.battle.air.FlierPrey;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

/**
 * What a cover fighter is told to do while it holds its ring: which aircraft to chase, and which known ground to work.
 *
 * <p>The fighter flies and shoots ({@code AirSystem}, {@code AirToAirFire}); this decides only <em>what at</em>, once
 * the fighter is on station and rested, a few times a second at most. Hostile aircraft come first, ground second, and
 * that order is the doctrine, not an accident of the code: a patrol that went after riflemen while a bomber made its run
 * on the ground it covers would be a patrol that missed the one thing it was sent for.
 *
 * <p><b>Fair knowledge.</b> A fighter in the sky sees the sky: aircraft inside its engagement radius are its own
 * business, as a sentry sees what comes over the wall. Ground is different. It is struck only when the <em>side</em> has
 * sighted it (some unit of the side with a clear line to it, as the director's own sightings are made), is not within
 * reach of the side's own people, and is not under anti-air the side believes would cost more than the airframe. The
 * pilot is not a free observer of the whole battlefield.
 *
 * <p>Ground threats are soft ground only. Works and armour are a bomber's work ({@link AirTasking}); a patrol that
 * dived on a gun nest with a cannon would be spending an airframe on a trade it was chosen for being bad at.
 *
 * <p>UNTUNED: every radius is a first-pass figure.
 */
final class CoverTasking {

    /** Ticks between looks, per fighter (staggered by the craft's id). */
    static final int PERIOD_TICKS = 10;
    /** Hostile aircraft this close to the covered ground are the patrol's business, in cells: far enough that a fast interceptor meets an inbound bomber well short of the ground, since a chase that begins at the bomb line ends after the bombs. UNTUNED. */
    static final float ENGAGE_CELLS = 110f;
    /** An aircraft whose aim point is this close to the covered ground is attacking it, and comes first, in cells. UNTUNED. */
    static final float PROTECT_CELLS = 30f;
    /** The share of an airframe a chase may be expected to cost in known anti-air before the patrol breaks it off. UNTUNED. */
    static final float CHASE_RISK_CEILING = 0.5f;
    /** Most fighters that chase one aircraft at once. */
    static final int MAX_HUNTERS = 2;
    /** Ground this far from the covered point is the patrol's business, in cells. UNTUNED. */
    static final float GROUND_REACH_CELLS = 36f;
    /** Hostile combatants about a point before it is worth a gun pass. */
    static final int MIN_CLUSTER = 4;
    /** Cells about a candidate within which its friends count toward the concentration. */
    static final float CLUSTER_RADIUS = 6f;
    /**
     * The patrol does not work ground with any of its own side's combatants this close to the aim, in cells. A gun pass
     * is a line of rounds, and the enemy a patrol is sent against is usually right beside the people it covers; the
     * figure is a little under the director's bomb-line margin because a cannon's footprint is narrower than a stick's.
     * UNTUNED.
     */
    static final float DANGER_CLOSE_CELLS = 8f;

    private CoverTasking() {}

    /** Gives one cover fighter work if it is on its ring, rested, and there is some; calls off a chase that has gone under known anti-air. */
    static void task(BattleSimulation sim, AirPoolService pool, long craft, ShuttleMission mission, Faction side) {
        CoverDuty duty = mission.cover;
        if (duty == null || mission.aborted || mission.destroyed) return;
        if (Math.floorMod(sim.getSimTickIndex() + craft, PERIOD_TICKS) != 0) return;
        if (mission.state == ShuttleState.INTERCEPT) {
            if (duty.preyId != 0L && underKnownAntiAir(sim, pool, side, duty.preyId)) duty.preyId = 0L;
            return;
        }
        if (mission.state != ShuttleState.ORBIT) return;
        if (!duty.readyForWork()) return;
        if (duty.preyId == 0L && duty.hasAirRounds()) {
            long prey = pickPrey(sim, pool, craft, side, duty);
            if (prey != 0L) {
                duty.preyId = prey;
                return;
            }
        }
        if (!duty.strafeRequested && duty.groundPassesLeft > 0) {
            float[] ground = pickGround(sim, pool, side, duty);
            if (ground != null) {
                duty.strafeRequested = true;
                duty.strafeX = ground[0];
                duty.strafeY = ground[1];
            }
        }
    }

    /**
     * The aircraft this fighter should chase: an armed hostile aircraft inside the engagement radius, those attacking the
     * covered ground before the rest, then the nearest to it; never one that {@link #MAX_HUNTERS} are already on.
     * {@code 0} when there is none.
     */
    static long pickPrey(BattleSimulation sim, AirPoolService pool, long craft, Faction side, CoverDuty duty) {
        World world = sim.world();
        AirTargetService air = sim.getRoster().airTargets();
        long best = 0L;
        double bestScore = Double.MAX_VALUE;
        for (long id : sim.getAirEntityIds()) {
            if (id == craft) continue;
            ShuttleMission m = world.mission(id);
            Faction faction = world.airFaction(id);
            AirBody body = world.kinematics(id);
            if (m == null || m.destroyed || faction == null || body == null || !side.hostileTo(faction)) continue;
            if (!air.isPresent(id) || !air.isAirborne(id) || !armed(m)) continue;
            double gap = Math.hypot(body.x - duty.coverX, body.y - duty.coverY);
            if (gap > ENGAGE_CELLS) continue;
            if (hunters(sim, id) >= MAX_HUNTERS || underKnownAntiAir(sim, pool, side, id)) continue;
            boolean attacking = Math.hypot(m.aimX() - duty.coverX, m.aimY() - duty.coverY) <= PROTECT_CELLS;
            double score = (attacking ? 0 : 1000) + gap;
            if (score < bestScore) {
                bestScore = score;
                best = id;
            }
        }
        // Fliers (Harriers) too: units, not aircraft, but what a patrol over the lanes is up there for
        // (`FlierPrey`). One over the covered ground is attacking it; an aircraft attacking it still comes first.
        UnitRosterService roster = sim.getRoster();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long id = sim.liveUnitAt(i);
            if (!FlierPrey.hostile(roster, id, side)) continue;
            double gap = Math.hypot(world.x(id) - duty.coverX, world.y(id) - duty.coverY);
            if (gap > ENGAGE_CELLS || hunters(sim, id) >= MAX_HUNTERS) continue;
            double score = (gap <= PROTECT_CELLS ? 500 : 1500) + gap;
            if (score >= bestScore || underKnownAntiAirAt(sim, pool, side, world.x(id), world.y(id))) continue;
            bestScore = score;
            best = id;
        }
        return best;
    }

    private static boolean underKnownAntiAirAt(BattleSimulation sim, AirPoolService pool, Faction side, float x, float y) {
        return pool.director(side).risk(sim, x, y, AirPoolKind.FIGHTER) > CHASE_RISK_CEILING;
    }

    /**
     * Whether the aircraft is over ground the side knows to be defended well enough that chasing it there costs a cover
     * fighter more than {@link #CHASE_RISK_CEILING} of an airframe. Only the guns the side has sighted count
     * ({@link AaRisk}); a gun nobody has seen is one the chase is surprised by. Strike sorties accept a ceiling of a whole
     * airframe because the target is worth it; a patrol that follows an enemy into a fort's reach for a bomber it could
     * have met on its own side of the wire is not.
     */
    static boolean underKnownAntiAir(BattleSimulation sim, AirPoolService pool, Faction side, long aircraft) {
        AirBody body = sim.world().kinematics(aircraft);
        if (body == null) {
            // A flier is a unit, read where it stands.
            return FlierPrey.hostile(sim.getRoster(), aircraft, side)
                    && underKnownAntiAirAt(sim, pool, side, sim.world().x(aircraft), sim.world().y(aircraft));
        }
        return underKnownAntiAirAt(sim, pool, side, body.x, body.y);
    }

    /** Whether an aircraft carries something that can hurt the covered ground: a strike, a gunship, another fighter on patrol. */
    static boolean armed(ShuttleMission mission) {
        return mission.strikeSortie || mission.gunship || mission.cover != null;
    }

    private static int hunters(BattleSimulation sim, long prey) {
        int n = 0;
        World world = sim.world();
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission m = world.mission(id);
            if (m != null && m.cover != null && m.cover.preyId == prey) n++;
        }
        return n;
    }

    /**
     * Ground worth a gun pass near the covered point, as {@code {x, y}}, or {@code null}: the densest hostile
     * concentration of soft targets the side has sighted, clear of its own people and of anti-air it believes too strong.
     */
    static float[] pickGround(BattleSimulation sim, AirPoolService pool, Faction side, CoverDuty duty) {
        World world = sim.world();
        LongBucket scratch = new LongBucket();
        sim.getUnitIndex().gather(duty.coverX, duty.coverY, GROUND_REACH_CELLS, scratch);
        AirStrikeDirector director = pool.director(side);
        if (scratch.size > 0) director.look(sim);
        float exposure = AirPoolKind.FIGHTER.aaExposure();
        float bestX = 0f;
        float bestY = 0f;
        int best = MIN_CLUSTER - 1;
        for (int i = 0; i < scratch.size; i++) {
            long u = scratch.ids[i];
            if (!world.isAlive(u) || !side.hostileTo(sim.identity().faction(u)) || !sim.identity().type(u).combatant) continue;
            float x = world.x(u);
            float y = world.y(u);
            int near = sim.getUnitIndex().countHostileCombatants(x, y, CLUSTER_RADIUS, side, 0L);
            if (near <= best) continue;
            if (AirTasking.classify(sim, side, x, y).ground() != AirTasking.Ground.OPEN) continue;
            if (!sighted(sim, side, x, y) || friendsWithin(sim, side, x, y, DANGER_CLOSE_CELLS)) continue;
            if (director.risk(sim, x, y) * exposure > AirStrikeDirector.RISK_CEILING) continue;
            best = near;
            bestX = x;
            bestY = y;
        }
        return best >= MIN_CLUSTER ? new float[]{bestX, bestY} : null;
    }

    /** Whether a unit of {@code side} has a clear line to the point inside the director's sighting range. */
    static boolean sighted(BattleSimulation sim, Faction side, float x, float y) {
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        long[] ours = roster.factionDenseArray(side);
        int count = roster.factionLiveCount(side);
        int stride = Math.max(1, count / 24);
        for (int i = 0; i < count; i += stride) {
            long watcher = ours[i];
            if (Math.hypot(world.x(watcher) - x, world.y(watcher) - y) > AirStrikeDirector.SIGHT_CELLS) continue;
            if (CommsOperators.lineClear(sim.getGrid(), world.cellX(watcher), world.cellY(watcher),
                    (int) Math.floor(x), (int) Math.floor(y), AirStrikeDirector.SIGHT_CELLS)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a live combatant of {@code side} stands within {@code radius} of the point. */
    static boolean friendsWithin(BattleSimulation sim, Faction side, float x, float y, float radius) {
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        long[] ours = roster.factionDenseArray(side);
        float limit = radius * radius;
        for (int i = 0, n = roster.factionLiveCount(side); i < n; i++) {
            long unit = ours[i];
            if (!sim.identity().type(unit).combatant) continue;
            float dx = world.x(unit) - x;
            float dy = world.y(unit) - y;
            if (dx * dx + dy * dy <= limit) return true;
        }
        return false;
    }
}

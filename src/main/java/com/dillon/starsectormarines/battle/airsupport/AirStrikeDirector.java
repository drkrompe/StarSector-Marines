package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirTargetService;
import com.dillon.starsectormarines.battle.air.EnemyConcentration;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.airsupport.AirPoolService.Outcome;
import com.dillon.starsectormarines.battle.airsupport.CommsOperators.Operator;
import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.fort.FortService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretRole;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * An AI side's fire-support officer: decides what ground is worth an aircraft and asks the side's comms operators to
 * direct one, through the same {@link AirPoolService#request} the player's power uses.
 *
 * <p><b>Fair knowledge.</b> The director never reads an enemy it could not have seen. It keeps a memory of hostile
 * emplacements any of its side's units has had a clear line to, and it designates only those, or a hostile
 * concentration an operator has in sight right now. Its estimate of the anti-air over a point is built from that same
 * memory ({@link AaRisk}), so a gun nobody has sighted is a gun the strike is surprised by.
 *
 * <p><b>Less useful against strong defences.</b> A target whose believed anti-air would cost more than
 * {@link #RISK_CEILING} of an airframe is skipped; a nest or a dug-in gun is struck while it stands alone or in a
 * thin defence, and a fort's massed guns are left to the ground force, as is every anti-air mount: the director never
 * designates a gun that shoots aircraft. A commander that wants a particular nest killed
 * calls {@link #strike} for it; the same risk judgement applies unless the caller overrides it.
 *
 * <p>UNTUNED: cadence, ceilings and values are first-pass figures.
 */
public final class AirStrikeDirector {

    /** A sighted, planted Bombard's worth against a lone nest's 3: above it, so the long arm is struck first. UNTUNED. */
    static final float BOMBARD_SCORE = 5f;
    /** Ticks between looks at the field. */
    static final int PERIOD_TICKS = 30;
    /** Range within which a unit of the side sights a hostile emplacement, in cells. UNTUNED. */
    static final float SIGHT_CELLS = 30f;
    /** Largest expected airframe share the director will risk on a strike. UNTUNED. */
    public static final float RISK_CEILING = 1.0f;
    /** Cells within which a point counts as one already struck. */
    static final float REPEAT_CELLS = 9f;
    /** Ticks before a struck point is a candidate again. UNTUNED. */
    static final int REPEAT_TICKS = 1200;
    /**
     * The director does not place a strike with any of its own side's combatants this close to the aim point. A
     * strike is a line of bombs, not a point, and the side's own troops are often exactly where its enemy is. UNTUNED.
     */
    static final float DANGER_CLOSE_CELLS = 10f;
    /**
     * Fighters a side keeps in hand for gun runs when it puts one up on cover: cover is a request for an airframe that
     * the same stock would otherwise strafe with, and a side that spent its last on a patrol would have none left for the
     * strafe a radio is asked for. UNTUNED.
     */
    public static final int COVER_RESERVE = 1;
    /** A cover fighter already on or bound for ground this close makes another request for it redundant, in cells. */
    static final float COVER_DEDUP_CELLS = 40f;
    /** Ticks the director waits before it considers cover again after it has asked for it. UNTUNED. */
    static final int COVER_RETRY_TICKS = 600;
    /** Hostile aircraft this close to a unit of the side are seen by it, in cells. */
    static final float AIR_SIGHT_CELLS = 45f;
    /** Friends about a point before it is ground worth covering. */
    static final int COVER_MIN_FRIENDS = 4;
    /** Cells about a friendly unit within which its friends count toward the ground to cover. */
    static final float COVER_CLUSTER_RADIUS = 8f;
    /** Cells from where an airframe of the side was lost within which the ground is believed defended. UNTUNED. */
    static final float LOSS_CELLS = 20f;
    /** Ticks a loss is remembered: three minutes. UNTUNED. */
    static final int LOSS_TICKS = 5400;
    /** Most units sampled per look when updating sightings. */
    private static final int SIGHT_SAMPLE = 48;

    private record Struck(float x, float y, int tick) {}

    private record Candidate(float x, float y, boolean believed, float score, AirPoolKind kind, long gun) {}

    private final Faction side;
    private final Set<Long> seenGuns = new HashSet<>();
    private final Set<Integer> seenForts = new HashSet<>();
    private FortService fortLookup;
    private final List<Struck> struck = new ArrayList<>();
    private boolean debugDone;
    private int nextCoverTick;

    public AirStrikeDirector(Faction side) {
        this.side = side;
    }

    /** Hostile emplacements this side has sighted and believes it knows of (alive or not; callers check). */
    public Set<Long> knownGuns() {
        return seenGuns;
    }

    /** Whether the side has sighted {@code gun}. */
    public boolean knows(long gun) {
        if (seenGuns.contains(gun)) return true;
        // A fort the side has looked at is disclosed whole: its anti-air yard is part of what it saw.
        Fort fort = fortOf(gun);
        return fort != null && seenForts.contains(fort.id());
    }

    private Fort fortOf(long gun) {
        return fortLookup == null ? null : fortLookup.airMountFort(gun);
    }

    /** One look at the field: update sightings and, if worthwhile ground is in reach of an operator, call a strike. */
    void tick(BattleSimulation sim, AirPoolService service) {
        int tick = sim.getSimTickIndex();
        if (tick % PERIOD_TICKS != side.ordinal() % PERIOD_TICKS) return;
        updateSightings(sim);
        noteHostileAir(sim);
        coverIfThreatened(sim, service, tick);
        if (service.defaultKind(side) == null) return;
        if (CommsOperators.available(sim, side).isEmpty()) return;
        AirPoolAllowance allowance = service.allowance(side);
        boolean debugDue = allowance.debugStrikeTick() >= 0 && tick >= allowance.debugStrikeTick() && !debugDone;
        List<Candidate> candidates = candidates(sim, service, debugDue);
        candidates.sort(Comparator.comparingDouble((Candidate c) -> c.score).reversed());
        for (Candidate c : candidates) {
            if (service.request(sim, side, c.kind, c.x, c.y, c.believed) == Outcome.CALLING) {
                struck.add(new Struck(c.x, c.y, tick));
                if (debugDue) debugDone = true;
                return;
            }
        }
    }

    /**
     * A commander's own request: direct the best airframe in stock onto {@code (x, y)}, which the commander says it
     * believes in. Refused (and nothing reserved) when no operator can, no airframe is in stock, or, unless
     * {@code ignoreRisk}, the believed anti-air over the point would cost more than the ceiling allows
     * ({@link Outcome#TOO_DEFENDED}).
     */
    public Outcome strike(BattleSimulation sim, AirPoolService service, float x, float y, boolean ignoreRisk) {
        return strike(sim, service, x, y, ignoreRisk, unit -> true);
    }

    /**
     * As {@link #strike(BattleSimulation, AirPoolService, float, float, boolean)}, directed only through an operator the
     * caller names: a company's plan asks through the observers it brought, not through whoever happens to be nearest.
     */
    public Outcome strike(BattleSimulation sim, AirPoolService service, float x, float y, boolean ignoreRisk,
                          LongPredicate operators) {
        AirPoolKind kind = AirTasking.kindAt(sim, service, side, x, y);
        if (kind == null) return Outcome.NO_AIRFRAME;
        updateSightings(sim);
        if (!ignoreRisk && risk(sim, x, y, kind) > RISK_CEILING) {
            refusals++;
            return Outcome.TOO_DEFENDED;
        }
        Outcome outcome = service.request(sim, side, kind, x, y, true, operators);
        if (outcome == Outcome.CALLING) struck.add(new Struck(x, y, sim.getSimTickIndex()));
        return outcome;
    }

    private int refusals;

    /** How many commander requests this side's officer has refused as too heavily defended, for evidence. */
    public int refusals() {
        return refusals;
    }

    /**
     * A commander's request for a fighter on combat air patrol over friendly ground at {@code (x, y)}: a muster point, a
     * company on the approach, a beachhead. The fighter holds a ring over it for a bounded time, engaging hostile
     * aircraft and, secondarily, soft ground threats near it that the side has sighted ({@link AirMission#COVER}).
     *
     * <p>Refused, with nothing reserved, when the side would have no fighter left for a strafe ({@link #COVER_RESERVE}),
     * when a fighter already covers the ground ({@link Outcome#ALREADY_COVERED}), when no operator can take the call, or
     * when the anti-air the side believes stands over the ground would cost a fighter more than the ceiling.
     */
    public Outcome cover(BattleSimulation sim, AirPoolService service, float x, float y, LongPredicate operators) {
        if (service.available(side, AirPoolKind.FIGHTER) <= COVER_RESERVE) return Outcome.NO_AIRFRAME;
        if (service.covers(side, x, y, COVER_DEDUP_CELLS)) return Outcome.ALREADY_COVERED;
        updateSightings(sim);
        if (risk(sim, x, y, AirPoolKind.FIGHTER) > RISK_CEILING) return Outcome.TOO_DEFENDED;
        return service.requestCover(sim, side, x, y, operators);
    }

    private int hostileAirSeenTick = Integer.MIN_VALUE / 2;

    /**
     * Ticks since a unit of this side last had a hostile armed aircraft in view, or a very large number when none has. The
     * side knows when it is being flown at; a plan that reads it is reading its own units' eyes, never the enemy's pool.
     */
    public int ticksSinceHostileAir(int tick) {
        return tick - hostileAirSeenTick;
    }

    /** The nearest hostile armed airborne craft within {@link #AIR_SIGHT_CELLS} of a sampled unit of the side, as {x, y}; null when none. */
    private float[] hostileAirInSight(BattleSimulation sim) {
        World world = sim.world();
        AirTargetService air = sim.getRoster().airTargets();
        UnitRosterService roster = sim.getRoster();
        long[] ours = roster.factionDenseArray(side);
        int count = roster.factionLiveCount(side);
        if (count <= 0) return null;
        int stride = Math.max(1, count / SIGHT_SAMPLE);
        float bestGap = Float.MAX_VALUE;
        float threatX = 0f;
        float threatY = 0f;
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission m = world.mission(id);
            Faction faction = world.airFaction(id);
            AirBody body = world.kinematics(id);
            if (m == null || m.destroyed || faction == null || body == null || !side.hostileTo(faction)) continue;
            if (!air.isPresent(id) || !air.isAirborne(id) || !CoverTasking.armed(m)) continue;
            for (int j = 0; j < count; j += stride) {
                long watcher = ours[j];
                float gap = (float) Math.hypot(world.x(watcher) - body.x, world.y(watcher) - body.y);
                if (gap <= AIR_SIGHT_CELLS && gap < bestGap) {
                    bestGap = gap;
                    threatX = body.x;
                    threatY = body.y;
                }
            }
        }
        return bestGap == Float.MAX_VALUE ? null : new float[]{threatX, threatY};
    }

    /**
     * The side's own reason to put up cover: a hostile armed aircraft that one of its units can see, over ground it is
     * standing on. The patrol goes over the densest group of the side's combatants near that aircraft. A side with no
     * hostile air in view asks for nothing here; a commander's cover over a muster point is {@link #cover}.
     */
    private void coverIfThreatened(BattleSimulation sim, AirPoolService service, int tick) {
        if (tick < nextCoverTick || service.available(side, AirPoolKind.FIGHTER) <= COVER_RESERVE) return;
        World world = sim.world();
        UnitRosterService roster = sim.getRoster();
        long[] ours = roster.factionDenseArray(side);
        int count = roster.factionLiveCount(side);
        if (count <= 0) return;
        int stride = Math.max(1, count / SIGHT_SAMPLE);
        float[] threat = hostileAirInSight(sim);
        if (threat == null) return;
        float threatX = threat[0];
        float threatY = threat[1];
        long densest = 0L;
        int bestFriends = COVER_MIN_FRIENDS - 1;
        for (int j = 0; j < count; j += stride) {
            long unit = ours[j];
            if (!sim.identity().type(unit).combatant || Math.hypot(world.x(unit) - threatX, world.y(unit) - threatY) > AIR_SIGHT_CELLS) continue;
            int friends = 0;
            for (int k = 0; k < count; k++) {
                long other = ours[k];
                if (sim.identity().type(other).combatant
                        && Math.hypot(world.x(other) - world.x(unit), world.y(other) - world.y(unit)) <= COVER_CLUSTER_RADIUS) friends++;
            }
            if (friends > bestFriends) {
                bestFriends = friends;
                densest = unit;
            }
        }
        nextCoverTick = tick + COVER_RETRY_TICKS;
        if (densest == 0L) return;
        Outcome outcome = cover(sim, service, world.x(densest), world.y(densest), unit -> true);
        if (outcome == Outcome.CALLING) nextCoverTick = tick + COVER_RETRY_TICKS * 4;
    }

    /** One look at the field without placing a strike: brings the side's sightings, and so its anti-air estimate, up to date. */
    public void look(BattleSimulation sim) {
        updateSightings(sim);
        noteHostileAir(sim);
    }

    /** Notes that a hostile armed aircraft is in view of the side this tick, if one is. */
    public void noteHostileAir(BattleSimulation sim) {
        if (hostileAirInSight(sim) != null) hostileAirSeenTick = sim.getSimTickIndex();
    }

    /**
     * The share of an airframe a pass over the point is believed to cost, from the guns this side has sighted and the
     * airframes it has lost there lately.
     */
    public float risk(BattleSimulation sim, float x, float y) {
        fortLookup = sim.forts();
        return AaRisk.at(sim, side, x, y, this::knows).expectedLossShare() + lossesNear(x, y, sim.getSimTickIndex());
    }

    private record Loss(float x, float y, int tick) {}

    private final List<Loss> losses = new ArrayList<>();

    /**
     * Notes an airframe of this side shot down on its way to {@code (x, y)}. The side saw it fall: whatever it did not
     * know stood there, it now knows the ground is defended.
     */
    void noteLoss(float x, float y, int tick) {
        losses.add(new Loss(x, y, tick));
    }

    /**
     * What the side's own losses add to its estimate over the point: a whole airframe for each one lost within
     * {@link #LOSS_CELLS} in the last {@link #LOSS_TICKS}, so a second sortie is not sent where the first just fell
     * unless what is believed there is otherwise nothing.
     */
    float lossesNear(float x, float y, int tick) {
        float share = 0f;
        for (Loss loss : losses) {
            if (tick - loss.tick > LOSS_TICKS) continue;
            if (Math.hypot(loss.x - x, loss.y - y) <= LOSS_CELLS) share += 1f;
        }
        return share;
    }

    /** As above for an airframe of {@code kind}: a fighter's low run takes more of the same fire ({@link AirPoolKind#aaExposure}). */
    public float risk(BattleSimulation sim, float x, float y, AirPoolKind kind) {
        return risk(sim, x, y) * kind.aaExposure();
    }

    private List<Candidate> candidates(BattleSimulation sim, AirPoolService service, boolean ignoreRisk) {
        List<Candidate> out = new ArrayList<>();
        World world = sim.world();
        int tick = sim.getSimTickIndex();
        struck.removeIf(s -> tick - s.tick > REPEAT_TICKS);
        for (long gun : seenGuns) {
            if (!world.isAlive(gun) || !side.hostileTo(sim.identity().faction(gun))) continue;
            // What shoots aircraft is the ground force's: an airframe sent straight over it is the trade it exists to win.
            if (shootsAir(sim, gun)) continue;
            float x = world.x(gun);
            float y = world.y(gun);
            if (recentlyStruck(x, y)) continue;
            AirPoolKind gunKind = service.available(side, AirPoolKind.BOMBER) > 0 ? AirPoolKind.BOMBER : null;
            // A cannon does little to a hardened position, so the director waits for a bomber to come back rather than
            // spend a fighter on a nest; a commander or the player who asks for one explicitly may.
            if (gunKind == null) continue;
            float risk = risk(sim, x, y, gunKind);
            if (!ignoreRisk && risk > RISK_CEILING) continue;
            if (dangerClose(sim, x, y)) continue;
            int near = sim.getUnitIndex().countHostileCombatants(x, y, 5f, side, 0L);
            out.add(new Candidate(x, y, true, 3f + 0.5f * near - 2f * risk, gunKind, gun));
        }
        // Only a concentration some operator has in sight is a candidate: the densest group anywhere on the map is a
        // thing no radio and no fort has seen.
        List<Operator> eyes = CommsOperators.available(sim, side);
        // A planted Bombard an operator has in sight is a designation of its own (`bombard.md`, Counters): soft,
        // rooted and hazing the line, it is the ground target an airframe is for. A fighter's cannon is enough.
        for (long bombard : sim.swarmBombards().plantedBodies()) {
            if (!world.isAlive(bombard) || !side.hostileTo(sim.identity().faction(bombard))) continue;
            float x = world.x(bombard);
            float y = world.y(bombard);
            if (recentlyStruck(x, y) || dangerClose(sim, x, y) || !sighted(sim, eyes, x, y)) continue;
            AirPoolKind kind = service.available(side, AirPoolKind.FIGHTER) > 0 ? AirPoolKind.FIGHTER
                    : service.available(side, AirPoolKind.BOMBER) > 0 ? AirPoolKind.BOMBER : null;
            if (kind == null) continue;
            float risk = risk(sim, x, y, kind);
            if (!ignoreRisk && risk > RISK_CEILING) continue;
            out.add(new Candidate(x, y, true, BOMBARD_SCORE - 2f * risk, kind, 0L));
        }
        // Counted on the ground: a bomb is a ground blast and no ground blast reaches a flier, so a pack overhead is no target.
        long densest = EnemyConcentration.densestOnGround(sim, side,
                unit -> sighted(sim, eyes, world.x(unit), world.y(unit)));
        if (densest != 0L) {
            float x = world.x(densest);
            float y = world.y(densest);
            if (!recentlyStruck(x, y) && !dangerClose(sim, x, y)) {
                int near = groundedHostiles(sim, x, y);
                AirTasking.Target target = AirTasking.classify(sim, side, x, y);
                AirPoolKind kind = AirTasking.choose(target, service.available(side, AirPoolKind.BOMBER),
                        service.available(side, AirPoolKind.FIGHTER));
                // A fighter against works or armour is the poor trade the pairing exists to avoid; the director waits.
                boolean poorTrade = kind == AirPoolKind.FIGHTER && target.ground() != AirTasking.Ground.OPEN;
                float risk = kind == null ? Float.MAX_VALUE : risk(sim, x, y, kind);
                if (kind != null && !poorTrade && (ignoreRisk || risk <= RISK_CEILING)) {
                    // Believed false: only a concentration an operator has in sight is designated.
                    out.add(new Candidate(x, y, false, near - 2f * risk, kind, 0L));
                }
            }
        }
        return out;
    }

    /** Whether {@code gun} is a dedicated anti-air mount: a flak or missile emplacement, a spore colony. */
    static boolean shootsAir(BattleSimulation sim, long gun) {
        if (!sim.turretState().isTurret(gun)) return false;
        StructureDef structure = sim.turretState().structure(gun);
        return structure != null && structure.targetRole == TurretRole.AA;
    }

    /** Whether any of the operators has an unobstructed line to the point, inside its own sight. */
    private static boolean sighted(BattleSimulation sim, List<Operator> eyes, float x, float y) {
        for (Operator eye : eyes) {
            if (CommsOperators.knowledge(sim, eye, x, y, false) == CommsOperators.Knowledge.SIGHT) return true;
        }
        return false;
    }

    /** Hostile combatants on the ground within {@link EnemyConcentration#CLUSTER_RADIUS} of the point. */
    private int groundedHostiles(BattleSimulation sim, float x, float y) {
        LongBucket near = new LongBucket();
        sim.getUnitIndex().gatherHostileCombatants(x, y, EnemyConcentration.CLUSTER_RADIUS, side, near);
        int grounded = 0;
        for (int i = 0; i < near.size; i++) {
            if (!sim.identity().type(near.ids[i]).isFlier()) grounded++;
        }
        return grounded;
    }

    /**
     * Whether a live combatant friendly to this side stands within {@link #DANGER_CLOSE_CELLS} of the point: its own,
     * and every side it fights beside, so an allied officer does not put a stick on the company it is fighting with.
     */
    boolean dangerClose(BattleSimulation sim, float x, float y) {
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        float limit = DANGER_CLOSE_CELLS * DANGER_CLOSE_CELLS;
        for (Faction friend : Faction.values()) {
            if (!side.friendlyTo(friend)) continue;
            long[] ours = roster.factionDenseArray(friend);
            for (int i = 0, n = roster.factionLiveCount(friend); i < n; i++) {
                long unit = ours[i];
                if (!sim.identity().type(unit).combatant) continue;
                float dx = world.x(unit) - x, dy = world.y(unit) - y;
                if (dx * dx + dy * dy <= limit) return true;
            }
        }
        return false;
    }

    private boolean recentlyStruck(float x, float y) {
        for (Struck s : struck) {
            if (Math.hypot(s.x - x, s.y - y) < REPEAT_CELLS) return true;
        }
        return false;
    }

    /** Adds every hostile emplacement a sampled unit of this side has a clear line to. */
    private void updateSightings(BattleSimulation sim) {
        fortLookup = sim.forts();
        sightForts(sim);
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        long[] ours = roster.factionDenseArray(side);
        int ourCount = roster.factionLiveCount(side);
        if (ourCount <= 0) return;
        int stride = Math.max(1, ourCount / SIGHT_SAMPLE);
        for (Faction enemy : Faction.values()) {
            if (!side.hostileTo(enemy)) continue;
            long[] theirs = roster.factionDenseArray(enemy);
            int theirCount = roster.factionLiveCount(enemy);
            for (int i = 0; i < theirCount; i++) {
                long gun = theirs[i];
                if (seenGuns.contains(gun) || !sim.identity().type(gun).isTurret()) continue;
                for (int j = 0; j < ourCount; j += stride) {
                    long watcher = ours[j];
                    if (Math.hypot(world.x(gun) - world.x(watcher), world.y(gun) - world.y(watcher)) > SIGHT_CELLS) continue;
                    if (CommsOperators.lineClear(sim.getGrid(), world.cellX(watcher), world.cellY(watcher),
                            world.cellX(gun), world.cellY(gun), SIGHT_CELLS)) {
                        seenGuns.add(gun);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Notes every hostile fort with an anti-air yard that a sampled unit of this side has a clear line to a cell of its
     * works. Seeing the works is seeing the fort, as for a commander ({@code forts.md}), and discloses its yard.
     */
    private void sightForts(BattleSimulation sim) {
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        long[] ours = roster.factionDenseArray(side);
        int ourCount = roster.factionLiveCount(side);
        if (ourCount <= 0) return;
        int stride = Math.max(1, ourCount / SIGHT_SAMPLE);
        for (Fort fort : sim.forts().forts()) {
            if (seenForts.contains(fort.id()) || fort.airMountIds().isEmpty()) continue;
            long mount = fort.airMountIds().get(0);
            if (!world.isAlive(mount) || !side.hostileTo(sim.identity().faction(mount))) continue;
            int[] cells = fort.exteriorCells();
            seen:
            for (int i = 0; i + 1 < cells.length; i += 2) {
                for (int j = 0; j < ourCount; j += stride) {
                    long watcher = ours[j];
                    if (Math.hypot(cells[i] - world.x(watcher), cells[i + 1] - world.y(watcher)) > SIGHT_CELLS) continue;
                    if (CommsOperators.lineClear(sim.getGrid(), world.cellX(watcher), world.cellY(watcher),
                            cells[i], cells[i + 1], SIGHT_CELLS)) {
                        seenForts.add(fort.id());
                        break seen;
                    }
                }
            }
        }
    }

    /** Operators the side could use right now, for readouts. */
    public List<Operator> operators(BattleSimulation sim) {
        return CommsOperators.available(sim, side);
    }
}

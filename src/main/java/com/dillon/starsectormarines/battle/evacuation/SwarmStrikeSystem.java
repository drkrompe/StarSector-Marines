package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The serial half of what swarm strains do to things that are not units.
 *
 * <p>Strain behaviours run in the parallel unit update, where the map and an
 * airborne hull cannot be touched: a Brute's blow on a wall goes through
 * {@code MapEditor}, and a Spitter's acid on a low flier goes through the
 * aimed-damage route, both serial-only. So a behaviour posts the act here and
 * {@link #tick} lands it in the battle's serial pass, through the same
 * structural and armour paths every other weapon uses.
 *
 * <p>Posts arrive in whatever order the workers finish; {@link #tick} sorts
 * them by attacker before applying, so the same battle breaks the same walls in
 * the same order on every replay.
 */
public final class SwarmStrikeSystem {

    /** A blow on one wall or window cell, or on one window edge when {@code edge} is set. */
    public record Blow(long attacker, int cellX, int cellY, Direction edge, int amount) {}

    /** Acid that lands on an airborne craft once the sim reaches {@code arrivalTick}. */
    public record AirRound(long attacker, long target, float damage, float penetration,
                           int arrivalTick) {}

    private static final Comparator<Blow> BLOW_ORDER = Comparator
            .comparingLong(Blow::attacker).thenComparingInt(Blow::cellX)
            .thenComparingInt(Blow::cellY);
    private static final Comparator<AirRound> ROUND_ORDER = Comparator
            .comparingLong(AirRound::attacker).thenComparingLong(AirRound::target);

    private final ConcurrentLinkedQueue<Blow> postedBlows = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<AirRound> postedRounds = new ConcurrentLinkedQueue<>();
    private final List<AirRound> inFlight = new ArrayList<>();
    private final List<Blow> drain = new ArrayList<>();
    private int blowsLanded;
    private int structuresBroken;
    private int airRoundsLanded;

    /** Thread-safe; called from a strain's update. */
    public void postBlow(long attacker, int cellX, int cellY, Direction edge, int amount) {
        postedBlows.add(new Blow(attacker, cellX, cellY, edge, amount));
    }

    /** Thread-safe; called from a strain's update. */
    public void postAirRound(long attacker, long target, float damage, float penetration,
                             int arrivalTick) {
        postedRounds.add(new AirRound(attacker, target, damage, penetration, arrivalTick));
    }

    /** Serial: lands every posted blow and every acid round that has arrived. */
    public void tick(BattleSimulation sim) {
        drain.clear();
        for (Blow blow; (blow = postedBlows.poll()) != null; ) drain.add(blow);
        drain.sort(BLOW_ORDER);
        for (Blow blow : drain) {
            // A fort's or an objective's wall takes many blows for a house wall's one.
            int amount = FortifiedWorks.swarmDamage(sim.getGrid(), blow.cellX(), blow.cellY(), blow.edge(),
                    blow.amount());
            boolean broke = blow.edge() == null
                    ? sim.mapEditor().damageWall(blow.cellX(), blow.cellY(), amount)
                    : sim.mapEditor().damageEdgeBarrier(blow.cellX(), blow.cellY(),
                            blow.edge(), amount);
            blowsLanded++;
            if (broke) structuresBroken++;
            sim.swarmCues().postAt(broke ? SwarmCues.Kind.WALL_BREAK : SwarmCues.Kind.WALL_BLOW,
                    blow.attacker(), blow.cellX(), blow.cellY(), sim);
        }

        int before = inFlight.size();
        for (AirRound round; (round = postedRounds.poll()) != null; ) inFlight.add(round);
        if (inFlight.size() > before) {
            inFlight.subList(before, inFlight.size()).sort(ROUND_ORDER);
        }
        int now = sim.getSimTickIndex();
        for (var it = inFlight.iterator(); it.hasNext(); ) {
            AirRound round = it.next();
            if (now < round.arrivalTick()) continue;
            it.remove();
            if (!sim.world().isAlive(round.target())) continue;
            var mission = sim.world().mission(round.target());
            if (mission == null || mission.destroyed || !mission.isOnMap()) continue;
            sim.applyAimedDamage(round.target(), round.attacker(), round.damage(),
                    round.penetration());
            airRoundsLanded++;
        }
    }

    /** Blows landed on walls and windows so far, for evidence. */
    public int blowsLanded() { return blowsLanded; }

    /** Of those, how many broke what they struck. */
    public int structuresBroken() { return structuresBroken; }

    /** Acid rounds that reached a live craft. */
    public int airRoundsLanded() { return airRoundsLanded; }
}

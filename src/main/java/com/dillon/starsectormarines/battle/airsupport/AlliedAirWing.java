package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;
import java.util.function.Predicate;

/**
 * The world's military air wing over a Rav Bloom battle (`air-nouns.md`, Forward observer): an {@link Faction#ALLY} pool
 * of bombers and fighters its own fire-support officer directs, and the forward observers the military posts with its
 * formations, through whom alone it can be called. The militia, the townspeople and the company bring no radio onto
 * this net.
 *
 * <p>Air cannot clear the map: the wing is a few airframes replaced slowly, every call needs an observer with the ground
 * in sight, and the officer judges the anti-air only from what the side has seen, so a mass's unseen spore colonies take
 * aircraft.
 *
 * <p>Where the mission laid it an airbase ({@link WingAirbase}), the wing is larger and flies from it, and keeps a
 * standing patrol of fighters over the formations while the air is contested ({@link StandingPatrol}).
 */
public final class AlliedAirWing {

    private AlliedAirWing() {}

    /** Salts the roving teams' kit rolls, seeded from the wing's home so one map rolls the same teams. */
    private static final long ROVING_SALT = 0x524F56494E47L;

    /**
     * The wing: two bombers back every minute, two fighters every forty-five seconds. UNTUNED first-pass
     * mission-design choice, smaller and slower than a Conquest company's own pool, since the wing serves a whole front.
     */
    static final AirPoolAllowance WING = AirPoolAllowance.of(
            new AirPoolStock(2, 2, 60f), new AirPoolStock(2, 2, 45f), true);

    /**
     * The wing with an airbase on the map: three bombers and four fighters, the base's own and one more of each flying
     * in from further back, replaced at the same pace. UNTUNED.
     */
    static final AirPoolAllowance BASED_WING = AirPoolAllowance.of(
            new AirPoolStock(3, 3, 60f), new AirPoolStock(4, 4, 45f), true);

    /** Standing patrol fighters up at once over a based wing's formations. UNTUNED. */
    static final int PATROL_UP = 2;

    /**
     * Gives the battle's allies the wing, flying in over {@code home} (ground behind their own line), posts one
     * observer with each of {@code posts}, and sends roving observer teams up from home now and then
     * ({@link RovingObservers}, watching from {@link BloomVantages}); returns the posts. Called once at setup, before the battle's first tick; any
     * pool the battle already declared for another side is kept.
     */
    public static ForwardObservers install(BattleSimulation sim, float homeX, float homeY,
                                           List<? extends ForwardObservers.Post> posts) {
        return install(sim, homeX, homeY, posts, null, List.of(), s -> false);
    }

    /**
     * As {@link #install(BattleSimulation, float, float, List)}, the wing based on {@code base} when there is one: the
     * larger establishment, its sorties rolling off the base's strip, and a standing patrol over {@code stations}
     * flown while {@code contested} says the air is.
     */
    public static ForwardObservers install(BattleSimulation sim, float homeX, float homeY,
                                           List<? extends ForwardObservers.Post> posts, WingAirbase base,
                                           List<? extends StandingPatrol.Station> stations,
                                           Predicate<BattleSimulation> contested) {
        ForwardObservers observers = new ForwardObservers(Faction.ALLY, posts);
        AirPoolService previous = sim.airPool();
        AirPoolDeclaration declared = previous == null ? AirPoolDeclaration.NONE : previous.declaration();
        AirPoolService service = new AirPoolService(declared.with(Faction.ALLY, base == null ? WING : BASED_WING));
        RovingObservers teams = new RovingObservers(Faction.ALLY, homeX, homeY, BloomVantages.of(homeX, homeY),
                (Float.floatToIntBits(homeX) * 31L + Float.floatToIntBits(homeY)) ^ ROVING_SALT);
        service.observedFrom(observers).rovingFrom(teams).homeAt(Faction.ALLY, homeX, homeY);
        if (base != null) {
            service.basedAt(base);
            if (!stations.isEmpty()) service.patrolledBy(new StandingPatrol(Faction.ALLY, stations, PATROL_UP, contested));
        }
        sim.setAirPool(service);
        return observers;
    }
}

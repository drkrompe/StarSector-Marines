package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/**
 * The world's military air wing over a Rav Bloom battle (`air-nouns.md`, Forward observer): an {@link Faction#ALLY} pool
 * of bombers and fighters its own fire-support officer directs, and the forward observers the military posts with its
 * formations, through whom alone it can be called. The militia, the townspeople and the company bring no radio onto
 * this net.
 *
 * <p>Air cannot clear the map: the wing is a few airframes replaced slowly, every call needs an observer with the ground
 * in sight, and the officer judges the anti-air only from what the side has seen, so a mass's unseen spore colonies take
 * aircraft.
 */
public final class AlliedAirWing {

    private AlliedAirWing() {}

    /**
     * The wing: two bombers back every minute, two fighters every forty-five seconds. UNTUNED first-pass
     * mission-design choice, smaller and slower than a Conquest company's own pool, since the wing serves a whole front.
     */
    static final AirPoolAllowance WING = AirPoolAllowance.of(
            new AirPoolStock(2, 2, 60f), new AirPoolStock(2, 2, 45f), true);

    /**
     * Gives the battle's allies the wing, flying in over {@code home} (ground behind their own line), and posts one
     * observer with each of {@code posts}; returns the posts. Called once at setup, before the battle's first tick; any
     * pool the battle already declared for another side is kept.
     */
    public static ForwardObservers install(BattleSimulation sim, float homeX, float homeY,
                                           List<? extends ForwardObservers.Post> posts) {
        ForwardObservers observers = new ForwardObservers(Faction.ALLY, posts);
        AirPoolService previous = sim.airPool();
        AirPoolDeclaration declared = previous == null ? AirPoolDeclaration.NONE : previous.declaration();
        AirPoolService service = new AirPoolService(declared.with(Faction.ALLY, WING));
        sim.setAirPool(service.observedFrom(observers).homeAt(Faction.ALLY, homeX, homeY));
        return observers;
    }
}

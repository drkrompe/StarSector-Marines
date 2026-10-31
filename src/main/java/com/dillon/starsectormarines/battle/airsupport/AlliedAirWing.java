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
    /** Salts the roving teams' kit rolls, seeded from the wing's home so one map rolls the same teams. */
    private static final long ROVING_SALT = 0x524F56494E47L;

    static final AirPoolAllowance WING = AirPoolAllowance.of(
            new AirPoolStock(2, 2, 60f), new AirPoolStock(2, 2, 45f), true);

    /**
     * Gives the battle's allies the wing, flying in over {@code home} (ground behind their own line), posts one
     * observer with each of {@code posts}, and sends roving observer teams up from home now and then
     * ({@link RovingObservers}, watching from {@link BloomVantages}); returns the posts. Called once at setup, before the battle's first tick; any
     * pool the battle already declared for another side is kept.
     */
    public static ForwardObservers install(BattleSimulation sim, float homeX, float homeY,
                                           List<? extends ForwardObservers.Post> posts) {
        ForwardObservers observers = new ForwardObservers(Faction.ALLY, posts);
        AirPoolService previous = sim.airPool();
        AirPoolDeclaration declared = previous == null ? AirPoolDeclaration.NONE : previous.declaration();
        AirPoolService service = new AirPoolService(declared.with(Faction.ALLY, WING));
        RovingObservers teams = new RovingObservers(Faction.ALLY, homeX, homeY, BloomVantages.of(homeX, homeY),
                (Float.floatToIntBits(homeX) * 31L + Float.floatToIntBits(homeY)) ^ ROVING_SALT);
        sim.setAirPool(service.observedFrom(observers).rovingFrom(teams).homeAt(Faction.ALLY, homeX, homeY));
        return observers;
    }

    /**
     * Bases the wing {@link #install} gave the allies on an air base of their own on the map
     * ({@code cordon-nouns.md}, Air base): one hardstand for each airframe of the wing's establishment, bombers first,
     * at {@code sites} in order (fewer sites lay fewer stands), every nose on {@code facingDegrees}. From then on the
     * wing takes off from and lands on those stands, its stock is what stands ready there, and a loss is replaced on its
     * stand after the wing's own regeneration time. Returns the base, or {@code null} when the battle has no wing or no
     * site was given.
     */
    public static HardstandAirBase baseAt(BattleSimulation sim, List<int[]> sites, float facingDegrees) {
        AirPoolService pool = sim.airPool();
        if (pool == null || !pool.hasPool(Faction.ALLY) || sites == null || sites.isEmpty()) return null;
        HardstandAirBase base = new HardstandAirBase(Faction.ALLY, sim.getAirfieldService(), WING);
        int next = 0;
        for (AirPoolKind kind : AirPoolKind.values()) {
            for (int i = 0; i < WING.stock(kind).cap() && next < sites.size(); i++, next++) {
                int[] site = sites.get(next);
                base.addStand(site[0], site[1], kind, facingDegrees);
            }
        }
        pool.basedAt(Faction.ALLY, base);
        return base;
    }

    /** Stands an air base for the wing lays: one for each airframe of its establishment. */
    public static int stands() {
        int n = 0;
        for (AirPoolKind kind : AirPoolKind.values()) n += WING.stock(kind).cap();
        return n;
    }
}

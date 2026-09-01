package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.turret.DefensePostKind;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * How hard a walled place is to take.
 *
 * <p>A mission dial, and it has two failure modes rather than one. A place a
 * handful of low-tier squads cannot get into is not a fight, it is a refusal; a
 * place a thousand marines walk through is not a climax. Neither is fixed by
 * tuning one number harder, because they are the same number pointed at
 * different forces — so the strength is stated by whoever knows what is being
 * sent, and the generator obeys.
 *
 * <p>Three things carry it, and they are different questions:
 *
 * <ul>
 *   <li>{@link #posts} — <em>what shoots back</em>, and how much of it. This is
 *       the one that decides the fight. A wall is a delay and a detour; a
 *       covered gate is a decision, and a rocket battery behind it is a clock.
 *       Stated as a count per {@link DefensePostKind} rather than a single
 *       number, because two light posts and two artillery batteries are not the
 *       same defence at any exchange rate.
 *   <li>{@link #gates} — how many ways in there are, which is about
 *       <em>manoeuvre</em>: several gates give an attacker somewhere to feint
 *       and somewhere to commit, one gate makes the approach the whole battle.
 *   <li>{@link #wallHp} — what it costs to make a new way in, which is about
 *       <em>materiel</em>: a wall worth less than a demolition charge is
 *       decoration, and one worth more than the force can spend is a detour.
 * </ul>
 *
 * <p>Gate count is capped rather than set: growth decides where roads cross the
 * outline, and this decides how many of those crossings stay open. A place whose
 * roads all leave by one route cannot be given three gates by asking. Post
 * counts are asked for the same way — a place with no room for a fifth
 * emplacement gets four, and {@code PrecinctDefenceStage} records the shortfall
 * rather than dropping it.
 */
public record Fortification(int gates, int wallHp, Map<DefensePostKind, Integer> posts) {

    /**
     * Which emplacements are placed first when the ground runs out.
     *
     * <p>Heaviest first, and the perimeter tiers ahead of the deep ones: a
     * gate covered by a battery of guns is what a defence is, and a rocket
     * battery with the gate wide open behind it is a curiosity. LARGE before
     * MEDIUM before LIGHT puts the heaviest cover on the widest gate, since
     * both lists are worked in order.
     */
    public static final List<DefensePostKind> PRECEDENCE = List.of(
            DefensePostKind.LARGE, DefensePostKind.MEDIUM, DefensePostKind.LIGHT,
            DefensePostKind.ARTILLERY, DefensePostKind.DRONE_HUB);

    public Fortification {
        if (gates < 1) {
            throw new IllegalArgumentException(
                    "a walled place with " + gates + " gates is a place nothing leaves");
        }
        if (wallHp < 1) {
            throw new IllegalArgumentException("a wall with " + wallHp + " hp is not a wall");
        }
        if (posts == null) throw new IllegalArgumentException("a fortification states its posts");
        EnumMap<DefensePostKind, Integer> copy = new EnumMap<>(DefensePostKind.class);
        for (Map.Entry<DefensePostKind, Integer> entry : posts.entrySet()) {
            if (entry.getValue() == null || entry.getValue() < 0) {
                throw new IllegalArgumentException(
                        "a fortification cannot own " + entry.getValue() + " " + entry.getKey());
            }
            if (entry.getValue() > 0) copy.put(entry.getKey(), entry.getValue());
        }
        posts = Collections.unmodifiableMap(copy);
    }

    /** A wall with nothing on it. Rarely what a mission wants; useful as a control. */
    public Fortification(int gates, int wallHp) {
        this(gates, wallHp, Map.of());
    }

    /**
     * A screen rather than a defence. Many ways in, a wall that comes down to
     * ordinary fire, and a pair of light posts — what a raid against an outpost
     * should meet.
     */
    public static final Fortification PICKET = new Fortification(6, 80,
            Map.of(DefensePostKind.LIGHT, 2));

    /**
     * The shipped default, and what the conquest fortress wall has always been
     * worth. A company-scale problem: the ways in are watched, and none of them
     * is watched by anything a squad cannot fight.
     */
    public static final Fortification GARRISON = new Fortification(3, 240,
            Map.of(DefensePostKind.LIGHT, 2, DefensePostKind.MEDIUM, 2));

    /**
     * Two ways in, a wall that wants deliberate breaching, and heavy weapons on
     * both of them with a battery behind. The approach now costs something
     * before the wall does.
     */
    public static final Fortification STRONGHOLD = new Fortification(2, 600,
            Map.of(DefensePostKind.MEDIUM, 2, DefensePostKind.LARGE, 3,
                    DefensePostKind.ARTILLERY, 1));

    /**
     * One way in, a wall nothing short of a siege opens, and enough emplacement
     * to make the ground in front of it the battle. For the capture a whole
     * force was raised for; against anything smaller it is a refusal.
     */
    public static final Fortification CITADEL = new Fortification(1, 1200,
            Map.of(DefensePostKind.MEDIUM, 2, DefensePostKind.LARGE, 4,
                    DefensePostKind.ARTILLERY, 2, DefensePostKind.DRONE_HUB, 1));

    public Fortification withGates(int count) {
        return new Fortification(count, wallHp, posts);
    }

    public Fortification withWallHp(int hp) {
        return new Fortification(gates, hp, posts);
    }

    /** The same place asked to hold {@code count} of one kind of emplacement. */
    public Fortification withPosts(DefensePostKind kind, int count) {
        EnumMap<DefensePostKind, Integer> next = new EnumMap<>(posts);
        next.put(kind, count);
        return new Fortification(gates, wallHp, next);
    }

    /** How many of one kind this asks for. */
    public int posts(DefensePostKind kind) {
        return posts.getOrDefault(kind, 0);
    }

    /** How many emplacements in total, of every kind. */
    public int postCount() {
        int total = 0;
        for (int count : posts.values()) total += count;
        return total;
    }
}

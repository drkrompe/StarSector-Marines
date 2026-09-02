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
 *
 * <p>Stated authored-wins, and derived when nobody states it: a {@link Demand}
 * resolves the world's defence rating against what the mission may ask of its
 * attacker. See {@code precincts.md}.
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

    /**
     * The ladder the named fortifications stand on, so one can be compared
     * with another and a place can be said to be no harder than something.
     */
    public enum Strength {
        PICKET, GARRISON, STRONGHOLD, CITADEL;

        /** The named fortification at this rung. */
        public Fortification fortification() {
            return switch (this) {
                case PICKET -> Fortification.PICKET;
                case GARRISON -> Fortification.GARRISON;
                case STRONGHOLD -> Fortification.STRONGHOLD;
                case CITADEL -> Fortification.CITADEL;
            };
        }

        /**
         * What a world's defence rating says is there.
         *
         * <p>The rating is the campaign's weighted read of the market's own
         * defences — ground batteries, an orbital station, a high command, a
         * shield — on a scale of roughly zero to seven. The breakpoints are the
         * ones the overwatch line already reads at, two and four, so the two
         * consumers of one fact do not disagree about what a fortified world
         * is; a citadel needs heavy batteries and a star fortress at least,
         * which is what "nothing short of a siege" ought to cost.
         */
        public static Strength forDefenceRating(int rating) {
            if (rating >= 6) return CITADEL;
            if (rating >= 4) return STRONGHOLD;
            if (rating >= 2) return GARRISON;
            return PICKET;
        }

        /** This rung moved by {@code rungs}, staying on the ladder. */
        public Strength nudged(int rungs) {
            Strength[] ladder = values();
            int at = Math.max(0, Math.min(ladder.length - 1, ordinal() + rungs));
            return ladder[at];
        }

        /** The lesser of this and a ceiling; {@code null} is no ceiling. */
        public Strength noHarderThan(Strength ceiling) {
            if (ceiling == null || ordinal() <= ceiling.ordinal()) return this;
            return ceiling;
        }
    }

    /**
     * What the mission says about what is being sent.
     *
     * <p>The dial has two failure modes and the world's rating alone cannot
     * avoid either: a heavily defended world would hand a First Contract job a
     * citadel, which is a refusal, and a market with one gun battery would hand
     * a full-strength operation a picket, which is no climax. So a fortification
     * is derived from two facts with different jobs. The <b>rating</b> says what
     * is there. The <b>tier</b> says the hardest thing an operation at that
     * scale may be asked to take, and caps it — after the risk has moved it, so
     * that risk never lifts a place past its tier, which is the mission-tier
     * law that a high-risk job stays recognisably smaller than the next tier's
     * operation. The force actually sent is not consulted, for the same reason
     * the map and the base defender count are not: a mission does not grow
     * because the player brought more lift.
     *
     * @param ceiling  the hardest place this operation may be asked to take
     * @param variance rungs the risk moves the world's answer, either way
     */
    public record Demand(Strength ceiling, int variance) {

        public Demand {
            if (ceiling == null) throw new IllegalArgumentException("a demand states its ceiling");
        }

        /**
         * Nobody said. The world's rung stands, which is what a derivation with
         * no mission behind it — a headless plan, a preview — gets.
         */
        public static final Demand UNSTATED = new Demand(Strength.CITADEL, 0);

        /** The fortification a world of this rating gets under this demand. */
        public Fortification resolve(int defenceRating) {
            return rung(defenceRating).fortification();
        }

        /**
         * The same answer as a rung on the ladder rather than as a
         * fortification.
         *
         * <p>What a lane's {@link LaneResistance} steps down from: an approach
         * is stated relative to the thing it leads to, so it needs the
         * objective's <em>place</em> on the ladder and not the loadout that
         * place resolved to.
         */
        public Strength rung(int defenceRating) {
            return Strength.forDefenceRating(defenceRating)
                    .nudged(variance)
                    .noHarderThan(ceiling);
        }
    }

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

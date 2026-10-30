package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.campaign.WellhollowBloomProgress;
import com.dillon.starsectormarines.ops.CorvusChapter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A rung of the Kit Ladder ({@link KitLadder}): the story mission after which the company store sells what is placed
 * on it ({@code progression-nouns.md}, Kit Ladder). The stations, their campaign order and what reaches each are data
 * ({@code data/marines/kit-ladder.json}, loaded by {@link KitLadderCatalog}); this class owns only the closed set of
 * {@link Reach} conditions a station may name. {@link #START} is what a new company already holds; {@link #NONE} is
 * never reached, so what is placed there is never sold.
 */
public final class UnlockStation implements Comparable<UnlockStation> {

    /** The first station: reached by every company from its founding. The data must declare it first. */
    public static final UnlockStation START = new UnlockStation("start", 0, Reach.ALWAYS);
    /** Never reached: an item placed here is never sold in the store. Implicit, never declared in the data. */
    public static final UnlockStation NONE = new UnlockStation("none", Integer.MAX_VALUE, Reach.NEVER);

    /** The station's id in the data and on the Kit Ladder page. */
    public final String id;
    private final int order;
    private final Reach reach;

    UnlockStation(String id, int order, Reach reach) {
        this.id = id;
        this.order = order;
        this.reach = reach;
    }

    /** What reaches this station. */
    public Reach reach() {
        return reach;
    }

    /** The installed station with this id ({@link #NONE} for "none"), or null for an id the ladder does not declare. */
    public static UnlockStation byId(String id) {
        if (NONE.id.equals(id)) return NONE;
        return KitLadderCatalog.require().station(id);
    }

    /** Every station a company can reach, in campaign order: the whole ladder but {@link #NONE}. */
    public static Set<UnlockStation> all() {
        return new LinkedHashSet<>(KitLadderCatalog.require().stations());
    }

    /**
     * The stations a campaign has passed. {@code storyCompleted} answers for the roster's completed story missions
     * (the Wayline opening); the other arcs are read from their own save-owned ledgers.
     */
    public static Set<UnlockStation> reached(Predicate<String> storyCompleted, CampaignState state) {
        Set<UnlockStation> reached = new LinkedHashSet<>();
        for (UnlockStation station : KitLadderCatalog.require().stations()) {
            if (station.reach.test(storyCompleted, state)) reached.add(station);
        }
        reached.add(START);
        return reached;
    }

    @Override
    public int compareTo(UnlockStation other) {
        return Integer.compare(order, other.order);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof UnlockStation station && station.id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }

    /**
     * What reaches a station: one of a closed set of condition types, named in the data as
     * {@code {"type": ..., <parameters>}} ({@code kit-ladder.md}, Data).
     *
     * <ul>
     *   <li>{@code always}: every company (the start).</li>
     *   <li>{@code storyMission} {@code mission}: the roster completed that story mission.</li>
     *   <li>{@code arcBeat} {@code arc}, {@code beat}: an arc's save-owned ledger records that beat
     *       ({@link #ARC_BEATS} lists every arc and beat the game reads).</li>
     * </ul>
     */
    public static final class Reach {
        static final Reach ALWAYS = new Reach("always", null, null, (story, state) -> true);
        static final Reach NEVER = new Reach("never", null, null, (story, state) -> false);

        /** Every arc a station may name, and for each its beats, read from the campaign's ledgers. */
        static final Map<String, Map<String, Predicate<CampaignState>>> ARC_BEATS = Map.of(
                "broken-supply-route", Map.of(
                        "junction-won", state -> route(state) != null && route(state).junctionWon(),
                        "closed", state -> route(state) != null && route(state).closed()),
                "corvus-account", Map.of(
                        "dust-ledger", state -> corvusWon(state, CorvusChapter.DUST_LEDGER),
                        "blind-watch", state -> corvusWon(state, CorvusChapter.BLIND_WATCH),
                        "cold-account", state -> corvusWon(state, CorvusChapter.COLD_ACCOUNT)),
                "shepherd-recovery", Map.of(
                        "claim-secured", state -> shepherd(state) != null && shepherd(state).claimSecured(),
                        "repaired", state -> shepherd(state) != null && shepherd(state).repaired(),
                        "departed", state -> shepherd(state) != null && shepherd(state).departed()),
                "wellhollow-bloom", Map.of(
                        "colony-breach", state -> bloomWon(state, WellhollowBloomProgress.Chapter.COLONY_BREACH),
                        "cordon-road", state -> bloomWon(state, WellhollowBloomProgress.Chapter.CORDON_ROAD),
                        "the-burn", state -> bloomWon(state, WellhollowBloomProgress.Chapter.THE_BURN),
                        "broad-front", state -> bloomWon(state, WellhollowBloomProgress.Chapter.BROAD_FRONT)),
                "agreus", Map.of(
                        "small-war-settled", state -> state.agreusArc != null && state.agreusArc.smallWarSettled()));

        /** The condition types, with the parameters each needs. */
        static final Map<String, List<String>> TYPES = Map.of(
                "always", List.of(), "storyMission", List.of("mission"), "arcBeat", List.of("arc", "beat"));

        private final String type;
        private final String first;
        private final String second;
        private final Test test;

        private interface Test {
            boolean test(Predicate<String> storyCompleted, CampaignState state);
        }

        private Reach(String type, String first, String second, Test test) {
            this.type = type;
            this.first = first;
            this.second = second;
            this.test = test;
        }

        /** Reached once the roster completes {@code missionId}. */
        static Reach storyMission(String missionId) {
            return new Reach("storyMission", missionId, null,
                    (story, state) -> story != null && story.test(missionId));
        }

        /** Reached once {@code arc}'s ledger records {@code beat}; null for an arc or beat the game does not read. */
        static Reach arcBeat(String arc, String beat) {
            Map<String, Predicate<CampaignState>> beats = ARC_BEATS.get(arc);
            Predicate<CampaignState> read = beats != null ? beats.get(beat) : null;
            if (read == null) return null;
            return new Reach("arcBeat", arc, beat, (story, state) -> state != null && read.test(state));
        }

        boolean test(Predicate<String> storyCompleted, CampaignState state) {
            return test.test(storyCompleted, state);
        }

        public String type() {
            return type;
        }

        @Override
        public String toString() {
            return type + (first != null ? " " + first : "") + (second != null ? " " + second : "");
        }

        private static BrokenSupplyRouteProgress route(CampaignState state) {
            return state.brokenSupplyRoute;
        }

        private static ShepherdRecoveryProgress shepherd(CampaignState state) {
            return state.shepherdRecovery;
        }

        private static boolean corvusWon(CampaignState state, CorvusChapter chapter) {
            CorvusWaylineProgress corvus = state.corvusWayline;
            if (corvus == null || !corvus.readable()) return false;
            return corvus.complete() || corvus.chapter().ordinal() > chapter.ordinal();
        }

        private static boolean bloomWon(CampaignState state, WellhollowBloomProgress.Chapter chapter) {
            WellhollowBloomProgress bloom = state.wellhollowBloom;
            return bloom != null && bloom.won() >= chapter.number();
        }
    }
}

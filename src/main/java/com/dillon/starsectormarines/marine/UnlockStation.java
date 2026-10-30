package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.campaign.WellhollowBloomProgress;
import com.dillon.starsectormarines.ops.CorvusChapter;
import com.dillon.starsectormarines.ops.OpeningOperationKind;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A rung of the Kit Ladder ({@link KitLadder}): the story mission after which the company store sells what is placed
 * on it ({@code progression-nouns.md}, Kit Ladder). Declared in campaign order. {@link #START} is what a new company
 * already holds; {@link #NONE} is never reached, so what is placed there is never sold.
 */
public enum UnlockStation {
    START("start"),
    /** Wayline opening. */
    RELIEF("relief"),
    COUNTERATTACK("counterattack"),
    /** Broken Supply Route. */
    JUNCTION("junction"),
    /** Settled either way: a lost run closes the route, and the ladder does not stall on it. */
    THUNDER_RUN("thunder-run"),
    /** Corvus Account. */
    DUST_LEDGER("dust-ledger"),
    BLIND_WATCH("blind-watch"),
    COLD_ACCOUNT("cold-account"),
    /** A Working Ship (Shepherd recovery). */
    SALVAGE_RIGHTS("salvage-rights"),
    COLD_START("cold-start"),
    WORKING_PASSAGE("working-passage"),
    /** The Wellhollow Bloom. */
    COLONY_BREACH("colony-breach"),
    CORDON_ROAD("cordon-road"),
    THE_BURN("the-burn"),
    BROAD_FRONT("broad-front"),
    /** Agreus, cut from the alpha. */
    A_SMALL_WAR("post-alpha-small-war"),
    NONE("none");

    /** The station's id on the Kit Ladder page. */
    public final String id;

    UnlockStation(String id) {
        this.id = id;
    }

    /** Every station a company can reach: the whole ladder but {@link #NONE}. */
    public static Set<UnlockStation> all() {
        return EnumSet.complementOf(EnumSet.of(NONE));
    }

    /**
     * The stations a campaign has passed. {@code storyCompleted} answers for the roster's completed story missions
     * (the Wayline opening); the other arcs are read from their own save-owned ledgers.
     */
    public static Set<UnlockStation> reached(Predicate<String> storyCompleted, CampaignState state) {
        Set<UnlockStation> reached = EnumSet.of(START);
        if (storyCompleted != null) {
            if (storyCompleted.test(OpeningOperationKind.RELIEF.missionId)) reached.add(RELIEF);
            if (storyCompleted.test(OpeningOperationKind.COUNTERATTACK.missionId)) reached.add(COUNTERATTACK);
        }
        if (state == null) return reached;
        BrokenSupplyRouteProgress route = state.brokenSupplyRoute;
        if (route != null && route.junctionWon()) reached.add(JUNCTION);
        if (route != null && route.closed()) reached.add(THUNDER_RUN);
        CorvusWaylineProgress corvus = state.corvusWayline;
        if (corvusWon(corvus, CorvusChapter.DUST_LEDGER)) reached.add(DUST_LEDGER);
        if (corvusWon(corvus, CorvusChapter.BLIND_WATCH)) reached.add(BLIND_WATCH);
        if (corvusWon(corvus, CorvusChapter.COLD_ACCOUNT)) reached.add(COLD_ACCOUNT);
        ShepherdRecoveryProgress shepherd = state.shepherdRecovery;
        if (shepherd != null && shepherd.claimSecured()) reached.add(SALVAGE_RIGHTS);
        if (shepherd != null && shepherd.repaired()) reached.add(COLD_START);
        if (shepherd != null && shepherd.departed()) reached.add(WORKING_PASSAGE);
        WellhollowBloomProgress bloom = state.wellhollowBloom;
        int bloomWon = bloom != null ? bloom.won() : 0;
        if (bloomWon >= WellhollowBloomProgress.Chapter.COLONY_BREACH.number()) reached.add(COLONY_BREACH);
        if (bloomWon >= WellhollowBloomProgress.Chapter.CORDON_ROAD.number()) reached.add(CORDON_ROAD);
        if (bloomWon >= WellhollowBloomProgress.Chapter.THE_BURN.number()) reached.add(THE_BURN);
        if (bloomWon >= WellhollowBloomProgress.Chapter.BROAD_FRONT.number()) reached.add(BROAD_FRONT);
        if (state.agreusArc != null && state.agreusArc.smallWarSettled()) reached.add(A_SMALL_WAR);
        return reached;
    }

    private static boolean corvusWon(CorvusWaylineProgress corvus, CorvusChapter chapter) {
        if (corvus == null || !corvus.readable()) return false;
        return corvus.complete() || corvus.chapter().ordinal() > chapter.ordinal();
    }
}

package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.salvage.SalvageResource;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.List;

/**
 * A scavenger crew working a ruins site ({@code ruins-expedition.md}, Occupants, Scavengers): the pirate ground
 * roster, sized by the ruins grade alone and never by the company it meets. The grade sets a strength budget in
 * {@link BattleForceScore} units; the crew spends it on its heavy first, then on infantry at the grade's share of
 * regulars, and posts the infantry at the dig and the camp, the rest walking patrol.
 *
 * <p>UNTUNED: the budgets are starting points, to be tuned against a player giving orders.
 *
 * @param budget      the grade's strength, in force-score units
 * @param risk        what the crew's gear and experience roll at
 * @param eliteShare  the share of the infantry that is regulars rather than bulk
 * @param heavies     the crew's machines, which join the patrol pool
 * @param elites      regulars bought
 * @param bulk        bulk bought
 * @param digPost     infantry posted at the dig, the vault
 * @param campPost    infantry posted at the camp, by the stash
 * @param patrolSize  members per patrol squad
 */
public record RuinsCrew(float budget, RiskLevel risk, float eliteShare, List<MechVariant> heavies,
                        int elites, int bulk, int digPost, int campPost, int patrolSize) {

    /** The crew's ground roster: a pirate salvage crew, whatever faction the ruined world was. */
    public static final String FACTION_ID = "pirates";
    /** Share of the infantry posted at the dig; the camp takes {@link #CAMP_SHARE}, patrols the rest. */
    static final float DIG_SHARE = 0.35f;
    static final float CAMP_SHARE = 0.2f;
    /** A post of fewer than this is not a post. */
    static final int MIN_POST = 2;
    /** How far along the vault route from the landing the camp stands: near the landing end. UNTUNED. */
    static final float CAMP_FROM_LANDING = 0.3f;
    /**
     * The fewest route cells between the landing and the camp, so the company never lands in it; a route too short
     * for that puts the camp halfway. UNTUNED.
     */
    static final int CAMP_MIN_CELLS_FROM_LANDING = 24;
    /** Where along the vault route, from the landing, the patrols start their beats, in the order they fill. */
    static final float[] PATROL_BEATS_FROM_LANDING = {0.55f, 0.8f, 0.4f};
    /** The stash's find: what a salvage crew keeps by its camp. */
    static final String STASH_ID = "ruins-stash";
    static final SalvageResource STASH_RESOURCE = SalvageResource.SUPPLIES;

    public RuinsCrew {
        heavies = List.copyOf(heavies);
        if (elites < 0 || bulk < 0 || digPost < 0 || campPost < 0 || digPost + campPost > elites + bulk
                || patrolSize < 1) {
            throw new IllegalArgumentException("a crew posts no more than it has");
        }
    }

    /** The crew a site of this grade holds. */
    public static RuinsCrew of(RuinsGrade grade, GroundRosterProfile roster) {
        if (grade == null) throw new IllegalArgumentException("a crew needs a grade");
        return switch (grade) {
            case SCATTERED -> spend(12f, RiskLevel.LOW, 0.15f, List.of(), 4);
            case WIDESPREAD -> spend(20f, RiskLevel.LOW, 0.2f, List.of(), 4);
            case EXTENSIVE -> spend(30f, RiskLevel.MEDIUM, 0.25f, List.of(), 5);
            case VAST -> spend(60f, RiskLevel.HIGH, 0.3f,
                    roster != null ? roster.heavySupportCycle(1) : List.of(), 5);
        };
    }

    /**
     * Spends a budget: the heavies first, then the most infantry the rest buys at the regulars' share, then the posts
     * out of the infantry. Never over budget.
     */
    static RuinsCrew spend(float budget, RiskLevel risk, float eliteShare, List<MechVariant> heavies, int patrolSize) {
        float infantryBudget = budget - BattleForceScore.defenders(0, 0, heavies);
        if (infantryBudget < 0f) throw new IllegalArgumentException("a crew's heavies fit its budget");
        float perHead = BattleForceScore.MILITIA
                + eliteShare * (BattleForceScore.REGULAR_INFANTRY - BattleForceScore.MILITIA);
        int heads = (int) Math.floor(infantryBudget / perHead + 1e-4f);
        int elites = Math.round(heads * eliteShare);
        while (heads > 0 && BattleForceScore.defenders(heads - elites, elites, List.of()) > infantryBudget + 1e-4f) {
            heads--;
            elites = Math.min(elites, Math.round(heads * eliteShare));
        }
        int dig = post(heads, DIG_SHARE);
        int camp = Math.min(heads - dig, post(heads, CAMP_SHARE));
        return new RuinsCrew(budget, risk, eliteShare, heavies, elites, heads - elites, dig, camp, patrolSize);
    }

    private static int post(int heads, float share) {
        return Math.min(heads, Math.max(MIN_POST, Math.round(heads * share)));
    }

    /**
     * The route point a share of the way from the landing to the vault.
     *
     * @param route       cell pairs from the vault (first) to the landing (last)
     * @param fromLanding 0 is the landing, 1 the vault
     */
    static int[] along(int[] route, float fromLanding) {
        if (route == null || route.length < 2 || (route.length & 1) != 0) {
            throw new IllegalArgumentException("a route of cell pairs");
        }
        int points = route.length / 2;
        float share = Math.max(0f, Math.min(1f, fromLanding));
        int index = Math.round((points - 1) * (1f - share));
        return new int[]{route[index * 2], route[index * 2 + 1]};
    }

    /**
     * The crew's camp on the vault route: {@link #CAMP_FROM_LANDING} of the way from the landing, but at least
     * {@link #CAMP_MIN_CELLS_FROM_LANDING} cells out, and never past halfway to the vault.
     */
    static int[] camp(int[] route) {
        along(route, 0f); // validates the route
        int points = route.length / 2;
        int fromLanding = Math.max(Math.round((points - 1) * CAMP_FROM_LANDING),
                Math.min(CAMP_MIN_CELLS_FROM_LANDING, (points - 1) / 2));
        int index = points - 1 - fromLanding;
        return new int[]{route[index * 2], route[index * 2 + 1]};
    }

    public int infantry() { return elites + bulk; }

    /** Infantry left to the patrol pool once the posts are filled. */
    public int patrolInfantry() { return infantry() - digPost - campPost; }

    /** The crew as the defender allocator takes it. */
    public DefenderRoster roster() {
        return DefenderRoster.explicit(elites, bulk, heavies, patrolSize, risk);
    }
}

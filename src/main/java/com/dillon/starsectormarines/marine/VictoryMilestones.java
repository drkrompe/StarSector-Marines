package com.dillon.starsectormarines.marine;

import java.util.List;

/**
 * The cards a company earns by winning, authored by hand ({@code progression-nouns.md}).
 *
 * <p>A milestone grants one whole card, never a weapon or a suit on its own: the
 * player collects cards and nothing else. Story copy may call it access to some
 * military equipment; what the company receives is the card. The ladder stays
 * deliberately modest — a reward, not twelve marines with a delete button.
 */
public final class VictoryMilestones {

    /**
     * One rung: paid once, the first time the company has won {@code victories}
     * operations, {@code highRiskVictories} of them at high risk.
     */
    public record Milestone(String id, int victories, int highRiskVictories, LoadoutCard card) { }

    // No rung at two victories: the Wayline opening's cards are A Proper Issue's choice
    // and Checkpoint Issue on sale once Relief settles (ArcIssueListings), never a gift.
    private static final List<Milestone> LADDER = List.of(
            new Milestone("victories-3", 3, 0,
                    new LoadoutCard(LoadoutCard.Kind.SHEET, SquadEquipmentDoctrines.SINDRIAN_SECURITY_ARMOR)),
            new Milestone("victories-4", 4, 0,
                    new LoadoutCard(LoadoutCard.Kind.WEAPON, SquadEquipmentDoctrines.TRENCH_CLEARING_WEAPONS)),
            // The first chase: proven operations plus one dangerous one.
            new Milestone("victories-5-high-risk", 5, 1,
                    new LoadoutCard(LoadoutCard.Kind.WEAPON, SquadEquipmentDoctrines.LINE_INFANTRY_WEAPONS)));

    private VictoryMilestones() {}

    public static List<Milestone> ladder() {
        return LADDER;
    }
}

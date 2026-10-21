package com.dillon.starsectormarines.battle.evacuation;

/**
 * One place the rescue cohort is holed up in, as the mission discloses it
 * ({@code colony-breach-nouns.md}, Redoubt). Number 1 is the main redoubt, the
 * shelter; the rest are holdouts.
 *
 * <p>Everything here is what the Marines know: where each redoubt's door is,
 * whether it has been reached, whether it has fallen, and how many of its
 * people are still behind the door or walking out.
 *
 * @param number          1 for the main redoubt, then the holdouts in order
 * @param approachX       the door a Marine must reach to release its people
 * @param approachY       the door a Marine must reach to release its people
 * @param left            the redoubt's building bounds
 * @param top             the redoubt's building bounds
 * @param right           the redoubt's building bounds
 * @param bottom          the redoubt's building bounds
 * @param garrisonSquadId the allied squad holding it, or {@code -1}
 * @param cohort          how many of the cohort it held at the start
 * @param releasedTick    the tick a Marine reached it, or {@code -1}
 * @param fellTick        the tick it fell, or {@code -1}
 * @param sealed          its people still behind the door, alive
 * @param walking         its people released and not yet aboard
 * @param walkingCellX    where its walking people are, or {@code -1}
 * @param walkingCellY    where its walking people are, or {@code -1}
 */
public record CivilianRedoubt(int number, int approachX, int approachY,
                              int left, int top, int right, int bottom,
                              int garrisonSquadId, int cohort,
                              int releasedTick, int fellTick,
                              int sealed, int walking,
                              int walkingCellX, int walkingCellY) {

    public boolean released() { return releasedTick >= 0; }

    public boolean fallen() { return fellTick >= 0; }

    public boolean isMain() { return number == 1; }

    /** Still waiting for relief: not reached, not fallen, someone alive inside. */
    public boolean pending() { return !released() && !fallen() && sealed > 0; }

    /** Whether a cell is inside the redoubt's building bounds, widened by {@code margin}. */
    public boolean contains(int x, int y, int margin) {
        return x >= left - margin && x <= right + margin && y >= top - margin && y <= bottom + margin;
    }
}

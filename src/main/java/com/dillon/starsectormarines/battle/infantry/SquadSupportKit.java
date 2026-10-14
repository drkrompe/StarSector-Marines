package com.dillon.starsectormarines.battle.infantry;

/**
 * The support kit a mission issues some of one side's squads: every {@code mortarTeamEvery}th squad of the force (counting
 * from the first) is a mortar team, one marine of it carrying a field mortar, and every {@code observerEvery}th squad carries a
 * forward observer's comms radio, which is what lets a side's commander call its air pool onto a target.
 *
 * <p>Declared by the mission the way mech lances and the air pool are: nothing about the map or the force's size decides it.
 * Both periods are UNTUNED first-pass choices; a non-positive period issues none of that item.
 *
 * @param mortarTeamEvery every Nth squad is a mortar team; non-positive for none
 * @param observerEvery   every Nth squad carries a comms radio; non-positive for none
 */
public record SquadSupportKit(int mortarTeamEvery, int observerEvery) {

    public static final SquadSupportKit NONE = new SquadSupportKit(0, 0);

    public SquadSupportKit {
        if (mortarTeamEvery < 0) mortarTeamEvery = 0;
        if (observerEvery < 0) observerEvery = 0;
    }

    public boolean isNone() {
        return mortarTeamEvery == 0 && observerEvery == 0;
    }

    /** Whether the {@code ordinal}th squad (zero-based) of the force is a mortar team: the last of each run of N. */
    public boolean mortarTeam(int ordinal) {
        return mortarTeamEvery > 0 && ordinal % mortarTeamEvery == mortarTeamEvery - 1;
    }

    /** Whether the {@code ordinal}th squad (zero-based) of the force carries a comms radio: the last of each run of N. */
    public boolean observer(int ordinal) {
        return observerEvery > 0 && ordinal % observerEvery == observerEvery - 1;
    }
}

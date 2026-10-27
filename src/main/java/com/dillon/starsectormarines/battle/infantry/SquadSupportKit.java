package com.dillon.starsectormarines.battle.infantry;

/**
 * The support kit a mission issues some of one side's squads: every {@code mortarTeamEvery}th squad of the force (counting
 * from the first) is a mortar team, one marine of it carrying a field mortar, and every {@code observerEvery}th squad carries a
 * forward observer's comms radio, which is what lets a side's commander call its air pool onto a target. Two further items
 * exist for the fixtures that exercise the close breach: every {@code satchelEvery}th squad carries a satchel charge (a sapper
 * squad, which an assault plan holds back until its approach is survivable), and every {@code smokeEvery}th a smoke grenade, the
 * long-lasting screen that approach may ask for.
 *
 * <p>Declared by the mission the way mech lances and the air pool are: nothing about the map or the force's size decides it.
 * Every period is an UNTUNED first-pass choice; a non-positive period issues none of that item.
 *
 * @param mortarTeamEvery every Nth squad is a mortar team; non-positive for none
 * @param observerEvery   every Nth squad carries a comms radio; non-positive for none
 * @param satchelEvery    every Nth squad carries a satchel charge; non-positive for none
 * @param smokeEvery      every Nth squad carries a smoke grenade; non-positive for none
 */
public record SquadSupportKit(int mortarTeamEvery, int observerEvery, int satchelEvery, int smokeEvery) {

    public static final SquadSupportKit NONE = new SquadSupportKit(0, 0, 0, 0);

    public SquadSupportKit {
        if (mortarTeamEvery < 0) mortarTeamEvery = 0;
        if (observerEvery < 0) observerEvery = 0;
        if (satchelEvery < 0) satchelEvery = 0;
        if (smokeEvery < 0) smokeEvery = 0;
    }

    /** The tube-and-radio kit alone: no sappers and no smoke. */
    public SquadSupportKit(int mortarTeamEvery, int observerEvery) {
        this(mortarTeamEvery, observerEvery, 0, 0);
    }

    public boolean isNone() {
        return mortarTeamEvery == 0 && observerEvery == 0 && satchelEvery == 0 && smokeEvery == 0;
    }

    /** The same kit also issuing satchel charges and smoke grenades at these periods. */
    public SquadSupportKit withSappers(int satchelEvery, int smokeEvery) {
        return new SquadSupportKit(mortarTeamEvery, observerEvery, satchelEvery, smokeEvery);
    }

    /** Whether the {@code ordinal}th squad (zero-based) of the force is a mortar team: the last of each run of N. */
    public boolean mortarTeam(int ordinal) {
        return mortarTeamEvery > 0 && ordinal % mortarTeamEvery == mortarTeamEvery - 1;
    }

    /** Whether the {@code ordinal}th squad (zero-based) of the force carries a comms radio: the last of each run of N. */
    public boolean observer(int ordinal) {
        return observerEvery > 0 && ordinal % observerEvery == observerEvery - 1;
    }

    /** Whether the {@code ordinal}th squad (zero-based) of the force carries a satchel charge: the last of each run of N. */
    public boolean sapper(int ordinal) {
        return satchelEvery > 0 && ordinal % satchelEvery == satchelEvery - 1;
    }

    /** Whether the {@code ordinal}th squad (zero-based) of the force carries a smoke grenade: the last of each run of N. */
    public boolean screener(int ordinal) {
        return smokeEvery > 0 && ordinal % smokeEvery == smokeEvery - 1;
    }
}

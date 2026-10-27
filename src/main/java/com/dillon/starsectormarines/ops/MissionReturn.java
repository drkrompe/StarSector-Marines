package com.dillon.starsectormarines.ops;

/**
 * Where a company page's way back to work leads: the contract board, or the briefing of the
 * mission selected and not yet fought. A fought mission never reaches a company page: every way
 * off its debrief settles it first ({@link MarineOpsContext#refitAfterSettlement}).
 */
enum MissionReturn {
    MISSIONS(ScreenId.MISSION_SELECT, "openingBarracksCommandMissions", "openingBarracksCommandMissionsWorkHere"),
    BRIEFING(ScreenId.BRIEFING, "openingBarracksCommandBriefingResume", "openingBarracksCommandBriefingResume");

    final ScreenId screen;
    private final String quietLabelKey;
    private final String callingLabelKey;

    MissionReturn(ScreenId screen, String quietLabelKey, String callingLabelKey) {
        this.screen = screen;
        this.quietLabelKey = quietLabelKey;
        this.callingLabelKey = callingLabelKey;
    }

    /**
     * Whether the way back to work asks for the player's eye: a mission selected and not yet
     * fought always does, since the company is mid-contract; the board only while a contact at
     * this planet has work the company may take.
     */
    boolean calls(boolean workHere) {
        return this == BRIEFING || workHere;
    }

    String labelKey(boolean workHere) {
        return calls(workHere) ? callingLabelKey : quietLabelKey;
    }

    static MissionReturn of(MarineOpsContext context) {
        return context.getSelectedMission() != null ? BRIEFING : MISSIONS;
    }
}

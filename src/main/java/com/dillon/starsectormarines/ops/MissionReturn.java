package com.dillon.starsectormarines.ops;

/**
 * Where a company page's way back to work leads: the contract board, or the briefing of the
 * mission selected and not yet fought. A fought mission never reaches a company page: every way
 * off its debrief settles it first ({@link MarineOpsContext#refitAfterSettlement}).
 */
enum MissionReturn {
    MISSIONS(ScreenId.MISSION_SELECT, "openingBarracksCommandMissions"),
    BRIEFING(ScreenId.BRIEFING, "openingBarracksCommandBriefing");

    final ScreenId screen;
    final String labelKey;

    MissionReturn(ScreenId screen, String labelKey) {
        this.screen = screen;
        this.labelKey = labelKey;
    }

    static MissionReturn of(MarineOpsContext context) {
        return context.getSelectedMission() != null ? BRIEFING : MISSIONS;
    }
}

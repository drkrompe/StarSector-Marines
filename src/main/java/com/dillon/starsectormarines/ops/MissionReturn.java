package com.dillon.starsectormarines.ops;

/**
 * Where a company page's way back to work leads: the contract board, the briefing of a mission
 * not yet fought, or the debrief of one fought but not yet settled. A fought mission stays
 * selected until its debrief settles ({@link MarineOpsContext#finishOperation}), and a page
 * reached from the debrief (its First Refit lesson) must lead back there, not to a briefing
 * for a battle already over.
 */
enum MissionReturn {
    MISSIONS(ScreenId.MISSION_SELECT, "openingBarracksCommandMissions"),
    BRIEFING(ScreenId.BRIEFING, "openingBarracksCommandBriefing"),
    DEBRIEF(ScreenId.RESULTS, "openingBarracksCommandDebrief");

    final ScreenId screen;
    final String labelKey;

    MissionReturn(ScreenId screen, String labelKey) {
        this.screen = screen;
        this.labelKey = labelKey;
    }

    static MissionReturn of(boolean missionSelected, boolean fought) {
        if (!missionSelected) return MISSIONS;
        return fought ? DEBRIEF : BRIEFING;
    }

    static MissionReturn of(MarineOpsContext context) {
        return of(context.getSelectedMission() != null, context.getLastOutcome() != null);
    }
}

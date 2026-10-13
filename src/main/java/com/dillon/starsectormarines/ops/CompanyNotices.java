package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import com.dillon.starsectormarines.campaign.start.CompanyBayRestoration;
import com.dillon.starsectormarines.campaign.start.SecondhandWingsCampaign;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.fs.starfarer.api.Global;
import java.util.ArrayList;
import java.util.List;

/** Unread opportunities, never another owner of rewards, eligibility or payment. */
final class CompanyNotices {
    enum Notice {
        SHEPHERD_RECOVERY("shepherd-recovery", MarineOpsPageNav.Page.HQ, ScreenId.COMPANY_HQ, "Mara's introduction to Tamsin Vale"),
        SECONDHAND_WINGS("secondhand-wings", MarineOpsPageNav.Page.BOAT_DECK, ScreenId.BOAT_DECK, "Secondhand Wings"),
        SECOND_BAY("second-bay", MarineOpsPageNav.Page.BOAT_DECK, ScreenId.BOAT_DECK, "Second bay restoration"),
        FIRST_REFIT("first-refit", MarineOpsPageNav.Page.BARRACKS, ScreenId.BARRACKS, "Castle's first refit"),
        HOUND_INDUCTION("hound-induction", MarineOpsPageNav.Page.MECH_LAB, ScreenId.MECH_LAB, "Wayward gantry induction");

        final String id, label;
        final MarineOpsPageNav.Page page;
        final ScreenId screen;
        Notice(String id, MarineOpsPageNav.Page page, ScreenId screen, String label) {
            this.id = id; this.page = page; this.screen = screen; this.label = label;
        }
        void review(MarineRoster roster) { if (roster != null) roster.reviewCompanyNotice(id); }
    }

    private CompanyNotices() { }

    static List<Notice> current() {
        MarineRoster roster = CompanyPreparationGuide.roster();
        if (roster == null || Global.getSector() == null) return List.of();
        var found = new ArrayList<>(unread(roster, CompanyBayRestoration.state(CompanyShipDesignation.aboard())));
        var recovery = ShepherdRecoveryCampaign.current();
        add(found, roster, Notice.SHEPHERD_RECOVERY, recovery != null && !recovery.claimSecured());
        return List.copyOf(found);
    }

    /** Funds and docking do not hide a newly unlocked opportunity. */
    static List<Notice> unread(MarineRoster roster, BoatBayState bay) {
        if (roster == null) return List.of();
        var found = new ArrayList<Notice>();
        add(found, roster, Notice.SECONDHAND_WINGS, SecondhandWingsCampaign.available(roster));
        add(found, roster, Notice.SECOND_BAY, bay == BoatBayState.DAMAGED
                && roster.hasCompletedStory(OpeningOperationKind.RELIEF.missionId));
        add(found, roster, Notice.FIRST_REFIT, FirstRefit.available(roster));
        add(found, roster, Notice.HOUND_INDUCTION, roster.mechBay().labLesson() > 0
                && roster.mechBay().labLesson() < 5);
        return List.copyOf(found);
    }

    private static void add(List<Notice> found, MarineRoster roster, Notice notice, boolean available) {
        if (available && !roster.hasReviewedCompanyNotice(notice.id)) found.add(notice);
    }

    static String summary(List<Notice> notices) {
        if (notices.isEmpty()) return "";
        Notice first = notices.get(0);
        String room = first.page == MarineOpsPageNav.Page.HQ ? "HQ" : first.page == MarineOpsPageNav.Page.BOAT_DECK ? "BOATS"
                : first.page == MarineOpsPageNav.Page.MECH_LAB ? "MECH LAB" : "BARRACKS";
        return "NEW / " + room + ": " + first.label
                + (notices.size() > 1 ? "  (+" + (notices.size() - 1) + ")" : "");
    }
}

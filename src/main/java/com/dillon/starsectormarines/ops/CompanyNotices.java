package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
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
        AGREUS_REFERRAL("agreus-referral", MarineOpsPageNav.Page.HQ, ScreenId.COMPANY_HQ, "openingCompanyNoticesAgreusReferral"),
        SHEPHERD_RECOVERY("shepherd-recovery", MarineOpsPageNav.Page.HQ, ScreenId.COMPANY_HQ, "openingCompanyNoticesMaraSIntroductionToTamsinVale"),
        WORKING_PASSAGE("shepherd-working-passage", MarineOpsPageNav.Page.HQ, ScreenId.COMPANY_HQ, "openingCompanyNoticesWorkingPassage"),
        COLD_START("shepherd-cold-start", MarineOpsPageNav.Page.HQ, ScreenId.COMPANY_HQ, "openingCompanyNoticesColdStart"),
        SECONDHAND_WINGS("secondhand-wings", MarineOpsPageNav.Page.BOAT_DECK, ScreenId.BOAT_DECK, "openingCompanyNoticesSecondhandWings"),
        SECOND_BAY("second-bay", MarineOpsPageNav.Page.BOAT_DECK, ScreenId.BOAT_DECK, "openingCompanyNoticesSecondBayRestoration"),
        FIRST_REFIT("first-refit", MarineOpsPageNav.Page.BARRACKS, ScreenId.BARRACKS, "openingCompanyNoticesCastleSFirstRefit"),
        HOUND_INDUCTION("hound-induction", MarineOpsPageNav.Page.MECH_LAB, ScreenId.MECH_LAB, "openingCompanyNoticesWaywardGantryInduction");

        final String id, labelKey;
        final MarineOpsPageNav.Page page;
        final ScreenId screen;
        Notice(String id, MarineOpsPageNav.Page page, ScreenId screen, String label) {
            this.id = id; this.page = page; this.screen = screen; this.labelKey = label;
        }
        void review(MarineRoster roster) { if (roster != null) roster.reviewCompanyNotice(id); }
        String label() { return Strings.get(labelKey); }
    }

    private CompanyNotices() { }

    static List<Notice> current() {
        MarineRoster roster = CompanyPreparationGuide.roster();
        if (roster == null || Global.getSector() == null) return List.of();
        var found = new ArrayList<>(unread(roster, CompanyBayRestoration.state(CompanyShipDesignation.aboard())));
        var recovery = ShepherdRecoveryCampaign.current();
        var agreus = AgreusArcCampaign.current();
        add(found, roster, Notice.AGREUS_REFERRAL, agreus != null && !agreus.completed());
        add(found, roster, Notice.SHEPHERD_RECOVERY, recovery != null && !recovery.claimSecured());
        add(found, roster, Notice.COLD_START, recovery != null && recovery.claimSecured() && !recovery.repaired());
        add(found, roster, Notice.WORKING_PASSAGE, recovery != null && recovery.repaired() && !recovery.departed());
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
        String room = first.page == MarineOpsPageNav.Page.HQ ? Strings.get("openingCompanyNoticesHq") : first.page == MarineOpsPageNav.Page.BOAT_DECK ? Strings.get("openingCompanyNoticesBoats")
                : first.page == MarineOpsPageNav.Page.MECH_LAB ? Strings.get("openingCompanyNoticesMechLab") : Strings.get("openingCompanyNoticesBarracks");
        return Strings.format("openingCompanyNoticesNew", room, first.label(), (notices.size() > 1 ? "  (+" + (notices.size() - 1) + ")" : ""));
    }
}

package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.OfficerInduction;
import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.battle.story.TermsOfProtectionProgress;
import com.dillon.starsectormarines.marine.LoadoutCard;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.text.NumberFormat;

/** Read-only conversations selected from the settled mission, never reward or completion authority. */
final class MissionDebriefConversation {
    private MissionDebriefConversation() { }

    static boolean coldAccount(MissionOutcome outcome) {
        return outcome != null && outcome.victory
                && (outcome.missionSource == MissionSource.STORY
                    && CorvusWaylineProgress.forMission(outcome.missionId) == CorvusChapter.COLD_ACCOUNT
                || DebugMissionCatalog.scenario(outcome.missionId, outcome.missionSource)
                    == DebugMissionCatalog.Scenario.COLD_ACCOUNT);
    }

    static boolean blindWatch(MissionOutcome outcome) {
        return outcome != null && outcome.victory && outcome.missionSource == MissionSource.STORY
                && CorvusWaylineProgress.forMission(outcome.missionId) == CorvusChapter.BLIND_WATCH;
    }

    static String title(MissionOutcome outcome) {
        if (smallWar(outcome)) return Strings.get("debriefTitleSmallWar");
        if (termsOfProtection(outcome)) return Strings.get("debriefTitleTermsOfProtection");
        if (yardOwners(outcome)) return Strings.get("debriefTitleYardOwners");
        if (limitedWarrant(outcome)) return Strings.get("debriefTitleLimitedWarrant");
        if (waterUnderSeal(outcome)) return Strings.get("debriefTitleWaterUnderSeal");
        if (outcome != null && outcome.workingPassage != null) return Strings.get("shepherdPassageTranscriptTitle");
        if (outcome != null && outcome.coldStart != null) return Strings.get("shepherdColdTranscriptTitle");
        if (outcome != null && outcome.salvageRights != null) return Strings.get("shepherdInspectionTranscriptTitle");
        if (firstContract(outcome)) return Strings.get("debriefTitleFirstContract");
        if (jangalaClosing(outcome, SupplyRouteCampaign.current())) return Strings.get("debriefTitleJangala");
        return Strings.get(coldAccount(outcome) || blindWatch(outcome)
                ? "debriefTitleAfterAction" : "debriefTitleRecovery");
    }

    static List<WaylineNarration.Line> lines(MissionOutcome outcome, WaylineNarration catalog) {
        if (smallWar(outcome)) return smallWarLines(outcome);
        if (termsOfProtection(outcome)) return termsLines(outcome);
        if (yardOwners(outcome)) return yardLines(outcome);
        if (limitedWarrant(outcome)) return warrantLines(outcome);
        if (waterUnderSeal(outcome)) return weirLines(outcome);
        if (outcome != null && outcome.victory && outcome.workingPassage != null) {
            if (outcome.missionSource.isDebug()) return List.of(
                    recoveryLine("departure-registry", true, Strings.get("shepherdDebriefPassageExercise")),
                    recoveryLine("departure-corridor", false, Strings.get("shepherdDebriefPassageExerciseTerms")),
                    recoveryLine("departure-crew", true, Strings.get("shepherdDebriefPassageExerciseCrew")));
            String farewell = outcome.workingPassage.militiaLost() > 0 ? "militia_losses"
                    : outcome.marinesLost > 0 ? "company_losses" : "jory";
            return authored(catalog, "story_shepherd_working_passage", "debrief.passage.",
                    "registry", "filters", "hands", "jangala", farewell);
        }
        if (outcome != null && outcome.victory && outcome.coldStart != null) {
            if (outcome.missionSource.isDebug()) return List.of(
                    recoveryLine("repair-complete", false, Strings.get("shepherdDebriefColdExercise")),
                    recoveryLine("repair-cost", true, Strings.get("shepherdDebriefColdExerciseCosts")));
            boolean losses = outcome.marinesLost > 0 || outcome.coldStart.crewLost() > 0;
            return authored(catalog, "story_shepherd_cold_start", "debrief.cold.", "builders", "worth",
                    losses ? "losses" : "checkpoints", losses ? "road_after_losses" : "paid_off");
        }
        if (outcome != null && outcome.victory && outcome.salvageRights != null) {
            if (outcome.missionSource.isDebug()) return List.of(
                    recoveryLine("registry", true, Strings.get("shepherdDebriefSalvageExercise")),
                    recoveryLine("terms", false, Strings.get("shepherdDebriefSalvageExerciseRepairParty")));
            return authored(catalog, "story_shepherd_salvage", "debrief.salvage.", "cables", "still_mine", "far",
                    "power", "one_more", "guards", outcome.salvageRights.crewLost() > 0 ? "losses" : "ready", "apron");
        }
        if (firstContract(outcome)) return List.of(
                firstContractLine("receipt", CASTLE, Strings.get("debriefReliefReceipt")),
                firstContractLine("muster", SABINE, Strings.get(outcome.marinesLost > 0
                        ? "debriefReliefMusterLosses" : "debriefReliefMusterClean")),
                firstContractLine("names", MARA, Strings.get("debriefReliefNames")),
                firstContractLine("cost", CASTLE, Strings.get(outcome.marinesLost > 0
                        ? "debriefReliefCostLosses" : "debriefReliefCostClean")),
                firstContractLine("next", MARA, Strings.get("debriefReliefNext")));
        var route = SupplyRouteCampaign.current();
        if (jangalaClosing(outcome, route)) return jangalaLines(catalog);
        if (blindWatch(outcome)) return List.of("initiative", "ready")
                .stream().map(beat -> catalog.find("story_corvus_blind_watch", "debrief.blind_watch." + beat))
                .filter(Objects::nonNull).toList();
        if (!coldAccount(outcome)) return HoundSalvageDebrief.lines(outcome, catalog);
        return List.of("orders", "consideration", "one_ship", "payroll", "way_out", "debt")
                .stream().map(beat -> catalog.find("story_corvus_cold_account", "debrief.cold_account." + beat))
                .filter(Objects::nonNull).toList();
    }

    /** Shepherd debrief turns are catalog lines, so a recorded take plays where one exists. */
    private static List<WaylineNarration.Line> authored(WaylineNarration catalog, String missionId, String prefix,
                                                        String... beats) {
        return Arrays.stream(beats).map(beat -> catalog.find(missionId, prefix + beat))
                .filter(Objects::nonNull).toList();
    }

    private static boolean smallWar(MissionOutcome o) {
        return o != null && o.missionSource == MissionSource.STORY && AgreusArcProgress.smallWar(o.missionId) && o.smallWar != null;
    }
    /**
     * Chapter 5's debrief: the selected agreement, confirmed losses and delivered rewards, read only
     * from the frozen outcome. The duel overhead, when one was fought, gets Castle's one line from its ledger,
     * win or lose: the ground result never depends on it.
     */
    static List<WaylineNarration.Line> smallWarLines(MissionOutcome o) {
        var r = o.smallWar;
        String route = Strings.get(r.houseCustody() ? "debriefRouteHouseCustody" : "debriefRouteYardCompact");
        String cost = Strings.format("debriefSluiceCost", o.marinesLost, r.custodiansLost(), r.holdersLost());
        var overhead = o.smallWarOverhead == null ? null : sluiceLine("overhead", "CASTLE", o.smallWarOverhead);
        if (!o.victory) return withOverhead(List.of(
                sluiceLine("withdrawal", "CASTLE", Strings.format(r.infrastructureLost() ? "debriefSluiceWithdrawalFuel"
                        : r.custodiansLost() >= 3 ? "debriefSluiceWithdrawalDead"
                        : "debriefSluiceWithdrawalIncomplete", cost)),
                sluiceLine("replacement", "RUSK", Strings.get(r.infrastructureLost()
                        ? "debriefSluiceReplacementFuel" : "debriefSluiceReplacement"))), overhead);
        int premium = r.houseCustody() ? 12000 : 0;
        String paid = NumberFormat.getIntegerInstance().format(o.payoutEarned + premium);
        String withdrawn = r.greyhookWithdrawn() > 0
                ? Strings.format("debriefSluiceWithdrawnSome", r.greyhookWithdrawn())
                : Strings.get("debriefSluiceWithdrawnNone");
        String closing = o.marinesLost > 0
                ? Strings.format("debriefSluiceClosingLosses", o.marinesLost)
                : Strings.get("debriefSluiceClosingClean");
        return withOverhead(List.of(
                sluiceLine("handover", "CASTLE", Strings.format("debriefSluiceHandover", route, withdrawn)),
                sluiceLine("award", "RUSK", Strings.format(premium > 0
                        ? "debriefSluiceAwardPremium" : "debriefSluiceAwardCompact", paid)),
                sluiceLine("cost", "CASTLE", cost),
                AgreusArcCampaign.VOSS_LAST,
                sluiceLine("closing", "CASTLE", closing)), overhead);
    }

    /** Puts the overhead line after the opening line, where Castle says what happened. */
    private static List<WaylineNarration.Line> withOverhead(List<WaylineNarration.Line> lines, WaylineNarration.Line overhead) {
        if (overhead == null) return lines;
        var out = new ArrayList<>(lines);
        out.add(1, overhead);
        return List.copyOf(out);
    }
    private static WaylineNarration.Line sluiceLine(String beat, String speaker, String text) {
        return new WaylineNarration.Line("small-war-debrief-" + beat, "story_agreus_small_war", "debrief.small_war." + beat, text, 12f, 10, 15f, List.of(),
                Strings.get(speaker.equals("CASTLE") ? "debriefSpeakerCastle" : "debriefSpeakerRusk"),
                speaker.equals("CASTLE") ? CompanyManagementGuide.portrait() : OpeningBroker.CREST, false, false);
    }
    private static boolean termsOfProtection(MissionOutcome o) {
        return o != null && o.missionSource == MissionSource.STORY && AgreusArcProgress.termsOfProtection(o.missionId) && o.termsOfProtection != null;
    }
    private static List<WaylineNarration.Line> termsLines(MissionOutcome o) {
        var r = o.termsOfProtection;
        boolean custody = r.route() == TermsOfProtectionProgress.Route.HOUSE_CUSTODY;
        String cost = Strings.format(custody ? "debriefTermsCostCustody" : "debriefTermsCostFitters",
                o.marinesLost, r.representativesLost(), r.techniciansLost(),
                custody ? r.guardsLost() : r.fittersLost(), r.constablesLost());
        if (!o.victory) {
            String why = r.carrierLost() ? "debriefTermsWhyCarrier"
                    : r.representativesLost() >= TermsOfProtectionProgress.REPRESENTATIVES ? "debriefTermsWhyRepresentatives"
                    : !r.commissioned() && r.techniciansLost() >= TermsOfProtectionProgress.TECHNICIANS ? "debriefTermsWhyTechnicians"
                    : "debriefTermsWhyNotRatified";
            return List.of(
                    termsLine("withdrawal", "CASTLE", Strings.format(why, cost)),
                    termsLine("replacement", "RUSK", Strings.get("debriefTermsReplacement")));
        }
        var card = LoadoutCard.parse(AgreusArcProgress.BOARDING_JACK_CARD);
        boolean pending = card == null || !card.presentationDeclared();
        // Whoever can actually speak to the work explains it; a dead witness is not quoted.
        var witness = r.techniciansLost() < TermsOfProtectionProgress.TECHNICIANS
                ? termsLine("dossier", Strings.get("debriefSpeakerUtilitiesCrew"), Strings.get("debriefTermsDossierCrew"))
                : termsLine("dossier", "CASTLE", Strings.get("debriefTermsDossierCastle"));
        return List.of(
                termsLine("ratified", Strings.get(custody ? "debriefSpeakerCustodyRecord" : "debriefSpeakerJointRecord"),
                        Strings.get(custody ? "debriefTermsRatifiedCustody" : "debriefTermsRatifiedCompact")),
                termsLine("award", "RUSK", Strings.get("debriefTermsAward")),
                witness,
                termsLine("issue", "CASTLE", Strings.get(pending
                        ? "debriefTermsIssuePending" : "debriefTermsIssueReady")),
                AgreusArcCampaign.RUSK_FAMILY,
                termsLine("drent", Strings.get("debriefSpeakerDrent"), AgreusArcCampaign.DRENT_LEAD),
                termsLine("cost", "CASTLE", Strings.format("debriefTermsClosing", cost)));
    }
    private static WaylineNarration.Line termsLine(String beat, String speaker, String text) {
        return new WaylineNarration.Line("terms-debrief-" + beat, "story_agreus_terms_of_protection", "debrief.terms_of_protection." + beat, text, 12f, 10, 15f, List.of(),
                shortSpeaker(speaker), speaker.equals("CASTLE") ? CompanyManagementGuide.portrait() : OpeningBroker.CREST, false, false);
    }
    /** Castle and Rusk are identified by id; any other speaker arrives as its own translated label. */
    private static String shortSpeaker(String speaker) {
        return speaker.equals("CASTLE") ? Strings.get("debriefSpeakerCastleShort")
                : speaker.equals("RUSK") ? Strings.get("debriefSpeakerRuskShort") : speaker;
    }
    private static boolean yardOwners(MissionOutcome o) {
        return o != null && o.missionSource == MissionSource.STORY && AgreusArcProgress.yardOwners(o.missionId) && o.yardOwners != null;
    }
    private static List<WaylineNarration.Line> yardLines(MissionOutcome o) {
        var r = o.yardOwners;
        String cost = Strings.format("debriefYardCost", o.marinesLost, r.techniciansLost(), r.constablesLost());
        if (!o.victory) return List.of(
                yardLine("withdrawal","CASTLE",Strings.format(r.toolingLost()
                        ? "debriefYardWithdrawalTooling" : "debriefYardWithdrawalIncomplete", cost)),
                yardLine("replacement","RUSK",Strings.get("debriefYardReplacement")));
        return List.of(
                yardLine("award","RUSK",Strings.get("debriefYardAward")),
                yardLine("issue","CASTLE",Strings.get("debriefYardIssue")),
                yardLine("order",Strings.get("debriefSpeakerRegistry"),Strings.get("debriefYardOrder")),
                yardLine("leases",Strings.get("debriefSpeakerArrears"),Strings.get("debriefYardLeases")),
                yardLine("counterproposal",Strings.get("debriefSpeakerCompact"),Strings.get("debriefYardCounterproposal")),
                AgreusArcCampaign.LENNA_GATE,
                AgreusArcCampaign.LENNA_LIEN,
                yardLine("cost","CASTLE",Strings.format("debriefYardClosing", cost)));
    }
    private static WaylineNarration.Line yardLine(String beat,String speaker,String text) {
        return new WaylineNarration.Line("yard-debrief-"+beat,"story_agreus_yard_owners","debrief.yard_owners."+beat,text,12f,10,15f,List.of(),
                shortSpeaker(speaker),speaker.equals("CASTLE") ? CompanyManagementGuide.portrait() : OpeningBroker.CREST,false,false);
    }
    private static boolean limitedWarrant(MissionOutcome outcome) {
        return outcome != null && outcome.missionSource == MissionSource.STORY
                && AgreusArcProgress.limitedWarrant(outcome.missionId) && outcome.limitedWarrant != null;
    }
    private static List<WaylineNarration.Line> warrantLines(MissionOutcome outcome) {
        var r = outcome.limitedWarrant;
        String cost = Strings.format("debriefWarrantCost", outcome.marinesLost, r.techniciansLost(), r.constablesLost());
        if (outcome.victory) return List.of(
                warrantLine("records", true, Strings.get("debriefWarrantRecords")),
                warrantLine("award", false, Strings.get("debriefWarrantAward")),
                warrantLine("issue", true, Strings.get("debriefWarrantIssue")),
                warrantLine("cost", true, cost));
        return List.of(warrantLine("withdrawal", true, Strings.format("debriefWarrantWithdrawal",
                        r.recordsRecovered() ? 1 : 0, r.cargoLost() ? 1 : 0)),
                warrantLine("unpaid", false, Strings.get("debriefWarrantUnpaid")),
                warrantLine("cost", true, cost));
    }
    private static WaylineNarration.Line warrantLine(String beat, boolean castle, String text) {
        return new WaylineNarration.Line("warrant-debrief-" + beat, "story_agreus_limited_warrant",
                "debrief.limited_warrant." + beat, text, 10f, 10, 15f, List.of(),
                Strings.get(castle ? "debriefSpeakerCastle" : "debriefSpeakerRusk"),
                castle ? CompanyManagementGuide.portrait() : MissionDialogue.speakerPortrait("vale"), false, false);
    }

    private static boolean waterUnderSeal(MissionOutcome outcome) {
        return outcome != null && outcome.missionSource == MissionSource.STORY
                && AgreusArcCampaign.owns(outcome.missionId) && outcome.waterUnderSeal != null;
    }

    private static List<WaylineNarration.Line> weirLines(MissionOutcome outcome) {
        var report = outcome.waterUnderSeal;
        String cost = Strings.format("debriefWeirCost", outcome.marinesLost, report.militiaLost(), report.techniciansLost());
        if (outcome.victory) return List.of(
                weirLine("secured", true, Strings.get("debriefWeirSecured")),
                weirLine("invoice", false, Strings.format("debriefWeirInvoice",
                        NumberFormat.getIntegerInstance().format(outcome.payoutEarned))),
                weirLine("cost", true, cost),
                weirLine("closure", false, Strings.get("debriefWeirClosure")));
        String work = Strings.format("debriefWeirWork", report.northRoomReconnected() ? 1 : 0,
                report.southRoomReconnected() ? 1 : 0, report.cargoLost() ? 1 : 0);
        return List.of(
                weirLine("withdrawal", true, Strings.format("debriefWeirWithdrawal", work)),
                weirLine("unpaid", false, Strings.get("debriefWeirUnpaid")),
                weirLine("cost", true, cost),
                weirLine("retry", false, Strings.get("debriefWeirRetry")));
    }

    private static WaylineNarration.Line weirLine(String beat, boolean castle, String text) {
        return new WaylineNarration.Line("weir-debrief-" + beat, "story_agreus_water_under_seal",
                "debrief.water_under_seal." + beat, text, 10f, 10, 15f, List.of(),
                Strings.get(castle ? "debriefSpeakerCastle" : "debriefSpeakerRusk"),
                castle ? CompanyManagementGuide.portrait() : MissionDialogue.speakerPortrait("vale"), false, false);
    }

    private static boolean firstContract(MissionOutcome outcome) {
        return outcome != null && outcome.victory && WaylineArc.chapter(outcome) == OpeningOperationKind.RELIEF;
    }

    private static WaylineNarration.Line recoveryLine(String beat, boolean castle, String text) {
        return new WaylineNarration.Line("shepherd-debrief-" + beat, "story_shepherd_salvage",
                "debrief.salvage." + beat, text, 10f, 10, 15f, List.of(),
                Strings.get(castle ? "debriefSpeakerCastle" : "debriefSpeakerTamsin"),
                castle ? CompanyManagementGuide.portrait() : MissionDialogue.speakerPortrait("vale"), false, false);
    }

    /**
     * The won Thunder Run, which closed the freight account. Only that attempt says goodbye to Jangala;
     * an exhausted account is a failure and keeps its factual report, as do retries and Route Security.
     */
    static boolean jangalaClosing(MissionOutcome outcome, BrokenSupplyRouteProgress route) {
        return outcome != null && outcome.victory && outcome.missionSource == MissionSource.STORY
                && outcome.freightOutcome != null && route != null && route.readable() && route.successful()
                && route.lastMissionId().equals(outcome.missionId);
    }

    /**
     * Ends Wayline's brush with bankruptcy: the star-port road is back and its owed contracts ship,
     * though nobody yet knows why it happened. The recorded turns come from the catalog; Castle's closing
     * line stays written until the Asharu lead it hands the company to is settled.
     */
    static List<WaylineNarration.Line> jangalaLines(WaylineNarration catalog) {
        var lines = new ArrayList<>(authored(catalog, JANGALA_MISSION, "debrief.jangala.", "road", "manifests", "why"));
        lines.add(writtenLine("jangala-closing-next", JANGALA_MISSION, "debrief.jangala.next", CASTLE,
                Strings.get("debriefJangalaNext")));
        return List.copyOf(lines);
    }
    private static final String JANGALA_MISSION = "story_supply_route_thunder_run";

    /** Written correspondence has no recording; keep it readable without offering silent playback. */
    private static final int CASTLE = 0, MARA = 1, SABINE = 2;
    private static WaylineNarration.Line firstContractLine(String beat, int speaker, String text) {
        return writtenLine("first-contract-" + beat, OpeningOperationKind.RELIEF.missionId, "debrief.relief." + beat,
                speaker, text);
    }
    private static WaylineNarration.Line writtenLine(String id, String missionId, String event, int speaker, String text) {
        return new WaylineNarration.Line(id, missionId, event, text, 10f, 10, 15f, List.of(),
                Strings.get(switch (speaker) { case CASTLE -> "debriefSpeakerCastle"; case SABINE -> "debriefSpeakerSabine";
                    default -> "debriefSpeakerMara"; }),
                switch (speaker) { case CASTLE -> CompanyManagementGuide.portrait(); case SABINE -> OfficerInduction.SABINE_PORTRAIT;
                    default -> OpeningBroker.PORTRAIT; }, false, false);
    }

    static List<WaylineNarration.Line> recordings(MissionOutcome outcome, WaylineNarration catalog) {
        return lines(outcome, catalog).stream().filter(WaylineNarration.Line::voiced).toList();
    }
}

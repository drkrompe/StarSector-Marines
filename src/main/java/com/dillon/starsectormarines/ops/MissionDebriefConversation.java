package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.OfficerInduction;
import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
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
        if (smallWar(outcome)) return "BLACK SLUICE / CASTLE, RUSK & VOSS";
        if (yardOwners(outcome)) return "LATCHKEY / CASTLE, RUSK & LENNA";
        if (limitedWarrant(outcome)) return "LIMITED WARRANT / CASTLE & RUSK";
        if (waterUnderSeal(outcome)) return "NORTH WEIR / CASTLE & RUSK";
        if (outcome != null && outcome.workingPassage != null) return Strings.get("shepherdPassageTranscriptTitle");
        if (outcome != null && outcome.coldStart != null) return Strings.get("shepherdColdTranscriptTitle");
        if (outcome != null && outcome.salvageRights != null) return Strings.get("shepherdInspectionTranscriptTitle");
        if (firstContract(outcome)) return "FIRST CONTRACT / CASTLE & MARA";
        return coldAccount(outcome) || blindWatch(outcome) ? "AFTER ACTION / CASTLE & MARA" : "RECOVERY AGREEMENT / TRANSCRIPT";
    }

    static List<WaylineNarration.Line> lines(MissionOutcome outcome, WaylineNarration catalog) {
        if (smallWar(outcome)) return smallWarLines(outcome);
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
                firstContractLine("receipt", CASTLE, "Payment's in. I counted it twice. Our first paid contract, Commander. Somebody paid us to do exactly what we said we'd do."),
                firstContractLine("muster", SABINE, outcome.marinesLost > 0
                        ? "I've counted twice. The roll's shorter. I'll give Castle the names."
                        : "All twelve back aboard. Counted twice."),
                firstContractLine("names", MARA, "The drivers asked who answered the call. I gave them your company's name. They'll remember it. It's the only good news they've had this month."),
                firstContractLine("cost", CASTLE, outcome.marinesLost > 0
                        ? "We've got names to take off the roll. I'll see to those before I count what we can afford."
                        : "Crew pay and supplies first. It's good to hold a receipt that isn't for something we had to sell."),
                firstContractLine("next", MARA, "Get your people in order. When you're ready, come back and we'll talk about the depot. I'd like this to be the start of something. Wayline needs it to be."));
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
     * from the frozen outcome. The duel overhead is not reported here until it exists and reports.
     */
    static List<WaylineNarration.Line> smallWarLines(MissionOutcome o) {
        var r = o.smallWar;
        String route = r.houseCustody() ? "House Custody" : "the Yard Compact";
        String cost = "Company casualties: " + o.marinesLost + ". Custody party lost: " + r.custodiansLost()
                + ". Holders lost: " + r.holdersLost() + ".";
        if (!o.victory) return List.of(
                sluiceLine("withdrawal", "CASTLE", (r.infrastructureLost() ? "The marked fuel storage was destroyed; the handover cannot stand on a burned depot. "
                        : r.custodiansLost() >= 3 ? "The whole custody party is dead; there is nobody left to take the site. "
                        : "The handover was not completed. ") + cost),
                sluiceLine("replacement", "RUSK", "No completion fee is paid. A new mobilization will bring a replacement custody party and holders"
                        + (r.infrastructureLost() ? " and refitted fuel controls" : "") + ". This loss stays on the account; the ratified agreement stands."));
        int premium = r.houseCustody() ? 12000 : 0;
        String paid = NumberFormat.getIntegerInstance().format(o.payoutEarned + premium);
        String withdrawn = r.greyhookWithdrawn() > 0
                ? r.greyhookWithdrawn() + " of Greyhook's people walked off along the service road on Voss's order; Berth Four held dispatch until we took it."
                : "Nobody we saw made it out along the service road; Berth Four held dispatch until we took it.";
        String lead = o.marinesLost > 0
                ? "We paid for that pier: " + o.marinesLost + (o.marinesLost == 1 ? " of ours is" : " of ours are") + " on the roll that won't come home. "
                : "Everyone we put down at Black Sluice is coming home. ";
        return List.of(
                sluiceLine("handover", "CASTLE", "Customs, fuel control and dispatch are held, the marked fuel storage is intact, and the custody party signed the handover in the control room under " + route + ". " + withdrawn),
                sluiceLine("award", "RUSK", "Your invoice is settled: " + paid + " credits" + (premium > 0
                        ? ", the 44,000 fee and the 12,000 premium House Custody promised." : ", the full 44,000 fee. The compact forwent the premium, as written.")
                        + " Black Sluice's surface facility and its levy route are out of Greyhook's hands. That is all I will claim for it."),
                sluiceLine("cost", "CASTLE", cost),
                AgreusArcCampaign.VOSS_LAST,
                sluiceLine("closing", "CASTLE", lead + "Rusk is asking what it would take to keep us here. He wasn't asking that when we arrived."));
    }
    private static WaylineNarration.Line sluiceLine(String beat, String speaker, String text) {
        return new WaylineNarration.Line("small-war-debrief-" + beat, "story_agreus_small_war", "debrief.small_war." + beat, text, 12f, 10, 15f, List.of(),
                speaker.equals("CASTLE") ? "CASTLE / COMPANY OPERATIONS" : "BARONET ELIAN RUSK / HOUSE RUSK",
                speaker.equals("CASTLE") ? CompanyManagementGuide.portrait() : OpeningBroker.CREST, false, false);
    }
    private static boolean yardOwners(MissionOutcome o) {
        return o != null && o.missionSource == MissionSource.STORY && AgreusArcProgress.yardOwners(o.missionId) && o.yardOwners != null;
    }
    private static List<WaylineNarration.Line> yardLines(MissionOutcome o) {
        var r = o.yardOwners;
        String cost = "Company casualties: " + o.marinesLost + ". Inspection technicians lost: " + r.techniciansLost()
                + ". Constables lost: " + r.constablesLost() + ".";
        if (!o.victory) return List.of(
                yardLine("withdrawal","CASTLE",(r.toolingLost() ? "The marked calibration machine was destroyed. " : "The inspection was not completed. ") + cost),
                yardLine("replacement","RUSK","No completion fee or pattern is released. A new mobilization will fund replacement inspectors and tooling refit. This loss stays on the account; no custody agreement has been chosen."));
        return List.of(
                yardLine("award","RUSK","Thirty-six thousand credits and the Recovered Arbalest refurbishment pattern. The inspection settles this contract; it does not settle the yard's custody."),
                yardLine("issue","CASTLE","Learn the Arbalest card from fleet cargo. Once both recovered patterns are learned, fit Salvage-assisted Section in the Armory: eight regular marines, two Specter breachers and two Arbalest support wearers. Their names and lift seats stay the same; no equipment has changed automatically."),
                yardLine("order","COMBINE REGISTRY / LEASE-ARBITRATION ORDER","Rusk's secured claim remains recognized under Combine administration. The yard's arrears are enforceable. Inspection grants neither repossession nor collection authority to this company."),
                yardLine("leases","HOUSE RUSK / ARREARS AND PROPOSED LEASES","The outstanding debt remains on the yard account. House Custody would place tool access under Rusk's office, with new workshop leases and debt deductions. These are proposed terms, not a signed instruction."),
                yardLine("counterproposal","LATCHKEY YARD ASSOCIATION / YARD COMPACT","Counterproposal: inspected common-service access continues under a signed repayment schedule. Rusk appoints security; the association operates the shops and keeps auditable service allocation. The debt stands."),
                AgreusArcCampaign.LENNA_GATE,
                AgreusArcCampaign.LENNA_LIEN,
                yardLine("cost","CASTLE",cost + " Both written agreements are at Company HQ. Read them, ask me to compare them, or leave them for now; nothing is committed until you sign one. The next commission waits on that signature."));
    }
    private static WaylineNarration.Line yardLine(String beat,String speaker,String text) {
        return new WaylineNarration.Line("yard-debrief-"+beat,"story_agreus_yard_owners","debrief.yard_owners."+beat,text,12f,10,15f,List.of(),
                speaker,speaker.equals("CASTLE") ? CompanyManagementGuide.portrait() : OpeningBroker.CREST,false,false);
    }
    private static boolean limitedWarrant(MissionOutcome outcome) {
        return outcome != null && outcome.missionSource == MissionSource.STORY
                && AgreusArcProgress.limitedWarrant(outcome.missionId) && outcome.limitedWarrant != null;
    }
    private static List<WaylineNarration.Line> warrantLines(MissionOutcome outcome) {
        var r = outcome.limitedWarrant;
        String cost = "Company casualties: " + outcome.marinesLost + ". Supplier technicians lost: " + r.techniciansLost()
                + ". Constables lost: " + r.constablesLost() + ". Their names stay with this account.";
        if (outcome.victory) return List.of(
                warrantLine("records", true, "The archive is authenticated and the calibration load is back. We have the files and the tools to use them."),
                warrantLine("award", false, "Thirty thousand credits, and the Recovered Specter refurbishment pattern under my supplier guarantee. That settles this recovery contract. It decides nothing about the yard's future management."),
                warrantLine("issue", true, "Learn the Recovered Specter card from fleet cargo, then choose Tri-Tachyon Specter Protection in the Armory. If we already hold the card or know the pattern, there is no second copy. Nobody's fitting has changed. This is repaired security stock, not military issue; keep someone watching the flank."),
                warrantLine("cost", true, cost));
        return List.of(warrantLine("withdrawal", true, (r.recordsRecovered() ? "The authenticated service archive is retained. " : "The service archive was not authenticated. ")
                        + (r.cargoLost() ? "The calibration load was destroyed." : "The calibration kit was not returned.")),
                warrantLine("unpaid", false, "No completion fee or pattern is released. I will fund a new calibration load and replacement recovery party when you recommission the work."),
                warrantLine("cost", true, cost));
    }
    private static WaylineNarration.Line warrantLine(String beat, boolean castle, String text) {
        return new WaylineNarration.Line("warrant-debrief-" + beat, "story_agreus_limited_warrant",
                "debrief.limited_warrant." + beat, text, 10f, 10, 15f, List.of(),
                castle ? "CASTLE / COMPANY OPERATIONS" : "BARONET ELIAN RUSK / HOUSE RUSK",
                castle ? CompanyManagementGuide.portrait() : MissionDialogue.speakerPortrait("vale"), false, false);
    }

    private static boolean waterUnderSeal(MissionOutcome outcome) {
        return outcome != null && outcome.missionSource == MissionSource.STORY
                && AgreusArcCampaign.owns(outcome.missionId) && outcome.waterUnderSeal != null;
    }

    private static List<WaylineNarration.Line> weirLines(MissionOutcome outcome) {
        var report = outcome.waterUnderSeal;
        String cost = "Company casualties: " + outcome.marinesLost + ". Constables lost: " + report.militiaLost()
                + ". Technicians lost: " + report.techniciansLost() + ". Keep those people on the record beside the work.";
        if (outcome.victory) return List.of(
                weirLine("secured", true, "The repair load reached the isolation switch. Both rooms are reconnected and the road gun is commissioned. The company resolved the attack; North Weir is working again."),
                weirLine("invoice", false, "Your invoice is honored: " + NumberFormat.getIntegerInstance().format(outcome.payoutEarned) + " credits. This was security work within my concession; the Combine's administration remains its own."),
                weirLine("cost", true, cost),
                weirLine("closure", false, "There is equipment in the Low Quay bonded stores your people could use. I can authorize its release. Read the Limited Warrant when you are ready; it covers recovery, not debt enforcement or control of the yard."));
        String work = "The north room " + (report.northRoomReconnected() ? "was reconnected" : "was not reconnected")
                + "; the south room " + (report.southRoomReconnected() ? "was reconnected" : "was not reconnected") + ". "
                + (report.cargoLost() ? "The repair load was destroyed." : "No repair-load loss is confirmed.");
        return List.of(
                weirLine("withdrawal", true, "North Weir has not been handed over. " + work),
                weirLine("unpaid", false, "The completion fee is unpaid. A return to North Weir will be a new mobilization: fresh repair cargo and a replacement technician party. No unfinished field work is signed off."),
                weirLine("cost", true, cost),
                weirLine("retry", false, "Replenish your people and boats, and bring a working tender. The contract can wait until your company is ready."));
    }

    private static WaylineNarration.Line weirLine(String beat, boolean castle, String text) {
        return new WaylineNarration.Line("weir-debrief-" + beat, "story_agreus_water_under_seal",
                "debrief.water_under_seal." + beat, text, 10f, 10, 15f, List.of(),
                castle ? "CASTLE / COMPANY OPERATIONS" : "BARONET ELIAN RUSK / HOUSE RUSK",
                castle ? CompanyManagementGuide.portrait() : MissionDialogue.speakerPortrait("vale"), false, false);
    }

    private static boolean firstContract(MissionOutcome outcome) {
        return outcome != null && outcome.victory && WaylineArc.chapter(outcome) == OpeningOperationKind.RELIEF;
    }

    private static WaylineNarration.Line recoveryLine(String beat, boolean castle, String text) {
        return new WaylineNarration.Line("shepherd-debrief-" + beat, "story_shepherd_salvage",
                "debrief.salvage." + beat, text, 10f, 10, 15f, List.of(),
                castle ? "CASTLE / COMPANY OPERATIONS" : "TAMSIN VALE / BASIN RECLAMATION",
                castle ? CompanyManagementGuide.portrait() : MissionDialogue.speakerPortrait("vale"), false, false);
    }

    /** Written correspondence has no recording; keep it readable without offering silent playback. */
    private static final int CASTLE = 0, MARA = 1, SABINE = 2;
    private static WaylineNarration.Line firstContractLine(String beat, int speaker, String text) {
        return new WaylineNarration.Line("first-contract-" + beat, OpeningOperationKind.RELIEF.missionId,
                "debrief.relief." + beat, text, 10f, 10, 15f, List.of(),
                switch (speaker) { case CASTLE -> "CASTLE / COMPANY OPERATIONS"; case SABINE -> "SABINE HALE / FIELD COMMAND";
                    default -> "MARA VENN / WAYLINE"; },
                switch (speaker) { case CASTLE -> CompanyManagementGuide.portrait(); case SABINE -> OfficerInduction.SABINE_PORTRAIT;
                    default -> OpeningBroker.PORTRAIT; }, false, false);
    }

    static List<WaylineNarration.Line> recordings(MissionOutcome outcome, WaylineNarration catalog) {
        return lines(outcome, catalog).stream().filter(WaylineNarration.Line::voiced).toList();
    }
}

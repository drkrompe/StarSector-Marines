package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
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
        if (limitedWarrant(outcome)) return "LIMITED WARRANT / CASTLE & RUSK";
        if (waterUnderSeal(outcome)) return "NORTH WEIR / CASTLE & RUSK";
        if (outcome != null && outcome.workingPassage != null) return "REGISTRY TRANSFER / CASTLE & VALE";
        if (outcome != null && outcome.coldStart != null) return "COLD START / CASTLE & VALE";
        if (outcome != null && outcome.salvageRights != null) return "RECOVERY AGREEMENT / CASTLE & VALE";
        if (firstContract(outcome)) return "FIRST CONTRACT / CASTLE & MARA";
        return coldAccount(outcome) || blindWatch(outcome) ? "AFTER ACTION / CASTLE & MARA" : "RECOVERY AGREEMENT / TRANSCRIPT";
    }

    static List<WaylineNarration.Line> lines(MissionOutcome outcome, WaylineNarration catalog) {
        if (limitedWarrant(outcome)) return warrantLines(outcome);
        if (waterUnderSeal(outcome)) return weirLines(outcome);
        if (outcome != null && outcome.victory && outcome.workingPassage != null) return List.of(
                recoveryLine("departure-registry", true, outcome.missionSource.isDebug()?"Departure exercise complete. No registry or fleet transfer is made from a training record.":"Transfer's through. Morrow's ours."),
                recoveryLine("departure-corridor", false, outcome.missionSource.isDebug()
                        ? "The contract would leave the Colossus shipment, yard and haul road with us. The company's final payment would be Morrow; this exercise makes no transfer."
                        : "All right. Have someone check the drone bay filters before you put her to work. The dust here gets into everything."),
                recoveryLine("departure-crew", true, outcome.missionSource.isDebug()
                        ? "A contracted departure would include berth offers to willing working hands. Fleet crew, on our payroll, with the ship's upkeep ours from handover."
                        : "We'll see to it. The hands who want to come with her have berths."),
                recoveryLine("departure-cost", false, outcome.workingPassage.militiaLost() > 0
                        ? "I'll have to go. There are people here waiting to hear from me. Look after her, Castle."
                        : outcome.marinesLost > 0 ? "I'm sorry about your people, Castle. Get the rest of them home."
                        : "They know her better than I do. Let them show you around before you start pulling things apart."));
        if (outcome != null && outcome.victory && outcome.coldStart != null) return List.of(
                recoveryLine("first-gun", true, "I was hoping for another workshop. Now I'm thinking about all the places we've had to hold with a handful of rifles."),
                recoveryLine("repair-complete", false, outcome.missionSource.isDebug()
                        ? "Repair exercise complete. No campaign payment is issued."
                        : "They've been keeping this yard together for years. Just don't take her anywhere yet. I've got haulers waiting for that road."),
                recoveryLine("repair-cost", true, outcome.missionSource.isDebug()
                        ? "Keep the exercise losses in the review. A contracted company has to cover supplies and crew pay before committing to departure."
                        : outcome.marinesLost > 0 || outcome.coldStart.crewLost() > 0
                            ? "We lost too many people today. I don't want to rush the next part."
                            : "Can she get herself out?"),
                recoveryLine("departure-pending", false, outcome.marinesLost > 0 || outcome.coldStart.crewLost() > 0
                        ? "Take the time. I'll arrange a watch here. When you're ready, we'll walk her out past the pumps. I want that road open for our haulers, too."
                        : "Not straight up from here. We'll walk her past the pumps, through the narrows. I'll leave people on the road behind you; I need it for our haulers."));
        if (outcome != null && outcome.victory && outcome.salvageRights != null) return List.of(
                recoveryLine("registry", true, outcome.missionSource.isDebug()
                        ? "Inspection exercise complete. No campaign claim or payment is issued."
                        : "The fitters think they can save her workshop. Are we still agreed?"),
                recoveryLine("terms", false, outcome.missionSource.isDebug()
                        ? "The actual contract includes a repair party. We'd supply the parts and fitters."
                        : "Yes. I've got people waiting to move that shipment. I'd rather you kept yours here than went looking for another contract."),
                recoveryLine("repair-bill", true, "Then keep the fitters on her. I'd like to know what we've put our name to."),
                recoveryLine("repair-terms", false, "They'll need shore power before they can tell you much more. I've put the parts aside."),
                recoveryLine("people", true, outcome.salvageRights.crewLost() > 0
                        ? "I'll want better cover for the next crew. Where are we sending them?"
                        : "Fair enough. Where do you want the company?"),
                recoveryLine("power", false, "Workshop first. I'll have a distribution rig waiting at the apron. Then we need the pumps cleared so they can connect shore power."));
        if (firstContract(outcome)) return List.of(
                firstContractLine("receipt", true, "Payment's in. I checked twice. Our first Wayline contract, Captain. Someone's paid us to do what we said we would."),
                firstContractLine("names", false, "The drivers asked who'd answered the call. I gave them your company's name. They'll remember it."),
                firstContractLine("cost", true, outcome.marinesLost > 0
                        ? "We've got names to take off the roll. I'll see to those before I start counting what we can afford."
                        : "I'll put crew pay and supplies first. It's good to have a receipt that isn't for something we had to sell."),
                firstContractLine("next", false, "Put your people in order. When you're ready, come back and we'll talk about the depot. I'd like this to be the start of something."));
        if (blindWatch(outcome)) return List.of("initiative", "ready")
                .stream().map(beat -> catalog.find("story_corvus_blind_watch", "debrief.blind_watch." + beat))
                .filter(Objects::nonNull).toList();
        if (!coldAccount(outcome)) return HoundSalvageDebrief.lines(outcome, catalog);
        return List.of("orders", "consideration", "one_ship", "payroll", "way_out", "debt")
                .stream().map(beat -> catalog.find("story_corvus_cold_account", "debrief.cold_account." + beat))
                .filter(Objects::nonNull).toList();
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
                castle ? CompanyManagementGuide.portrait() : OpeningBroker.CREST, false, false);
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
                castle ? CompanyManagementGuide.portrait() : OpeningBroker.CREST, false, false);
    }

    private static boolean firstContract(MissionOutcome outcome) {
        return outcome != null && outcome.victory && WaylineArc.chapter(outcome) == OpeningOperationKind.RELIEF;
    }

    private static WaylineNarration.Line recoveryLine(String beat, boolean castle, String text) {
        return new WaylineNarration.Line("shepherd-debrief-" + beat, "story_shepherd_salvage",
                "debrief.salvage." + beat, text, 10f, 10, 15f, List.of(),
                castle ? "CASTLE / COMPANY OPERATIONS" : "TAMSIN VALE / BASIN RECLAMATION",
                castle ? CompanyManagementGuide.portrait() : OpeningBroker.CREST, false, false);
    }

    /** Written correspondence has no recording; keep it readable without offering silent playback. */
    private static WaylineNarration.Line firstContractLine(String beat, boolean castle, String text) {
        return new WaylineNarration.Line("first-contract-" + beat, OpeningOperationKind.RELIEF.missionId,
                "debrief.relief." + beat, text, 10f, 10, 15f, List.of(),
                castle ? "CASTLE / COMPANY OPERATIONS" : "MARA VENN / WAYLINE",
                castle ? CompanyManagementGuide.portrait() : OpeningBroker.PORTRAIT, false, false);
    }

    static List<WaylineNarration.Line> recordings(MissionOutcome outcome, WaylineNarration catalog) {
        return lines(outcome, catalog).stream().filter(WaylineNarration.Line::voiced).toList();
    }
}

package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
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
        if (waterUnderSeal(outcome)) return "NORTH WEIR / CASTLE & RUSK";
        if (outcome != null && outcome.workingPassage != null) return "REGISTRY TRANSFER / CASTLE & VALE";
        if (outcome != null && outcome.coldStart != null) return "COLD START / CASTLE & VALE";
        if (outcome != null && outcome.salvageRights != null) return "RECOVERY AGREEMENT / CASTLE & VALE";
        if (firstContract(outcome)) return "FIRST CONTRACT / CASTLE & MARA";
        return coldAccount(outcome) || blindWatch(outcome) ? "AFTER ACTION / CASTLE & MARA" : "RECOVERY AGREEMENT / TRANSCRIPT";
    }

    static List<WaylineNarration.Line> lines(MissionOutcome outcome, WaylineNarration catalog) {
        if (waterUnderSeal(outcome)) return weirLines(outcome);
        if (outcome != null && outcome.victory && outcome.workingPassage != null) return List.of(
                recoveryLine("departure-registry", true, outcome.missionSource.isDebug()?"Departure exercise complete. No registry or fleet transfer is made from a training record.":"Registry accepted. Morrow is ours. Hull, workshop, and a very long list of maintenance jobs."),
                recoveryLine("departure-corridor", false, outcome.missionSource.isDebug()
                        ? "The contract would leave the Colossus stores, yard and haul road with us. The company's final payment would be Morrow; this exercise makes no transfer."
                        : "The Colossus stores, yard and haul road stay with us. Morrow is your final payment, as agreed. Now I can put our crews back to work. If the Hegemony comes asking about the salvage, I'll answer for our claims."),
                recoveryLine("departure-crew", true, outcome.missionSource.isDebug()
                        ? "A contracted departure would include berth offers to willing working hands. Fleet crew, on our payroll, with the ship's upkeep ours from handover."
                        : "We'll honor the berth offers to her willing working hands. Fleet crew, on our payroll. Her upkeep is our problem from here. You bought a working yard; we earned a working ship."),
                recoveryLine("departure-cost", false, outcome.marinesLost > 0 || outcome.workingPassage.militiaLost() > 0?"And people paid for it. Keep their names with the contract record, Castle. I'll see to ours.":"That's a bargain I can take back to the shops. Keep her working, Castle."));
        if (outcome != null && outcome.victory && outcome.coldStart != null) return List.of(
                recoveryLine("first-gun", true, "That is a position we do not have to fill with another squad. Keep the crew covered. Let the machine do its job."),
                recoveryLine("repair-complete", false, "Drone bay answers. Pumps hold pressure. "+(outcome.missionSource.isDebug()?"This is a training record; no campaign payment is issued. ":"Your repair payment is released. ")+"Morrow stays in the cradle until we clear her departure corridor."),
                recoveryLine("repair-cost", true, outcome.missionSource.isDebug()
                        ? "Keep the exercise losses in the review. A contracted company has to cover supplies and crew pay before committing to departure."
                        : outcome.marinesLost > 0 || outcome.coldStart.crewLost() > 0
                            ? "Account for our losses before we commit again. That fee has to keep the company in the field. We still have a ship to bring out."
                            : "Repair fee accounted for. Supplies and crew pay first; then we finish earning that ship."),
                recoveryLine("departure-pending", false, "I need that road carrying salvage before another outfit decides to stake a claim. I'll supply the holders; your company clears the passage. Replenish before we go. Your claim and the completed repairs stand."));
        if (outcome != null && outcome.victory && outcome.salvageRights != null) return List.of(
                recoveryLine("registry", true, "Put Morrow's registry in the agreement. We bring your crews back to work; when this ship flies, it flies with us."),
                recoveryLine("terms", false, "The Colossus holds more work than our shops can handle. We keep her stores and the yard; you earn Morrow by finishing the repairs and departure. That's why I'm willing to part with her. "+(outcome.missionSource.isDebug()?"This is a training record; no campaign claim or payment is issued.":"The inspection fee is released. Your claim is protected.")),
                recoveryLine("people", true, outcome.salvageRights.crewLost() > 0
                        ? "Record the inspection losses with ours. We need skilled hands for the next job; machinery alone won't get her flying. What gets her power back?"
                        : outcome.missionSource.isDebug()
                            ? "Inspection exercise signed off. What would get her power back?"
                            : "Inspection signed off. The company has a fee and a claim worth finishing. What gets her power back?"),
                recoveryLine("power", false, "A replacement distribution rig and shore power from the pumps. I'll supply the repair party. We pay our way in Hegemony space, but I would rather be unloading cargo than explaining its value to an inspector. Get your company ready."));
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
                weirLine("closure", false, "You have my sponsorship for the work you completed. Put your company in order. I will contact you when the next contract is ready."));
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

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
                recoveryLine("departure-corridor", false, "The corridor is ours again. The Colossus stores and our yard stay with the cooperative; Morrow is the agreed payment for bringing that work back. Keep her working, Castle."),
                recoveryLine("departure-crew", true, "The berth offer stands for willing working hands. We will carry enough fleet crew for her minimum complement; nobody is being signed into a marine squad."),
                recoveryLine("departure-cost", false, outcome.marinesLost > 0 || outcome.workingPassage.militiaLost() > 0?"Keep the people we lost on the record with the ship we brought out.":"A working ship, and a road we can use again."));
        if (outcome != null && outcome.victory && outcome.coldStart != null) return List.of(
                recoveryLine("first-gun", true, "That is a position we do not have to fill with another squad. Keep the crew covered. Let the machine do its job."),
                recoveryLine("repair-complete", false, "Drone bay answers. Pumps hold pressure. "+(outcome.missionSource.isDebug()?"This is a training record; no campaign payment is issued. ":"Your repair payment is released. ")+"Morrow stays in the cradle until we clear her departure corridor."),
                recoveryLine("repair-cost", true, outcome.marinesLost > 0 || outcome.coldStart.crewLost() > 0
                        ? "Put the people we lost on the roll before we count the machinery."
                        : "The work is signed off. We still have a ship to bring home."),
                recoveryLine("departure-pending", false, "Working Passage is ready when your company is. Replenish your company; the recovery claim and completed repairs remain protected."));
        if (outcome != null && outcome.victory && outcome.salvageRights != null) return List.of(
                recoveryLine("registry", true, "Put Morrow's registry in the agreement. We bring your crews back to work; when this ship flies, it flies with us."),
                recoveryLine("terms", false, "Morrow's hull and drone workshop, after the repair and departure contracts. We keep the broken Colossus, its cargo and equipment, and the fixed yard. Reopening that work is worth the smaller tender. "+(outcome.missionSource.isDebug()?"This is a training record; no campaign claim or payment is issued.":"Today's payment is released; the claim is protected.")),
                recoveryLine("people", true, outcome.salvageRights.crewLost() > 0
                        ? "We lost people getting that inspection done. Put their names beside ours before anyone starts dividing the machinery."
                        : "Your inspection team is back. Let's keep doing it that way. What gets her power back?"),
                recoveryLine("power", false, "The pump road and a replacement distribution rig. We can wake the drone bay on shore power. I'll arrange the repair mobilization. Get your company ready."));
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

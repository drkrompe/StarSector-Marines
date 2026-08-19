package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.dillon.starsectormarines.campaign.personnel.CaptainDiscoverySalvageListener;
import com.dillon.starsectormarines.marine.CaptainCandidate;
import com.dillon.starsectormarines.marine.CaptainCandidateState;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Rule-command adapter for resolving a recovered captain after vanilla salvage. */
public class CaptainDiscoveryCMD extends BaseCommandPlugin {

    static final String OPTION_ACCEPT = "starsectorMarinesCaptainAccept";
    static final String OPTION_DECLINE = "starsectorMarinesCaptainDecline";
    static final String OPTION_LATER = "starsectorMarinesCaptainLater";
    static final String OPTION_DONE = "starsectorMarinesCaptainDone";

    private final Supplier<MarineRoster> rosterSupplier;

    public CaptainDiscoveryCMD() {
        this(CaptainDiscoveryCMD::currentRoster);
    }

    CaptainDiscoveryCMD(Supplier<MarineRoster> rosterSupplier) {
        this.rosterSupplier = rosterSupplier;
    }

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || params == null || params.isEmpty()) return false;
        MemoryAPI memory = getEntityMemory(memoryMap);
        MarineRoster roster = rosterSupplier.get();
        if (memory == null || roster == null) return false;

        String action = params.get(0).getString(memoryMap);
        if ("done".equals(action)) {
            finish(dialog, memory);
            return true;
        }

        CaptainCandidate candidate = pendingCandidate(roster, memory);
        if (candidate == null || candidate.state() != CaptainCandidateState.AVAILABLE) {
            finish(dialog, memory);
            return false;
        }

        switch (action) {
            case "show" -> showCandidate(dialog, roster, candidate);
            case "accept" -> acceptCandidate(dialog, roster, candidate);
            case "decline" -> declineCandidate(dialog, roster, candidate);
            case "later" -> finish(dialog, memory);
            default -> { return false; }
        }
        return true;
    }

    private static void showCandidate(
            InteractionDialogAPI dialog, MarineRoster roster, CaptainCandidate candidate) {
        TextPanelAPI text = dialog.getTextPanel();
        text.addParagraph("Behind an armored inspection hatch, the salvage crews recover "
                + "a sealed cryo-pod with one living occupant.");
        text.addImage(candidate.portraitSprite());
        text.addParagraph(candidate.name() + " is identified as a "
                + candidate.startingRank().displayName() + ". "
                + traitSummary(candidate));
        text.addParagraph("The pod is aboard and stable. You may offer a commission now "
                + "or leave the survivor in stasis for review under Marine Operations.");
        addCandidateOptions(dialog, roster, candidate);
    }

    private static void acceptCandidate(
            InteractionDialogAPI dialog, MarineRoster roster, CaptainCandidate candidate) {
        MarineCaptain captain = roster.acceptCaptainCandidate(candidate.sourceKey());
        if (captain == null) {
            dialog.getTextPanel().addParagraph("Every captain berth is occupied. The pod "
                    + "remains safely in stasis and the offer is unchanged.");
            addCandidateOptions(dialog, roster, candidate);
            return;
        }
        dialog.getTextPanel().addParagraph(captain.name()
                + " accepts the commission and joins the active captain roster.");
        addDoneOption(dialog);
    }

    private static void declineCandidate(
            InteractionDialogAPI dialog, MarineRoster roster, CaptainCandidate candidate) {
        if (!roster.declineCaptainCandidate(candidate.sourceKey())) {
            addDoneOption(dialog);
            return;
        }
        dialog.getTextPanel().addParagraph("You transfer " + candidate.name()
                + " to an independent rescue service without offering a commission.");
        addDoneOption(dialog);
    }

    private static void addCandidateOptions(
            InteractionDialogAPI dialog, MarineRoster roster, CaptainCandidate candidate) {
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();
        options.addOption("Offer " + candidate.name() + " a commission", OPTION_ACCEPT);
        if (!roster.hasRoom()) {
            options.setEnabled(OPTION_ACCEPT, false);
            options.setTooltip(OPTION_ACCEPT,
                    "The captain roster is full. Review the survivor later in Personnel.");
        }
        options.addOption("Transfer the survivor to rescue services", OPTION_DECLINE);
        options.addOption("Keep the pod in stasis and decide later", OPTION_LATER);
        dialog.setOptionOnEscape("Keep the pod in stasis and decide later", OPTION_LATER);
    }

    private static void addDoneOption(InteractionDialogAPI dialog) {
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();
        options.addOption("Continue", OPTION_DONE);
        dialog.setOptionOnEscape("Continue", OPTION_DONE);
    }

    private static CaptainCandidate pendingCandidate(MarineRoster roster, MemoryAPI memory) {
        if (!memory.contains(CaptainDiscoverySalvageListener.PENDING_SOURCE_MEMORY_KEY)) {
            return null;
        }
        return roster.captainCandidateBySource(
                memory.getString(CaptainDiscoverySalvageListener.PENDING_SOURCE_MEMORY_KEY));
    }

    private static String traitSummary(CaptainCandidate candidate) {
        return candidate.startingTrait() == null
                ? "No surviving specialist record accompanies the pod."
                : "Service records note the "
                        + candidate.startingTrait().displayName() + " specialty.";
    }

    private static void finish(InteractionDialogAPI dialog, MemoryAPI memory) {
        memory.unset(CaptainDiscoverySalvageListener.PENDING_SOURCE_MEMORY_KEY);
        memory.unset(CaptainDiscoverySalvageListener.KEEP_SALVAGE_DIALOG_MEMORY_KEY);
        dialog.dismiss();
    }

    private static MarineRoster currentRoster() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster() : null;
    }
}

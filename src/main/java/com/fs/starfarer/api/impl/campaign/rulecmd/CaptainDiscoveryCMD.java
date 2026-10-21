package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.dillon.starsectormarines.i18n.Strings;
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
        text.addParagraph(Strings.get("captainDiscoveryPod"));
        text.addImage(candidate.portraitSprite());
        text.addParagraph(Strings.format("captainDiscoveryIdentified", candidate.name(), candidate.startingRank().displayName(), traitSummary(candidate)));
        text.addParagraph(Strings.get("captainDiscoveryOffer"));
        addCandidateOptions(dialog, roster, candidate);
    }

    private static void acceptCandidate(
            InteractionDialogAPI dialog, MarineRoster roster, CaptainCandidate candidate) {
        MarineCaptain captain = roster.acceptCaptainCandidate(candidate.sourceKey());
        if (captain == null) {
            dialog.getTextPanel().addParagraph(Strings.get("captainDiscoveryBerthsFull"));
            addCandidateOptions(dialog, roster, candidate);
            return;
        }
        dialog.getTextPanel().addParagraph(Strings.format("captainDiscoveryAccepted", captain.name()));
        addDoneOption(dialog);
    }

    private static void declineCandidate(
            InteractionDialogAPI dialog, MarineRoster roster, CaptainCandidate candidate) {
        if (!roster.declineCaptainCandidate(candidate.sourceKey())) {
            addDoneOption(dialog);
            return;
        }
        dialog.getTextPanel().addParagraph(Strings.format("captainDiscoveryTransferred", candidate.name()));
        addDoneOption(dialog);
    }

    private static void addCandidateOptions(
            InteractionDialogAPI dialog, MarineRoster roster, CaptainCandidate candidate) {
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();
        options.addOption(Strings.format("captainDiscoveryOptionOffer", candidate.name()), OPTION_ACCEPT);
        if (!roster.hasRoom()) {
            options.setEnabled(OPTION_ACCEPT, false);
            options.setTooltip(OPTION_ACCEPT,
                    Strings.get("captainDiscoveryRosterFull"));
        }
        options.addOption(Strings.get("captainDiscoveryOptionTransfer"), OPTION_DECLINE);
        options.addOption(Strings.get("captainDiscoveryOptionLater"), OPTION_LATER);
        dialog.setOptionOnEscape(Strings.get("captainDiscoveryOptionLater"), OPTION_LATER);
    }

    private static void addDoneOption(InteractionDialogAPI dialog) {
        OptionPanelAPI options = dialog.getOptionPanel();
        options.clearOptions();
        options.addOption(Strings.get("captainDiscoveryContinue"), OPTION_DONE);
        dialog.setOptionOnEscape(Strings.get("captainDiscoveryContinue"), OPTION_DONE);
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
                ? Strings.get("captainDiscoveryNoRecord")
                : Strings.format("captainDiscoverySpecialty", candidate.startingTrait().displayName());
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

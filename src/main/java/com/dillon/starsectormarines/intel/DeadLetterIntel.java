package com.dillon.starsectormarines.intel;

import com.dillon.starsectormarines.campaign.AbandonedColonyArchiveOutcome;
import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignEventType;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.SilentColonyEvent;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.Set;

/** Reconstructible choice surface for Silent Colony expedition signals. */
public final class DeadLetterIntel extends BaseIntelPlugin {

    public static final String TAG = "marines_dead_letter";
    private static final String TITLE = "Dead Letter";
    private static final String BTN_COMMIT = "silent-colony-commit:";
    private static final String BTN_REFUSE = "silent-colony-refuse:";

    private transient String feedback;
    private transient long feedbackEventId = -1L;

    @Override
    protected String getName() {
        return TITLE;
    }

    @Override
    public String getSmallDescriptionTitle() {
        return TITLE;
    }

    @Override
    public boolean hasSmallDescription() {
        return false;
    }

    @Override
    public boolean hasLargeDescription() {
        return true;
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara(TITLE, getTitleColor(mode), 0f);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = new LinkedHashSet<>();
        tags.add(TAG);
        return tags;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        CampaignState state = state();
        int row = activeEventRow(state);
        if (row < 0) row = latestTerminalRow(state);
        MarketAPI market = row >= 0 ? market(state, row) : null;
        return market != null ? market.getPrimaryEntity() : null;
    }

    @Override
    public boolean shouldRemoveIntel() {
        return false;
    }

    @Override
    public boolean isHidden() {
        return shouldHide(state());
    }

    @Override
    public String getSortString() {
        return "AAE_" + TITLE;
    }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width,
                                       float height) {
        TooltipMakerAPI ui = panel.createUIElement(width, height, true);
        CampaignState state = state();
        ui.addSectionHeading(TITLE, Color.WHITE, new Color(40, 40, 40),
                Alignment.LMID, 0f);
        if (state == null) {
            ui.addPara("The dead-band receiver is offline.", 10f);
            panel.addUIElement(ui).inTL(0f, 0f);
            return;
        }

        int row = activeEventRow(state);
        if (row >= 0 && feedbackEventId != state.eventId[row]) feedback = null;
        if (feedback != null) ui.addPara(feedback, 8f);
        if (row < 0) {
            String summary = terminalSummary(state, latestTerminalRow(state));
            ui.addPara(summary != null ? summary
                    : "No authenticated dead-colony signals.", 10f);
            panel.addUIElement(ui).inTL(0f, 0f);
            return;
        }

        String siteName = marketName(state, row);
        CampaignEventState eventState = CampaignEventState.fromByte(
                state.eventState[row]);
        if (eventState == CampaignEventState.PENDING_CHOICE) {
            ui.addPara(initialNarrative(siteName), 10f,
                    Color.LIGHT_GRAY, Color.WHITE);
            ui.addPara("Expedition stores: %s supplies and %s fuel", 6f,
                    Color.LIGHT_GRAY, Color.WHITE,
                    String.valueOf(state.eventSuppliesRequired[row]),
                    String.valueOf(state.eventFuelRequired[row]));
            ui.addPara("Signal window: %s days remaining", 6f,
                    Color.LIGHT_GRAY, Color.WHITE,
                    String.valueOf(daysRemaining(state, row,
                            currentDay(state))));
            long eventId = state.eventId[row];
            ui.addButton("Mount the expedition", BTN_COMMIT + eventId,
                    220f, 24f, 10f);
            ui.addButton("Leave it silent", BTN_REFUSE + eventId,
                    220f, 24f, 4f);
        } else {
            ui.addPara("The expedition to %s is funded. No field report has "
                            + "returned from the dead settlement.",
                    10f, Color.LIGHT_GRAY, Color.WHITE, siteName);
        }

        panel.addUIElement(ui).inTL(0f, 0f);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (!(buttonId instanceof String)) return;
        CampaignState state = state();
        if (state == null) return;
        String button = (String) buttonId;
        long eventId;
        SilentColonyEvent.Result result;
        if (button.startsWith(BTN_COMMIT)) {
            eventId = parseLong(button.substring(BTN_COMMIT.length()));
            result = SilentColonyEvent.commit(state, eventId,
                    currentDay(state));
        } else if (button.startsWith(BTN_REFUSE)) {
            eventId = parseLong(button.substring(BTN_REFUSE.length()));
            result = SilentColonyEvent.refuse(state, eventId,
                    currentDay(state));
        } else {
            return;
        }
        feedback = feedback(result);
        feedbackEventId = eventId;
        ui.updateUIForItem(this);
    }

    static boolean shouldHide(CampaignState state) {
        return activeEventRow(state) < 0 && latestTerminalRow(state) < 0;
    }

    static int activeEventRow(CampaignState state) {
        if (state == null) return -1;
        for (int row = state.eventCount - 1; row >= 0; row--) {
            if (!validBaseRow(state, row)) continue;
            CampaignEventState eventState = CampaignEventState.fromByte(
                    state.eventState[row]);
            if (eventState == CampaignEventState.PENDING_CHOICE
                    || eventState == CampaignEventState.COMMITTED) {
                return row;
            }
        }
        return -1;
    }

    static int latestTerminalRow(CampaignState state) {
        if (state == null) return -1;
        for (int row = state.eventCount - 1; row >= 0; row--) {
            if (!validBaseRow(state, row)) continue;
            CampaignEventState eventState = CampaignEventState.fromByte(
                    state.eventState[row]);
            if ((eventState == CampaignEventState.REFUSED
                    || eventState == CampaignEventState.EXPIRED)
                    || (eventState == CampaignEventState.RESOLVED
                        && validResolution(state, row))) {
                return row;
            }
        }
        return -1;
    }

    static int daysRemaining(CampaignState state, int row, int day) {
        if (state == null || row < 0 || row >= state.eventCount) return 0;
        return Math.max(0, state.eventDeadlineTick[row] - day);
    }

    static String initialNarrative(String siteName) {
        return "An automated distress burst has escaped " + siteName
                + ", a settlement the Sector records as dead. The signal "
                + "cannot confirm survivors or identify what ended the colony.";
    }

    static String terminalSummary(CampaignState state, int row) {
        if (!validBaseRow(state, row)) return null;
        CampaignEventState eventState = CampaignEventState.fromByte(
                state.eventState[row]);
        String siteName = marketName(state, row);
        if (eventState == CampaignEventState.REFUSED) {
            return "The signal from " + siteName
                    + " was left unanswered. Nothing further is known.";
        }
        if (eventState == CampaignEventState.EXPIRED) {
            return "The dead-band signal from " + siteName
                    + " faded before an expedition was mounted.";
        }
        if (eventState != CampaignEventState.RESOLVED
                || !validResolution(state, row)) {
            return null;
        }
        int rescued = state.eventCiviliansRescued[row];
        int atRisk = state.eventCiviliansAtRisk[row];
        AbandonedColonyArchiveOutcome archive =
                AbandonedColonyArchiveOutcome.fromByte(
                        state.eventColonyArchiveOutcome[row]);
        String archiveText = archive == AbandonedColonyArchiveOutcome.RECOVERED
                ? "The sealed colony archive was recovered."
                : "The sealed colony archive was lost.";
        return "Expedition concluded at " + siteName + ": " + rescued
                + " of " + atRisk + " survivors extracted. " + archiveText;
    }

    static String feedback(SilentColonyEvent.Result result) {
        if (result == SilentColonyEvent.Result.COMMITTED) {
            return "Expedition stores transferred. The field team is committed.";
        }
        if (result == SilentColonyEvent.Result.REFUSED) {
            return "The dead-colony signal was left unanswered.";
        }
        if (result == SilentColonyEvent.Result.INSUFFICIENT_RESOURCES) {
            return "The fleet lacks the required supplies or fuel; nothing "
                    + "was transferred.";
        }
        return "That signal can no longer accept this response.";
    }

    private static boolean validBaseRow(CampaignState state, int row) {
        return state != null && row >= 0 && row < state.eventCount
                && state.eventId[row] > 0L
                && CampaignEventType.fromByte(state.eventType[row])
                    == CampaignEventType.SILENT_COLONY
                && state.marketRegistry.get(state.eventMarketId[row]) != null
                && state.eventSuppliesRequired[row] > 0
                && state.eventFuelRequired[row] > 0
                && state.eventCiviliansAtRisk[row] > 0
                && state.eventColonyThreatSeed[row] >= 0L;
    }

    private static boolean validResolution(CampaignState state, int row) {
        int rescued = state.eventCiviliansRescued[row];
        return state.eventResolvedTick[row] >= 0 && rescued >= 0
                && rescued <= state.eventCiviliansAtRisk[row]
                && AbandonedColonyArchiveOutcome.fromByte(
                    state.eventColonyArchiveOutcome[row])
                    != AbandonedColonyArchiveOutcome.NONE;
    }

    private static CampaignState state() {
        CampaignStateScript script = CampaignStateScript.getInstance();
        return script != null ? script.state() : null;
    }

    private static MarketAPI market(CampaignState state, int row) {
        if (Global.getSector() == null || Global.getSector().getEconomy() == null) {
            return null;
        }
        String marketId = state.marketRegistry.get(state.eventMarketId[row]);
        return marketId != null
                ? Global.getSector().getEconomy().getMarket(marketId) : null;
    }

    private static String marketName(CampaignState state, int row) {
        MarketAPI market = market(state, row);
        if (market != null && market.getName() != null) return market.getName();
        String marketId = state.marketRegistry.get(state.eventMarketId[row]);
        return marketId != null ? marketId : "an unknown site";
    }

    private static int currentDay(CampaignState state) {
        return Global.getSector() != null
                ? (int) Global.getSector().getClock().getDay()
                : Math.max(0, state.lastTickDay);
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }
}

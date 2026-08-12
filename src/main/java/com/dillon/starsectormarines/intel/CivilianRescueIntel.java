package com.dillon.starsectormarines.intel;

import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignEventType;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.CivilianRescueEvent;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.Set;

/** Diegetic choice surface for active civilian-rescue distress calls. */
public final class CivilianRescueIntel extends BaseIntelPlugin {

    public static final String TAG = "marines_distress_net";
    private static final String TITLE = "Distress Net";
    private static final String BTN_COMMIT = "rescue-commit:";
    private static final String BTN_REFUSE = "rescue-refuse:";

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
        if (row < 0) row = latestResolvedRow(state);
        MarketAPI market = row >= 0 ? market(state, row) : null;
        return market != null ? market.getPrimaryEntity() : null;
    }

    @Override
    public boolean shouldRemoveIntel() {
        return false;
    }

    @Override
    public String getSortString() {
        return "AAB_" + TITLE;
    }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width,
                                       float height) {
        TooltipMakerAPI ui = panel.createUIElement(width, height, true);
        CampaignState state = state();
        if (state == null) {
            ui.addPara("The distress receiver is offline.", 10f);
            panel.addUIElement(ui).inTL(0f, 0f);
            return;
        }

        ui.addSectionHeading(TITLE, Color.WHITE, new Color(40, 40, 40),
                com.fs.starfarer.api.ui.Alignment.LMID, 0f);

        int row = activeEventRow(state);
        if (row >= 0 && feedbackEventId != state.eventId[row]) feedback = null;
        if (feedback != null) ui.addPara(feedback, 8f);
        if (row < 0) {
            int resolvedRow = latestResolvedRow(state);
            if (resolvedRow >= 0) {
                addResolvedDispatch(ui, state, resolvedRow);
            } else {
                ui.addPara("No active civilian distress calls.", 10f);
            }
            panel.addUIElement(ui).inTL(0f, 0f);
            return;
        }

        MarketAPI market = market(state, row);
        String marketName = market != null && market.getName() != null
                ? market.getName() : marketId(state, row);
        ui.addPara("Emergency traffic from %s reports a civilian evacuation "
                        + "under immediate threat.",
                10f, Color.LIGHT_GRAY, Color.WHITE, marketName);
        ui.addPara("Civilians at risk: %s", 6f, Color.LIGHT_GRAY, Color.WHITE,
                String.valueOf(state.eventCiviliansAtRisk[row]));

        CampaignEventState eventState = CampaignEventState.fromByte(
                state.eventState[row]);
        if (eventState == CampaignEventState.PENDING_CHOICE) {
            ui.addPara("Relief commitment: %s supplies and %s fuel",
                    6f, Color.LIGHT_GRAY, Color.WHITE,
                    String.valueOf(state.eventSuppliesRequired[row]),
                    String.valueOf(state.eventFuelRequired[row]));
            ui.addPara("Response window: %s days remaining",
                    6f, Color.LIGHT_GRAY, Color.WHITE,
                    String.valueOf(daysRemaining(state, row, currentDay(state))));
            long eventId = state.eventId[row];
            ui.addButton("Commit relief stores", BTN_COMMIT + eventId,
                    220f, 24f, 10f);
            ui.addButton("Decline the call", BTN_REFUSE + eventId,
                    220f, 24f, 4f);
        } else {
            ui.addPara("Relief stores are committed. Deploy the civilian "
                    + "evacuation from Marine Operations at %s.",
                    10f, Color.LIGHT_GRAY, Color.WHITE, marketName);
        }

        panel.addUIElement(ui).inTL(0f, 0f);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (!(buttonId instanceof String)) return;
        String button = (String) buttonId;
        CampaignState state = state();
        if (state == null) return;
        int day = currentDay(state);

        if (button.startsWith(BTN_COMMIT)) {
            long eventId = parseLong(button.substring(BTN_COMMIT.length()));
            CivilianRescueEvent.Result result = CivilianRescueEvent.commit(
                    state, eventId, day);
            feedback = commitFeedback(result);
            feedbackEventId = eventId;
        } else if (button.startsWith(BTN_REFUSE)) {
            long eventId = parseLong(button.substring(BTN_REFUSE.length()));
            CivilianRescueEvent.Result result = CivilianRescueEvent.refuse(
                    state, eventId, day);
            feedback = refuseFeedback(result);
            feedbackEventId = eventId;
        } else {
            return;
        }
        ui.updateUIForItem(this);
    }

    static int activeEventRow(CampaignState state) {
        if (state == null) return -1;
        for (int row = state.eventCount - 1; row >= 0; row--) {
            if (CampaignEventType.fromByte(state.eventType[row])
                    != CampaignEventType.CIVILIAN_RESCUE) {
                continue;
            }
            CampaignEventState eventState = CampaignEventState.fromByte(
                    state.eventState[row]);
            if (eventState == CampaignEventState.PENDING_CHOICE
                    || eventState == CampaignEventState.COMMITTED) {
                return row;
            }
        }
        return -1;
    }

    static int latestResolvedRow(CampaignState state) {
        if (state == null) return -1;
        for (int row = state.eventCount - 1; row >= 0; row--) {
            if (CampaignEventType.fromByte(state.eventType[row])
                    == CampaignEventType.CIVILIAN_RESCUE
                    && CampaignEventState.fromByte(state.eventState[row])
                    == CampaignEventState.RESOLVED) {
                return row;
            }
        }
        return -1;
    }

    static String resolvedSummary(CampaignState state, int row) {
        if (state == null || row < 0 || row >= state.eventCount
                || CampaignEventType.fromByte(state.eventType[row])
                != CampaignEventType.CIVILIAN_RESCUE
                || CampaignEventState.fromByte(state.eventState[row])
                != CampaignEventState.RESOLVED) {
            return null;
        }
        int rescued = state.eventCiviliansRescued[row];
        int atRisk = state.eventCiviliansAtRisk[row];
        return "Evacuation concluded: " + rescued + " of " + atRisk
                + " civilians rescued.";
    }

    private static void addResolvedDispatch(TooltipMakerAPI ui,
                                            CampaignState state, int row) {
        MarketAPI market = market(state, row);
        String marketName = market != null && market.getName() != null
                ? market.getName() : marketId(state, row);
        ui.addPara("Latest resolved call — %s", 10f,
                Color.LIGHT_GRAY, Color.WHITE, marketName);
        ui.addPara(resolvedSummary(state, row), 6f);
        ui.addPara("Resolution received on day %s.", 6f,
                Color.LIGHT_GRAY, Color.WHITE,
                String.valueOf(state.eventResolvedTick[row]));
    }

    static int daysRemaining(CampaignState state, int row, int day) {
        if (state == null || row < 0 || row >= state.eventCount) return 0;
        return Math.max(0, state.eventDeadlineTick[row] - day);
    }

    private static CampaignState state() {
        CampaignStateScript script = CampaignStateScript.getInstance();
        return script != null ? script.state() : null;
    }

    private static MarketAPI market(CampaignState state, int row) {
        if (Global.getSector() == null || Global.getSector().getEconomy() == null) {
            return null;
        }
        String marketId = marketId(state, row);
        return marketId != null
                ? Global.getSector().getEconomy().getMarket(marketId) : null;
    }

    private static String marketId(CampaignState state, int row) {
        String marketId = state.marketRegistry.get(state.eventMarketId[row]);
        return marketId != null ? marketId : "unknown market";
    }

    private static int currentDay(CampaignState state) {
        return Global.getSector() != null
                ? (int) Global.getSector().getClock().getDay()
                : Math.max(0, state.lastTickDay);
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    private static String commitFeedback(CivilianRescueEvent.Result result) {
        if (result == CivilianRescueEvent.Result.COMMITTED) {
            return "Relief stores transferred. Mission response is pending.";
        }
        if (result == CivilianRescueEvent.Result.INSUFFICIENT_RESOURCES) {
            return "The fleet lacks the required supplies or fuel; nothing "
                    + "was transferred.";
        }
        return "The distress call can no longer accept that commitment.";
    }

    private static String refuseFeedback(CivilianRescueEvent.Result result) {
        return result == CivilianRescueEvent.Result.REFUSED
                ? "The distress call was declined."
                : "The distress call can no longer be declined.";
    }
}

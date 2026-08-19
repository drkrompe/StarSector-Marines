package com.dillon.starsectormarines.intel;

import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignEventType;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.DefectorAsylumEvent;
import com.dillon.starsectormarines.campaign.DefectorAsylumOutcome;
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

/** Reconstructible two-stage choice surface for defector-asylum events. */
public final class DefectorAsylumIntel extends BaseIntelPlugin {

    public static final String TAG = "marines_encrypted_channel";
    private static final String TITLE = "Encrypted Channel";
    private static final String BTN_GRANT = "asylum-grant:";
    private static final String BTN_REFUSE = "asylum-refuse:";
    private static final String BTN_PROTECT = "asylum-protect:";
    private static final String BTN_BETRAY = "asylum-betray:";

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
    public String getSortString() {
        return "AAC_" + TITLE;
    }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width,
                                       float height) {
        TooltipMakerAPI ui = panel.createUIElement(width, height, true);
        CampaignState state = state();
        if (state == null) {
            ui.addPara("The encrypted receiver is offline.", 10f);
            panel.addUIElement(ui).inTL(0f, 0f);
            return;
        }

        ui.addSectionHeading(TITLE, Color.WHITE, new Color(40, 40, 40),
                com.fs.starfarer.api.ui.Alignment.LMID, 0f);

        int row = activeEventRow(state);
        if (row >= 0 && feedbackEventId != state.eventId[row]) feedback = null;
        if (feedback != null) ui.addPara(feedback, 8f);
        if (row < 0) {
            int terminalRow = latestTerminalRow(state);
            String summary = terminalSummary(state, terminalRow);
            ui.addPara(summary != null ? summary
                    : "No active encrypted transmissions.", 10f);
            panel.addUIElement(ui).inTL(0f, 0f);
            return;
        }

        String actor = houseName(state, state.eventActorHouseId[row]);
        String target = houseName(state, state.eventTargetHouseId[row]);
        String market = marketName(state, row);
        CampaignEventState eventState = CampaignEventState.fromByte(
                state.eventState[row]);
        long eventId = state.eventId[row];
        int day = currentDay(state);

        if (eventState == CampaignEventState.PENDING_CHOICE) {
            ui.addPara(initialNarrative(actor, target, market), 10f,
                    Color.LIGHT_GRAY, Color.WHITE);
            ui.addPara("Asylum requires %s supplies and %s fuel.", 6f,
                    Color.LIGHT_GRAY, Color.WHITE,
                    String.valueOf(state.eventSuppliesRequired[row]),
                    String.valueOf(state.eventFuelRequired[row]));
            ui.addPara("Response window: %s days remaining", 6f,
                    Color.LIGHT_GRAY, Color.WHITE,
                    String.valueOf(daysRemaining(state, row, day)));
            ui.addButton("Grant asylum", BTN_GRANT + eventId,
                    220f, 24f, 10f);
            ui.addButton("Refuse asylum", BTN_REFUSE + eventId,
                    220f, 24f, 4f);
        } else if (eventState == CampaignEventState.COMMITTED) {
            ui.addPara("Asylum was granted. The defector remains under company "
                    + "protection aboard the fleet.", 10f);
        } else if (eventState == CampaignEventState.PENDING_FOLLOWUP) {
            ui.addPara(followupNarrative(actor,
                    state.eventCreditsOffered[row]), 10f,
                    Color.LIGHT_GRAY, Color.WHITE);
            ui.addPara("Offer window: %s days remaining", 6f,
                    Color.LIGHT_GRAY, Color.WHITE,
                    String.valueOf(daysRemaining(state, row, day)));
            ui.addButton("Keep your word", BTN_PROTECT + eventId,
                    220f, 24f, 10f);
            ui.addButton("Hand over the defector", BTN_BETRAY + eventId,
                    220f, 24f, 4f);
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
        long eventId;
        DefectorAsylumEvent.Result result;

        if (button.startsWith(BTN_GRANT)) {
            eventId = parseLong(button.substring(BTN_GRANT.length()));
            result = DefectorAsylumEvent.commit(state, eventId, day);
        } else if (button.startsWith(BTN_REFUSE)) {
            eventId = parseLong(button.substring(BTN_REFUSE.length()));
            result = DefectorAsylumEvent.refuse(state, eventId, day);
        } else if (button.startsWith(BTN_PROTECT)) {
            eventId = parseLong(button.substring(BTN_PROTECT.length()));
            result = DefectorAsylumEvent.protect(state, eventId, day);
        } else if (button.startsWith(BTN_BETRAY)) {
            eventId = parseLong(button.substring(BTN_BETRAY.length()));
            result = DefectorAsylumEvent.betray(state, eventId, day);
        } else {
            return;
        }

        feedback = feedback(result);
        feedbackEventId = eventId;
        ui.updateUIForItem(this);
    }

    static int activeEventRow(CampaignState state) {
        if (state == null) return -1;
        for (int row = state.eventCount - 1; row >= 0; row--) {
            if (CampaignEventType.fromByte(state.eventType[row])
                    != CampaignEventType.DEFECTOR_ASYLUM) {
                continue;
            }
            CampaignEventState eventState = CampaignEventState.fromByte(
                    state.eventState[row]);
            if (eventState == CampaignEventState.PENDING_CHOICE
                    || eventState == CampaignEventState.COMMITTED
                    || eventState == CampaignEventState.PENDING_FOLLOWUP) {
                return row;
            }
        }
        return -1;
    }

    static int latestTerminalRow(CampaignState state) {
        if (state == null) return -1;
        for (int row = state.eventCount - 1; row >= 0; row--) {
            if (CampaignEventType.fromByte(state.eventType[row])
                    == CampaignEventType.DEFECTOR_ASYLUM
                    && CampaignEventState.fromByte(state.eventState[row])
                    .isTerminal()) {
                return row;
            }
        }
        return -1;
    }

    static int daysRemaining(CampaignState state, int row, int day) {
        if (state == null || row < 0 || row >= state.eventCount) return 0;
        CampaignEventState eventState = CampaignEventState.fromByte(
                state.eventState[row]);
        int deadline = eventState == CampaignEventState.PENDING_FOLLOWUP
                ? state.eventFollowupDeadlineTick[row]
                : state.eventDeadlineTick[row];
        return Math.max(0, deadline - day);
    }

    static String initialNarrative(String actor, String target, String market) {
        return "A defector carrying evidence about a discovered operation "
                + "reports that " + actor + " is moving against " + target
                + " at " + market + ". The source requests company asylum.";
    }

    static String followupNarrative(String actor, int credits) {
        return actor + " has opened a buyout channel and offers " + credits
                + " credits for custody of the defector. The company granted "
                + "asylum; handing the defector over would break that promise.";
    }

    static String terminalSummary(CampaignState state, int row) {
        if (state == null || row < 0 || row >= state.eventCount
                || CampaignEventType.fromByte(state.eventType[row])
                != CampaignEventType.DEFECTOR_ASYLUM) {
            return null;
        }
        CampaignEventState eventState = CampaignEventState.fromByte(
                state.eventState[row]);
        if (eventState == CampaignEventState.REFUSED) {
            return "The asylum request was refused. The encrypted channel is closed.";
        }
        if (eventState == CampaignEventState.EXPIRED) {
            return "Contact was lost before the asylum request received an answer.";
        }
        if (eventState != CampaignEventState.RESOLVED) return null;

        DefectorAsylumOutcome outcome = DefectorAsylumOutcome.fromByte(
                state.eventDefectorOutcome[row]);
        if (outcome == DefectorAsylumOutcome.PROTECTED) {
            return "The company kept its word. The defector remains under protection.";
        }
        if (outcome == DefectorAsylumOutcome.BETRAYED) {
            return "The defector was handed over to "
                    + houseName(state, state.eventActorHouseId[row]) + ".";
        }
        return null;
    }

    static String feedback(DefectorAsylumEvent.Result result) {
        if (result == DefectorAsylumEvent.Result.COMMITTED) {
            return "Asylum granted. Supplies and fuel were transferred once.";
        }
        if (result == DefectorAsylumEvent.Result.REFUSED) {
            return "The asylum request was refused.";
        }
        if (result == DefectorAsylumEvent.Result.PROTECTED) {
            return "The company will keep its word and maintain protection.";
        }
        if (result == DefectorAsylumEvent.Result.BETRAYED) {
            return "The defector was handed over. The offered credits were received.";
        }
        if (result == DefectorAsylumEvent.Result.INSUFFICIENT_RESOURCES) {
            return "The fleet lacks the required supplies or fuel; nothing was transferred.";
        }
        if (result == DefectorAsylumEvent.Result.PAYMENT_UNAVAILABLE) {
            return "The payment channel is unavailable; custody was not transferred.";
        }
        return "That choice is no longer available.";
    }

    private static CampaignState state() {
        CampaignStateScript script = CampaignStateScript.getInstance();
        return script != null ? script.state() : null;
    }

    private static String houseName(CampaignState state, long houseId) {
        int row = state != null ? state.houseIndex(houseId) : -1;
        if (row < 0) return "an unknown house";
        String name = state.houseDisplayName[row];
        return name != null ? name : "house#" + houseId;
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
        String id = state.marketRegistry.get(state.eventMarketId[row]);
        return id != null ? id : "an unknown market";
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
}

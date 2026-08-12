package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.StationingIncidentPayload;
import com.dillon.starsectormarines.campaign.StationingIncidentType;
import com.dillon.starsectormarines.campaign.systems.StationingAssignmentService;
import com.dillon.starsectormarines.campaign.systems.StationingContractTerms;
import com.dillon.starsectormarines.campaign.systems.StationingWithdrawalService;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.awt.Color;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;

/** Dedicated captain/marine/term commitment screen for stationing contracts. */
public final class StationingScreen implements Screen {

    private static final Color HEADER = new Color(0xC8, 0xE0, 0xFF);
    private static final Color VALUE = new Color(0xE0, 0xE8, 0xFF);
    private static final Color ACCEPT = new Color(0xC8, 0xFF, 0xE0);
    private static final Color BLOCKED = new Color(0xFF, 0x80, 0x80);
    private static final float PAD = 24f;
    private static final float ROW = 34f;
    private static final float BTN_H = 32f;
    private static final float SMALL_BTN_W = 44f;

    private final WidgetRoot widgets = new WidgetRoot();
    private PositionAPI position;
    private MarineOpsContext ctx;
    private long lastContractId = -1L;
    private int marineCount;
    private int requestedMonths = 1;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        long contractId = ctx.getSelectedStationingContractId();
        if (contractId != lastContractId) {
            lastContractId = contractId;
            marineCount = Math.min(100, availableMarines());
            requestedMonths = 1;
            selectFirstActiveCaptain();
        }
        rebuild();
    }

    private void rebuild() {
        widgets.clear();
        CampaignState state = state();
        int row = state != null ? state.contractIndex(ctx.getSelectedStationingContractId()) : -1;
        if (row < 0) {
            ctx.goTo(ScreenId.MISSION_SELECT);
            return;
        }

        ContractType type = ContractType.fromByte(state.contractType[row]);
        ContractState contractState = ContractState.fromByte(state.contractState[row]);
        if (contractState == ContractState.ACTIVE) {
            rebuildManagement(state, row, type);
            return;
        }
        if (contractState != ContractState.OFFERED) {
            onBack();
            return;
        }
        int patronRow = state.houseIndex(state.contractPatronHouseId[row]);
        HouseRank rank = patronRow >= 0 ? HouseRank.fromByte(state.houseRank[patronRow]) : null;
        int available = availableMarines();
        marineCount = available > 0 ? Math.max(1, Math.min(marineCount, available)) : 0;
        StationingContractTerms terms = StationingContractTerms.create(
                type, rank, marineCount, requestedMonths);

        float x = position.getX() + PAD;
        float top = position.getY() + position.getHeight() - PAD;
        float width = position.getWidth() - 2f * PAD;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                Strings.get("stationingHeader") + " — " + displayType(type),
                x, top, HEADER));

        float y = top - 48f;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                Strings.get("stationingCaptain"), x, y, HEADER));
        y -= ROW;
        for (MarineCaptain captain : activeCaptains()) {
            widgets.add(new CaptainRowWidget(captain, x, y - 24f, width, 30f,
                    ctx::getSelectedCaptainId, id -> {
                        ctx.setSelectedCaptainId(id);
                        rebuild();
                    }));
            y -= ROW;
        }

        y -= 12f;
        String marines = MessageFormat.format(Strings.get("stationingMarines"), marineCount);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, marines, x, y, VALUE));
        addAdjustButtons(x + width - 2f * SMALL_BTN_W - 8f, y - 8f,
                () -> adjustMarines(-10), () -> adjustMarines(10));
        y -= ROW;

        int termDays = terms != null ? terms.termDays : requestedMonths * 30;
        String term = MessageFormat.format(Strings.get("stationingTerm"), termDays);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, term, x, y, VALUE));
        addAdjustButtons(x + width - 2f * SMALL_BTN_W - 8f, y - 8f,
                () -> adjustMonths(-1), () -> adjustMonths(1));
        y -= ROW;

        int monthly = terms != null ? terms.monthlyRetainer : 0;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                MessageFormat.format(Strings.get("stationingRetainer"),
                        NumberFormat.getIntegerInstance().format(monthly)),
                x, y, VALUE));

        boolean canAccept = terms != null && ctx.getSelectedCaptain() != null;
        float buttonY = position.getY() + PAD;
        float buttonW = (width - 12f) / 2f;
        widgets.add(new ButtonWidget(x, buttonY, buttonW, BTN_H, this::onBack));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("actionBack"),
                x + 12f, buttonY + BTN_H - 6f, HEADER));
        float acceptX = x + buttonW + 12f;
        widgets.add(new ButtonWidget(acceptX, buttonY, buttonW, BTN_H,
                canAccept ? this::onAccept : null));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("stationingAccept"),
                acceptX + 12f, buttonY + BTN_H - 6f, canAccept ? ACCEPT : BLOCKED));
        if (!canAccept) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("stationingBlocked"),
                    x, buttonY + BTN_H + 26f, BLOCKED));
        }
    }

    private void rebuildManagement(CampaignState state, int row, ContractType type) {
        float x = position.getX() + PAD;
        float top = position.getY() + position.getHeight() - PAD;
        float width = position.getWidth() - 2f * PAD;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                Strings.get("stationingManageHeader") + " — " + displayType(type),
                x, top, HEADER));

        int captainSlot = state.contractCaptainId[row];
        String captainId = captainSlot >= 0 ? state.captainRegistry.get(captainSlot) : null;
        MarineCaptain captain = captainById(captainId);
        String captainName = captain != null ? captain.name() : Strings.get("stationingUnknownCaptain");
        int day = Global.getSector() != null ? (int) Global.getSector().getClock().getDay() : 0;
        int daysRemaining = Math.max(0, state.contractExpiresTick[row] - day);

        float y = top - 52f;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                MessageFormat.format(Strings.get("stationingAssignedCaptain"), captainName),
                x, y, VALUE));
        y -= ROW;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                MessageFormat.format(Strings.get("stationingMarines"),
                        state.contractMarinesCommitted[row]), x, y, VALUE));
        y -= ROW;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                MessageFormat.format(Strings.get("stationingRemaining"), daysRemaining),
                x, y, VALUE));
        y -= ROW;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                MessageFormat.format(Strings.get("stationingRetainer"),
                        NumberFormat.getIntegerInstance().format(
                                state.contractRetainerPerMonth[row])), x, y, VALUE));
        StationingIncidentPayload incident = StationingIncidentPayload.from(
                state, state.contractId[row]);
        if (incident != null) {
            y -= ROW + 4f;
            widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                    Strings.get("stationingIncidentPending"), x, y, BLOCKED));
            y -= ROW;
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    incidentLabel(incident.type), x, y, VALUE));
            y -= ROW;
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    MessageFormat.format(Strings.get("stationingIncidentDetachment"),
                            captainName, incident.committedMarines), x, y, VALUE));
        }
        y -= ROW + 8f;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Strings.get("stationingWithdrawWarning"), x, y, BLOCKED));

        float buttonY = position.getY() + PAD;
        float buttonW = (width - 12f) / 2f;
        widgets.add(new ButtonWidget(x, buttonY, buttonW, BTN_H, this::onBack));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("actionBack"),
                x + 12f, buttonY + BTN_H - 6f, HEADER));
        float withdrawX = x + buttonW + 12f;
        widgets.add(new ButtonWidget(withdrawX, buttonY, buttonW, BTN_H, this::onWithdraw));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Strings.get("stationingWithdraw"),
                withdrawX + 12f, buttonY + BTN_H - 6f, BLOCKED));
    }

    private void addAdjustButtons(float x, float y, Runnable minus, Runnable plus) {
        widgets.add(new ButtonWidget(x, y, SMALL_BTN_W, BTN_H, minus));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, "−",
                x + 16f, y + BTN_H - 6f, HEADER));
        float plusX = x + SMALL_BTN_W + 8f;
        widgets.add(new ButtonWidget(plusX, y, SMALL_BTN_W, BTN_H, plus));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, "+",
                plusX + 16f, y + BTN_H - 6f, HEADER));
    }

    private void adjustMarines(int delta) {
        int available = availableMarines();
        if (available <= 0) return;
        marineCount = Math.max(1, Math.min(available, marineCount + delta));
        rebuild();
    }

    private void adjustMonths(int delta) {
        requestedMonths = Math.max(1, Math.min(6, requestedMonths + delta));
        rebuild();
    }

    private void onAccept() {
        CampaignState state = state();
        MarineCaptain captain = ctx.getSelectedCaptain();
        if (state == null || captain == null) return;
        int day = Global.getSector() != null ? (int) Global.getSector().getClock().getDay() : 0;
        if (StationingAssignmentService.accept(state, ctx.getSelectedStationingContractId(),
                captain, marineCount, requestedMonths, day)) {
            ctx.setSelectedStationingContractId(-1L);
            ctx.setSelectedCaptainId(null);
            ctx.goTo(ScreenId.MISSION_SELECT);
        } else {
            rebuild();
        }
    }

    private void onBack() {
        ctx.setSelectedStationingContractId(-1L);
        ctx.goTo(ScreenId.MISSION_SELECT);
    }

    private void onWithdraw() {
        CampaignState state = state();
        if (state == null) return;
        int day = Global.getSector() != null ? (int) Global.getSector().getClock().getDay() : 0;
        if (StationingWithdrawalService.withdraw(
                state, ctx.getSelectedStationingContractId(), day)) {
            ctx.setSelectedStationingContractId(-1L);
            ctx.setSelectedCaptainId(null);
            ctx.goTo(ScreenId.MISSION_SELECT);
        } else {
            rebuild();
        }
    }

    private void selectFirstActiveCaptain() {
        List<MarineCaptain> active = activeCaptains();
        MarineCaptain selected = ctx.getSelectedCaptain();
        if (selected == null || !active.contains(selected)) {
            ctx.setSelectedCaptainId(active.isEmpty() ? null : active.get(0).id());
        }
    }

    private static String displayType(ContractType type) {
        if (type == ContractType.GARRISON) return "Garrison";
        if (type == ContractType.CADRE) return "Cadre";
        return "Unknown";
    }

    private static String incidentLabel(StationingIncidentType type) {
        switch (type) {
            case FACTORY_ACCIDENT: return Strings.get("stationingIncidentFactoryAccident");
            case LIVE_FIRE_RAID: return Strings.get("stationingIncidentLiveFireRaid");
            case DEFECTOR_LEAD: return Strings.get("stationingIncidentDefectorLead");
            case NONE:
            default: return Strings.get("stationingIncidentUnknown");
        }
    }

    private static CampaignState state() {
        CampaignStateScript script = CampaignStateScript.getInstance();
        return script != null ? script.state() : null;
    }

    private static List<MarineCaptain> activeCaptains() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster().active() : Collections.emptyList();
    }

    private static MarineCaptain captainById(String id) {
        if (id == null) return null;
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster().byId(id) : null;
    }

    private static int availableMarines() {
        if (Global.getSector() == null) return 0;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        return fleet != null && fleet.getCargo() != null ? fleet.getCargo().getMarines() : 0;
    }

    @Override
    public void advance(float dt) {
        widgets.advance(dt);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        widgets.processInput(events);
    }

    @Override
    public void render(float alphaMult) {
        widgets.render(alphaMult);
    }
}

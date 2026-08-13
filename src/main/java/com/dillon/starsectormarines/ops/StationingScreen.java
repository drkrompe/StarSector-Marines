package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.GarrisonDefensePayload;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;
import com.dillon.starsectormarines.campaign.StationingIncidentPayload;
import com.dillon.starsectormarines.campaign.StationingIncidentType;
import com.dillon.starsectormarines.campaign.systems.StationingAssignmentService;
import com.dillon.starsectormarines.campaign.systems.StationingContractTerms;
import com.dillon.starsectormarines.campaign.systems.StationingWithdrawalService;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ops.detachment.CaptainDeploymentPolicy;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.awt.Color;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Dedicated captain/marine/term commitment screen for stationing contracts. */
public final class StationingScreen implements Screen {

    private static final Color HEADER = new Color(0xC8, 0xE0, 0xFF);
    private static final Color VALUE = new Color(0xE0, 0xE8, 0xFF);
    private static final Color ACCEPT = new Color(0xC8, 0xFF, 0xE0);
    private static final Color BLOCKED = new Color(0xFF, 0x80, 0x80);
    private static final Color INCIDENT = new Color(0xFF, 0xD0, 0x70);
    private static final float PAD = 24f;
    private static final float ROW = 34f;
    private static final float BTN_H = 32f;
    private static final float SMALL_BTN_W = 44f;

    private final WidgetRoot widgets = new WidgetRoot();
    private PositionAPI position;
    private MarineOpsContext ctx;
    private long lastContractId = -1L;
    private int requestedMonths = 1;
    private int squadPage;
    private final Set<String> selectedSquadIds = new LinkedHashSet<>();

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        long contractId = ctx.getSelectedStationingContractId();
        if (contractId != lastContractId) {
            lastContractId = contractId;
            requestedMonths = 1;
            squadPage = 0;
            selectFirstActiveCaptain();
            initializeFormation();
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
        if (contractState == ContractState.ACTIVE
                || contractState == ContractState.IN_PROGRESS) {
            rebuildManagement(state, row, type);
            return;
        }
        if (contractState != ContractState.OFFERED) {
            onBack();
            return;
        }
        int patronRow = state.houseIndex(state.contractPatronHouseId[row]);
        HouseRank rank = patronRow >= 0 ? HouseRank.fromByte(state.houseRank[patronRow]) : null;
        MarineRoster roster = roster();
        MarineCaptain selectedCaptain = ctx.getSelectedCaptain();
        int selectedTeams = CaptainDeploymentPolicy.selectedCount(roster, selectedSquadIds);
        int teamCap = selectedCaptain != null ? selectedCaptain.rank().fireteamCap() : 0;
        int active = selectedStatusCount(roster, MarineSoldierStatus.ACTIVE);
        int wia = selectedStatusCount(roster, MarineSoldierStatus.WIA);
        int living = active + wia;
        StationingContractTerms terms = StationingContractTerms.create(
                type, rank, living, requestedMonths);

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
                        selectCaptain(id);
                        rebuild();
                    }));
            y -= ROW;
        }

        y -= 12f;
        boolean commandValid = selectedTeams > 0 && active > 0
                && CaptainDeploymentPolicy.isValidCommand(
                        roster, selectedCaptain, selectedSquadIds)
                && selectionAvailable(roster);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "DETACHMENT  " + selectedTeams + " / " + teamCap + " fireteams"
                        + "   " + active + " RTD   " + wia + " WIA",
                x, y, commandValid ? ACCEPT : BLOCKED));
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
        y -= ROW + 4f;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "Select whole fireteams — home formation selected by default",
                x, y, HEADER));
        y -= ROW;

        List<MarineSquad> candidates = lineSquads(roster);
        float buttonY = position.getY() + PAD;
        float navY = buttonY + BTN_H + 8f;
        float listFloor = navY + BTN_H + 8f;
        int rowsPerColumn = Math.max(1, (int) ((y - listFloor) / ROW));
        int pageSize = rowsPerColumn * 2;
        int pages = Math.max(1, (candidates.size() + pageSize - 1) / pageSize);
        squadPage = Math.max(0, Math.min(squadPage, pages - 1));
        int start = squadPage * pageSize;
        int end = Math.min(candidates.size(), start + pageSize);
        float columnWidth = (width - 8f) / 2f;
        for (int index = start; index < end; index++) {
            int local = index - start;
            int column = local / rowsPerColumn;
            int rowInColumn = local % rowsPerColumn;
            addStationingSquad(candidates.get(index),
                    x + column * (columnWidth + 8f), y - rowInColumn * ROW,
                    columnWidth, roster, selectedCaptain);
        }
        if (pages > 1) {
            addPageButton(x, navY, 116f, "Prev Teams", squadPage > 0 ? () -> {
                squadPage--;
                rebuild();
            } : null, squadPage > 0);
            addPageButton(x + 124f, navY, 150f,
                    "Next " + (squadPage + 1) + "/" + pages,
                    squadPage + 1 < pages ? () -> {
                        squadPage++;
                        rebuild();
                    } : null, squadPage + 1 < pages);
        }

        boolean canAccept = terms != null && commandValid;
        float buttonW = (width - 12f) / 2f;
        widgets.add(new ButtonWidget(x, buttonY, buttonW, BTN_H, this::onBack));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("actionBack"),
                x + 12f, buttonY + BTN_H - 6f, HEADER));
        float acceptX = x + buttonW + 12f;
        widgets.add(new ButtonWidget(acceptX, buttonY, buttonW, BTN_H,
                canAccept ? this::onAccept : null));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("stationingAccept"),
                acceptX + 12f, buttonY + BTN_H - 6f, canAccept ? ACCEPT : BLOCKED));
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
        MarineRoster roster = roster();
        List<MarineSquad> stationed = roster != null
                ? roster.squadsStationedOn(state.contractId[row])
                : Collections.emptyList();
        if (stationed.isEmpty()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    MessageFormat.format(Strings.get("stationingMarines"),
                            state.contractMarinesCommitted[row]), x, y, VALUE));
            y -= ROW;
        } else {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "Fireteams: " + squadNames(stationed), x, y, VALUE));
            y -= ROW;
            int active = statusCount(roster, squadIds(stationed), MarineSoldierStatus.ACTIVE);
            int wia = statusCount(roster, squadIds(stationed), MarineSoldierStatus.WIA);
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    active + " RTD   " + wia + " WIA   "
                            + state.contractMarinesCommitted[row] + " living committed",
                    x, y, VALUE));
            y -= ROW;
        }
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
        GarrisonDefensePayload defense = GarrisonDefensePayload.from(
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
        } else if (defense != null) {
            y -= ROW + 4f;
            widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                    Strings.get("garrisonDefensePending"), x, y, INCIDENT));
            y -= ROW;
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    defenseLabel(defense.triggerType), x, y, VALUE));
            y -= ROW;
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    MessageFormat.format(Strings.get("stationingIncidentDetachment"),
                            captainName, defense.committedMarines), x, y, VALUE));
        }
        y -= ROW + 8f;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Strings.get("stationingWithdrawWarning"), x, y, BLOCKED));

        float buttonY = position.getY() + PAD;
        boolean incidentPending = incident != null;
        boolean defensePending = defense != null;
        boolean responsePending = incidentPending || defensePending;
        float gap = 8f;
        float buttonW = responsePending ? (width - 2f * gap) / 3f : (width - 12f) / 2f;
        widgets.add(new ButtonWidget(x, buttonY, buttonW, BTN_H, this::onBack));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("actionBack"),
                x + 12f, buttonY + BTN_H - 6f, HEADER));
        float withdrawX;
        if (responsePending) {
            float respondX = x + buttonW + gap;
            widgets.add(new ButtonWidget(respondX, buttonY, buttonW, BTN_H,
                    incidentPending ? () -> onRespond(incident) : () -> onRespond(defense)));
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    Strings.get("stationingIncidentRespond"),
                    respondX + 12f, buttonY + BTN_H - 6f, INCIDENT));
            withdrawX = respondX + buttonW + gap;
        } else {
            withdrawX = x + buttonW + 12f;
        }
        widgets.add(new ButtonWidget(withdrawX, buttonY, buttonW, BTN_H,
                defensePending ? null : this::onWithdraw));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Strings.get(defensePending ? "garrisonDefenseResolveFirst" : "stationingWithdraw"),
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

    private void addStationingSquad(MarineSquad squad, float x, float y, float width,
                                    MarineRoster roster, MarineCaptain captain) {
        boolean selected = selectedSquadIds.contains(squad.id());
        boolean available = roster.isSquadAvailable(squad.id());
        boolean canToggle = selected || CaptainDeploymentPolicy.canAdd(
                roster, captain, selectedSquadIds, squad.id());
        int ready = roster.readyCount(squad);
        int wia = statusCount(roster, Collections.singletonList(squad.id()),
                MarineSoldierStatus.WIA);
        String suffix = !available ? " · AWAY"
                : !canToggle ? " · COMMAND LIMIT" : "";
        widgets.add(new ButtonWidget(x, y - 25f, width, 29f, canToggle ? () -> {
            if (!selectedSquadIds.remove(squad.id())) selectedSquadIds.add(squad.id());
            rebuild();
        } : null));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                (selected ? "[X] " : "[ ] ") + squad.name()
                        + " · " + ready + " RTD"
                        + (wia > 0 ? " · " + wia + " WIA" : "") + suffix,
                x + 7f, y, selected ? ACCEPT : canToggle ? VALUE : BLOCKED));
    }

    private void addPageButton(float x, float y, float width, String label,
                               Runnable action, boolean enabled) {
        widgets.add(new ButtonWidget(x, y, width, BTN_H, action));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, label,
                x + 8f, y + BTN_H - 6f, enabled ? HEADER : BLOCKED));
    }

    private void adjustMonths(int delta) {
        requestedMonths = Math.max(1, Math.min(6, requestedMonths + delta));
        rebuild();
    }

    private void onAccept() {
        CampaignState state = state();
        MarineCaptain captain = ctx.getSelectedCaptain();
        MarineRoster roster = roster();
        if (state == null || roster == null || captain == null) return;
        int day = Global.getSector() != null ? (int) Global.getSector().getClock().getDay() : 0;
        if (StationingAssignmentService.acceptNamed(
                state, ctx.getSelectedStationingContractId(), roster, captain,
                selectedSquadIds, requestedMonths, day)) {
            selectedSquadIds.clear();
            lastContractId = -1L;
            ctx.setSelectedStationingContractId(-1L);
            ctx.setSelectedCaptainId(null);
            ctx.goTo(ScreenId.MISSION_SELECT);
        } else {
            rebuild();
        }
    }

    private void onBack() {
        selectedSquadIds.clear();
        lastContractId = -1L;
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

    private void onRespond(StationingIncidentPayload payload) {
        if (payload == null || ctx.planet == null) return;
        String factionId = ctx.market != null && ctx.market.getFaction() != null
                ? ctx.market.getFaction().getId() : null;
        Mission mission = StationingIncidentMissionFactory.create(
                payload, ctx.planet.getName(), factionId);
        if (mission == null) {
            rebuild();
            return;
        }
        ctx.setSelectedMission(mission);
        ctx.setSelectedCaptainId(payload.captainId);
        ctx.goTo(ScreenId.BRIEFING);
    }

    private void onRespond(GarrisonDefensePayload payload) {
        if (payload == null || ctx.planet == null) return;
        String factionId = ctx.market != null && ctx.market.getFaction() != null
                ? ctx.market.getFaction().getId() : null;
        Mission mission = GarrisonDefenseMissionFactory.create(
                payload, ctx.planet.getName(), factionId);
        if (mission == null) {
            rebuild();
            return;
        }
        ctx.setSelectedMission(mission);
        ctx.setSelectedCaptainId(payload.captainId);
        ctx.goTo(ScreenId.BRIEFING);
    }

    private void selectFirstActiveCaptain() {
        List<MarineCaptain> active = activeCaptains();
        MarineCaptain selected = ctx.getSelectedCaptain();
        if (selected == null || !active.contains(selected)) {
            ctx.setSelectedCaptainId(active.isEmpty() ? null : active.get(0).id());
        }
    }

    private void selectCaptain(String captainId) {
        ctx.setSelectedCaptainId(captainId);
        squadPage = 0;
        initializeFormation();
    }

    private void initializeFormation() {
        selectedSquadIds.clear();
        MarineRoster roster = roster();
        selectedSquadIds.addAll(CaptainDeploymentPolicy.defaultSquadIds(
                roster, ctx.getSelectedCaptain()));
    }

    private boolean selectionAvailable(MarineRoster roster) {
        if (roster == null) return false;
        for (String squadId : selectedSquadIds) {
            if (!roster.isSquadAvailable(squadId)) return false;
        }
        return true;
    }

    private int selectedStatusCount(MarineRoster roster, MarineSoldierStatus status) {
        return statusCount(roster, selectedSquadIds, status);
    }

    private static int statusCount(MarineRoster roster, Iterable<String> squadIds,
                                   MarineSoldierStatus status) {
        if (roster == null || squadIds == null || status == null) return 0;
        int count = 0;
        for (String squadId : squadIds) {
            for (MarineSoldier soldier : roster.squadMembers(roster.squadById(squadId))) {
                if (soldier.status() == status) count++;
            }
        }
        return count;
    }

    private static List<MarineSquad> lineSquads(MarineRoster roster) {
        if (roster == null) return Collections.emptyList();
        List<MarineSquad> result = new ArrayList<>();
        for (MarineSquad squad : roster.squads()) {
            if (!squad.reserve()) result.add(squad);
        }
        return result;
    }

    private static List<String> squadIds(Iterable<MarineSquad> squads) {
        List<String> result = new ArrayList<>();
        for (MarineSquad squad : squads) result.add(squad.id());
        return result;
    }

    private static String squadNames(List<MarineSquad> squads) {
        StringBuilder names = new StringBuilder();
        for (MarineSquad squad : squads) {
            if (names.length() > 0) names.append(", ");
            names.append(squad.name());
        }
        return names.toString();
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

    private static String defenseLabel(GarrisonDefenseTriggerType type) {
        switch (type) {
            case RIVAL_STRIKE: return Strings.get("garrisonDefenseRivalStrike");
            case VANILLA_RAID: return Strings.get("garrisonDefenseVanillaRaid");
            case INTERNAL_FLIP: return Strings.get("garrisonDefenseInternalFlip");
            case NONE:
            default: return Strings.get("garrisonDefenseUnknown");
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

    private static MarineRoster roster() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster() : null;
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

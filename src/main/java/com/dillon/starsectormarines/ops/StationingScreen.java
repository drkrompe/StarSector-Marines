package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.GarrisonDefensePayload;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;
import com.dillon.starsectormarines.campaign.HouseRank;
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
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.fs.starfarer.api.Global;

import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** MLX-authored stationing offer and active-assignment workspace. */
public final class StationingScreen extends MissionFlowMlxScreen {

    static final String ROOT_COMPONENT = "stationing-screen";
    static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/missions/stationing-screen.mlx");

    private long lastContractId = -1L;
    private int requestedMonths = 1;
    private final Set<String> selectedSquadIds = new LinkedHashSet<>();

    public StationingScreen() {
        super(ROOT_COMPONENT, COMPONENT_PATHS);
    }

    @Override
    protected void onAttach() {
        long contractId = context.getSelectedStationingContractId();
        if (contractId == lastContractId) return;
        lastContractId = contractId;
        requestedMonths = 1;
        selectFirstActiveCaptain();
        initializeFormation();
    }

    @Override
    protected Map<String, Object> props() {
        CampaignState state = state();
        int row = state != null ? state.contractIndex(context.getSelectedStationingContractId()) : -1;
        if (row < 0) return unavailableProps();
        ContractType type = ContractType.fromByte(state.contractType[row]);
        ContractState contractState = ContractState.fromByte(state.contractState[row]);
        if (contractState == ContractState.ACTIVE || contractState == ContractState.IN_PROGRESS) {
            return managementProps(state, row, type);
        }
        if (contractState != ContractState.OFFERED) return unavailableProps();
        return offerProps(state, row, type);
    }

    private Map<String, Object> offerProps(CampaignState state, int row, ContractType type) {
        MarineRoster roster = roster();
        MarineCaptain captain = context.getSelectedCaptain();
        int selectedSquads = CaptainDeploymentPolicy.selectedCount(roster, selectedSquadIds);
        int commandCap = captain != null ? captain.rank().squadCommandCap() : 0;
        int active = selectedStatusCount(roster, MarineSoldierStatus.ACTIVE);
        int wia = selectedStatusCount(roster, MarineSoldierStatus.WIA);
        int patronRow = state.houseIndex(state.contractPatronHouseId[row]);
        HouseRank rank = patronRow >= 0 ? HouseRank.fromByte(state.houseRank[patronRow]) : null;
        StationingContractTerms terms = StationingContractTerms.create(
                type, rank, active + wia, requestedMonths);
        boolean commandValid = selectedSquads > 0 && active > 0
                && CaptainDeploymentPolicy.isValidCommand(roster, captain, selectedSquadIds)
                && selectionAvailable(roster);

        Map<String, Object> props = commonProps(displayType(type) + " contract",
                "Configure the named detachment and commercial term before accepting.",
                "OFFERED ASSIGNMENT");
        props.put("overviewRows", List.of(
                row("stationing-state", "Status", "Offer pending"),
                row("stationing-command", "Command", captain != null ? captain.name() : "No captain"),
                row("stationing-strength", "Detachment", selectedSquads + " / " + commandCap
                        + " squads · " + active + " RTD · " + wia + " WIA")));
        props.put("captainHeader", "COMMANDING CAPTAIN");
        props.put("captainRows", captainRows());
        props.put("formationHeader", "AVAILABLE LINE SQUADS");
        props.put("formationSummary", commandValid ? "DETACHMENT READY" : "DETACHMENT INCOMPLETE");
        props.put("squadRows", offerSquadRows(roster, captain));
        props.put("noticeClasses", "stationing-notice neutral");
        props.put("noticeTitle", "AVAILABILITY COMMITMENT");
        props.put("noticeBody", "Selected squads and their captain remain unavailable for ordinary operations until this posting settles.");
        props.put("termClasses", "stationing-term-controls surface-dark");
        props.put("termLabel", "CONTRACT TERM");
        props.put("termValue", (terms != null ? terms.termDays : requestedMonths * 30) + " days");
        props.put("retainerLabel", "MONTHLY RETAINER");
        props.put("retainerValue", credits(terms != null ? terms.monthlyRetainer : 0));
        props.put("minusDisabled", requestedMonths <= 1);
        props.put("minusAction", (Runnable) () -> adjustMonths(-1));
        props.put("plusDisabled", requestedMonths >= 6);
        props.put("plusAction", (Runnable) () -> adjustMonths(1));
        props.put("primaryDisabled", terms == null || !commandValid);
        props.put("primaryClasses", "good-surface");
        props.put("primaryLabel", Strings.get("stationingAccept"));
        props.put("primaryAction", (Runnable) this::onAccept);
        hideSecondary(props);
        return props;
    }

    private Map<String, Object> managementProps(CampaignState state, int row, ContractType type) {
        MarineRoster roster = roster();
        int captainSlot = state.contractCaptainId[row];
        String captainId = captainSlot >= 0 ? state.captainRegistry.get(captainSlot) : null;
        MarineCaptain captain = captainById(captainId);
        String captainName = captain != null ? captain.name() : Strings.get("stationingUnknownCaptain");
        int day = Global.getSector() != null ? CampaignClock.day() : 0;
        int daysRemaining = Math.max(0, state.contractExpiresTick[row] - day);
        List<MarineSquad> stationed = roster != null
                ? roster.squadsStationedOn(state.contractId[row]) : List.of();
        int active = statusCount(roster, squadIds(stationed), MarineSoldierStatus.ACTIVE);
        int wia = statusCount(roster, squadIds(stationed), MarineSoldierStatus.WIA);
        StationingIncidentPayload incident = StationingIncidentPayload.from(
                state, state.contractId[row], roster);
        GarrisonDefensePayload defense = GarrisonDefensePayload.from(
                state, state.contractId[row], roster);

        Map<String, Object> props = commonProps("Active " + displayType(type) + " assignment",
                "Review the bound detachment, remaining term, and any response owed.",
                "ACTIVE POSTING");
        props.put("overviewRows", List.of(
                row("stationing-state", "Status", incident != null || defense != null
                        ? "Response pending" : "In good standing"),
                row("stationing-command", "Command", captainName),
                row("stationing-strength", "Detachment", stationed.isEmpty()
                        ? state.contractMarinesCommitted[row] + " committed marines"
                        : stationed.size() + " squads · " + active + " RTD · " + wia + " WIA"),
                row("stationing-remaining", "Time remaining", daysRemaining + " days")));
        props.put("captainHeader", "POSTED COMMAND");
        props.put("captainRows", List.of(captainCard("stationing-posted-captain",
                captainName, captain != null ? captain.rank().displayName() : "Bound captain",
                true, () -> { })));
        props.put("formationHeader", "STATIONED DETACHMENT");
        props.put("formationSummary", active + " RTD · " + wia + " WIA · "
                + state.contractMarinesCommitted[row] + " LIVING");
        props.put("squadRows", managementSquadRows(roster, stationed));
        props.put("termClasses", "stationing-term-controls hidden");
        props.put("termLabel", "");
        props.put("termValue", "");
        props.put("retainerLabel", "MONTHLY RETAINER");
        props.put("retainerValue", credits(state.contractRetainerPerMonth[row]));
        props.put("minusDisabled", true);
        props.put("minusAction", (Runnable) () -> { });
        props.put("plusDisabled", true);
        props.put("plusAction", (Runnable) () -> { });
        configureResponse(props, incident, defense, captainName);
        return props;
    }

    private void configureResponse(Map<String, Object> props,
                                   StationingIncidentPayload incident,
                                   GarrisonDefensePayload defense, String captainName) {
        if (incident != null) {
            props.put("noticeClasses", "stationing-notice danger");
            props.put("noticeTitle", Strings.get("stationingIncidentPending"));
            props.put("noticeBody", incidentLabel(incident.type) + " · "
                    + MessageFormat.format(Strings.get("stationingIncidentDetachment"),
                    captainName, incident.committedMarines));
            props.put("secondaryDisabled", false);
            props.put("secondaryClasses", "warning-surface");
            props.put("secondaryLabel", Strings.get("stationingIncidentRespond"));
            props.put("secondaryAction", (Runnable) () -> onRespond(incident));
            configureWithdraw(props, false);
        } else if (defense != null) {
            props.put("noticeClasses", "stationing-notice warning");
            props.put("noticeTitle", Strings.get("garrisonDefensePending"));
            props.put("noticeBody", defenseLabel(defense.triggerType) + " · "
                    + MessageFormat.format(Strings.get("stationingIncidentDetachment"),
                    captainName, defense.committedMarines));
            props.put("secondaryDisabled", false);
            props.put("secondaryClasses", "warning-surface");
            props.put("secondaryLabel", Strings.get("stationingIncidentRespond"));
            props.put("secondaryAction", (Runnable) () -> onRespond(defense));
            configureWithdraw(props, true);
        } else {
            props.put("noticeClasses", "stationing-notice danger");
            props.put("noticeTitle", "EARLY WITHDRAWAL");
            props.put("noticeBody", Strings.get("stationingWithdrawWarning"));
            hideSecondary(props);
            configureWithdraw(props, false);
        }
    }

    private static void hideSecondary(Map<String, Object> props) {
        props.put("secondaryDisabled", true);
        props.put("secondaryClasses", "hidden");
        props.put("secondaryLabel", "");
        props.put("secondaryAction", (Runnable) () -> { });
    }

    private void configureWithdraw(Map<String, Object> props, boolean blocked) {
        props.put("primaryDisabled", blocked);
        props.put("primaryClasses", "danger-surface");
        props.put("primaryLabel", Strings.get(blocked
                ? "garrisonDefenseResolveFirst" : "stationingWithdraw"));
        props.put("primaryAction", (Runnable) this::onWithdraw);
    }

    private Map<String, Object> unavailableProps() {
        Map<String, Object> props = commonProps("Stationing unavailable",
                "This offer or assignment no longer exists.", "NO ACTIVE RECORD");
        props.put("overviewRows", List.of(row("stationing-state", "Status", "Closed")));
        props.put("captainHeader", "COMMANDING CAPTAIN");
        props.put("captainRows", List.of());
        props.put("formationHeader", "DETACHMENT");
        props.put("formationSummary", "NO PERSONNEL BOUND");
        props.put("squadRows", List.of());
        props.put("noticeClasses", "stationing-notice danger");
        props.put("noticeTitle", "CONTRACT CLOSED");
        props.put("noticeBody", "Return to the contract board for current work.");
        props.put("termClasses", "stationing-term-controls hidden");
        props.put("termLabel", ""); props.put("termValue", "");
        props.put("retainerLabel", "MONTHLY RETAINER"); props.put("retainerValue", "—");
        props.put("minusDisabled", true); props.put("plusDisabled", true);
        props.put("minusAction", (Runnable) () -> { }); props.put("plusAction", (Runnable) () -> { });
        props.put("primaryDisabled", true); props.put("primaryClasses", "");
        props.put("primaryLabel", "UNAVAILABLE"); props.put("primaryAction", (Runnable) () -> { });
        hideSecondary(props);
        return props;
    }

    static Map<String, Object> previewProps(boolean management, boolean responsePending) {
        Runnable none = () -> { };
        Map<String, Object> props = commonProps(management
                        ? "Active Garrison assignment" : "Garrison contract",
                management ? "Review the bound detachment and remaining term."
                        : "Configure the named detachment and commercial term before accepting.",
                management ? "ACTIVE POSTING" : "OFFERED ASSIGNMENT");
        props.put("overviewRows", management ? List.of(
                row("stationing-state", "Status", responsePending ? "Response pending" : "In good standing"),
                row("stationing-command", "Command", "Mira Hale"),
                row("stationing-strength", "Detachment", "3 squads · 34 RTD · 2 WIA"),
                row("stationing-remaining", "Time remaining", "47 days")) : List.of(
                row("stationing-state", "Status", "Offer pending"),
                row("stationing-command", "Command", "Mira Hale"),
                row("stationing-strength", "Detachment", "3 / 4 squads · 34 RTD · 2 WIA")));
        props.put("captainHeader", management ? "POSTED COMMAND" : "COMMANDING CAPTAIN");
        props.put("captainRows", management ? List.of(
                captainCard("stationing-captain-preview-0", "Mira Hale",
                        "Lieutenant · 4 squad command", true, none)) : List.of(
                captainCard("stationing-captain-preview-0", "Mira Hale",
                        "Lieutenant · 4 squad command", true, none),
                captainCard("stationing-captain-preview-1", "Tomas Venn",
                        "Ensign · 2 squad command", false, none)));
        props.put("formationHeader", management ? "STATIONED DETACHMENT" : "AVAILABLE LINE SQUADS");
        props.put("formationSummary", management ? "34 RTD · 2 WIA · 36 LIVING" : "DETACHMENT READY");
        List<SquadRow> previewSquads = new ArrayList<>(List.of(
                squadCard("stationing-squad-preview-0", "1st Squad", "12 RTD", "Field Rifles / Standard Plate", true, false, none),
                squadCard("stationing-squad-preview-1", "2nd Squad", "11 RTD · 1 WIA", "Assault Rifles / Heavy Plate", true, false, none),
                squadCard("stationing-squad-preview-2", "3rd Squad", "11 RTD · 1 WIA", "Marksman Rifles / Scout Plate", true, false, none)));
        if (!management) {
            previewSquads.add(squadCard("stationing-squad-preview-3", "4th Squad",
                    "12 RTD", "Available", false, false, none));
        }
        props.put("squadRows", List.copyOf(previewSquads));
        props.put("termClasses", management ? "stationing-term-controls hidden" : "stationing-term-controls surface-dark");
        props.put("termLabel", "CONTRACT TERM"); props.put("termValue", "60 days");
        props.put("retainerLabel", "MONTHLY RETAINER"); props.put("retainerValue", "48,000 credits");
        props.put("minusDisabled", management); props.put("plusDisabled", management);
        props.put("minusAction", none); props.put("plusAction", none);
        props.put("noticeClasses", responsePending ? "stationing-notice warning" : "stationing-notice neutral");
        props.put("noticeTitle", responsePending ? "GARRISON DEFENSE — RESPONSE PENDING" : "AVAILABILITY COMMITMENT");
        props.put("noticeBody", responsePending ? "Rival strike force approaching · Mira Hale with 36 marines on site."
                : "Selected squads and their captain remain unavailable until this posting settles.");
        props.put("backAction", none);
        props.put("primaryDisabled", responsePending);
        props.put("primaryClasses", management ? "danger-surface" : "good-surface");
        props.put("primaryLabel", responsePending ? "RESOLVE RESPONSE FIRST"
                : management ? "WITHDRAW EARLY" : "ACCEPT ASSIGNMENT");
        props.put("primaryAction", none);
        if (responsePending) {
            props.put("secondaryDisabled", false); props.put("secondaryClasses", "warning-surface");
            props.put("secondaryLabel", "RESPOND"); props.put("secondaryAction", none);
        } else hideSecondary(props);
        return props;
    }

    private static Map<String, Object> commonProps(String title, String subtitle, String kicker) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("title", title); props.put("subtitle", subtitle); props.put("kicker", kicker);
        props.put("backAction", (Runnable) () -> { });
        return props;
    }

    private List<CaptainRow> captainRows() {
        List<CaptainRow> rows = new ArrayList<>();
        MarineCaptain selected = context.getSelectedCaptain();
        for (MarineCaptain captain : activeCaptains()) {
            rows.add(captainCard("stationing-captain-" + rows.size(), captain.name(),
                    captain.rank().displayName() + " · " + captain.rank().squadCommandCap()
                            + " squad command",
                    selected != null && selected.id().equals(captain.id()),
                    () -> selectCaptain(captain.id())));
        }
        return List.copyOf(rows);
    }

    private List<SquadRow> offerSquadRows(MarineRoster roster, MarineCaptain captain) {
        if (roster == null) return List.of();
        List<SquadRow> rows = new ArrayList<>();
        for (MarineSquad squad : lineSquads(roster)) {
            boolean selected = selectedSquadIds.contains(squad.id());
            boolean available = roster.isSquadAvailable(squad.id());
            boolean canToggle = selected || CaptainDeploymentPolicy.canAdd(
                    roster, captain, selectedSquadIds, squad.id());
            int ready = roster.readyCount(squad);
            int wia = statusCount(roster, List.of(squad.id()), MarineSoldierStatus.WIA);
            String status = ready + " RTD" + (wia > 0 ? " · " + wia + " WIA" : "");
            String detail = !available ? "Away on another commitment"
                    : !canToggle ? "Command capacity reached" : "Available for stationing";
            rows.add(squadCard("stationing-squad-" + rows.size(), squad.name(), status,
                    detail, selected, !canToggle, () -> toggleSquad(squad.id())));
        }
        return List.copyOf(rows);
    }

    private static List<SquadRow> managementSquadRows(MarineRoster roster,
                                                       List<MarineSquad> stationed) {
        if (roster == null) return List.of();
        List<SquadRow> rows = new ArrayList<>();
        for (MarineSquad squad : stationed) {
            int active = statusCount(roster, List.of(squad.id()), MarineSoldierStatus.ACTIVE);
            int wia = statusCount(roster, List.of(squad.id()), MarineSoldierStatus.WIA);
            rows.add(squadCard("stationing-posted-squad-" + rows.size(), squad.name(),
                    active + " RTD" + (wia > 0 ? " · " + wia + " WIA" : ""),
                    "Bound to this posting", true, true, () -> { }));
        }
        return List.copyOf(rows);
    }

    private static CaptainRow captainCard(String id, String name, String detail,
                                           boolean selected, Runnable action) {
        return new CaptainRow(id, "captain-card" + (selected ? " selected" : ""),
                name, detail, action);
    }

    private static SquadRow squadCard(String id, String name, String status,
                                      String detail, boolean selected,
                                      boolean disabled, Runnable action) {
        return new SquadRow(id, "stationing-squad-card" + (selected ? " selected" : ""),
                name, status, "label heading stationing-squad-status "
                        + (selected ? "tone-good" : disabled ? "tone-danger" : "tone-edge"),
                detail, disabled, action);
    }

    private static OverviewRow row(String id, String label, String value) {
        return new OverviewRow(id, label, value);
    }

    private void adjustMonths(int delta) {
        requestedMonths = Math.max(1, Math.min(6, requestedMonths + delta));
        rebuildDocument();
    }

    private void selectCaptain(String captainId) {
        context.setSelectedCaptainId(captainId);
        initializeFormation();
        rebuildDocument();
    }

    private void toggleSquad(String squadId) {
        if (!selectedSquadIds.remove(squadId)) selectedSquadIds.add(squadId);
        rebuildDocument();
    }

    private void onAccept() {
        CampaignState state = state();
        MarineCaptain captain = context.getSelectedCaptain();
        MarineRoster roster = roster();
        if (state == null || roster == null || captain == null) return;
        int day = Global.getSector() != null ? CampaignClock.day() : 0;
        if (StationingAssignmentService.acceptNamed(state,
                context.getSelectedStationingContractId(), roster, captain,
                selectedSquadIds, requestedMonths, day)) finish();
        else rebuildDocument();
    }

    private void onWithdraw() {
        CampaignState state = state();
        if (state == null) return;
        int day = Global.getSector() != null ? CampaignClock.day() : 0;
        if (StationingWithdrawalService.withdraw(
                state, context.getSelectedStationingContractId(), day)) finish();
        else rebuildDocument();
    }

    private void onRespond(StationingIncidentPayload payload) {
        applyResponse(StationingResponseLaunch.respond(context, payload));
    }

    private void onRespond(GarrisonDefensePayload payload) {
        applyResponse(StationingResponseLaunch.respond(context, payload));
    }

    private void applyResponse(StationingResponseLaunch.Result result) {
        if (result == StationingResponseLaunch.Result.NO_FORCE_RESOLVED) finish();
        else if (result == StationingResponseLaunch.Result.UNAVAILABLE) rebuildDocument();
    }

    private void onBack() { finish(); }

    private void finish() {
        selectedSquadIds.clear();
        lastContractId = -1L;
        context.setSelectedStationingContractId(-1L);
        context.setSelectedCaptainId(null);
        context.goTo(ScreenId.MISSION_SELECT);
    }

    private void selectFirstActiveCaptain() {
        List<MarineCaptain> active = activeCaptains();
        MarineCaptain selected = context.getSelectedCaptain();
        if (selected == null || !active.contains(selected)) {
            context.setSelectedCaptainId(active.isEmpty() ? null : active.get(0).id());
        }
    }

    private void initializeFormation() {
        selectedSquadIds.clear();
        selectedSquadIds.addAll(CaptainDeploymentPolicy.defaultSquadIds(
                roster(), context.getSelectedCaptain()));
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
            MarineSquad squad = roster.squadById(squadId);
            if (squad == null) continue;
            for (MarineSoldier soldier : roster.squadMembers(squad)) {
                if (soldier.status() == status) count++;
            }
        }
        return count;
    }

    private static List<MarineSquad> lineSquads(MarineRoster roster) {
        if (roster == null) return List.of();
        List<MarineSquad> result = new ArrayList<>();
        for (MarineSquad squad : roster.squads()) if (!squad.reserve()) result.add(squad);
        return result;
    }

    private static List<String> squadIds(Iterable<MarineSquad> squads) {
        List<String> result = new ArrayList<>();
        for (MarineSquad squad : squads) result.add(squad.id());
        return result;
    }

    private static String displayType(ContractType type) {
        if (type == ContractType.GARRISON) return "Garrison";
        if (type == ContractType.CADRE) return "Cadre";
        return "Unknown";
    }

    private static String credits(int value) {
        return NumberFormat.getIntegerInstance().format(value) + " credits";
    }

    private static String incidentLabel(StationingIncidentType type) {
        return switch (type) {
            case FACTORY_ACCIDENT -> Strings.get("stationingIncidentFactoryAccident");
            case LIVE_FIRE_RAID -> Strings.get("stationingIncidentLiveFireRaid");
            case DEFECTOR_LEAD -> Strings.get("stationingIncidentDefectorLead");
            case NONE -> Strings.get("stationingIncidentUnknown");
        };
    }

    private static String defenseLabel(GarrisonDefenseTriggerType type) {
        return switch (type) {
            case RIVAL_STRIKE -> Strings.get("garrisonDefenseRivalStrike");
            case VANILLA_RAID -> Strings.get("garrisonDefenseVanillaRaid");
            case INTERNAL_FLIP -> Strings.get("garrisonDefenseInternalFlip");
            case NONE -> Strings.get("garrisonDefenseUnknown");
        };
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

    @Override protected void onCancel() { onBack(); }

    @Override
    protected List<String> requiredElementIds() {
        return List.of("stationing-root", "stationing-header", "stationing-body",
                "stationing-overview", "stationing-formation", "stationing-captains",
                "stationing-squads", "stationing-actions", "stationing-back",
                "stationing-primary", "stationing-secondary");
    }

    record OverviewRow(String id, String label, String value) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "labelId" -> id + "-label";
                case "valueId" -> id + "-value"; case "label" -> label;
                case "value" -> value; default -> null;
            };
        }
    }

    record CaptainRow(String id, String classes, String name, String detail,
                      Runnable action) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "classes" -> classes; case "nameId" -> id + "-name";
                case "detailId" -> id + "-detail"; case "name" -> name;
                case "detail" -> detail; case "action" -> action; default -> null;
            };
        }
    }

    record SquadRow(String id, String classes, String name, String status,
                    String tone, String detail, boolean disabled, Runnable action)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "classes" -> classes; case "topId" -> id + "-top";
                case "nameId" -> id + "-name"; case "statusId" -> id + "-status";
                case "detailId" -> id + "-detail"; case "name" -> name;
                case "status" -> status; case "tone" -> tone; case "detail" -> detail;
                case "disabled" -> disabled; case "action" -> action; default -> null;
            };
        }
    }
}

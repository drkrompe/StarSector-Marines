package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.Posting;
import com.dillon.starsectormarines.campaign.systems.StationingOfferLookup;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.detachment.MissionForceEnvelope;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** MLX-authored client, contract, and mission-detail workspace. */
public final class MissionSelectScreen implements Screen {

    static final String ROOT_COMPONENT = "mission-select";
    static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/missions/mission-select.mlx");

    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), COMPONENT_PATHS);

    private MarineOpsContext context;
    private Runnable dismissDialog;
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        boolean reactivating = input == null;
        context = ctx;
        this.dismissDialog = dismissDialog;
        viewport = MarineOpsUiViewport.from(position);
        if (document == null || reactivating) installDocument();
        else document.layout(viewport.documentWidth(), viewport.documentHeight());
        input = new StarsectorUiInputAdapter(document, viewport);
    }

    private void installDocument() {
        MarkupInstance candidate = markup.reloadAndBuild(reactor, ROOT_COMPONENT, props());
        UiDocument built;
        try {
            requireWiredElements(candidate);
            built = new UiDocument(candidate.root());
            for (var style : candidate.styles()) built.addStyleSheet(style);
            built.theme(MarineOpsThemes.standard()).onCancel(this::onBack);
            if (viewport != null) built.layout(viewport.documentWidth(), viewport.documentHeight());
        } catch (RuntimeException failure) {
            candidate.close();
            throw failure;
        }
        UiDocument previousDocument = document;
        MarkupInstance previousInstance = markupInstance;
        document = built;
        markupInstance = candidate;
        if (previousDocument != null) previousDocument.deactivateInput();
        if (previousInstance != null) previousInstance.close();
        if (viewport != null) input = new StarsectorUiInputAdapter(document, viewport);
    }

    private Map<String, Object> props() {
        Client selectedClient = context != null ? context.getSelectedClient() : null;
        Mission selectedMission = context != null ? context.getSelectedMission() : null;
        List<Mission> missions = selectedClient != null
                ? context.getMissionsFor(selectedClient) : List.of();
        List<StationingRow> stationing = stationingRows(selectedClient);

        Map<String, Object> props = baseProps();
        props.put("locationLabel", locationLabel());
        props.put("planetImage", context != null ? context.planetTexture : null);
        props.put("backAction", (Runnable) this::onBack);
        props.put("armoryAction", (Runnable) this::onArmory);
        props.put("clients", clientRows(selectedClient));
        props.put("clientEmpty", context == null || context.clients.isEmpty()
                ? "No clients have work at this market." : "");
        props.put("missionHeader", selectedClient != null
                ? selectedClient.displayName : "Contracts");
        props.put("missionSummary", missionSummary(selectedClient, missions));
        props.put("missions", missionRows(missions, selectedMission));
        props.put("stationingRows", stationing);
        props.put("missionEmpty", selectedClient == null
                ? "Choose a client to inspect their current work."
                : missions.isEmpty() && stationing.isEmpty()
                        ? "Nothing pending from this client." : "");
        putDetail(props, selectedMission);
        return props;
    }

    static Map<String, Object> previewProps() {
        Map<String, Object> props = baseProps();
        Runnable none = () -> { };
        props.put("locationLabel", "Jangala  ·  Corvus System");
        props.put("planetImage", null);
        props.put("backAction", none);
        props.put("armoryAction", none);
        props.put("clientEmpty", "");
        props.put("clients", List.of(
                new ClientRow("client-debug", "client-name-debug", "client-rep-debug",
                        null, "client-card selected", "DEBUG — All Missions",
                        "Neutral  ·  developer catalogue", false, none),
                new ClientRow("client-hegemony", "client-name-hegemony", "client-rep-hegemony",
                        null, "client-card", "Hegemony Command",
                        "Favorable  ·  2 current offers", false, none),
                new ClientRow("client-independent", "client-name-independent", "client-rep-independent",
                        null, "client-card", "Independent Liaison",
                        "Neutral  ·  no current offer", false, none)));
        props.put("missionHeader", "DEBUG — All Missions");
        props.put("missionSummary", "Developer catalogue  ·  23 eligible operation fixtures");
        List<MissionRow> rows = new ArrayList<>();
        rows.add(previewMission(0, MissionType.ASSAULT, OperationTier.FIRST_CONTRACT, 1, 17_500, false, none));
        rows.add(previewMission(1, MissionType.ASSAULT, OperationTier.VETERAN, 6, 20_500, false, none));
        rows.add(previewMission(2, MissionType.SABOTAGE, OperationTier.FIRST_CONTRACT, 1, 20_500, true, none));
        rows.add(previewMission(3, MissionType.SABOTAGE, OperationTier.ESTABLISHED, 2, 25_000, false, none));
        rows.add(previewMission(4, MissionType.RAID, OperationTier.VETERAN, 5, 28_500, false, none));
        rows.add(previewMission(5, MissionType.CONQUEST, OperationTier.FULL_STRENGTH, 84, 185_000, false, none));
        props.put("missions", List.copyOf(rows));
        props.put("stationingRows", List.of());
        props.put("missionEmpty", "");
        props.put("detailClasses", "mission-detail panel");
        props.put("detailKicker", "SABOTAGE  ·  MEDIUM RISK");
        props.put("detailTitle", "Sabotage — First Contract");
        props.put("detailMeta", "Jangala  ·  First Contract  ·  1 squad recommended");
        props.put("detailFlavor", "Infiltrate the target complex, disable its critical systems, and withdraw before the garrison can mass against the team.");
        props.put("detailRows", List.of(
                detailRow("tier", "Operation scale", "First Contract"),
                detailRow("presence", "Field presence", "1 squad active · reserve held off-map"),
                detailRow("opposition", "Opposition", "Regular core · veterans possible"),
                detailRow("lift", "Lift required", "2 sorties"),
                detailRow("payout", "Payout", "20,500 credits")));
        props.put("briefDisabled", false);
        props.put("briefAction", none);
        props.put("declineDisabled", false);
        props.put("declineAction", none);
        return props;
    }

    private static MissionRow previewMission(int index, MissionType type,
                                             OperationTier tier, int squads,
                                             int payout, boolean selected,
                                             Runnable action) {
        String base = "mission-preview-" + index;
        int active = type == MissionType.SABOTAGE ? 1
                : type == MissionType.RAID ? 2 : 0;
        String presence = active > 0 ? "  ·  " + active + " max active" : "";
        return new MissionRow(base, base + "-glyph", base + "-title",
                base + "-meta", base + "-payout",
                "mission-card " + typeClass(type) + (selected ? " selected" : ""),
                Character.toString(type.glyph), type.name() + " — " + tier.displayName,
                tier.displayName + "  ·  " + squads + (squads == 1 ? " squad" : " squads") + presence,
                NumberFormat.getIntegerInstance().format(payout) + " cr", action);
    }

    private List<ClientRow> clientRows(Client selected) {
        if (context == null) return List.of();
        List<ClientRow> rows = new ArrayList<>();
        for (int i = 0; i < context.clients.size(); i++) {
            Client client = context.clients.get(i);
            String base = "client-" + i;
            boolean isSelected = client == selected;
            String status = client.locked && client.lockReason != null
                    ? "Unavailable"
                    : client.repLevel != null ? client.repLevel.getDisplayName() : "";
            rows.add(new ClientRow(base, base + "-name", base + "-rep",
                    client.crestPath,
                    "client-card" + (isSelected ? " selected" : "")
                            + (client.locked ? " locked" : ""),
                    client.displayName, status, client.locked,
                    () -> selectClient(client)));
        }
        return List.copyOf(rows);
    }

    private List<MissionRow> missionRows(List<Mission> missions, Mission selected) {
        List<MissionRow> rows = new ArrayList<>();
        NumberFormat credits = NumberFormat.getIntegerInstance();
        for (int i = 0; i < missions.size(); i++) {
            Mission mission = missions.get(i);
            String base = "mission-" + i;
            boolean isSelected = selected != null && selected.id.equals(mission.id);
            int recommended = MissionForceEnvelope.recommendedSquads(mission);
            String active = mission.fieldPresencePolicy.limited()
                    ? "  ·  " + mission.fieldPresencePolicy.activeSquadLimit() + " max active"
                    : "";
            int multiplier = mission.cashMultiplier & 0xFF;
            if (multiplier <= 0) multiplier = 100;
            long payout = (long) mission.payout * multiplier / 100L;
            rows.add(new MissionRow(base, base + "-glyph", base + "-title",
                    base + "-meta", base + "-payout",
                    "mission-card " + typeClass(mission.type)
                            + (isSelected ? " selected" : ""),
                    Character.toString(mission.type.glyph), mission.name,
                    mission.tier.displayName + "  ·  " + recommended
                            + (recommended == 1 ? " squad" : " squads") + active,
                    credits.format(payout) + " cr", () -> selectMission(mission)));
        }
        return List.copyOf(rows);
    }

    private List<StationingRow> stationingRows(Client selected) {
        if (MarineOpsContext.isPolityClient(selected)) return postingRows();
        long active = findActiveStationing(selected);
        long contractId = active >= 0L ? active : findStationingOffer(selected);
        if (contractId < 0L) return List.of();
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) return List.of();
        CampaignState state = script.state();
        int row = state.contractIndex(contractId);
        if (row < 0) return List.of();
        ContractType type = ContractType.fromByte(state.contractType[row]);
        String title = type == ContractType.GARRISON
                ? "Garrison Contract" : "Cadre Contract";
        return List.of(new StationingRow("stationing-contract",
                "stationing-contract-title", "stationing-contract-action",
                title, active >= 0L ? "Manage assignment" : "Configure assignment",
                () -> configureStationing(contractId)));
    }

    /**
     * The polity's one row. A posting is not offered and not accepted — there is no
     * OFFERED row to find, so the row exists whether or not anything is posted yet and
     * the draft sentinel carries "post here" into the stationing screen.
     */
    private List<StationingRow> postingRows() {
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null || context == null || context.market == null) return List.of();
        CampaignState state = script.state();
        int row = Posting.activeRowAt(state,
                state.marketRegistry.intern(context.market.getId()));
        long contractId = row >= 0 ? state.contractId[row] : Posting.DRAFT_CONTRACT_ID;
        return List.of(new StationingRow("stationing-contract",
                "stationing-contract-title", "stationing-contract-action",
                Strings.get("postingTitle"),
                Strings.get(row >= 0 ? "postingRowManage" : "postingRowCreate"),
                () -> configureStationing(contractId)));
    }

    private void putDetail(Map<String, Object> props, Mission mission) {
        boolean present = mission != null;
        props.put("detailClasses", "mission-detail panel" + (present ? "" : " empty-detail"));
        props.put("detailKicker", present
                ? mission.type.name() + "  ·  " + mission.risk.name() + " RISK"
                : "MISSION DOSSIER");
        props.put("detailTitle", present ? mission.name : "Select a contract");
        props.put("detailMeta", present
                ? mission.tier.displayName + "  ·  "
                        + MissionForceEnvelope.recommendedSquads(mission)
                        + " squads recommended"
                : "Choose a row in the contract list.");
        props.put("detailFlavor", present ? mission.flavor :
                "The selected operation's scale, field-presence limit, opposition, lift, and payout will appear here before you enter deployment planning.");
        props.put("detailRows", present ? detailRows(mission) : List.of());
        props.put("briefDisabled", !present);
        props.put("briefAction", (Runnable) this::briefSelected);
        props.put("declineDisabled", !present);
        props.put("declineAction", (Runnable) this::collapseSelected);
    }

    private static List<DetailRow> detailRows(Mission mission) {
        int multiplier = mission.cashMultiplier & 0xFF;
        if (multiplier <= 0) multiplier = 100;
        long payout = (long) mission.payout * multiplier / 100L;
        return List.of(
                detailRow("tier", "Operation scale", mission.tier.displayName),
                detailRow("presence", "Field presence", mission.fieldPresencePolicy.briefingText()),
                detailRow("opposition", "Opposition", MissionForceEnvelope.oppositionExpectation(mission.risk)),
                detailRow("lift", "Lift required", mission.requiredDrops + (mission.requiredDrops == 1 ? " sortie" : " sorties")),
                detailRow("payout", "Payout", NumberFormat.getIntegerInstance().format(payout) + " credits"));
    }

    private static DetailRow detailRow(String key, String label, String value) {
        String base = "detail-" + key;
        return new DetailRow(base, base + "-label", base + "-value", label, value);
    }

    private static Map<String, Object> baseProps() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("screenTitle", "Marine Operations");
        props.put("clientHeader", "Clients");
        props.put("catalogHeader", "Contracts");
        props.put("detailHeader", "Dossier");
        return props;
    }

    private String locationLabel() {
        if (context == null) return "";
        String market = context.market != null ? context.market.getName() : null;
        String planet = context.planet != null ? context.planet.getName() : null;
        String location = market != null ? market : planet != null ? planet : "Unknown market";
        return location + "  ·  Current local offers";
    }

    private static String missionSummary(Client client, List<Mission> missions) {
        if (client == null) return "Choose a client to inspect current work.";
        if (MarineOpsContext.DEBUG_CLIENT_FACTION_ID.equals(client.factionId)) {
            return "Developer catalogue  ·  " + missions.size() + " eligible operation fixtures";
        }
        if (MarineOpsContext.isPolityClient(client)) return Strings.get("postingClientSummary");
        return missions.size() + (missions.size() == 1 ? " current offer" : " current offers")
                + "  ·  most markets will have none";
    }

    private static String typeClass(MissionType type) {
        return "type-" + (type != null ? type.name().toLowerCase(Locale.ROOT) : "unknown");
    }

    private long findStationingOffer(Client selected) {
        if (selected == null || selected.patronHouseId < 0L || context.market == null) return -1L;
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) return -1L;
        CampaignState state = script.state();
        int marketId = state.marketRegistry.intern(context.market.getId());
        return StationingOfferLookup.find(state, selected.patronHouseId, marketId);
    }

    private long findActiveStationing(Client selected) {
        if (selected == null || selected.patronHouseId < 0L || context.market == null) return -1L;
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) return -1L;
        CampaignState state = script.state();
        int marketId = state.marketRegistry.intern(context.market.getId());
        return StationingOfferLookup.findActive(state, selected.patronHouseId, marketId);
    }

    private void selectClient(Client client) {
        if (context == null || client == null || client.locked) return;
        context.setSelectedClient(client);
        context.setSelectedMission(null);
        installDocument();
    }

    private void selectMission(Mission mission) {
        if (context == null) return;
        context.setSelectedMission(mission);
        installDocument();
    }

    private void collapseSelected() {
        if (context == null) return;
        context.setSelectedMission(null);
        installDocument();
    }

    private void briefSelected() {
        if (context != null && context.getSelectedMission() != null) {
            context.goTo(ScreenId.BRIEFING);
        }
    }

    private void configureStationing(long contractId) {
        if (context == null) return;
        context.setSelectedMission(null);
        context.setSelectedStationingContractId(contractId);
        context.goTo(ScreenId.STATIONING);
    }

    private void onArmory() {
        if (context != null) context.openCompanyArmoryFrom(ScreenId.MISSION_SELECT);
    }

    private void onBack() {
        if (dismissDialog != null) dismissDialog.run();
    }

    private static void requireWiredElements(MarkupInstance component) {
        for (String id : List.of(
                "mission-select-root", "mission-select-header", "mission-select-body",
                "mission-client-panel", "mission-client-list", "mission-back",
                "mission-armory", "mission-catalog-panel", "mission-list",
                "mission-detail-panel", "mission-detail-rows", "mission-brief",
                "mission-collapse")) component.requireElement(id);
    }

    @Override public void advance(float dt) {
        if (markupInstance != null) markupInstance.flush();
        if (document != null) document.advance(dt);
    }
    @Override public void render(float alphaMult) {
        if (document != null && viewport != null) document.render(viewport, alphaMult);
    }
    @Override public void processInput(List<InputEventAPI> events) {
        if (input != null) input.process(events);
    }
    @Override public void detach() {
        if (document != null) document.deactivateInput();
        input = null;
    }

    record ClientRow(String id, String nameId, String repId, String crest,
                     String classes, String name, String reputation,
                     boolean disabled, Runnable action) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "nameId" -> nameId; case "repId" -> repId;
                case "crestId" -> id + "-crest"; case "copyId" -> id + "-copy";
                case "crest" -> crest; case "classes" -> classes; case "name" -> name;
                case "reputation" -> reputation; case "disabled" -> disabled;
                case "action" -> action; default -> null;
            };
        }
    }

    record MissionRow(String id, String glyphId, String titleId, String metaId,
                      String payoutId, String classes, String glyph, String title,
                      String meta, String payout, Runnable action) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "glyphId" -> glyphId; case "titleId" -> titleId;
                case "copyId" -> id + "-copy";
                case "metaId" -> metaId; case "payoutId" -> payoutId;
                case "classes" -> classes; case "glyph" -> glyph; case "title" -> title;
                case "meta" -> meta; case "payout" -> payout; case "action" -> action;
                default -> null;
            };
        }
    }

    record DetailRow(String id, String labelId, String valueId,
                     String label, String value) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "labelId" -> labelId; case "valueId" -> valueId;
                case "label" -> label; case "value" -> value; default -> null;
            };
        }
    }

    record StationingRow(String id, String titleId, String actionId,
                         String title, String actionLabel, Runnable action)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "titleId" -> titleId; case "actionId" -> actionId;
                case "title" -> title; case "actionLabel" -> actionLabel;
                case "action" -> action; default -> null;
            };
        }
    }
}

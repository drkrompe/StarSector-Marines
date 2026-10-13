package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
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
import java.util.function.Consumer;

/** MLX-authored client, contract, and mission-detail workspace. */
public final class MissionSelectScreen implements Screen {

    static final String ROOT_COMPONENT = "mission-select";
    static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/missions/mission-dialogue.mlx",
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
        putCompanyNews(props, CompanyNotices.current(), () -> {
            var notices = CompanyNotices.current();
            if (context == null || notices.isEmpty()) return;
            var notice = notices.get(0);
            context.goTo(notice.screen);
        });
        props.put("clients", clientRows(selectedClient));
        props.put("clientEmpty", context == null || context.clients.isEmpty()
                ? Strings.get("missionSelectNoClientsHaveWorkAtThisMarket") : "");
        props.put("missionHeader", selectedClient != null
                ? selectedClient.displayName : Strings.get("missionSelectContracts"));
        props.put("missionSummary", missionSummary(selectedClient, missions));
        props.put("missions", missionRows(missions, selectedMission, this::selectMission));
        props.put("stationingRows", stationing);
        props.put("missionEmpty", selectedClient == null
                ? Strings.get("missionSelectChooseAClientToInspectTheirCurrent")
                : missions.isEmpty() && stationing.isEmpty()
                        ? Strings.get("missionSelectNothingPendingFromThisClient") : "");
        MarineRosterScript script = Global.getSector() == null ? null : MarineRosterScript.getInstance();
        putBroker(props, selectedClient, script == null ? null : script.roster());
        OpeningBroker.Destination office = OpeningBroker.current();
        String referral = OpeningBroker.referral(selectedClient, script == null ? null : script.roster(), office);
        putDetail(props, selectedMission, this::briefSelected, this::collapseSelected);
        if (!referral.isEmpty()) putReferral(props, office, referral);
        BrokenSupplyRouteProgress route = SupplyRouteCampaign.current();
        if(selectedClient!=null && "marines_tamsin_vale".equals(selectedClient.contactId)) {
            putShepherd(props, ShepherdRecoveryCampaign.current());
        }
        if (AgreusArcCampaign.isContact(selectedClient)) putAgreus(props);
        if (OpeningBroker.isContact(selectedClient) && script != null && WaylineArc.stage(script.roster()) == WaylineArc.Stage.COMPLETE) {
            if (CorvusWaylineCampaign.atSite(context.planet))
                putCorvus(props, route, selectedMission);
            else putSupplyRoute(props, route, selectedMission, SupplyRouteCampaign.destination() != null);
        }
        if (selectedMission == null && AgreusArcCampaign.current() != null && !AgreusArcCampaign.current().completed() && (OpeningBroker.isContact(selectedClient)
                || selectedClient != null && "marines_tamsin_vale".equals(selectedClient.contactId)))
            putAgreusReferral(props);
        return props;
    }

    static void putShepherd(Map<String,Object> props, ShepherdRecoveryProgress progress) {
            props.put("missionSummary",Strings.get("missionSelectShepherdTitle"));
            props.put("brokerClasses","broker-dossier console-readout");
            props.put("brokerPortrait",OpeningBroker.CREST);
            props.put("brokerName",ShepherdRecoveryCampaign.NAME);
            props.put("brokerRole",Strings.get("missionSelectBASINRECLAMATIONCOOPERATIVEINDEPENDENT"));
            props.put("brokerIntroduction",progress!=null && progress.departed()
                    ? Strings.get("missionSelectTheHaulersAreMovingAgainGiveMy")
                    : progress!=null && progress.repaired()
                        ? Strings.get("missionSelectTheFittersHaveDoneWhatTheyCan")
                        : progress!=null && progress.claimSecured()
                            ? progress.shorePower() ? Strings.get("missionSelectPowerSOnWeVeStillGot")
                                : Strings.get("missionSelectMyPeopleAreBackInTheWorkshop")
                            : Strings.get("missionSelectVennGaveMeYourNameIHave"));
            props.put("brokerProgress",progress!=null && progress.departed()?Strings.get("missionSelectCOMPLETEMORROWREGISTRYTRANSFERRED"):progress!=null && progress.repaired()?Strings.get("missionSelectWORKINGPASSAGEDEPARTURECLEARANCE"):progress!=null && progress.claimSecured()?Strings.get("missionSelectCOLDSTARTSHOREPOWERREPAIRS"):Strings.get("missionSelectSALVAGERIGHTSNIGHTRECOVERY"));
    }

    static void putAgreus(Map<String, Object> props) {
        putAgreus(props, AgreusArcCampaign.current(), AgreusArcCampaign.accessReason());
    }

    static void putAgreus(Map<String, Object> props, AgreusArcProgress p, String accessReason) {
        props.put("missionSummary", Strings.get("missionSelectAgreusTitle"));
        props.put("brokerClasses", "broker-dossier console-readout");
        props.put("brokerPortrait", OpeningBroker.CREST);
        props.put("brokerName", AgreusArcCampaign.NAME);
        props.put("brokerRole", Strings.get("missionSelectHOUSERUSKKOCOMBINECONCESSION"));
        props.put("brokerIntroduction", p != null && p.warrantCompleted()
                ? Strings.get("missionSelectRuskTheServiceArchiveAndCalibrationLoadAre")
                : p != null && p.completed()
                ? Strings.get("missionSelectRuskNorthWeirIsWorkingRecoverTheLow")
                : AgreusArcCampaign.REFERRAL);
        props.put("brokerProgress", p != null && p.warrantCompleted()
                ? Strings.get("missionSelectNORTHWEIRRESTOREDLIMITEDWARRANTCOMPLETE56000")
                : p != null && p.completed() ? Strings.get("missionSelectNORTHWEIRRESTOREDLIMITEDWARRANTAVAILABLEReviewThe")
                : Strings.format("missionSelectAgreusProgress", accessReason == null || accessReason.isBlank()
                        ? "" : Strings.format("missionSelectAccessSuffix", accessReason)));
    }
    static void putAgreusReferral(Map<String, Object> props) {
        var d = AgreusArcCampaign.destination();
        props.put("missionSummary", Strings.get("missionSelectValeSReferenceAgreusArcadia"));
        props.put("detailKicker", Strings.get("missionSelectOPTIONALAFTERMORROWSHANDOVER"));
        props.put("detailTitle", Strings.format("missionSelectMeet", AgreusArcCampaign.NAME));
        props.put("detailMeta", d == null ? Strings.get("missionSelectAgreusArcadiaAccessPaused") : Strings.format("missionSelectRuskLocation", d.label()));
        props.put("detailFlavor", AgreusArcCampaign.REFERRAL + " " + AgreusArcCampaign.accessReason());
    }

    static void putCorvus(Map<String, Object> props, BrokenSupplyRouteProgress route, Mission selectedMission) {
        props.put("missionSummary", Strings.get("missionSelectCorvusTitle"));
        props.put("brokerPortrait", OpeningBroker.PORTRAIT);
        props.put("brokerName", OpeningBroker.NAME);
        props.put("brokerRole", Strings.get("missionSelectWAYLINECORVUSOPERATIONS"));
        CorvusChapter chapter = selectedMission == null ? null : CorvusWaylineProgress.forMission(selectedMission.id);
        props.put("brokerIntroduction", chapter == CorvusChapter.BLIND_WATCH
                ? Strings.get("missionSelectYourAsharuRecordsGaveUsATarget")
                : chapter == CorvusChapter.COLD_ACCOUNT
                ? Strings.get("missionSelectTheListeningPostIsSilentYourCompany")
                : CorvusWaylineCampaign.introduction(route));
        props.put("brokerProgress", CorvusWaylineCampaign.isBattle(selectedMission)
                ? selectedMission.name + " / " + selectedMission.targetPlanetName : Strings.get("missionSelectSABLEWAKEROUTEINTERDICTION"));
    }

    static void putSupplyRoute(Map<String, Object> props, BrokenSupplyRouteProgress route, Mission selectedMission, boolean accessible) {
        props.put("missionSummary", Strings.get("missionSelectBROKENSUPPLYROUTE"));
        props.put("brokerIntroduction", route == null ? Strings.get("missionSelectMaraVennIonaSerrNeedsTheFreightCorridor") : route.status());
        props.put("brokerProgress", route == null ? Strings.get("missionSelectNEWDISPATCHIONASERR") : Strings.format("missionSelectRoutePayments", NumberFormat.getIntegerInstance().format(route.storyPayment()), NumberFormat.getIntegerInstance().format(route.recurringPayment())));
        if (route != null && !accessible) props.put("brokerIntroduction", Strings.format("missionSelectRoutePaused", route.planetName()));
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
                detailRow("tier", Strings.get("missionSelectOperationScale"), Strings.get("missionSelectTierFIRST_CONTRACT")),
                detailRow("presence", Strings.get("missionSelectFieldPresence"), "1 squad active · reserve held off-map"),
                detailRow("opposition", Strings.get("missionSelectOpposition"), "Regular core · veterans possible"),
                detailRow("lift", Strings.get("missionSelectLiftRequired"), "2 sorties"),
                detailRow("payout", Strings.get("missionSelectPayout"), "20,500 credits")));
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
                    ? Strings.get("missionSelectUnavailable")
                    : client.repLevel != null ? client.repLevel.getDisplayName() : "";
            if (OpeningBroker.isContact(client)) status = Strings.get("missionSelectIndependentBroker");
            rows.add(new ClientRow(base, base + "-name", base + "-rep",
                    client.crestPath,
                    "client-card" + (isSelected ? " selected" : "")
                            + (client.locked ? " locked" : ""),
                    client.displayName, status, client.locked,
                    () -> selectClient(client)));
        }
        return List.copyOf(rows);
    }

    static List<MissionRow> missionRows(List<Mission> missions, Mission selected, Consumer<Mission> select) {
        List<MissionRow> rows = new ArrayList<>();
        NumberFormat credits = NumberFormat.getIntegerInstance();
        for (int i = 0; i < missions.size(); i++) {
            Mission mission = missions.get(i);
            String base = "mission-" + i;
            boolean isSelected = selected != null && selected.id.equals(mission.id);
            int recommended = MissionForceEnvelope.recommendedSquads(mission);
            String active = mission.fieldPresencePolicy.limited()
                    ? Strings.format("missionSelectActiveLimit", mission.fieldPresencePolicy.activeSquadLimit())
                    : "";
            int multiplier = mission.cashMultiplier & 0xFF;
            if (multiplier <= 0) multiplier = 100;
            long payout = (long) mission.payout * multiplier / 100L;
            rows.add(new MissionRow(base, base + "-glyph", base + "-title",
                    base + "-meta", base + "-payout",
                    "mission-card " + typeClass(mission.type)
                            + (isSelected ? " selected" : ""),
                    Character.toString(mission.type.glyph), mission.name,
                    Strings.format("missionSelectRowMeta", mission.source == MissionSource.RECURRING
                            ? Strings.get("missionSelectRepeatable") : mission.source == MissionSource.STORY
                            ? Strings.get("missionSelectStory") : tierName(mission.tier), recommended, active),
                    ShepherdRecoveryProgress.workingPassage(mission.id) ? "MORROW"
                            : SupplyRouteCampaign.freight(mission) ? Strings.format("missionSelectMaximumCredits", credits.format(payout)) : Strings.format("missionSelectCompactCredits", credits.format(payout)), () -> select.accept(mission)));
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
                ? Strings.get("missionSelectGarrisonContract") : Strings.get("missionSelectCadreContract");
        return List.of(new StationingRow("stationing-contract",
                "stationing-contract-title", "stationing-contract-action",
                title, active >= 0L ? Strings.get("missionSelectManageAssignment") : Strings.get("missionSelectConfigureAssignment"),
                () -> configureStationing(contractId)));
    }

    /**
     * The polity's two rows. A posting is not offered and not accepted — there is no
     * OFFERED row to find, so the row exists whether or not anything is posted yet and
     * the draft sentinel carries "post here" into the stationing screen. Beside it sits
     * the colony's own ground doctrine ({@code polity-ground-doctrine.md}), which is
     * about the polity's troops rather than about the company's.
     */
    private List<StationingRow> postingRows() {
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null || context == null || context.market == null) return List.of();
        CampaignState state = script.state();
        int row = Posting.activeRowAt(state,
                state.marketRegistry.intern(context.market.getId()));
        long contractId = row >= 0 ? state.contractId[row] : Posting.DRAFT_CONTRACT_ID;
        return List.of(
                new StationingRow("stationing-contract",
                        "stationing-contract-title", "stationing-contract-action",
                        Strings.get("postingTitle"),
                        Strings.get(row >= 0 ? "postingRowManage" : "postingRowCreate"),
                        () -> configureStationing(contractId)),
                new StationingRow("polity-doctrine-row",
                        "polity-doctrine-row-title", "polity-doctrine-row-action",
                        Strings.get("polityDoctrineTitle"),
                        Strings.get("polityDoctrineRowAction"),
                        this::openPolityDoctrine));
    }

    static void putDetail(Map<String, Object> props, Mission mission, Runnable brief, Runnable collapse) {
        boolean present = mission != null;
        StoryScene scene = StoryScene.forMission(mission);
        props.put("detailImageClasses", scene == null ? "mission-detail-image-wrap surface-dark"
                : "mission-detail-image-wrap story-location surface-dark");
        props.put("detailImage", scene == null ? props.get("planetImage") : scene.image);
        props.put("detailHeader", scene == null ? Strings.get("missionSelectDossier") : Strings.format("missionSelectLocationFile", scene.location));
        props.put("detailClasses", "mission-detail panel" + (present ? "" : " empty-detail"));
        props.put("detailKicker", present
                ? Strings.format("missionSelectOperationRisk", operationName(mission), Strings.get(mission.risk.displayKey))
                : Strings.get("missionSelectMISSIONDOSSIER"));
        props.put("detailTitle", present ? mission.name : Strings.get("missionSelectSelectAContract"));
        props.put("detailMeta", present
                ? Strings.format("missionSelectRecommendation", tierName(mission.tier), MissionForceEnvelope.recommendedSquads(mission))
                : Strings.get("missionSelectChooseARowInTheContractList"));
        props.put("detailFlavor", present ? mission.flavor :
                Strings.get("missionSelectTheSelectedOperationSScaleFieldPresence"));
        MissionDialogue.put(props, "detail", mission);
        props.put("detailRows", present ? detailRows(mission) : List.of());
        props.put("briefLabel", Strings.get("missionSelectBRIEFDEPLOY"));
        props.put("briefDisabled", !present);
        props.put("briefAction", brief);
        props.put("declineDisabled", !present);
        props.put("declineAction", collapse);
    }

    private static List<DetailRow> detailRows(Mission mission) {
        if (AgreusArcCampaign.isBattle(mission) && AgreusArcProgress.limitedWarrant(mission.id)) return List.of(
                detailRow("force", Strings.get("missionSelectCompany"), Strings.get("missionSelect2FullRegularSquadsRecommendedMinimum4Ready")),
                detailRow("support", Strings.get("missionSelectRecoveryParty"), Strings.get("missionSelectOwnedTender4ConstablesAnd1SupplierTechnician")),
                detailRow("claim", Strings.get("missionSelectLowQuay"), Strings.get("missionSelectAuthenticateRecordsRecoverTheCalibrationKitEscortIts")),
                detailRow("terms", Strings.get("missionSelectLimitedWarrant"), Strings.get("missionSelectDeployAcceptsThePrintedRecoveryTermsNoDebt")),
                detailRow("payout", Strings.get("missionSelectVictoryAward"), Strings.get("missionSelect30000CreditsRecoveredSpecterCardOnceExplicit")));
        if (AgreusArcCampaign.isBattle(mission)) return List.of(
                detailRow("force", Strings.get("missionSelectCompany"), Strings.get("missionSelect2SquadsMinimum4ReadyEachYour")),
                detailRow("support", Strings.get("missionSelectConstruction"), Strings.get("missionSelectActualOwnedEligibleTenderRequired8Constables")),
                detailRow("claim", Strings.get("missionSelectNorthWeir"), Strings.get("missionSelectEscortRepairCargoReconnectBothRoomsBuild")),
                detailRow("payout", Strings.get("missionSelectVictoryFee"), Strings.get("missionSelect26000CreditsOnceNoSuitAward")));
        if(ShepherdRecoveryCampaign.isBattle(mission)&&ShepherdRecoveryProgress.workingPassage(mission.id))return List.of(
                detailRow("force",Strings.get("missionSelectCompany"),Strings.get("missionSelect2SquadsYourFittedBoatsOptionalOwned")),
                detailRow("support",Strings.get("missionSelectCooperative"),Strings.get("missionSelect8MilitiaHoldCapturedPositionsMorrowBuilds")),
                detailRow("claim",Strings.get("missionSelectDeparture"),Strings.get("missionSelectPumpBendNarrowsDefendedClimbOut")),
                detailRow("payout",Strings.get("missionSelectPaymentInKind"),Strings.get("missionSelectMorrowShepherdConstructionTenderWillingMinimumCrew")));
        if(ShepherdRecoveryCampaign.isBattle(mission))return List.of(
                detailRow("force",Strings.get("missionSelectCompany"),Strings.get("missionSelect2SquadsRecommendedOptionalOwnedMech")),
                detailRow("support",Strings.get("missionSelectCooperative"),ShepherdRecoveryProgress.coldStart(mission.id)
                        ? Strings.get("missionSelect14Militia2RepairTechnicians") : Strings.get("missionSelect8Militia2InspectionTechnicians")),
                detailRow("claim",Strings.get("missionSelectAgreement"),ShepherdRecoveryProgress.coldStart(mission.id)
                        ? Strings.get("missionSelectClaimProtectedCompleteRepairsAndDepartureTo")
                        : Strings.get("missionSelect18000CrInspection22000Cr")),
                detailRow("payout",Strings.get("missionSelectPaymentOnVictory"),Strings.format("missionSelectCredits", NumberFormat.getIntegerInstance().format(mission.payout))));
        if (CorvusWaylineCampaign.isBattle(mission)) return List.of(
                detailRow("force", Strings.get("missionSelectCompanyCommitment"), Strings.format("missionSelectCorvusForce", MissionForceEnvelope.recommendedSquads(mission))),
                detailRow("lift", Strings.get("missionSelectCompanyLift"), Strings.get("missionSelect14FittedBoatsTripsFollowThe")),
                detailRow("opposition", Strings.get("missionSelectSableWakeCrew"), Strings.get("missionSelectOpposition" + mission.risk.name())),
                detailRow("payout", Strings.get("missionSelectPaymentOnVictory"), Strings.format("missionSelectCredits", NumberFormat.getIntegerInstance().format(mission.payout))));
        if (SupplyRouteCampaign.owns(mission.id)) return List.of(
                detailRow("force", Strings.get("missionSelectCompanyCommitment"), Strings.get("missionSelect2SquadsRecommendedAtLeast4Ready")),
                detailRow("lift", Strings.get("missionSelectCompanyLift"), Strings.get("missionSelect14FittedBoatsTripsFollowThe")),
                detailRow("opposition", Strings.get("missionSelectOpposition"), Strings.get("missionSelectLocalRaidersNoFighterOrMechSupport")),
                detailRow("payout", Strings.get("missionSelectPaymentOnVictory"), SupplyRouteCampaign.freight(mission) ? Strings.get("missionSelect60004000PerDeliveredCarrier")
                        : Strings.format("missionSelectCredits", NumberFormat.getIntegerInstance().format(mission.payout))));
        int multiplier = mission.cashMultiplier & 0xFF;
        if (multiplier <= 0) multiplier = 100;
        long payout = (long) mission.payout * multiplier / 100L;
        boolean opening = OpeningOperationKind.fromMission(mission) != null;
        String lift = opening
                ? Strings.format("missionSelectOpeningLift", Math.max(0, mission.requiredDrops - mission.employerShuttles), mission.employerShuttles)
                : Strings.format("missionSelectSorties", mission.requiredDrops);
        return List.of(
                detailRow("tier", Strings.get("missionSelectOperationScale"), tierName(mission.tier)),
                detailRow("presence", Strings.get("missionSelectFieldPresence"), fieldPresence(mission.fieldPresencePolicy)),
                detailRow("opposition", Strings.get("missionSelectOpposition"), opening ? Strings.get("missionSelectLocalBanditMilitia") : Strings.get("missionSelectOpposition" + mission.risk.name())),
                detailRow("lift", Strings.get("missionSelectLiftRequired"), lift),
                detailRow("payout", Strings.get("missionSelectPayout"), Strings.format("missionSelectCredits", NumberFormat.getIntegerInstance().format(payout))));
    }

    private static DetailRow detailRow(String key, String label, String value) {
        String base = "detail-" + key;
        return new DetailRow(base, base + "-label", base + "-value", label, value);
    }

    private static Map<String, Object> baseProps() {
        Map<String, Object> props = new LinkedHashMap<>();
        MissionDialogue.put(props, "detail", null);
        putCompanyNews(props, List.of(), () -> { });
        putBroker(props, null);
        props.put("briefLabel", Strings.get("missionSelectBRIEFDEPLOY"));
        props.put("screenTitle", Strings.get("missionSelectMarineOperations"));
        props.put("clientHeader", Strings.get("missionSelectClients"));
        props.put("catalogHeader", Strings.get("missionSelectContracts"));
        props.put("detailHeader", Strings.get("missionSelectDossier"));
        props.put("detailImageClasses", "mission-detail-image-wrap surface-dark");
        props.put("detailImage", null);
        return props;
    }

    static void putCompanyNews(Map<String, Object> props, List<CompanyNotices.Notice> notices, Runnable open) {
        props.put("companyNewsClasses", notices.isEmpty() ? "company-news hidden" : "company-news console-action");
        props.put("companyNewsLabel", CompanyNotices.summary(notices));
        props.put("companyNewsAction", open);
    }

    static void putReferral(Map<String, Object> props, OpeningBroker.Destination office, String referral) {
        props.put("missionEmpty", Strings.format("missionSelectOfficeReferral", office.label()));
        props.put("missionSummary", Strings.format("missionSelectReferralHeader", OpeningBroker.NAME));
        props.put("detailKicker", Strings.get("missionSelectFIRSTCONTRACTLEAD"));
        props.put("detailTitle", Strings.format("missionSelectMeet", OpeningBroker.NAME));
        props.put("detailMeta", Strings.format("missionSelectBrokerLocation", office.label()));
        props.put("detailFlavor", referral);
    }

    static void putBroker(Map<String, Object> props, Client client) {
        putBroker(props, client, null);
    }

    static void putBroker(Map<String, Object> props, Client client, MarineRoster roster) {
        boolean contact = OpeningBroker.isContact(client);
        props.put("brokerClasses", contact ? "broker-dossier console-readout" : "broker-hidden");
        props.put("brokerPortrait", contact ? OpeningBroker.PORTRAIT : null);
        props.put("brokerName", contact ? OpeningBroker.NAME : "");
        props.put("brokerRole", contact ? Strings.get("missionSelectINDEPENDENTWAYLINECOOPERATIVE") : "");
        props.put("brokerIntroduction", contact ? WaylineArc.introduction(roster) : "");
        props.put("brokerProgress", contact ? WaylineArc.progress(roster) : "");
    }

    private String locationLabel() {
        if (context == null) return "";
        String market = context.market != null ? context.market.getName() : null;
        String planet = context.planet != null ? context.planet.getName() : null;
        String location = market != null ? market : planet != null ? planet : Strings.get("missionSelectUnknownMarket");
        return Strings.format("missionSelectLocalOffers", location);
    }

    static String missionSummary(Client client, List<Mission> missions) {
        if (client == null) return Strings.get("missionSelectChooseAClientToInspectCurrentWork");
        if (OpeningBroker.isContact(client)) return Strings.get("missionSelectWaylineTitle");
        if (MarineOpsContext.DEBUG_CLIENT_FACTION_ID.equals(client.factionId)) {
            return Strings.format("missionSelectDebugCount", missions.size());
        }
        if (MarineOpsContext.isPolityClient(client)) {
            // A polity row is a live raid and nothing else, so a non-empty list is the
            // one thing worth saying before the player opens it.
            return Strings.get(missions == null || missions.isEmpty()
                    ? "postingClientSummary" : "postingClientSummaryRaid");
        }
        return Strings.format("missionSelectOfferCount", missions.size());
    }

    private static String operationName(Mission mission) {
        if (!SupplyRouteCampaign.owns(mission.id)) return Strings.get(mission.type.displayKey);
        return Strings.get(SupplyRouteCampaign.freight(mission)
                ? "missionSelectOperationFreight" : "missionSelectOperationCapture");
    }

    private static String fieldPresence(FieldPresencePolicy policy) {
        return policy.limited() ? Strings.format("missionSelectFieldReserve", policy.activeSquadLimit())
                : Strings.get("missionSelectFieldUnrestricted");
    }

    private static String tierName(OperationTier tier) {
        return Strings.get("missionSelectTier" + tier.name());
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
        if (MarineOpsContext.DEBUG_CLIENT_FACTION_ID.equals(client.factionId)) {
            context.goTo(ScreenId.DEBUG_MISSIONS);
            return;
        }
        context.setSelectedClient(client);
        context.setSelectedMission(null);
        installDocument();
    }

    private void selectMission(Mission mission) {
        if (context == null) return;
        if (mission != null && SupplyRouteCampaign.owns(mission.id) && !SupplyRouteCampaign.introduce(context.planet)) return;
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

    private void openPolityDoctrine() {
        if (context == null) return;
        context.setSelectedMission(null);
        context.goTo(ScreenId.POLITY_DOCTRINE);
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

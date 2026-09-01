package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.DevConfig;
import com.dillon.starsectormarines.battle.flyby.DebugAirRoster;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.flyby.PlayerFleetWings;
import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.campaign.CampaignCommodityPresentation;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.CivilWarOfferAcceptance;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.ContractEligibility;
import com.dillon.starsectormarines.campaign.PlanetaryAssaultTerms;
import com.dillon.starsectormarines.campaign.systems.RivalStrikeGarrisonService;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.ops.detachment.CaptainDeploymentPolicy;
import com.dillon.starsectormarines.ops.detachment.PersonnelReadiness;
import com.dillon.starsectormarines.ops.detachment.MissionForceEnvelope;
import com.dillon.starsectormarines.ops.detachment.TaskForce;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarinePersonnelLogistics;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.detachment.CommandDeck;
import com.dillon.starsectormarines.ops.detachment.DebugCompany;
import com.dillon.starsectormarines.ops.detachment.DebugCompanyStage;
import com.dillon.starsectormarines.ops.detachment.PlayerFleetPowerSources;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.SpriteThumbWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiViewport;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.starsector.StarsectorUiInputAdapter;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.PositionAPI;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glLineWidth;
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * The canonical pre-battle surface (see {@code command-powers-nouns.md}). Reached from
 * the mission-select list via the dossier card's <em>Brief &amp; Deploy</em>
 * action ({@link CommsConsolePanel}).
 *
 * <p>The full-canvas two-column layout's left "mission" column holds the
 * briefing details, salvage negotiation, and captain selection; the right
 * "detachment" column splits into <em>Your Fleet Brings</em> (the player's
 * committed transports + fighter cover + power-source ships, opt-in toggles) and <em>Employer
 * Provides</em> (the contract's shuttles / fighter support / offered powers,
 * read-only), with Deploy / Back below. The old decorative planet map is gone —
 * its space is reclaimed for the action area.
 *
 * <p>Deploy resolves the committed detachment and launches the battle via
 * {@link MissionLaunch#buildSimulation} → {@link ScreenId#BATTLE}. Back returns
 * to {@link ScreenId#MISSION_SELECT}; client + cache state on the context
 * survive the trip.
 */
public class BriefingScreen implements Screen {

    private static final Logger LOG = Global.getLogger(BriefingScreen.class);
    static final String ROOT_COMPONENT = "mission-briefing";
    static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/missions/mission-briefing.mlx");

    private static final Color FRAME_COLOR   = new Color(0x4A, 0x6B, 0x8C);
    private static final Color HEADER_COLOR  = new Color(0xC8, 0xE0, 0xFF);
    private static final Color LABEL_COLOR   = new Color(0x8F, 0xA8, 0xC0);
    private static final Color VALUE_COLOR   = new Color(0xE0, 0xE8, 0xFF);
    private static final Color FLAVOR_COLOR  = new Color(0xC0, 0xD0, 0xE8);
    private static final Color ACCEPT_COLOR  = new Color(0xC8, 0xFF, 0xE0);
    /** Red used for the Transport row + Deploy label when the player can't actually fly the mission. */
    private static final Color BLOCKED_COLOR = new Color(0xFF, 0x80, 0x80);

    private static final float INNER_PAD   = 12f;
    private static final float ROW_GAP     = 28f;
    private static final float LABEL_COL_W = 96f;
    private static final float BTN_H       = 32f;
    private static final float BTN_GAP     = 12f;
    private static final float SECTION_GAP = 16f;
    private static final float SQUAD_ROW_H = 32f;
    private static final float SQUAD_ROW_GAP = 4f;
    private static final int DEBUG_MAX_CONQUEST_DROP_ZONES = 4;
    private static final int DEBUG_MAX_CONQUEST_PAIRS_PER_ZONE = 4;
    private static final float DEBUG_CONQUEST_JITTER_STEP_SEC = 0.25f;
    private static final float DEBUG_MAX_CONQUEST_JITTER_SEC = 3f;
    /** Ship-sprite thumbnail box at the left of each fleet (transport / carrier) row. */
    private static final float THUMB = 24f;

    /** Top-y of the flavor paragraph in the left column, cached for renderFlavor. */
    private float flavorY;
    /** Wrap width for the flavor paragraph (left column width minus pads). */
    private float flavorW;
    /** Left edge of the flavor paragraph. */
    private float flavorX;

    private final WidgetRoot widgets = new WidgetRoot();
    private final Reactor reactor = new Reactor();
    private final MarkupLoader markup = new MarkupLoader(
            path -> Global.getSettings().loadText(path), COMPONENT_PATHS);

    private PositionAPI position;
    private MarineOpsContext ctx;
    private BriefingLayout layout;
    private UiViewport viewport;
    private UiDocument document;
    private MarkupInstance markupInstance;
    private StarsectorUiInputAdapter input;

    private String lastSelectedMissionId;
    /**
     * Snapshot of the ship's boats taken at the start of
     * each {@link #rebuild()}. Indices are stable within a single briefing layout,
     * so the deselection set keeps referring to the same ships even as rows are redrawn.
     */
    private List<FittedBoat> cachedAvailable = java.util.Collections.emptyList();

    /**
     * Synthetic transport configuration used only by debug missions. Unlike the
     * production fleet rows, this is an exact roster: every selected transport
     * has this type, and the count is capped to the mission's drop count so each
     * configured hull appears in the battle manifest.
     */
    private ShuttleType debugTransportType = ShuttleType.VALKYRIE;
    private int debugTransportCount = 1;
    private ConquestArrivalConfig debugConquestArrivalConfig =
            ConquestArrivalConfig.DEFAULT;

    /**
     * Debug mission Mech Support roster controls; stable across ordinary
     * rebuilds. Seeded from the debug company's stage and re-seeded whenever
     * the stage changes, so supporting arms move with the campaign point —
     * the picker still overrides, since it is the mech-family testing tool.
     */
    private int debugMechCount = DebugCompanyStage.FIRST_CONTRACT.mechs;
    private int debugMechRoll;

    /**
     * Indices into {@link #cachedCarriers} the player has deselected for the
     * current mission's fighter cover. Default empty = all carriers committed.
     * Reset when the mission changes.
     */
    private final java.util.Set<Integer> deselectedCarriers = new java.util.HashSet<>();
    /** Snapshot of {@link PlayerFleetWings#committableCarriers()} taken per {@link #rebuild()}, so toggle indices stay stable across a layout. */
    private List<PlayerFleetWings.CarrierBay> cachedCarriers = java.util.Collections.emptyList();
    /** Stable fleet-member ids held back as command-power sources. */
    private final java.util.Set<String> deselectedPowerSources = new java.util.HashSet<>();
    /** Fleet members that can source at least one command power. */
    private List<PlayerFleetPowerSources.SourceShip> cachedPowerSources = java.util.Collections.emptyList();
    /** Power roster available from the committed source ships plus employer. */
    private List<CommandPower> cachedAvailablePowers = java.util.Collections.emptyList();
    /** Stable ids selected into the current mission's command deck. */
    private final java.util.Set<String> selectedPowerIds = new java.util.LinkedHashSet<>();
    private boolean commandDeckInitialized;
    /** Developer controls are opt-in and never consume briefing width on entry. */
    private boolean debugDrawerExpanded;

    /**
     * Debug aircraft-picker selections — keys {@code "<SIDE>|<PROFILE>"} (e.g.
     * {@code "DEFENDER|TALON"}). Each toggled-on pair force-spawns a debug wing on
     * that side at deploy. Gated behind {@link DevConfig#DEBUG_AIRCRAFT_PICKER};
     * intentionally NOT reset on mission switch — it's a test config, not mission
     * state, so a chosen calibration setup carries across missions.
     */
    private final java.util.Set<String> debugAirSelections = new java.util.HashSet<>();

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        boolean reactivating = input == null;
        this.position = position;
        this.ctx = ctx;
        viewport = MarineOpsUiViewport.from(position);
        if (document == null || reactivating) rebuild();
        else document.layout(viewport.documentWidth(), viewport.documentHeight());
        input = new StarsectorUiInputAdapter(document, viewport);
    }

    private void rebuild() {
        if (ctx == null) return;
        MarineRosterScript personnel = MarineRosterScript.getInstance();
        if (personnel != null) personnel.ensureStartingCompany();

        // Default to the first ACTIVE captain if nothing's selected yet — saves
        // a click for the common case. User's pick survives across re-attaches.
        if (ctx.getSelectedCaptainId() == null) {
            MarineRosterScript script = MarineRosterScript.getInstance();
            if (script != null) {
                List<MarineCaptain> active = script.roster().active();
                if (!active.isEmpty()) ctx.setSelectedCaptainId(active.get(0).id());
            }
        }

        Mission m = ctx.getSelectedMission();
        initializeCaptainFormation(m);

        // Selection scope is per-mission — clear the deselection set when the
        // player switches missions so they don't carry over hidden state.
        if (m != null && !m.id.equals(lastSelectedMissionId)) {
            lastSelectedMissionId = m.id;
            deselectedCarriers.clear();
            deselectedPowerSources.clear();
            selectedPowerIds.clear();
            commandDeckInitialized = false;
            debugConquestArrivalConfig = m.conquestArrivalConfig();
        }
        // Snapshot the available transports + carriers once per build so toggle indices are stable.
        // Debug missions use an exact synthetic roster controlled by the picker;
        // production missions continue to reflect only the real player fleet.
        // The lift is the boats aboard the ship the company is on, not transports
        // shopped for out of the fleet. A debug briefing still owns its own
        // roster through its picker, which is the one place a synthetic set of
        // craft is the point.
        cachedAvailable = m != null && m.source.isDebug()
                ? debugTransportRoster(m)
                : ShipsBoatsAboard.lift();
        cachedCarriers = PlayerFleetWings.committableCarriers();
        cachedPowerSources = PlayerFleetPowerSources.committableShips();
        cachedAvailablePowers = availablePowers(m);
        java.util.Set<String> availableIds = new java.util.HashSet<>();
        for (CommandPower power : cachedAvailablePowers) availableIds.add(power.id);
        selectedPowerIds.retainAll(availableIds);
        if (!commandDeckInitialized) {
            selectedPowerIds.addAll(CommandDeck.defaultSelection(cachedAvailablePowers));
            commandDeckInitialized = true;
        }

        installDocument();
    }

    private void installDocument() {
        MarkupInstance candidate = markup.reloadAndBuild(
                reactor, ROOT_COMPONENT, retainedProps());
        UiDocument built;
        try {
            requireRetainedElements(candidate);
            built = new UiDocument(candidate.root());
            for (var style : candidate.styles()) built.addStyleSheet(style);
            built.theme(MarineOpsThemes.standard()).onCancel(this::onBack);
            if (viewport != null) built.layout(
                    viewport.documentWidth(), viewport.documentHeight());
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

    private Map<String, Object> retainedProps() {
        Map<String, Object> props = BriefingViewModel.baseProps();
        Mission mission = ctx != null ? ctx.getSelectedMission() : null;
        if (mission == null) {
            props.putAll(BriefingViewModel.previewProps(false));
            props.put("missionTitle", "No mission selected");
            props.put("deployDisabled", true);
            props.put("deployAction", (Runnable) () -> { });
            props.put("backAction", (Runnable) this::onBack);
            return props;
        }

        props.put("missionKicker", mission.type.name() + "  ·  "
                + mission.risk.name() + " RISK  ·  " + mission.tier.displayName);
        props.put("missionTitle", mission.name);
        props.put("missionFlavor", mission.flavor != null ? mission.flavor : "");
        props.put("missionRows", retainedMissionRows(mission));
        props.put("termActions", retainedTermActions(mission));
        props.put("captains", retainedCaptainRows(mission));
        props.put("captainEmpty", retainedCaptainRows(mission).isEmpty()
                ? Strings.get("briefingNoCaptains") : "");

        boolean debug = mission.source.isDebug();
        props.put("debugDrawerClasses", !debug ? "debug-drawer absent"
                : "debug-drawer " + (debugDrawerExpanded ? "expanded" : "collapsed"));
        props.put("debugWorkspaceClasses", debug && debugDrawerExpanded
                ? "panel debug-workspace"
                : "debug-workspace debug-workspace-collapsed");
        props.put("debugToggleClasses", debug
                ? "debug-drawer-toggle" : "debug-drawer-toggle-hidden");
        props.put("debugToggleLabel", debugDrawerExpanded ? "HIDE" : "DEBUG");
        props.put("debugToggleDisabled", !debug);
        props.put("debugToggleAction", debug
                ? (Runnable) this::toggleDebugDrawer : (Runnable) () -> { });
        props.put("tierSummary", debug
                ? mission.tier.displayName + "  ·  "
                        + MissionForceEnvelope.recommendedSquads(mission) + " squads  ·  "
                        + mission.requiredDrops + " sorties"
                : "");
        props.put("tierSteps", debug ? retainedTierSteps(mission) : List.of());
        props.put("debugControls", debug ? retainedDebugControls(mission) : List.of());
        props.put("airRows", debug && DevConfig.DEBUG_AIRCRAFT_PICKER
                ? retainedAirRows() : List.of());

        PersonnelReadiness readiness = !debug ? personnelReadiness(mission) : null;
        props.put("personnelSummary", debug
                ? ctx.getDebugCompanyStage().summary(ctx.getDebugSquadCount())
                : retainedPersonnelSummary(mission, readiness));
        props.put("personnelClasses", "label commitment-summary "
                + (debug || readiness.ready() ? "tone-good" : "tone-danger"));
        props.put("experienceSummary", debug
                ? "DEBUG fixture company  ·  roster is not consumed"
                : "Issued experience  ·  " + MissionForceEnvelope.selectedExperience(
                        liveRoster(), ctx.getSelectedMarineSquadIds()).display());
        props.put("taskForceRows", !debug && mission.source != MissionSource.STATIONING
                ? retainedTaskForceRows() : List.of());

        int used = CommandDeck.used(cachedAvailablePowers, selectedPowerIds);
        props.put("commandSummary", used + " / " + CommandDeck.BUDGET + " slots");
        props.put("powerRows", retainedPowerRows());
        props.put("powerEmpty", cachedAvailablePowers.isEmpty()
                ? "No powers available from fleet or employer." : "");
        props.put("sourceRows", retainedSourceRows());
        props.put("sourceEmpty", cachedPowerSources.isEmpty()
                ? "No fleet ships provide command powers." : "");
        props.put("transportRows", retainedTransportRows(mission));
        props.put("carrierRows", retainedCarrierRows());
        props.put("employerRows", retainedEmployerRows(mission));
        putRetainedActions(props, mission, readiness);
        return props;
    }

    static Map<String, Object> previewProps(boolean conquest) {
        return BriefingViewModel.previewProps(conquest);
    }

    static Map<String, Object> previewProps(boolean conquest, boolean debugExpanded) {
        return BriefingViewModel.previewProps(conquest, debugExpanded);
    }

    private void toggleDebugDrawer() {
        debugDrawerExpanded = !debugDrawerExpanded;
        rebuild();
    }

    private List<BriefingViewModel.InfoRow> retainedMissionRows(Mission mission) {
        List<BriefingViewModel.InfoRow> rows = new ArrayList<>();
        rows.add(BriefingViewModel.info("type", "Type",
                Strings.get(mission.type.displayKey), "tone-accent"));
        rows.add(BriefingViewModel.info("risk", "Risk",
                Strings.get(mission.risk.displayKey), ""));
        int cashMult = mission.cashMultiplier & 0xFF;
        if (cashMult <= 0) cashMult = 100;
        long payout = (long) mission.payout * cashMult / 100L;
        rows.add(BriefingViewModel.info("payout", "Payout",
                NumberFormat.getIntegerInstance().format(payout) + " credits", "tone-good"));
        int salvageBaseline = mission.contractSalvageBaseline & 0xFF;
        if (salvageBaseline > 0) {
            int negotiated = mission.contractSalvageNegotiated & 0xFF;
            rows.add(BriefingViewModel.info("salvage", "Salvage rights",
                    negotiated + "%  ·  cash " + signedPercent(cashMult - 100), ""));
        }
        rows.add(BriefingViewModel.info("requirements", "Requires",
                mission.requirements, ""));
        int recommended = MissionForceEnvelope.recommendedSquads(mission);
        rows.add(BriefingViewModel.info("scale", "Operation scale",
                mission.tier.displayName + "  ·  recommends " + recommended
                        + (recommended == 1 ? " squad" : " squads"), "tone-accent"));
        rows.add(BriefingViewModel.info("presence", "Field presence",
                mission.fieldPresencePolicy.briefingText(), ""));
        rows.add(BriefingViewModel.info("opposition", "Opposition",
                MissionForceEnvelope.oppositionExpectation(mission.risk), ""));
        rows.add(BriefingViewModel.info("enemy-air", "Enemy air",
                summarizeWings(mission.enemyFighterSupport, Faction.DEFENDER), ""));
        return List.copyOf(rows);
    }

    private List<BriefingViewModel.ChoiceRow> retainedCaptainRows(Mission mission) {
        MarineRosterScript script = MarineRosterScript.getInstance();
        List<MarineCaptain> captains;
        if (mission.source == MissionSource.STATIONING) {
            MarineCaptain selected = ctx.getSelectedCaptain();
            captains = selected == null ? List.of() : List.of(selected);
        } else {
            captains = script != null ? script.roster().active() : List.of();
        }
        List<BriefingViewModel.ChoiceRow> rows = new ArrayList<>();
        for (int i = 0; i < captains.size(); i++) {
            MarineCaptain captain = captains.get(i);
            boolean selected = captain.id().equals(ctx.getSelectedCaptainId());
            rows.add(new BriefingViewModel.ChoiceRow("captain-" + i,
                    "choice-row" + (selected ? " selected" : ""),
                    captain.name(), captain.rank().displayName() + "  ·  command cap "
                            + captain.rank().squadCommandCap() + " squads",
                    false, () -> selectCaptain(captain, mission), captain.portraitSprite()));
        }
        return List.copyOf(rows);
    }

    private List<BriefingViewModel.ChoiceRow> retainedTermActions(Mission mission) {
        int baseline = mission.contractSalvageBaseline & 0xFF;
        if (baseline <= 0 || !negotiationOpen(mission)) return List.of();
        int current = mission.contractSalvageNegotiated & 0xFF;
        return List.of(
                new BriefingViewModel.ChoiceRow("briefing-salvage-less", "choice-row",
                        "TRADE SALVAGE FOR CASH", "-10 salvage rights  ·  higher payout",
                        current <= 0, () -> adjustSalvage(-10), null),
                new BriefingViewModel.ChoiceRow("briefing-salvage-more", "choice-row",
                        "TAKE MORE SALVAGE", "+10 salvage rights  ·  lower payout",
                        current >= baseline, () -> adjustSalvage(10), null));
    }

    private void selectCaptain(MarineCaptain captain, Mission mission) {
        ctx.setSelectedCaptainId(captain.id());
        initializeCaptainFormation(mission);
        rebuild();
    }

    private List<BriefingViewModel.TierStep> retainedTierSteps(Mission mission) {
        List<BriefingViewModel.TierStep> steps = new ArrayList<>();
        OperationTier[] tiers = OperationTier.values();
        String[] numerals = { "I", "II", "III", "IV", "V" };
        for (int i = 0; i < tiers.length; i++) {
            OperationTier tier = tiers[i];
            boolean disabled = !tier.atLeast(mission.type.tierFloor);
            boolean selected = tier == mission.tier;
            steps.add(new BriefingViewModel.TierStep("briefing-tier-" + i,
                    "tier-step" + (selected ? " selected" : "")
                            + (disabled ? " locked" : ""),
                    numerals[i], tier.displayName, disabled,
                    () -> adjustDebugOperationTier(tier)));
        }
        return List.copyOf(steps);
    }

    private List<BriefingViewModel.DebugControl> retainedDebugControls(Mission mission) {
        List<BriefingViewModel.DebugControl> controls = new ArrayList<>();
        int squads = ctx.getDebugSquadCount();
        controls.add(new BriefingViewModel.DebugControl("debug-company-squads",
                "Company squads", Integer.toString(squads), "-10", "-", "+", "+10", "",
                "debug-cycle-absent", squads <= 0, squads <= 0, false, false, true,
                () -> adjustDebugSquadCount(-10), () -> adjustDebugSquadCount(-1),
                () -> adjustDebugSquadCount(1), () -> adjustDebugSquadCount(10),
                () -> { }));

        if (DevConfig.DEBUG_MECH_SUPPORT_PICKER) {
            controls.add(new BriefingViewModel.DebugControl("debug-player-mechs",
                    "Player mechs", Integer.toString(debugMechCount), "-10", "-", "+", "+10", "REROLL",
                    "debug-cycle", debugMechCount <= 0, debugMechCount <= 0, false, false, false,
                    () -> adjustDebugMechCount(-10), () -> adjustDebugMechCount(-1),
                    () -> adjustDebugMechCount(1), () -> adjustDebugMechCount(10),
                    this::rerollDebugMechs));
        }
        if (mission.type == MissionType.CONQUEST) {
            controls.add(conquestControl("debug-drop-zones", "Drop zones",
                    Integer.toString(debugConquestArrivalConfig.dropZoneCount()),
                    debugConquestArrivalConfig.dropZoneCount() <= 1,
                    debugConquestArrivalConfig.dropZoneCount() >= DEBUG_MAX_CONQUEST_DROP_ZONES,
                    () -> adjustDebugConquestDropZones(-1), () -> adjustDebugConquestDropZones(1)));
            controls.add(conquestControl("debug-pairs-zone", "Pairs / zone",
                    Integer.toString(debugConquestArrivalConfig.shuttlePairsPerZone()),
                    debugConquestArrivalConfig.shuttlePairsPerZone() <= 1,
                    debugConquestArrivalConfig.shuttlePairsPerZone() >= DEBUG_MAX_CONQUEST_PAIRS_PER_ZONE,
                    () -> adjustDebugConquestPairsPerZone(-1), () -> adjustDebugConquestPairsPerZone(1)));
            controls.add(conquestControl("debug-arrival-jitter", "Timing jitter",
                    debugConquestArrivalConfig.timingJitterSec() + " s",
                    debugConquestArrivalConfig.timingJitterSec() <= 0f,
                    debugConquestArrivalConfig.timingJitterSec() >= DEBUG_MAX_CONQUEST_JITTER_SEC,
                    () -> adjustDebugConquestJitter(-DEBUG_CONQUEST_JITTER_STEP_SEC),
                    () -> adjustDebugConquestJitter(DEBUG_CONQUEST_JITTER_STEP_SEC)));
        }
        int maxTransports = Math.max(0, mission.requiredDrops);
        controls.add(new BriefingViewModel.DebugControl("debug-transports",
                "Transport · " + debugTransportType.displayName(),
                Integer.toString(debugTransportCount), "<", "-", "+", ">", "TYPE",
                "debug-cycle", false, debugTransportCount <= 0, debugTransportCount >= maxTransports,
                false, false, this::previousDebugTransportType,
                () -> adjustDebugTransportCount(-1, maxTransports),
                () -> adjustDebugTransportCount(1, maxTransports),
                this::nextDebugTransportType, this::nextDebugTransportType));
        return List.copyOf(controls);
    }

    private static BriefingViewModel.DebugControl conquestControl(
            String id, String label, String value, boolean minusDisabled,
            boolean plusDisabled, Runnable minus, Runnable plus) {
        return new BriefingViewModel.DebugControl(id, label, value,
                "", "-", "+", "", "", "debug-cycle-absent",
                true, minusDisabled, plusDisabled, true, true,
                () -> { }, minus, plus, () -> { }, () -> { });
    }

    private void rerollDebugMechs() {
        debugMechRoll++;
        rebuild();
    }

    private List<BriefingViewModel.AirRow> retainedAirRows() {
        List<BriefingViewModel.AirRow> rows = new ArrayList<>();
        for (FighterProfile profile : FighterProfile.values()) {
            String attackKey = Faction.MARINE.name() + "|" + profile.name();
            String defendKey = Faction.DEFENDER.name() + "|" + profile.name();
            boolean attack = debugAirSelections.contains(attackKey);
            boolean defend = debugAirSelections.contains(defendKey);
            rows.add(new BriefingViewModel.AirRow("debug-air-" + profile.name().toLowerCase(),
                    profileDisplayName(profile.name()), attack ? "[x] ATK" : "[ ] ATK",
                    defend ? "[x] DEF" : "[ ] DEF", attack ? "selected" : "",
                    defend ? "selected" : "", () -> toggleDebugAir(attackKey),
                    () -> toggleDebugAir(defendKey)));
        }
        return List.copyOf(rows);
    }

    private void toggleDebugAir(String key) {
        if (!debugAirSelections.remove(key)) debugAirSelections.add(key);
        rebuild();
    }

    private String retainedPersonnelSummary(Mission mission, PersonnelReadiness readiness) {
        if (mission.source == MissionSource.STATIONING) {
            return readiness.selectedReady() + " / " + readiness.requiredSeats()
                    + " ready  ·  " + readiness.selectedShortfall() + " short";
        }
        if (MissionForceEnvelope.allowsUnderstrength(mission)) {
            return readiness.selectedReady() + " ready  ·  minimum "
                    + readiness.requiredSeats() + "  ·  recommend "
                    + MissionForceEnvelope.recommendedSquads(mission) + " squads";
        }
        TaskForce force = selectedTaskForce();
        return readiness.selectedReady() + " / " + readiness.requiredSeats()
                + " selected  ·  " + force.squadCount() + " squads  ·  "
                + force.officerCount() + " officers";
    }

    private List<BriefingViewModel.InfoRow> retainedTaskForceRows() {
        TaskForce force = selectedTaskForce();
        if (force.officerCount() < 2) return List.of();
        List<BriefingViewModel.InfoRow> rows = new ArrayList<>();
        int index = 0;
        for (TaskForce.Element element : force.elements()) {
            boolean bad = !element.fit() || element.overCap();
            String name = element.officer != null
                    ? element.officer.rank().displayName() + " " + element.officer.name()
                    : "Unassigned";
            String detail = element.squads.size() + " squads  ·  " + element.marines
                    + " marines" + (element.inherited ? "  ·  attached" : "");
            rows.add(BriefingViewModel.info("task-force-" + index++, name, detail,
                    bad ? "tone-danger" : ""));
        }
        return List.copyOf(rows);
    }

    private List<BriefingViewModel.ChoiceRow> retainedPowerRows() {
        List<BriefingViewModel.ChoiceRow> rows = new ArrayList<>();
        for (int i = 0; i < cachedAvailablePowers.size(); i++) {
            CommandPower power = cachedAvailablePowers.get(i);
            boolean selected = selectedPowerIds.contains(power.id);
            boolean canAdd = CommandDeck.canAdd(cachedAvailablePowers, selectedPowerIds, power);
            int weight = CommandDeck.weight(power);
            String detail = weight + (weight == 1 ? " slot" : " slots") + "  ·  "
                    + Math.round(power.cpCost) + " CP"
                    + (power.supplyCost > 0 ? "  ·  " + power.supplyCost + " supplies" : "");
            rows.add(new BriefingViewModel.ChoiceRow("briefing-power-" + i,
                    "choice-row" + (selected ? " selected" : ""),
                    power.displayName, detail, !selected && !canAdd,
                    () -> togglePower(power.id), null));
        }
        return List.copyOf(rows);
    }

    private void togglePower(String powerId) {
        if (!selectedPowerIds.remove(powerId)) selectedPowerIds.add(powerId);
        rebuild();
    }

    private List<BriefingViewModel.ChoiceRow> retainedSourceRows() {
        List<BriefingViewModel.ChoiceRow> rows = new ArrayList<>();
        for (int i = 0; i < cachedPowerSources.size(); i++) {
            PlayerFleetPowerSources.SourceShip source = cachedPowerSources.get(i);
            boolean committed = !deselectedPowerSources.contains(source.memberId);
            rows.add(new BriefingViewModel.ChoiceRow("briefing-source-" + i,
                    "choice-row" + (committed ? " selected" : ""),
                    source.shipName, summarizePowers(source.powers), false,
                    () -> togglePowerSource(source.memberId), source.spriteName));
        }
        return List.copyOf(rows);
    }

    private void togglePowerSource(String memberId) {
        if (!deselectedPowerSources.remove(memberId)) deselectedPowerSources.add(memberId);
        rebuild();
    }

    private List<BriefingViewModel.InfoRow> retainedTransportRows(Mission mission) {
        if (mission.source == MissionSource.STATIONING) {
            return List.of(BriefingViewModel.info("transport-local", "Local lifts",
                    Strings.get("briefingStationedLocalLifts"), ""));
        }
        if (mission.source.isDebug()) {
            return List.of(BriefingViewModel.info("transport-debug",
                    debugTransportCount + " × " + debugTransportType.displayName(),
                    debugTransportType.capacity + " seats each  ·  "
                            + mission.requiredDrops + " required sorties",
                    isTransportSufficient(mission, lift()) ? "tone-good" : "tone-danger"));
        }
        if (cachedAvailable.isEmpty()) {
            return List.of(BriefingViewModel.info("transport-none", "No boats aboard",
                    "Mission cannot launch without lift", "tone-danger"));
        }
        List<BriefingViewModel.InfoRow> rows = new ArrayList<>();
        ShuttleType type = cachedAvailable.get(0).pattern();
        rows.add(BriefingViewModel.info("transport-fleet",
                cachedAvailable.size() + " × " + type.displayName(),
                mission.requiredDrops + " required sorties", "tone-good"));
        String carrier = ShipsBoatsAboard.carrier();
        if (carrier != null) rows.add(BriefingViewModel.info(
                "transport-carrier", "Carried by", carrier, ""));
        return List.copyOf(rows);
    }

    private List<BriefingViewModel.ChoiceRow> retainedCarrierRows() {
        List<BriefingViewModel.ChoiceRow> rows = new ArrayList<>();
        for (int i = 0; i < cachedCarriers.size(); i++) {
            final int index = i;
            PlayerFleetWings.CarrierBay carrier = cachedCarriers.get(i);
            boolean committed = !deselectedCarriers.contains(i);
            rows.add(new BriefingViewModel.ChoiceRow("briefing-carrier-" + i,
                    "choice-row" + (committed ? " selected" : ""), carrier.shipName,
                    carrier.bayCount() + (carrier.bayCount() == 1 ? " mapped bay" : " mapped bays"),
                    false, () -> toggleCarrier(index), carrier.spriteName));
        }
        return List.copyOf(rows);
    }

    private void toggleCarrier(int index) {
        if (!deselectedCarriers.remove(index)) deselectedCarriers.add(index);
        rebuild();
    }

    private List<BriefingViewModel.InfoRow> retainedEmployerRows(Mission mission) {
        List<BriefingViewModel.InfoRow> rows = new ArrayList<>();
        rows.add(BriefingViewModel.info("employer-transport", "Transport",
                mission.source.isDebug() ? "Overridden by DEBUG picker"
                        : mission.employerShuttles > 0
                                ? mission.employerShuttles + " Aeroshuttle sorties"
                                : Strings.get("briefingAirNone"), ""));
        rows.add(BriefingViewModel.info("employer-air", "Allied air",
                summarizeWings(mission.clientFighterSupport, Faction.MARINE), ""));
        if (mission.employerPowerIds != null && !mission.employerPowerIds.isEmpty()) {
            rows.add(BriefingViewModel.info("employer-powers", "Command powers",
                    summarizePowerIds(mission.employerPowerIds), ""));
        }
        return List.copyOf(rows);
    }

    private void putRetainedActions(Map<String, Object> props, Mission mission,
                                    PersonnelReadiness readiness) {
        boolean debug = mission.source.isDebug();
        boolean transportOk = mission.source == MissionSource.STATIONING
                || isTransportSufficient(mission, lift());
        boolean personnelOk = debug || readiness == null || readiness.ready();
        boolean commandOk = captainCommandReady(mission);
        boolean canAccept = transportOk && personnelOk && commandOk;
        MarineRoster roster = liveRoster();
        int shortfall = readiness != null ? readiness.companyShortfall() : 0;
        int reserve = roster != null ? roster.readyReserveCount() : 0;
        int cargo = MarinePersonnelLogistics.availableCargoMarines();
        int reinforcement = Math.min(shortfall, reserve + cargo);
        int cargoCost = Math.max(0, reinforcement - Math.min(shortfall, reserve));
        boolean canReinforce = transportOk && commandOk && readiness != null
                && readiness.needsPersonnel() && reinforcement > 0;

        props.put("assignLabel", debug ? "" : "ASSIGN SQUADS");
        props.put("assignClasses", debug ? "briefing-assign-hidden" : "");
        props.put("assignDisabled", debug);
        props.put("assignAction", debug ? (Runnable) () -> { }
                : (Runnable) this::openSquadDeployment);

        Runnable deployAction = canAccept ? this::onAccept
                : canReinforce ? () -> reinforce(roster, shortfall)
                : !transportOk || !commandOk || readiness == null ? () -> { }
                : readiness.needsPersonnel() ? () -> { }
                : this::openSquadDeployment;
        String label = canAccept ? "DEPLOY"
                : !transportOk ? "INSUFFICIENT TRANSPORT"
                : !commandOk ? "SELECT COMMANDER"
                : readiness != null && readiness.needsPersonnel()
                        ? canReinforce ? "REINFORCE +" + reinforcement + "  ·  " + cargoCost + " CARGO"
                                : "NEED " + shortfall + "  ·  NO MARINES"
                        : readiness != null ? "ASSIGN " + readiness.selectedShortfall() : "BLOCKED";
        props.put("deployLabel", label);
        props.put("deployClasses", "briefing-deploy "
                + (canAccept ? "good-surface" : canReinforce ? "" : "danger-surface briefing-blocked"));
        props.put("deployDisabled", !canAccept && !canReinforce
                && (readiness == null || readiness.needsPersonnel() || !transportOk || !commandOk));
        props.put("deployAction", deployAction);
        props.put("backAction", (Runnable) this::onBack);
    }

    private void reinforce(MarineRoster roster, int shortfall) {
        MarinePersonnelLogistics.fillLineShortfall(roster, shortfall);
        rebuild();
    }

    private static String signedPercent(int value) {
        return (value >= 0 ? "+" : "") + value + "%";
    }

    private static void requireRetainedElements(MarkupInstance component) {
        for (String id : List.of(
                "mission-briefing-root", "mission-briefing-header",
                "mission-briefing-body", "mission-overview", "mission-info-rows",
                "mission-captain-list", "mission-planning", "mission-debug-drawer",
                "mission-debug-toggle", "mission-debug-workspace",
                "mission-tier-track", "mission-debug-controls", "mission-commitment",
                "mission-loadout-grid", "mission-power-list", "mission-source-list",
                "mission-transport-list", "mission-employer-list", "mission-assign",
                "mission-deploy", "mission-briefing-back")) component.requireElement(id);
    }

    // ---- left column: mission details + salvage + captain ----

    private void buildMissionColumn(Mission m) {
        float x = layout.leftCol.x + INNER_PAD;
        float y = layout.leftCol.y + layout.leftCol.h - INNER_PAD;
        float labelX = x;
        float valueX = x + LABEL_COL_W;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, Strings.get("briefingHeader"),
                labelX, y, HEADER_COLOR));
        y -= ROW_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("missionPopupType"),
                labelX, y, LABEL_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get(m.type.displayKey),
                valueX, y, m.type.color));
        y -= ROW_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("missionPopupRisk"),
                labelX, y, LABEL_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get(m.risk.displayKey),
                valueX, y, m.risk.color));
        y -= ROW_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("missionPopupPayout"),
                labelX, y, LABEL_COLOR));
        int cashMult = m.cashMultiplier & 0xFF;
        if (cashMult <= 0) cashMult = 100;
        long effectivePayout = (long) m.payout * cashMult / 100L;
        String payoutStr = MessageFormat.format(
                Strings.get("payoutFmt"),
                NumberFormat.getIntegerInstance().format(effectivePayout));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, payoutStr, valueX, y, VALUE_COLOR));
        y -= ROW_GAP;

        // Salvage negotiation — contract-bound missions only. −/+ trade salvage
        // for cash per contracts-nouns.md.
        int salvageBaseline = m.contractSalvageBaseline & 0xFF;
        if (salvageBaseline > 0) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingSalvage"),
                    labelX, y, LABEL_COLOR));
            int negotiated = m.contractSalvageNegotiated & 0xFF;
            int cashBonus = cashMult - 100;
            int phaseEntitlement = m.salvageNegotiated & 0xFF;
            String salvageStr = m.salvageBaseline != m.contractSalvageBaseline
                    ? MessageFormat.format(Strings.get("briefingPhaseSalvageFmt"),
                            negotiated, phaseEntitlement, cashBonus)
                    : MessageFormat.format(
                            Strings.get("briefingSalvageFmt"), negotiated, cashBonus);
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, salvageStr, valueX, y, VALUE_COLOR));

            if (negotiationOpen(m)) {
                float btnSize = 22f;
                float btnY = y - btnSize + 6f;
                float plusX = layout.leftCol.x + layout.leftCol.w - INNER_PAD - btnSize;
                float minusX = plusX - btnSize - 4f;
                widgets.add(new ButtonWidget(minusX, btnY, btnSize, btnSize, () -> adjustSalvage(-10)));
                widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingSalvageMinus"),
                        minusX + 6f, y, HEADER_COLOR));
                widgets.add(new ButtonWidget(plusX, btnY, btnSize, btnSize, () -> adjustSalvage(+10)));
                widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingSalvagePlus"),
                        plusX + 6f, y, HEADER_COLOR));
            }
            y -= ROW_GAP;
        }

        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("missionPopupRequires"),
                labelX, y, LABEL_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, m.requirements, valueX, y, VALUE_COLOR));
        y -= ROW_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20, "Operation Scale",
                labelX, y, LABEL_COLOR));
        int recommendedSquads = MissionForceEnvelope.recommendedSquads(m);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                m.tier.displayName + " · recommends " + recommendedSquads
                        + (recommendedSquads == 1 ? " squad" : " squads"),
                valueX, y, VALUE_COLOR));
        y -= ROW_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20, "Field Presence",
                labelX, y, LABEL_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                m.fieldPresencePolicy.briefingText(), valueX, y, VALUE_COLOR));
        y -= ROW_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20, "Opposition Quality",
                labelX, y, LABEL_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                MissionForceEnvelope.oppositionExpectation(m.risk),
                valueX, y, VALUE_COLOR));
        y -= ROW_GAP;

        // Opposition intel — enemy air. Neither side's contribution; it's what
        // the target fields against the drop.
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingEnemyAir"),
                labelX, y, LABEL_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                summarizeWings(m.enemyFighterSupport, Faction.DEFENDER), valueX, y, VALUE_COLOR));
        y -= ROW_GAP;

        // Flavor paragraph extent — drawn wrapped in renderFlavor (LabelWidget
        // is single-line). Captain section sits below it.
        flavorX = x;
        flavorY = y - SECTION_GAP / 2f;
        flavorW = layout.leftCol.w - 2 * INNER_PAD;

        buildCaptainSection(m);
    }

    private void buildCaptainSection(Mission m) {
        float x = layout.leftCol.x + INNER_PAD;
        float w = layout.leftCol.w - 2 * INNER_PAD;

        float flavorHeight = m.flavor != null
                ? Fonts.ORBITRON_20.measureWrappedHeight(m.flavor, flavorW)
                : 0f;
        float sectionTop = flavorY - flavorHeight - SECTION_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, Strings.get("briefingSquadLead"),
                x, sectionTop, HEADER_COLOR));

        float listTop = sectionTop - 28f;
        // Reserve the same bottom band the right column gives Deploy/Back, so the
        // captain list and the buttons line up across columns. leftCol.y ==
        // rightCol.y (both built from the shared bodyBottom in BriefingLayout).
        float buttonsTop = layout.leftCol.y + INNER_PAD + BTN_H + SECTION_GAP;

        MarineRosterScript script = MarineRosterScript.getInstance();
        List<MarineCaptain> captains;
        if (m.source == MissionSource.STATIONING) {
            MarineCaptain assigned = ctx.getSelectedCaptain();
            captains = assigned != null
                    ? java.util.Collections.singletonList(assigned)
                    : java.util.Collections.<MarineCaptain>emptyList();
        } else {
            captains = script != null
                    ? script.roster().active()
                    : java.util.Collections.<MarineCaptain>emptyList();
        }

        if (captains.isEmpty()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingNoCaptains"),
                    x, listTop, LABEL_COLOR));
            return;
        }

        float rowY = listTop - SQUAD_ROW_H;
        for (MarineCaptain c : captains) {
            if (rowY < buttonsTop) break; // out of room, overflow handled in a polish pass
            widgets.add(new CaptainRowWidget(c, x, rowY, w, SQUAD_ROW_H,
                    ctx::getSelectedCaptainId, id -> {
                        ctx.setSelectedCaptainId(id);
                        initializeCaptainFormation(m);
                        rebuild();
                    }));
            rowY -= SQUAD_ROW_H + SQUAD_ROW_GAP;
        }
    }

    // ---- right column: Your Fleet Brings / Employer Provides ----

    private void buildDetachmentColumn(Mission m) {
        float x = layout.rightCol.x + INNER_PAD;
        float valueX = x + LABEL_COL_W;
        float y = layout.rightCol.y + layout.rightCol.h - INNER_PAD;
        float rowW = layout.rightCol.w - 2 * INNER_PAD;
        // Bottom band reserved for the Deploy/Back row (see buildButtons). Rows
        // stop here rather than overdrawing the buttons — under a tall list
        // (e.g. a large real fleet) the lowest sections (employer readout)
        // truncate first; transports + the gate, which come first, win.
        float floor = layout.rightCol.y + INNER_PAD + BTN_H + SECTION_GAP;

        if (m.source == MissionSource.STATIONING) {
            PersonnelReadiness readiness = personnelReadiness(m);
            widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                    Strings.get("briefingStationedDetachment"), x, y, HEADER_COLOR));
            y -= ROW_GAP;
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    Strings.get("briefingStationedPersonnel"), x, y, LABEL_COLOR));
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    m.requirements, valueX, y, VALUE_COLOR));
            y -= ROW_GAP;
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "Personnel", x, y, LABEL_COLOR));
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    readiness.selectedReady() + " / " + readiness.requiredSeats()
                            + " ready · short " + readiness.selectedShortfall(),
                    valueX, y, readiness.ready() ? ACCEPT_COLOR : BLOCKED_COLOR));
            y -= ROW_GAP;
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    Strings.get("briefingTransport"), x, y, LABEL_COLOR));
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    Strings.get("briefingStationedLocalLifts"), valueX, y, VALUE_COLOR));
            y -= ROW_GAP + SECTION_GAP;
            buildCommandDeck(x, y, rowW, floor);
            return;
        }

        // Difficulty is mission scale, not the debug company's size and not
        // risk. Keep it first so every DEBUG briefing can move along the same
        // campaign ladder even when the support lists below are tall.
        if (m.source.isDebug()) {
            y = buildDebugDifficultySlider(m, x, y, rowW, floor);
            y -= SECTION_GAP;
        }

        // Mission-shape controls follow scale so the DEBUG Conquest fixture
        // knobs cannot truncate under the general support pickers below.
        if (m.source == MissionSource.DEBUG
                && m.type == MissionType.CONQUEST) {
            y = buildDebugConquestArrivalPicker(x, y, rowW, floor);
            y -= SECTION_GAP;
        }

        // Debug air picker (dev-gated) follows the mission-shape controls and
        // remains ahead of the potentially tall transport/carrier lists.
        if (DevConfig.DEBUG_AIRCRAFT_PICKER) {
            y = buildDebugAirPanel(x, y, rowW, floor);
            y -= SECTION_GAP;
        }

        if (m.source.isDebug()) {
            y = buildDebugCompanyPicker(x, y, rowW, floor);
            y -= SECTION_GAP;
        }

        if (DevConfig.DEBUG_MECH_SUPPORT_PICKER && m.source.isDebug()) {
            y = buildDebugMechPicker(m, x, y, rowW, floor);
            y -= SECTION_GAP;
        }

        // === YOUR FLEET BRINGS ===
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, Strings.get("briefingYourFleet"), x, y, HEADER_COLOR));
        y -= ROW_GAP;

        if (m.source.isDebug()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, "Personnel", x, y, LABEL_COLOR));
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    ctx.getDebugCompanyStage().summary(ctx.getDebugSquadCount()),
                    valueX, y, ACCEPT_COLOR));
        } else {
            PersonnelReadiness readiness = personnelReadiness(m);
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, "Personnel", x, y, LABEL_COLOR));
            String personnelLine = MissionForceEnvelope.allowsUnderstrength(m)
                    ? readiness.selectedReady() + " ready · minimum "
                            + readiness.requiredSeats() + " · recommend "
                            + MissionForceEnvelope.recommendedSquads(m)
                            + (MissionForceEnvelope.recommendedSquads(m) == 1
                                    ? " squad" : " squads")
                    : readiness.selectedReady() + "/" + readiness.requiredSeats()
                            + " selected · " + readiness.companyReady()
                            + " company · " + readiness.selectedShortfall() + " short";
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, personnelLine,
                    valueX, y, readiness.ready() ? ACCEPT_COLOR : BLOCKED_COLOR));
            TaskForce force = selectedTaskForce();
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    force.squadCount() + " squads · " + force.officerCount()
                            + (force.officerCount() == 1 ? " officer" : " officers"),
                    x + rowW - 190f, y, captainCommandReady(m)
                            ? ACCEPT_COLOR : BLOCKED_COLOR));
        }
        y -= ROW_GAP;

        if (!m.source.isDebug() && m.source != MissionSource.STATIONING && y >= floor) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "Issued Experience", x, y, LABEL_COLOR));
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    MissionForceEnvelope.selectedExperience(liveRoster(),
                            ctx.getSelectedMarineSquadIds()).display(),
                    valueX, y, VALUE_COLOR));
            y -= ROW_GAP;
        }

        // Task force — one row per officer once the operation needs more than
        // one. A single-officer deployment says nothing new, so it stays quiet.
        if (!m.source.isDebug() && m.source != MissionSource.STATIONING) {
            y = buildTaskForceRows(x, y, rowW, floor);
        }

        y = buildCommandDeck(x, y, rowW, floor);
        y -= SECTION_GAP;

        // Power-bearing ships — one commitment controls every capability that
        // member supplies. Employer powers remain available independently.
        if (!cachedPowerSources.isEmpty()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, "Power Sources", x, y, LABEL_COLOR));
            y -= ROW_GAP;
            for (PlayerFleetPowerSources.SourceShip source : cachedPowerSources) {
                if (y < floor) return;
                boolean committed = !deselectedPowerSources.contains(source.memberId);
                String rowLabel = (committed ? "[x] " : "[ ] ") + source.shipName
                        + " — " + summarizePowers(source.powers)
                        + (committed ? "" : " — held back");
                widgets.add(new ButtonWidget(x, y - BTN_H + 6f, rowW, BTN_H, () -> {
                    if (!deselectedPowerSources.remove(source.memberId)) {
                        deselectedPowerSources.add(source.memberId);
                    }
                    rebuild();
                }));
                widgets.add(new SpriteThumbWidget(source.spriteName, x, y - 20f, THUMB, THUMB));
                widgets.add(new LabelWidget(Fonts.ORBITRON_20, rowLabel,
                        x + THUMB + 10f, y, committed ? VALUE_COLOR : LABEL_COLOR));
                y -= ROW_GAP;
            }
            y -= SECTION_GAP;
        }

        // Transport — debug missions get an exact type/count picker; production
        // missions retain one commitment toggle per real fleet transport.
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingTransport"), x, y, LABEL_COLOR));
        y -= ROW_GAP;
        if (m.source.isDebug()) {
            y = buildDebugTransportPicker(m, x, y, rowW, floor);
        } else {
            int selectedPersonnel = PersonnelReadiness.assessSelection(liveRoster(),
                    ctx.getSelectedMarineSquadIds(), 0).selectedReady();
            List<ShuttleAssignment> manifest = MissionForceEnvelope.allowsUnderstrength(m)
                    ? DetachmentResolver.buildShuttleManifestForPersonnel(
                            m, lift(), selectedPersonnel)
                    : DetachmentResolver.buildShuttleManifest(
                            m, lift());
            // Read out rather than chosen from. The boats are the ship's, so
            // what this section reports is an establishment: how many, of what,
            // and how hard each is working to cover the drops.
            y = buildLiftReadout(m, manifest, x, y, floor);
        }
        boolean transportOk = isTransportSufficient(m, lift());
        if (!transportOk && y >= floor) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    MissionForceEnvelope.allowsUnderstrength(m)
                            ? "No lift: this ship carries no boats"
                            : "No lift: no boats aboard and none from the employer",
                    x + 6f, y, BLOCKED_COLOR));
            y -= ROW_GAP;
        }

        // Fighter cover — one opt-in toggle per committable carrier.
        if (!cachedCarriers.isEmpty() && y >= floor) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingFighterCover"), x, y, LABEL_COLOR));
            y -= ROW_GAP;
            for (int i = 0; i < cachedCarriers.size(); i++) {
                if (y < floor) return;
                final int idx = i;
                PlayerFleetWings.CarrierBay carrier = cachedCarriers.get(i);
                boolean committed = !deselectedCarriers.contains(idx);
                String marker = committed ? "[x]" : "[ ]";
                String rowLabel = marker + " " + carrier.shipName
                        + " (" + carrier.bayCount() + (carrier.bayCount() == 1 ? " bay" : " bays") + ")"
                        + (committed ? "" : " — held back");
                Color rowColor = committed ? VALUE_COLOR : LABEL_COLOR;
                ButtonWidget toggle = new ButtonWidget(x, y - BTN_H + 6f, rowW, BTN_H,
                        () -> {
                            if (deselectedCarriers.contains(idx)) deselectedCarriers.remove(idx);
                            else deselectedCarriers.add(idx);
                            rebuild();
                        });
                widgets.add(toggle);
                widgets.add(new SpriteThumbWidget(carrier.spriteName, x, y - 20f, THUMB, THUMB));
                widgets.add(new LabelWidget(Fonts.ORBITRON_20, rowLabel, x + THUMB + 10f, y, rowColor));
                y -= ROW_GAP;
            }
        }

        // === EMPLOYER PROVIDES === (read-only co-source)
        y -= SECTION_GAP;
        if (y < floor) return;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, Strings.get("briefingEmployerProvides"), x, y, HEADER_COLOR));
        y -= ROW_GAP;

        if (y >= floor) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingTransport"), x, y, LABEL_COLOR));
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    m.source.isDebug()
                            ? "Overridden by debug picker"
                            : m.employerShuttles > 0
                                    ? m.employerShuttles + "× Aeroshuttle"
                                    : Strings.get("briefingAirNone"),
                    valueX, y, VALUE_COLOR));
            y -= ROW_GAP;
        }

        if (y >= floor) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingAlliedAir"), x, y, LABEL_COLOR));
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    summarizeWings(m.clientFighterSupport, Faction.MARINE), valueX, y, VALUE_COLOR));
            y -= ROW_GAP;
        }

        if (y >= floor && m.employerPowerIds != null && !m.employerPowerIds.isEmpty()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingPowers"), x, y, LABEL_COLOR));
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    summarizePowerIds(m.employerPowerIds), valueX, y, VALUE_COLOR));
            y -= ROW_GAP;
        }
    }

    /** Compact two-column command deck; returns the next free row below it. */
    private float buildCommandDeck(float x, float y, float rowW, float floor) {
        int used = CommandDeck.used(cachedAvailablePowers, selectedPowerIds);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "COMMAND DECK  " + used + " / " + CommandDeck.BUDGET,
                x, y, used <= CommandDeck.BUDGET ? HEADER_COLOR : BLOCKED_COLOR));
        y -= ROW_GAP;
        if (cachedAvailablePowers.isEmpty()) {
            if (y >= floor) widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "No powers available from fleet or employer", x, y, LABEL_COLOR));
            return y - ROW_GAP;
        }

        for (CommandPower power : cachedAvailablePowers) {
            if (y < floor) return y;
            boolean selected = selectedPowerIds.contains(power.id);
            boolean canAdd = CommandDeck.canAdd(cachedAvailablePowers, selectedPowerIds, power);
            Runnable toggle = selected || canAdd ? () -> {
                if (!selectedPowerIds.remove(power.id)) selectedPowerIds.add(power.id);
                rebuild();
            } : null;
            widgets.add(new ButtonWidget(x, y - BTN_H + 6f, rowW, BTN_H, toggle));
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    (selected ? "[x] " : "[ ] ") + power.displayName,
                    x + 7f, y, selected ? ACCEPT_COLOR : canAdd ? VALUE_COLOR : LABEL_COLOR));
            String cost = CommandDeck.weight(power) + " slot"
                    + (CommandDeck.weight(power) == 1 ? "" : "s")
                    + " · " + Math.round(power.cpCost) + " CP"
                    + (power.supplyCost > 0 ? " · " + power.supplyCost + " sup" : "");
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, cost,
                    x + rowW - 190f, y, selected || canAdd ? LABEL_COLOR : BLOCKED_COLOR));
            y -= ROW_GAP;
        }
        return y;
    }

    private List<CommandPower> availablePowers(Mission mission) {
        if (mission == null) return Collections.emptyList();
        List<CommandPower> powers = mission.source == MissionSource.STATIONING
                ? DetachmentResolver.resolveStationed(mission).powers
                : DetachmentResolver.resolve(mission, lift(), committedWings(),
                        committedPowerSourceMembers()).powers;
        return debugMechRoster(mission) != null
                ? debugMechRoster(mission).applyTo(powers) : powers;
    }

    private void buildButtons() {
        // Squad assignment / Deploy / Back at the bottom of the detachment column.
        float availableW = layout.rightCol.w - 2 * INNER_PAD;
        float btnW = (availableW - 2f * BTN_GAP) / 3f;
        float btnY = layout.rightCol.y + INNER_PAD;
        float squadsX = layout.rightCol.x + INNER_PAD;
        float deployX = squadsX + btnW + BTN_GAP;
        float backX   = deployX + btnW + BTN_GAP;

        // Deploy gating — when transport is short, the button is non-functional
        // and the label flips to a red "Insufficient Transport".
        Mission m = ctx.getSelectedMission();
        boolean debugPersonnel = m != null && m.source.isDebug();
        PersonnelReadiness readiness = m != null && !debugPersonnel
                ? personnelReadiness(m) : null;
        boolean transportOk = m == null || m.source == MissionSource.STATIONING
                || isTransportSufficient(m, lift());
        boolean personnelOk = debugPersonnel || m == null || readiness.ready();
        boolean commandOk = m == null || captainCommandReady(m);
        boolean canAccept = transportOk && personnelOk && commandOk;
        MarineRoster personnelRoster = liveRoster();
        int personnelShortfall = readiness != null ? readiness.companyShortfall() : 0;
        int reserveAvailable = personnelRoster != null
                ? personnelRoster.readyReserveCount() : 0;
        int cargoAvailable = MarinePersonnelLogistics.availableCargoMarines();
        int reinforcementAvailable = Math.min(personnelShortfall,
                reserveAvailable + cargoAvailable);
        int cargoCost = Math.max(0, reinforcementAvailable
                - Math.min(personnelShortfall, reserveAvailable));
        boolean canReinforce = transportOk && commandOk && readiness != null
                && readiness.needsPersonnel() && reinforcementAvailable > 0;

        ButtonWidget squads = new ButtonWidget(squadsX, btnY, btnW, BTN_H,
                debugPersonnel ? () -> {
                    ctx.cycleDebugCompanyStage();
                    debugMechCount = ctx.getDebugCompanyStage().mechs;
                    rebuild();
                } : this::openSquadDeployment);
        widgets.add(squads);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                debugPersonnel
                        ? "Company: " + ctx.getDebugCompanyStage().displayName
                                + " x" + ctx.getDebugSquadCount()
                        : "Assign Squads",
                squadsX + 8f, btnY + BTN_H - 6f, HEADER_COLOR));

        Runnable deployAction = canAccept ? this::onAccept
                : !transportOk || readiness == null ? null
                : !commandOk ? null
                : readiness.needsPersonnel()
                        ? canReinforce ? () -> {
                            MarinePersonnelLogistics.fillLineShortfall(
                                    personnelRoster, personnelShortfall);
                            rebuild();
                        } : null
                        : this::openSquadDeployment;
        ButtonWidget deploy = new ButtonWidget(deployX, btnY, btnW, BTN_H, deployAction);
        widgets.add(deploy);
        boolean showMarineCommodity = readiness != null && readiness.needsPersonnel();
        if (showMarineCommodity) {
            String marineIcon = CampaignCommodityPresentation.INSTANCE
                    .commodityIcon(Commodities.MARINES);
            if (!marineIcon.isBlank()) {
                widgets.add(new SpriteThumbWidget(marineIcon,
                        deployX + 8f, btnY + (BTN_H - 22f) * 0.5f, 22f, 22f));
            }
        }
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                canAccept ? Strings.get("briefingAccept")
                        : !transportOk ? Strings.get("briefingAcceptBlocked")
                        : !commandOk ? "Select Commander"
                        : readiness.needsPersonnel()
                                ? reinforcementAvailable > 0
                                        ? "Reinforce +" + reinforcementAvailable
                                                + " · " + cargoCost + " cargo"
                                        : "Need " + personnelShortfall + " · No marines"
                                : "Assign " + readiness.selectedShortfall(),
                deployX + INNER_PAD + (showMarineCommodity ? 24f : 0f),
                btnY + BTN_H - 6f,
                canAccept ? ACCEPT_COLOR : canReinforce ? VALUE_COLOR : BLOCKED_COLOR));

        ButtonWidget back = new ButtonWidget(backX, btnY, btnW, BTN_H, this::onBack);
        widgets.add(back);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("actionBack"),
                backX + INNER_PAD, btnY + BTN_H - 6f, HEADER_COLOR));
    }

    private PersonnelReadiness personnelReadiness(Mission m) {
        MarineRoster roster = liveRoster();
        return m != null && m.source != MissionSource.STATIONING
                ? PersonnelReadiness.assessSelection(roster,
                        ctx.getSelectedMarineSquadIds(), requiredPersonnelSeats(m))
                : PersonnelReadiness.assess(roster,
                        ctx.getSelectedMarineSquadIds(), requiredPersonnelSeats(m));
    }

    private static MarineRoster liveRoster() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster() : null;
    }

    private int requiredPersonnelSeats(Mission m) {
        int base = basePersonnelSeats(m);
        if (MissionForceEnvelope.allowsUnderstrength(m)) {
            return MissionForceEnvelope.minimumPersonnel(m, base);
        }
        if (m == null || m.type != MissionType.CONQUEST) return base;
        MarineRoster roster = liveRoster();
        int selected = PersonnelReadiness.assessSelection(roster,
                ctx.getSelectedMarineSquadIds(), 0).selectedReady();
        return Math.max(base, selected);
    }

    private int basePersonnelSeats(Mission m) {
        if (m == null) return 0;
        List<ShuttleAssignment> manifest = DetachmentResolver.buildShuttleManifest(
                m, m.source == MissionSource.STATIONING
                        ? Collections.emptyList() : lift());
        int firstPlayer = m.source == MissionSource.STATIONING
                ? 0 : DetachmentResolver.employerPhysicalShipCount(m);
        return CampaignMarineDeployment.requiredSeats(manifest, firstPlayer);
    }

    private void openSquadDeployment() {
        Mission m = ctx.getSelectedMission();
        if (m == null) return;
        List<ShuttleAssignment> manifest = DetachmentResolver.buildShuttleManifest(
                m, m.source == MissionSource.STATIONING
                        ? java.util.Collections.emptyList() : lift());
        int firstPlayer = m.source == MissionSource.STATIONING
                ? 0 : DetachmentResolver.employerPhysicalShipCount(m);
        int seats = MissionForceEnvelope.allowsUnderstrength(m)
                ? MissionForceEnvelope.recommendedPersonnel(m)
                : CampaignMarineDeployment.requiredSeats(manifest, firstPlayer);
        ctx.setMarineDeploymentCapacity(seats);

        MarineRosterScript script = MarineRosterScript.getInstance();
        if (script != null && seats > 0) {
            initializeCaptainFormation(m);
        }
        ctx.goTo(ScreenId.SQUAD_DEPLOYMENT);
    }

    private void initializeCaptainFormation(Mission mission) {
        if (mission == null || mission.source.isDebug()
                || mission.source == MissionSource.STATIONING
                || ctx.hasInitializedMarineSquadSelectionFor(mission)) return;
        MarineRosterScript script = MarineRosterScript.getInstance();
        MarineRoster roster = script != null ? script.roster() : null;
        ctx.replaceMarineSquadSelection(CaptainDeploymentPolicy.defaultSquadIds(
                roster, ctx.getSelectedCaptain()));
    }

    /** The officers the current squad selection puts in the field. */
    private TaskForce selectedTaskForce() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        return TaskForce.of(script != null ? script.roster() : null,
                ctx.getSelectedCaptain(), ctx.getSelectedMarineSquadIds());
    }

    /**
     * One row per officer in the task force, with what each is leading. Only
     * rendered when more than one officer is involved — for a single-officer
     * operation the summary line above already said it.
     */
    private float buildTaskForceRows(float x, float y, float rowW, float floor) {
        TaskForce force = selectedTaskForce();
        if (force.officerCount() < 2) return y;
        for (TaskForce.Element element : force.elements()) {
            if (y < floor) return y;
            boolean bad = !element.fit() || element.overCap();
            String name = element.officer != null
                    ? element.officer.rank().displayName() + " " + element.officer.name()
                    : "Unassigned";
            String detail = element.squads.size()
                    + (element.squads.size() == 1 ? " squad · " : " squads · ")
                    + element.marines + " marines"
                    + (element.inherited ? " · attached" : "");
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, "  " + name,
                    x, y, bad ? BLOCKED_COLOR : LABEL_COLOR));
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, detail,
                    x + rowW - 240f, y, bad ? BLOCKED_COLOR : VALUE_COLOR));
            y -= ROW_GAP;
        }
        return y;
    }

    private boolean captainCommandReady(Mission mission) {
        if (mission == null || mission.source.isDebug()
                || mission.source == MissionSource.STATIONING) return true;
        MarineRosterScript script = MarineRosterScript.getInstance();
        MarineRoster roster = script != null ? script.roster() : null;
        return CaptainDeploymentPolicy.isValidCommand(roster,
                ctx.getSelectedCaptain(), ctx.getSelectedMarineSquadIds());
    }

    /** Currently-committed carriers — {@link #cachedCarriers} minus the deselected. */
    private List<PlayerFleetWings.CarrierBay> committedCarriers() {
        List<PlayerFleetWings.CarrierBay> out = new java.util.ArrayList<>();
        for (int i = 0; i < cachedCarriers.size(); i++) {
            if (!deselectedCarriers.contains(i)) out.add(cachedCarriers.get(i));
        }
        return out;
    }

    /** Marine-side fighter cover from the committed carriers (player side only). */
    private FlybyRoster committedWings() {
        return PlayerFleetWings.rosterFrom(committedCarriers());
    }

    /** Exact fleet members committed as command-power capability sources. */
    private List<FleetMemberAPI> committedPowerSourceMembers() {
        List<FleetMemberAPI> out = new java.util.ArrayList<>();
        for (PlayerFleetPowerSources.SourceShip source : cachedPowerSources) {
            if (!deselectedPowerSources.contains(source.memberId)) out.add(source.member);
        }
        return out;
    }

    // ---- debug-only pickers ----

    /** Mission-scale dial; risk, player force, and field presence stay separate. */
    private float buildDebugDifficultySlider(Mission mission, float x, float y,
                                             float rowW, float floor) {
        if (y < floor) return y;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "MISSION DEBUG — scale demanded", x, y, HEADER_COLOR));
        y -= ROW_GAP;
        if (y - OperationTierSliderWidget.DEFAULT_HEIGHT < floor) return y;

        widgets.add(new OperationTierSliderWidget(
                x, y - OperationTierSliderWidget.DEFAULT_HEIGHT,
                rowW, OperationTierSliderWidget.DEFAULT_HEIGHT,
                mission.type, mission.tier, this::adjustDebugOperationTier));
        return y - OperationTierSliderWidget.DEFAULT_HEIGHT;
    }

    private void adjustDebugOperationTier(OperationTier tier) {
        Mission adjusted = DebugMissionDifficulty.atTier(
                ctx.getSelectedMission(), tier);
        if (adjusted == null) return;
        DebugCompanyStage stage = debugCompanyStageFor(tier);
        ctx.setDebugCompanyStage(stage);
        debugMechCount = stage.mechs;
        ctx.setSelectedMission(adjusted);
        debugTransportCount = Math.min(debugTransportCount,
                adjusted.requiredDrops);
        rebuild();
    }

    static DebugCompanyStage debugCompanyStageFor(OperationTier tier) {
        return switch (tier) {
            case FIRST_CONTRACT -> DebugCompanyStage.FIRST_CONTRACT;
            case ESTABLISHED -> DebugCompanyStage.ESTABLISHED;
            case VETERAN -> DebugCompanyStage.VETERAN_COMPANY;
            case REINFORCED -> DebugCompanyStage.REINFORCED;
            case FULL_STRENGTH -> DebugCompanyStage.FULL_STRENGTH;
        };
    }

    /**
     * Squad dial for the debug company. The stage sets the default; this
     * overrides the size without touching the quality, because "how many
     * marines does this mission need" is the balance question a fixed stage
     * ladder cannot answer. Debug scenarios impose no authored upper ceiling;
     * Conquest expands its reusable arrival cycles to carry the selection.
     */
    private float buildDebugCompanyPicker(float x, float y, float rowW, float floor) {
        if (y < floor) return y;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "COMPANY DEBUG — squads deployed", x, y, HEADER_COLOR));
        y -= ROW_GAP;
        if (y < floor) return y;

        int squads = ctx.getDebugSquadCount();
        float arrowW = 34f;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, "Squads", x, y, LABEL_COLOR));
        float controlX = x + 70f;
        addDebugTransportButton(controlX, y, 42f, "-10",
                squads > 0 ? () -> adjustDebugSquadCount(-10) : null);
        addDebugTransportButton(controlX + 46f, y, arrowW, "-",
                squads > 0 ? () -> adjustDebugSquadCount(-1) : null);
        widgets.add(new ButtonWidget(controlX + 84f, y - BTN_H + 6f, 56f, BTN_H, null));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Integer.toString(squads),
                controlX + 100f, y, squads > 0 ? ACCEPT_COLOR : BLOCKED_COLOR));
        addDebugTransportButton(controlX + 144f, y, arrowW, "+",
                squads < Integer.MAX_VALUE ? () -> adjustDebugSquadCount(1) : null);
        addDebugTransportButton(controlX + 182f, y, 42f, "+10",
                squads < Integer.MAX_VALUE ? () -> adjustDebugSquadCount(10) : null);
        addDebugTransportButton(controlX + 232f, y, 88f, "Stage",
                () -> {
                    ctx.cycleDebugCompanyStage();
                    debugMechCount = ctx.getDebugCompanyStage().mechs;
                    rebuild();
                });
        y -= ROW_GAP;

        if (y >= floor) {
            DebugCompanyStage stage = ctx.getDebugCompanyStage();
            boolean overCap = squads > stage.officerRank.squadCommandCap();
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, stage.summary(squads),
                    x, y, overCap ? BLOCKED_COLOR : VALUE_COLOR));
            y -= ROW_GAP;
        }
        return y;
    }

    private void adjustDebugSquadCount(int delta) {
        ctx.setDebugSquadCount(adjustDebugCount(
                ctx.getDebugSquadCount(), delta));
        rebuild();
    }

    private float buildDebugMechPicker(Mission mission, float x, float y,
                                       float rowW, float floor) {
        if (y < floor) return y;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "MECH DEBUG — player support", x, y, HEADER_COLOR));
        y -= ROW_GAP;
        if (y < floor) return y;

        float arrowW = 34f;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, "Count", x, y, LABEL_COLOR));
        float controlX = x + 70f;
        addDebugTransportButton(controlX, y, 42f, "-10",
                debugMechCount > 0 ? () -> adjustDebugMechCount(-10) : null);
        addDebugTransportButton(controlX + 46f, y, arrowW, "-",
                debugMechCount > 0 ? () -> adjustDebugMechCount(-1) : null);
        widgets.add(new ButtonWidget(controlX + 84f, y - BTN_H + 6f,
                56f, BTN_H, null));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Integer.toString(debugMechCount),
                controlX + 100f, y,
                debugMechCount > 0 ? ACCEPT_COLOR : BLOCKED_COLOR));
        addDebugTransportButton(controlX + 144f, y, arrowW, "+",
                debugMechCount < Integer.MAX_VALUE
                        ? () -> adjustDebugMechCount(1) : null);
        addDebugTransportButton(controlX + 182f, y, 42f, "+10",
                debugMechCount < Integer.MAX_VALUE
                        ? () -> adjustDebugMechCount(10) : null);
        float rerollX = controlX + 232f;
        addDebugTransportButton(rerollX, y, 88f, "Reroll", () -> {
            debugMechRoll++;
            rebuild();
        });
        y -= ROW_GAP;

        if (y >= floor) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    debugMechSummary(debugMechRoster(mission)), x, y,
                    debugMechCount > 0 ? VALUE_COLOR : LABEL_COLOR));
            y -= ROW_GAP;
        }
        return y;
    }

    private void adjustDebugMechCount(int delta) {
        debugMechCount = adjustDebugCount(debugMechCount, delta);
        rebuild();
    }

    /** Non-negative count arithmetic with only the Java representation as a ceiling. */
    static int adjustDebugCount(int count, int delta) {
        long adjusted = (long) count + delta;
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, adjusted));
    }

    private DebugMechRoster debugMechRoster(Mission mission) {
        if (!DevConfig.DEBUG_MECH_SUPPORT_PICKER || mission == null
                || !mission.source.isDebug()) return null;
        long seed = 31L * mission.id.hashCode() + debugMechRoll;
        return DebugMechRoster.randomized(debugMechCount, seed);
    }

    private static String debugMechSummary(DebugMechRoster roster) {
        if (roster == null || roster.count() == 0) return "No player mech drops";
        int bulwarks = 0;
        int hounds = 0;
        int siroccos = 0;
        for (MechVariant variant : roster.variants()) {
            if (variant == MechVariant.BULWARK) bulwarks++;
            else if (variant == MechVariant.HOUND) hounds++;
            else if (variant == MechVariant.SIROCCO) siroccos++;
        }
        List<String> parts = new ArrayList<>();
        if (bulwarks > 0) parts.add(bulwarks + "x Bulwark");
        if (hounds > 0) parts.add(hounds + "x Hound");
        if (siroccos > 0) parts.add(siroccos + "x Sirocco");
        long drops = ((long) roster.count() + MechSupport.LANCE_SIZE - 1L)
                / MechSupport.LANCE_SIZE;
        return drops + (drops == 1 ? " lance · " : " lances · ")
                + String.join(" · ", parts);
    }

    /** Exact debug-only transport type/count controls. */
    private float buildDebugTransportPicker(Mission mission, float x, float y,
                                            float rowW, float floor) {
        if (y < floor) return y;
        float labelW = 70f;
        float arrowW = 34f;
        float typeW = Math.min(170f, rowW - labelW - 2f * arrowW - 12f);
        float controlX = x + labelW;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20, "Type", x, y, LABEL_COLOR));
        addDebugTransportButton(controlX, y, arrowW, "<", this::previousDebugTransportType);
        addDebugTransportButton(controlX + arrowW + 4f, y, typeW,
                debugTransportType.displayName(), this::nextDebugTransportType);
        addDebugTransportButton(controlX + arrowW + typeW + 8f, y, arrowW,
                ">", this::nextDebugTransportType);
        y -= ROW_GAP;

        if (y < floor) return y;
        int maxCount = Math.max(0, mission.requiredDrops);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, "Count", x, y, LABEL_COLOR));
        addDebugTransportButton(controlX, y, arrowW, "-",
                debugTransportCount > 0 ? () -> adjustDebugTransportCount(-1, maxCount) : null);
        float countW = 56f;
        widgets.add(new ButtonWidget(controlX + arrowW + 4f, y - BTN_H + 6f,
                countW, BTN_H, null));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Integer.toString(debugTransportCount),
                controlX + arrowW + 22f, y,
                debugTransportCount > 0 ? ACCEPT_COLOR : BLOCKED_COLOR));
        addDebugTransportButton(controlX + arrowW + countW + 8f, y, arrowW, "+",
                debugTransportCount < maxCount
                        ? () -> adjustDebugTransportCount(1, maxCount) : null);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                debugTransportType.capacity + " seats each · "
                        + mission.requiredDrops + " drops",
                controlX + 2f * arrowW + countW + 22f, y, LABEL_COLOR));
        return y - ROW_GAP;
    }

    private void addDebugTransportButton(float x, float y, float w,
                                         String label, Runnable action) {
        widgets.add(new ButtonWidget(x, y - BTN_H + 6f, w, BTN_H, action));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, label,
                x + (w <= 40f ? 11f : 7f), y,
                action != null ? VALUE_COLOR : LABEL_COLOR));
    }

    private void previousDebugTransportType() {
        ShuttleType[] types = ShuttleType.values();
        int index = (debugTransportType.ordinal() - 1 + types.length) % types.length;
        debugTransportType = types[index];
        rebuild();
    }

    private void nextDebugTransportType() {
        ShuttleType[] types = ShuttleType.values();
        debugTransportType = types[(debugTransportType.ordinal() + 1) % types.length];
        rebuild();
    }

    private void adjustDebugTransportCount(int delta, int maxCount) {
        debugTransportCount = Math.max(0,
                Math.min(maxCount, debugTransportCount + delta));
        rebuild();
    }

    /**
     * A debug briefing's synthetic craft are patterns at standard fit. The
     * picker chooses a hull rather than one of the company's boats, so there
     * is no yard's work on them to carry.
     */
    private List<FittedBoat> debugTransportRoster(Mission mission) {
        int count = Math.max(0, Math.min(mission.requiredDrops, debugTransportCount));
        if (count != debugTransportCount) debugTransportCount = count;
        List<FittedBoat> roster = new ArrayList<>(count);
        for (int i = 0; i < count; i++) roster.add(FittedBoat.standard(debugTransportType));
        return roster;
    }

    /** Debug-list Conquest controls for the mission-authored arrival shape. */
    private float buildDebugConquestArrivalPicker(float x, float y,
                                                   float rowW, float floor) {
        if (y < floor) return y;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "CONQUEST ARRIVALS", x, y, HEADER_COLOR));
        y -= ROW_GAP;
        y = buildDebugArrivalStepper("Drop zones",
                Integer.toString(debugConquestArrivalConfig.dropZoneCount()),
                debugConquestArrivalConfig.dropZoneCount() > 1
                        ? () -> adjustDebugConquestDropZones(-1) : null,
                debugConquestArrivalConfig.dropZoneCount()
                        < DEBUG_MAX_CONQUEST_DROP_ZONES
                        ? () -> adjustDebugConquestDropZones(1) : null,
                x, y, rowW, floor);
        y = buildDebugArrivalStepper("Pairs / zone",
                Integer.toString(debugConquestArrivalConfig.shuttlePairsPerZone()),
                debugConquestArrivalConfig.shuttlePairsPerZone() > 1
                        ? () -> adjustDebugConquestPairsPerZone(-1) : null,
                debugConquestArrivalConfig.shuttlePairsPerZone()
                        < DEBUG_MAX_CONQUEST_PAIRS_PER_ZONE
                        ? () -> adjustDebugConquestPairsPerZone(1) : null,
                x, y, rowW, floor);
        return buildDebugArrivalStepper("Timing jitter",
                debugConquestArrivalConfig.timingJitterSec() + " s",
                debugConquestArrivalConfig.timingJitterSec() > 0f
                        ? () -> adjustDebugConquestJitter(
                                -DEBUG_CONQUEST_JITTER_STEP_SEC) : null,
                debugConquestArrivalConfig.timingJitterSec()
                        < DEBUG_MAX_CONQUEST_JITTER_SEC
                        ? () -> adjustDebugConquestJitter(
                                DEBUG_CONQUEST_JITTER_STEP_SEC) : null,
                x, y, rowW, floor);
    }

    private float buildDebugArrivalStepper(String label, String value,
                                            Runnable decrease, Runnable increase,
                                            float x, float y, float rowW,
                                            float floor) {
        if (y < floor) return y;
        float labelW = 116f;
        float arrowW = 34f;
        float valueW = Math.min(74f, rowW - labelW - 2f * arrowW - 12f);
        float controlX = x + labelW;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, label, x, y, LABEL_COLOR));
        addDebugTransportButton(controlX, y, arrowW, "-", decrease);
        widgets.add(new ButtonWidget(controlX + arrowW + 4f,
                y - BTN_H + 6f, valueW, BTN_H, null));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, value,
                controlX + arrowW + 12f, y, VALUE_COLOR));
        addDebugTransportButton(controlX + arrowW + valueW + 8f,
                y, arrowW, "+", increase);
        return y - ROW_GAP;
    }

    private void adjustDebugConquestDropZones(int delta) {
        int next = Math.max(1, Math.min(DEBUG_MAX_CONQUEST_DROP_ZONES,
                debugConquestArrivalConfig.dropZoneCount() + delta));
        debugConquestArrivalConfig =
                debugConquestArrivalConfig.withDropZoneCount(next);
        rebuild();
    }

    private void adjustDebugConquestPairsPerZone(int delta) {
        int next = Math.max(1, Math.min(DEBUG_MAX_CONQUEST_PAIRS_PER_ZONE,
                debugConquestArrivalConfig.shuttlePairsPerZone() + delta));
        debugConquestArrivalConfig =
                debugConquestArrivalConfig.withShuttlePairsPerZone(next);
        rebuild();
    }

    private void adjustDebugConquestJitter(float delta) {
        float next = Math.max(0f, Math.min(DEBUG_MAX_CONQUEST_JITTER_SEC,
                debugConquestArrivalConfig.timingJitterSec() + delta));
        next = Math.round(next / DEBUG_CONQUEST_JITTER_STEP_SEC)
                * DEBUG_CONQUEST_JITTER_STEP_SEC;
        debugConquestArrivalConfig =
                debugConquestArrivalConfig.withTimingJitterSec(next);
        rebuild();
    }

    // ---- debug air picker (DevConfig.DEBUG_AIRCRAFT_PICKER) ----

    /**
     * Renders the per-side fighter force-spawn toggles at the top of the right
     * column (one row per {@link FighterProfile}, an ATK + DEF toggle each), and
     * returns the new y cursor. Only called when the dev flag is on.
     */
    private float buildDebugAirPanel(float x, float y, float rowW, float floor) {
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, "AIR DEBUG — force-spawn (atk / def)", x, y, HEADER_COLOR));
        y -= ROW_GAP;
        float nameW = 96f;
        float togW = 64f;
        float togGap = 8f;
        float atkX = x + nameW;
        float defX = atkX + togW + togGap;
        for (FighterProfile p : FighterProfile.values()) {
            if (y < floor) return y;
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, profileDisplayName(p.name()), x, y, LABEL_COLOR));
            addDebugAirToggle(p, Faction.MARINE,   "ATK", atkX, y, togW);
            addDebugAirToggle(p, Faction.DEFENDER, "DEF", defX, y, togW);
            y -= ROW_GAP;
        }
        return y;
    }

    /** One {@code [x]}/{@code [ ]} toggle button for a (profile, side) pair in the debug picker. */
    private void addDebugAirToggle(FighterProfile profile, Faction side, String text, float bx, float y, float w) {
        final String key = side.name() + "|" + profile.name();
        boolean on = debugAirSelections.contains(key);
        widgets.add(new ButtonWidget(bx, y - BTN_H + 6f, w, BTN_H, () -> {
            if (!debugAirSelections.remove(key)) debugAirSelections.add(key);
            rebuild();
        }));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                (on ? "[x] " : "[ ] ") + text, bx + 4f, y, on ? ACCEPT_COLOR : LABEL_COLOR));
    }

    /** Force-spawned wings from the debug picker (both sides), or {@link FlybyRoster#EMPTY} when off / nothing picked. */
    private FlybyRoster debugWings() {
        if (!DevConfig.DEBUG_AIRCRAFT_PICKER || debugAirSelections.isEmpty()) return FlybyRoster.EMPTY;
        java.util.List<FighterWing> wings = new java.util.ArrayList<>();
        for (String key : debugAirSelections) {
            int bar = key.indexOf('|');
            if (bar <= 0) continue;
            Faction side = Faction.valueOf(key.substring(0, bar));
            FighterProfile profile = FighterProfile.valueOf(key.substring(bar + 1));
            wings.add(DebugAirRoster.wing(profile, side));
        }
        return wings.isEmpty() ? FlybyRoster.EMPTY : new FlybyRoster(wings);
    }

    /**
     * Compact one-line summary of the wings on a side. Example output:
     * {@code "2× Broadsword, 1× Talon"} or "None" when empty. Counts collapse
     * multiple wings of the same profile.
     */
    private static String summarizeWings(FlybyRoster roster, Faction side) {
        if (roster == null) return Strings.get("briefingAirNone");
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (FighterWing w : roster.wingsForSide(side)) {
            String key = profileDisplayName(w.profile.name());
            counts.merge(key, w.sortieCount, Integer::sum);
        }
        if (counts.isEmpty()) return Strings.get("briefingAirNone");
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, Integer> e : counts.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getValue()).append("× ").append(e.getKey());
        }
        return sb.toString();
    }

    /** Title-cases the enum name (TALON → Talon, BROADSWORD → Broadsword). */
    private static String profileDisplayName(String enumName) {
        if (enumName == null || enumName.isEmpty()) return "";
        return enumName.charAt(0) + enumName.substring(1).toLowerCase();
    }

    /** Friendly summary of employer-offered power ids ("recon_ping" → "Recon Ping"). */
    private static String summarizePowerIds(List<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (id == null || id.isEmpty()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(prettifyId(id));
        }
        return sb.length() == 0 ? Strings.get("briefingAirNone") : sb.toString();
    }

    private static String summarizePowers(List<CommandPower> powers) {
        StringBuilder sb = new StringBuilder();
        for (CommandPower power : powers) {
            if (power == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(power.displayName);
        }
        return sb.length() == 0 ? Strings.get("briefingAirNone") : sb.toString();
    }

    /** "recon_ping" → "Recon Ping". */
    private static String prettifyId(String id) {
        StringBuilder s = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            if (s.length() > 0) s.append(' ');
            s.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return s.toString();
    }

    /**
     * Mission has a transport — the floor is "at least one source delivering
     * marines." Employer contributes drops up to their cover; selected player
     * transports cycle to fill any remaining gap.
     */
    private static boolean isTransportSufficient(Mission m, List<FittedBoat> selectedShuttles) {
        if (MissionForceEnvelope.allowsUnderstrength(m)) {
            return selectedShuttles != null && !selectedShuttles.isEmpty();
        }
        return m.source.isDebug()
                ? !selectedShuttles.isEmpty()
                : m.employerShuttles >= 1 || !selectedShuttles.isEmpty();
    }

    /**
     * The lift this briefing is planning around: every boat aboard.
     *
     * <p>All of them, with no subset to choose. A ship's boats are a fitting
     * rather than a shopping list, so there is no per-hull commitment left to
     * make — what the company can put on the ground is what she carries, and
     * holding one back would be declining to use a lifeboat.
     */
    private List<FittedBoat> lift() {
        return cachedAvailable;
    }

    /**
     * Maps each boat to the cycle count the manifest gave it. Boats the manifest
     * did not need are absent (caller treats missing as 0).
     */
    private java.util.Map<Integer, Integer> computePlayerCyclesByIndex(
            Mission m, List<ShuttleAssignment> manifest) {
        java.util.Map<Integer, Integer> out = new java.util.HashMap<>();
        int employerPhysical = DetachmentResolver.employerPhysicalShipCount(m);
        for (int k = 0; k < cachedAvailable.size()
                && (employerPhysical + k) < manifest.size(); k++) {
            out.put(k, manifest.get(employerPhysical + k).cycles);
        }
        return out;
    }

    /**
     * The lift, read out: how many boats of what, how hard each is working, and
     * which ship they come off.
     *
     * <p>A summary rather than a list of rows. Every boat aboard is the same
     * boat — a ship carries a class of them, not an assortment — so six
     * identical lines would be six ways of saying one thing, and the number is
     * what a player is actually reading for.
     */
    private float buildLiftReadout(Mission m, List<ShuttleAssignment> manifest,
                                   float x, float y, float floor) {
        if (cachedAvailable.isEmpty() || y < floor) return y;
        java.util.Map<Integer, Integer> cyclesByBoat = computePlayerCyclesByIndex(m, manifest);
        int fewest = Integer.MAX_VALUE;
        int most = 0;
        for (int index = 0; index < cachedAvailable.size(); index++) {
            int cycles = cyclesByBoat.getOrDefault(index, 0);
            fewest = Math.min(fewest, cycles);
            most = Math.max(most, cycles);
        }
        ShuttleType type = cachedAvailable.get(0).pattern();
        StringBuilder label = new StringBuilder();
        label.append(cachedAvailable.size()).append(" x ").append(type.displayName());
        if (most > 0) {
            label.append("  (").append(fewest == most ? String.valueOf(most)
                    : fewest + "-" + most).append(" sorties each)");
        }
        widgets.add(new SpriteThumbWidget(type.spritePath, x, y - 20f, THUMB, THUMB));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, label.toString(),
                x + THUMB + 10f, y, VALUE_COLOR));
        y -= ROW_GAP;

        String carrier = ShipsBoatsAboard.carrier();
        if (carrier != null && y >= floor) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, "Carried by: " + carrier,
                    x + THUMB + 10f, y, LABEL_COLOR));
            y -= ROW_GAP;
        }
        return y;
    }

    private void onAccept() {
        Mission m = ctx.getSelectedMission();
        if (m == null) return;
        CampaignStateScript campaignScript = CampaignStateScript.getInstance();
        if (m.contractId >= 0L && campaignScript != null) {
            boolean civilWarParticipation =
                    CivilWarOfferAcceptance.isOfferedParticipation(
                            campaignScript.state(), m.contractId);
            if (!ContractEligibility.contractAcceptable(
                    campaignScript.state(), m.contractId)
                    || (civilWarParticipation
                        && !CivilWarOfferAcceptance.canAccept(
                            campaignScript.state(), m.contractId))) {
                rebuild();
                return;
            }
            int row = campaignScript.state().contractIndex(m.contractId);
            if (row >= 0 && ContractType.fromByte(campaignScript.state().contractType[row])
                    == ContractType.PLANETARY_ASSAULT
                    && !PlanetaryAssaultTerms.lockForDeployment(campaignScript.state(), m)) {
                rebuild();
                return;
            }
            if (civilWarParticipation) {
                int day = Global.getSector() != null
                        ? CampaignClock.day() : 0;
                if (!CivilWarOfferAcceptance.acceptMission(
                        campaignScript.state(), m.contractId, day)) {
                    rebuild();
                    return;
                }
            }
        }
        MarineCaptain c = ctx.getSelectedCaptain();
        String captainStr = c != null ? c.id() + " (" + c.name() + ")" : "none";
        LOG.info("MarineOps: deploy mission id=" + m.id + " name='" + m.name
                + "' type=" + m.type + " captain=" + captainStr);

        // Resolve the committed detachment (transports + marine fighter cover +
        // command powers) and build the battle. The deselected transports are
        // already filtered out of lift().
        Mission launchMission = m.source == MissionSource.DEBUG
                && m.type == MissionType.CONQUEST
                ? Mission.builder(m)
                        .conquestArrivalConfig(debugConquestArrivalConfig)
                        .build()
                : m;
        MissionLaunch.PreparedBattle prepared = MissionLaunch.prepareSimulation(
                ctx, launchMission,
                m.source == MissionSource.STATIONING
                        ? java.util.Collections.emptyList() : lift(),
                m.source == MissionSource.STATIONING ? FlybyRoster.EMPTY : committedWings(),
                m.source == MissionSource.STATIONING ? FlybyRoster.EMPTY : debugWings(),
                selectedPowerIds,
                m.source == MissionSource.STATIONING
                        ? java.util.Collections.emptyList() : committedPowerSourceMembers(),
                debugMechRoster(m));
        try {
            if (m.contractId >= 0L && campaignScript != null) {
                int day = Global.getSector() != null
                        ? CampaignClock.day() : 0;
                RivalStrikeGarrisonService.armForContractLaunch(
                        campaignScript.state(), m.contractId, day);
            }
        } catch (RuntimeException | Error failure) {
            prepared.close();
            throw failure;
        }
        ctx.setBattle(prepared.simulation(), prepared.fixture(),
                prepared.detachment());
        ctx.goTo(ScreenId.BATTLE);
    }

    private void onBack() {
        ctx.goTo(ScreenId.MISSION_SELECT);
    }

    /**
     * Trades salvage for cash (or vice versa) by {@code delta} percentage
     * points, clamping to {@code [0, salvageBaseline]}. Mission is immutable so
     * we build a new instance carrying the updated negotiated + cash multiplier
     * and swap it into the context.
     *
     * <p>Curve per {@code contracts-nouns.md}:
     * {@code cashMultiplier = 100 + (baseline − negotiated) * 0.5}.
     */
    private void adjustSalvage(int delta) {
        Mission m = ctx.getSelectedMission();
        if (m == null) return;
        int baseline = m.contractSalvageBaseline & 0xFF;
        if (baseline <= 0) return;
        int current = m.contractSalvageNegotiated & 0xFF;
        int next = Math.max(0, Math.min(baseline, current + delta));
        if (next == current) return;
        int cashMult = 100 + (baseline - next) / 2;
        int phaseNegotiated = Math.min(m.salvageBaseline & 0xFF, next);

        Mission replaced = Mission.builder(m)
                .salvageNegotiated(phaseNegotiated)
                .cashMultiplier(cashMult)
                .contractSalvageNegotiated(next)
                .build();
        ctx.setSelectedMission(replaced);
        rebuild();
    }

    private static boolean negotiationOpen(Mission mission) {
        CampaignStateScript script = CampaignStateScript.getInstance();
        return script == null || PlanetaryAssaultTerms.negotiationOpen(
                script.state(), mission.contractId);
    }

    @Override
    public void advance(float dt) {
        if (markupInstance != null) markupInstance.flush();
        if (document != null) document.advance(dt);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (input != null) input.process(events);
    }

    @Override
    public void render(float alphaMult) {
        if (document != null && viewport != null) document.render(viewport, alphaMult);
    }

    @Override
    public void detach() {
        if (document != null) document.deactivateInput();
        input = null;
    }

    private void renderFlavor(float alphaMult) {
        if (ctx == null) return;
        Mission m = ctx.getSelectedMission();
        if (m == null || m.flavor == null || m.flavor.isEmpty()) return;
        Fonts.ORBITRON_20.drawStringWrapped(m.flavor, flavorX, flavorY, flavorW, FLAVOR_COLOR, alphaMult);
    }

    private static void drawFrame(ColumnRect r, float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(
                FRAME_COLOR.getRed()   / 255f,
                FRAME_COLOR.getGreen() / 255f,
                FRAME_COLOR.getBlue()  / 255f,
                0.85f * alphaMult);
        glLineWidth(1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(r.x,         r.y);
        glVertex2f(r.x + r.w,   r.y);
        glVertex2f(r.x + r.w,   r.y + r.h);
        glVertex2f(r.x,         r.y + r.h);
        glEnd();
    }
}

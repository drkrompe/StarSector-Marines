package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractEligibility;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.CampaignMechSquad;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ops.detachment.Detachment;
import com.dillon.starsectormarines.ops.detachment.DebugCompany;
import com.dillon.starsectormarines.ops.detachment.DebugCompanyStage;
import com.dillon.starsectormarines.ops.battleview.BattleSprites;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.ops.loot.LootManifest;
import com.dillon.starsectormarines.ops.loot.LootSettlementPlan;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Shared state for the marine ops screens — planet, market, texture path, the
 * resolved client list, and the player's current selection as they click
 * through. Threaded into every {@link OpsPanel} via {@link OpsPanel#attach}.
 *
 * <p>Clients are resolved once at construction:
 * <ul>
 *   <li>Planet's owning faction (if any, and not Independent/Pirates already)</li>
 *   <li>{@link Factions#INDEPENDENT} broker — always present, never locked</li>
 *   <li>{@link Factions#PIRATES} contact — always present, never locked</li>
 *   <li>Any other faction with market presence in the same star system that
 *       the player isn't hostile with</li>
 * </ul>
 * Rep gating: factions are <em>locked</em> (visible but unselectable) when the
 * player's relationship is HOSTILE or worse. Pirates/Independent ignore gating.
 */
public class MarineOpsContext {

    public final PlanetAPI planet;
    public final MarketAPI market;
    public final String planetTexture;
    public final List<Client> clients;

    private Client selectedClient;
    private Mission selectedMission;
    /** Captain chosen to lead the accepted mission. Sticky across screen swaps. */
    private String selectedCaptainId;
    /** Persistent squads selected for the current mission's player-controlled seats. */
    private final LinkedHashSet<String> selectedMarineSquadIds = new LinkedHashSet<>();
    private String squadSelectionMissionId;
    /** Distinguishes an intentional empty selection from the legacy implicit-whole-line state. */
    private boolean marineSquadSelectionInitialized;
    private int marineDeploymentCapacity;
    /** Picker-only fixture; debug missions never consume or mutate the campaign roster. */
    private DebugCompanyStage debugCompanyStage = DebugCompanyStage.FIRST_CONTRACT;
    /**
     * The company {@link #debugCompanyStage} describes, built once and held so
     * the briefing names the marines that actually land. Rebuilt on a stage
     * change; {@code MarineRoster} construction rolls names and aptitudes, so
     * a fresh build per read would show one company and deploy another.
     */
    private MarineRoster debugCompanyRoster;
    /**
     * Squad-count override for the debug company, or {@code -1} to use the
     * stage's own size. Size and quality are separate axes: "how many marines
     * does this mission actually need" is the balance question a fixed ladder
     * cannot answer, so the briefing gets a dial.
     */
    private int debugSquadCount = -1;
    /** Stationing offer selected for the dedicated assignment screen. */
    private long selectedStationingContractId = -1L;
    /** Current battle simulation — built by the accept path (MissionLaunch), read by BattleScreen. */
    private BattleSimulation battleSimulation;
    /** Tick-zero construction facts retained for debug capture/replay; not mutable battle state. */
    private BattleFixture battleFixture;
    /** Detachment committed to the current battle — resolved by {@link MissionLaunch}, kept for the battle UI / debug. */
    private Detachment detachment;
    /** Frozen outcome from the most recent applied mission — read by ResultsScreen. */
    private MissionOutcome lastOutcome;
    /** Frozen deterministic recovery roll; cargo settlement happens in the later picker. */
    private LootManifest lootManifest = LootManifest.EMPTY;
    /** Exactly-once gate + receipt for the current manifest's cargo settlement. */
    private boolean lootSettlementStarted;
    private LootSettlementPlan lootSettlement;
    private ScreenId currentScreen = ScreenId.MISSION_SELECT;
    /** Screen and ready-seat target associated with the current armory visit. */
    private ScreenId armoryReturnScreen = ScreenId.MISSION_SELECT;
    /** Back target for the retained company workspace inside an armory visit. */
    private ScreenId fleetArmoryWorkspaceBackScreen = ScreenId.FLEET_ARMORY_OVERVIEW;

    /** Mission lists cached per client so positions stay stable across re-layouts. */
    private final Map<String, List<Mission>> missionsByClient = new HashMap<>();

    private CompanyDeck companyDeck;
    /** The ship {@link #companyDeck} was generated for, so a transfer rebuilds it. */
    private String companyDeckShipId;

    public MarineOpsContext(PlanetAPI planet) {
        this.planet = planet;
        MarketAPI m = null;
        String tex = null;
        if (planet != null) {
            m = planet.getMarket();
            if (planet.getSpec() != null) {
                tex = planet.getSpec().getTexture();
            }
        }
        this.market = m;
        this.planetTexture = tex;
        this.clients = Collections.unmodifiableList(resolveClients(planet, m));
    }

    /**
     * The company ship's interior, or null for a player who owns no ship at all.
     *
     * <p>Held here because every room screen is a camera onto the same ship:
     * building one deck per screen would let the Mech Lab and a berthing screen
     * disagree about the vessel they are both aboard. Which rooms exist is read
     * off this, so a hull with no vehicle bay has no route to a Mech Lab.
     *
     * <p>Rebuilt when the company moves house, and only then. The deck follows
     * the designation rather than the session, so transferring changes what the
     * room screens show without either screen learning that a transfer
     * happened.
     */
    public CompanyDeck companyDeck() {
        FleetMemberAPI aboard = CompanyShipDesignation.aboard();
        String hull = aboard == null ? null : aboard.getId();
        if (companyDeck != null && Objects.equals(hull, companyDeckShipId)) return companyDeck;
        if (companyDeck != null) companyDeck.dismiss();
        companyDeckShipId = hull;
        CompanyShip ship = CompanyShipResolver.read(aboard);
        companyDeck = ship == null ? null : CompanyDeck.home(ship,
                CompanyShipDesignation.deckSeedFor(hull),
                new BattleSprites(), MarineOpsContext::companyLance,
                MarineOpsContext::companyMarines);
        return companyDeck;
    }

    /** The machines parked in the company ship's berths: whatever the player owns. */
    private static List<MechVariant> companyLance() {
        List<MechVariant> lance = new ArrayList<>();
        MarineRosterScript script = MarineRosterScript.getInstance();
        MarineRoster roster = script == null ? null : script.roster();
        // The player's own bay, not a fresh one: MechBay seeds a starter squad
        // in its constructor, so a new instance always parks the same machine
        // in the berths whatever the company actually owns.
        CampaignMechSquad squad = roster == null ? null : roster.mechBay().activeSquad();
        if (squad != null) {
            for (CampaignMech mech : squad.mechs()) lance.add(mech.variant());
        }
        return lance;
    }

    /**
     * The marines aboard: every fit member of a squad that is not away.
     *
     * <p>Away is the operative word. A squad on a stationing contract is
     * somewhere else in the sector, and putting it in the berthing would have
     * the player watch marines eat dinner on a ship they are not on. Everybody
     * else is home, whichever squad they belong to - the ship carries the
     * company, not the squad a screen happens to have selected.
     */
    private static List<MarineSoldier> companyMarines() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        return companyMarines(script != null ? script.roster() : null);
    }

    /** @see #companyMarines() */
    static List<MarineSoldier> companyMarines(MarineRoster roster) {
        if (roster == null) return List.of();
        List<MarineSoldier> aboard = new ArrayList<>();
        for (MarineSquad squad : roster.squads()) {
            if (squad.stationed()) continue;
            for (String id : roster.manningMemberIds(squad)) {
                MarineSoldier soldier = roster.soldierById(id);
                if (soldier != null && soldier.status() == MarineSoldierStatus.ACTIVE) {
                    aboard.add(soldier);
                }
            }
        }
        return List.copyOf(aboard);
    }

    /**
     * What the company ship has, for the room screens' navigation shell.
     *
     * <p>Three answers, because two would make the shell lie. A hull that
     * cannot hold a vehicle bay never will, and a hull whose deck is still
     * being laid out has one or does not and nobody knows yet — and the second
     * lasts a second or two. Reported as the first, it would tell a player
     * their capital had no bay.
     */
    MarineOpsPageNav.Aboard roomAboard(RoomPurpose purpose) {
        CompanyDeck ship = companyDeck();
        if (ship == null) return MarineOpsPageNav.Aboard.NO;
        if (!ship.ready()) return MarineOpsPageNav.Aboard.UNKNOWN;
        return ship.has(purpose)
                ? MarineOpsPageNav.Aboard.YES : MarineOpsPageNav.Aboard.NO;
    }

    public Client getSelectedClient() {
        return selectedClient;
    }

    public void setSelectedClient(Client client) {
        this.selectedClient = client;
    }

    public Mission getSelectedMission() {
        return selectedMission;
    }

    public void setSelectedMission(Mission mission) {
        if (selectedMission == null || mission == null || !selectedMission.id.equals(mission.id)) {
            selectedMarineSquadIds.clear();
            squadSelectionMissionId = null;
            marineSquadSelectionInitialized = false;
            marineDeploymentCapacity = 0;
        }
        this.selectedMission = mission;
    }

    public Set<String> getSelectedMarineSquadIds() {
        return Collections.unmodifiableSet(selectedMarineSquadIds);
    }

    public void setMarineDeploymentCapacity(int capacity) {
        marineDeploymentCapacity = Math.max(0, capacity);
        Mission mission = selectedMission;
        squadSelectionMissionId = mission != null ? mission.id : null;
    }

    public int getMarineDeploymentCapacity() { return marineDeploymentCapacity; }

    public DebugCompanyStage getDebugCompanyStage() {
        return debugCompanyStage;
    }

    public void cycleDebugCompanyStage() {
        debugCompanyStage = debugCompanyStage.next();
        debugSquadCount = -1;
        debugCompanyRoster = null;
    }

    /** Squads the debug company fields — the override when set, else the stage's own size. */
    public int getDebugSquadCount() {
        return debugSquadCount >= 0 ? debugSquadCount : debugCompanyStage.squads;
    }

    public void setDebugSquadCount(int squads) {
        int normalized = DebugCompany.normalizeSquads(squads);
        if (normalized == debugSquadCount) return;
        debugSquadCount = normalized;
        debugCompanyRoster = null;
    }

    /** The debug company at the selected stage. Detached — never the campaign roster. */
    public MarineRoster getDebugCompanyRoster() {
        if (debugCompanyRoster == null) {
            debugCompanyRoster = DebugCompany.roster(debugCompanyStage, getDebugSquadCount());
        }
        return debugCompanyRoster;
    }

    public boolean isMarineSquadSelected(String squadId) {
        return selectedMarineSquadIds.contains(squadId);
    }

    public void toggleMarineSquad(String squadId) {
        if (squadId == null) return;
        if (!selectedMarineSquadIds.remove(squadId)) selectedMarineSquadIds.add(squadId);
        markMarineSquadSelectionInitialized();
    }

    public void selectMarineSquad(String squadId) {
        if (squadId != null) {
            selectedMarineSquadIds.add(squadId);
            markMarineSquadSelectionInitialized();
        }
    }

    public void replaceMarineSquadSelection(Iterable<String> squadIds) {
        selectedMarineSquadIds.clear();
        if (squadIds != null) {
            for (String squadId : squadIds) {
                if (squadId != null) selectedMarineSquadIds.add(squadId);
            }
        }
        markMarineSquadSelectionInitialized();
    }

    public boolean hasInitializedMarineSquadSelectionFor(Mission mission) {
        return marineSquadSelectionInitialized && mission != null
                && mission.id.equals(squadSelectionMissionId);
    }

    public boolean hasSquadSelectionFor(Mission mission) {
        return mission != null && mission.id.equals(squadSelectionMissionId)
                && !selectedMarineSquadIds.isEmpty();
    }

    public ScreenId getCurrentScreen() {
        return currentScreen;
    }

    /** Request a screen transition; the plugin observes this and re-attaches. */
    /**
     * Show this screen, unless it is a room aboard a ship that is not ready to
     * be walked around.
     *
     * <p>A room view is a camera on a deck, and until she is laid out and
     * crewed there is nothing for it to be a camera on. She is got ready away
     * from the frame the player is looking at, so the honest thing is for the
     * route not to be taken — the shell shows those pages as being got ready
     * rather than as absent, and they open the moment she is. Guarded here
     * rather than at each button because there is more than one way to a room:
     * the navigation shell, headquarters' own tiles, and whatever comes next.
     */
    public void goTo(ScreenId screen) {
        if (screen != null && screen.aboard() && !shipReady()) return;
        this.currentScreen = screen;
    }

    /** Whether the company ship can be walked around yet. */
    private boolean shipReady() {
        CompanyDeck ship = companyDeck();
        return ship != null && ship.ready();
    }

    /** Opens the owned-company landing view used by the campaign Company HQ. */
    public void openCompanyArmoryFrom(ScreenId returnScreen) {
        armoryReturnScreen = returnScreen != null ? returnScreen : ScreenId.COMPANY_HQ;
        fleetArmoryWorkspaceBackScreen = ScreenId.FLEET_ARMORY_OVERVIEW;
        goTo(ScreenId.FLEET_ARMORY_OVERVIEW);
    }

    /** Enters one company's retained armory while preserving its immediate back target. */
    public void openFleetArmoryWorkspaceFrom(ScreenId backScreen) {
        fleetArmoryWorkspaceBackScreen = backScreen != null
                ? backScreen : ScreenId.FLEET_ARMORY_OVERVIEW;
        goTo(ScreenId.FLEET_ARMORY);
    }

    public void returnFromFleetArmoryWorkspace() {
        goTo(fleetArmoryWorkspaceBackScreen);
    }

    public void returnFromArmory() {
        goTo(armoryReturnScreen);
    }

    public String getSelectedCaptainId() {
        return selectedCaptainId;
    }

    public void setSelectedCaptainId(String captainId) {
        if (selectedCaptainId == null ? captainId != null
                : !selectedCaptainId.equals(captainId)) {
            selectedMarineSquadIds.clear();
            squadSelectionMissionId = null;
            marineSquadSelectionInitialized = false;
        }
        this.selectedCaptainId = captainId;
    }

    private void markMarineSquadSelectionInitialized() {
        Mission mission = selectedMission;
        squadSelectionMissionId = mission != null ? mission.id : null;
        marineSquadSelectionInitialized = mission != null;
    }

    public long getSelectedStationingContractId() {
        return selectedStationingContractId;
    }

    public void setSelectedStationingContractId(long contractId) {
        this.selectedStationingContractId = contractId;
    }

    /**
     * Resolves the selected captain id against the live roster. Returns null if
     * nothing is selected, the roster script isn't installed, or the captain id
     * no longer exists (e.g. dismissed mid-flight). Call sites should re-check
     * status if they need {@code ACTIVE}-only.
     */
    public MarineCaptain getSelectedCaptain() {
        if (selectedCaptainId == null) return null;
        MarineRosterScript script = MarineRosterScript.getInstance();
        if (script == null) return null;
        return script.roster().byId(selectedCaptainId);
    }

    public BattleSimulation getBattleSimulation() {
        return battleSimulation;
    }

    public void setBattleSimulation(BattleSimulation simulation) {
        setBattle(simulation, null, detachment);
    }

    /** Atomically replaces the active battle and all of its launch metadata. */
    public void setBattle(BattleSimulation simulation, BattleFixture fixture,
                          Detachment battleDetachment) {
        if (battleSimulation != null && battleSimulation != simulation) {
            battleSimulation.close();
        }
        this.battleSimulation = simulation;
        this.battleFixture = simulation != null ? fixture : null;
        this.detachment = simulation != null ? battleDetachment : null;
    }

    public BattleFixture getBattleFixture() {
        return battleFixture;
    }

    public Detachment getDetachment() {
        return detachment;
    }

    public void setDetachment(Detachment detachment) {
        this.detachment = detachment;
    }

    public MissionOutcome getLastOutcome() {
        return lastOutcome;
    }

    public void setLastOutcome(MissionOutcome outcome) {
        this.lastOutcome = outcome;
    }

    public LootManifest getLootManifest() {
        return lootManifest;
    }

    public void setLootManifest(LootManifest manifest) {
        this.lootManifest = manifest != null ? manifest : LootManifest.EMPTY;
        this.lootSettlementStarted = false;
        this.lootSettlement = null;
    }

    public boolean isLootSettlementStarted() {
        return lootSettlementStarted;
    }

    public LootSettlementPlan getLootSettlement() {
        return lootSettlement;
    }

    /** Claims the exactly-once settlement gate before any cargo mutation. */
    public boolean tryBeginLootSettlement() {
        if (lootSettlementStarted) return false;
        lootSettlementStarted = true;
        return true;
    }

    public void completeLootSettlement(LootSettlementPlan settlement) {
        this.lootSettlement = settlement;
    }

    /** Explicit Results-screen forfeit; closes the same gate without cargo mutation. */
    public void forfeitLoot() {
        if (!lootSettlementStarted) {
            lootSettlementStarted = true;
            lootSettlement = new LootSettlementPlan(Collections.emptyList());
        }
    }

    /** Clears the finished mission while preserving its closed settlement gate. */
    public void clearResolvedMission() {
        selectedMission = null;
        if (battleSimulation != null) battleSimulation.close();
        battleSimulation = null;
        battleFixture = null;
        detachment = null;
        lastOutcome = null;
        lootManifest = LootManifest.EMPTY;
        selectedMarineSquadIds.clear();
        squadSelectionMissionId = null;
        marineSquadSelectionInitialized = false;
        marineDeploymentCapacity = 0;
        missionsByClient.clear();
    }

    /**
     * Returns the mission list for this client at this planet, generating + caching
     * lazily. Cache key is the client's factionId so the same planet+client always
     * returns the same list across re-layouts (markers don't shuffle when the player
     * clicks around).
     */
    public List<Mission> getMissionsFor(Client client) {
        if (client == null) return Collections.emptyList();
        String key = client.identity();
        List<Mission> cached = missionsByClient.get(key);
        if (cached != null) return cached;
        List<Mission> generated;
        if (DISTRESS_CLIENT_FACTION_ID.equals(client.factionId)) {
            Mission eventMission = localCampaignEventMission();
            generated = eventMission != null
                    ? Collections.singletonList(eventMission)
                    : Collections.emptyList();
        } else {
            generated = Collections.unmodifiableList(
                    MissionGenerator.generate(planet, client));
        }
        missionsByClient.put(key, generated);
        return generated;
    }

    /** Magic factionId for the synthetic debug client. {@link MissionGenerator} branches on this. */
    public static final String DEBUG_CLIENT_FACTION_ID = "marines_debug_client";
    /** Local mission-only client for a committed civilian distress response. */
    public static final String DISTRESS_CLIENT_FACTION_ID =
            "marines_distress_net_client";

    private static List<Client> resolveClients(PlanetAPI planet, MarketAPI market) {
        List<Client> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        // Player faction is used to look up relationships against everyone else.
        FactionAPI player = Global.getSector() != null
                ? Global.getSector().getPlayerFaction()
                : null;

        // 0. Debug client — synthetic entry exposing every eligible
        //    MissionType × OperationTier pairing at medium risk for playtesting.
        //    Gated by DevConfig.DEBUG_CLIENT.
        if (com.dillon.starsectormarines.DevConfig.DEBUG_CLIENT && market != null) {
            out.add(new Client(DEBUG_CLIENT_FACTION_ID,
                    "DEBUG — All Missions",
                    null,                       // no crest sprite; ClientRowWidget falls back
                    RepLevel.NEUTRAL,
                    false, null));
            seen.add(DEBUG_CLIENT_FACTION_ID);
        }

        // 0b. A committed rescue is visible only at its frozen local market.
        // The synthetic client is always open: the player already paid the
        // event's voluntary commitment cost, so faction reputation cannot
        // relock deployment afterward.
        CampaignStateScript campaignScript = CampaignStateScript.getInstance();
        CampaignState campaignState = campaignScript != null
                ? campaignScript.state() : null;
        int rescueRow = market != null
                ? CivilianRescueLocalMission.committedRow(
                campaignState, market.getId()) : -1;
        int colonyRow = market != null
                ? SilentColonyLocalMission.committedRow(
                campaignState, market.getId()) : -1;
        if (market != null && (rescueRow >= 0 || colonyRow >= 0)) {
            String crest = market.getFaction() != null
                    ? market.getFaction().getCrest() : null;
            out.add(new Client(DISTRESS_CLIENT_FACTION_ID,
                    colonyRow >= 0
                            ? "Dead Letter — Silent Colony Expedition"
                            : "Distress Net — Civilian Evacuation",
                    crest,
                    RepLevel.NEUTRAL, false, null));
            seen.add(DISTRESS_CLIENT_FACTION_ID);
        }

        // 1. Planet's owning faction (if it has one)
        if (market != null && market.getFaction() != null) {
            FactionAPI mainFaction = market.getFaction();
            if (seen.add(mainFaction.getId())) {
                out.add(buildClient(mainFaction, player, false));
            }
        }

        // 2. Independent broker — always present
        FactionAPI independent = Global.getSector() != null
                ? Global.getSector().getFaction(Factions.INDEPENDENT)
                : null;
        if (independent != null && seen.add(independent.getId())) {
            out.add(buildClient(independent, player, true));
        }

        // 3. Pirate contact — always present
        FactionAPI pirates = Global.getSector() != null
                ? Global.getSector().getFaction(Factions.PIRATES)
                : null;
        if (pirates != null && seen.add(pirates.getId())) {
            out.add(buildClient(pirates, player, true));
        }

        // 4. Other factions with market presence in the same system
        if (planet != null) {
            StarSystemAPI system = planet.getStarSystem();
            if (system != null) {
                for (PlanetAPI p : system.getPlanets()) {
                    MarketAPI pm = p.getMarket();
                    if (pm == null || pm.getFaction() == null) continue;
                    String fid = pm.getFaction().getId();
                    if (seen.add(fid)) {
                        out.add(buildClient(pm.getFaction(), player, false));
                    }
                }
            }
        }

        // 5. Campaign-tier patron houses with offers, recoveries, or active
        //    stationing assignments at this market.
        //    One Client per patron — missions come from contracts[], not the
        //    industry catalog (MissionGenerator branches on patronHouseId).
        if (market != null) {
            appendPatronClients(out, market, player);
        }

        return out;
    }

    private Mission localCampaignEventMission() {
        if (market == null || planet == null) return null;
        CampaignStateScript script = CampaignStateScript.getInstance();
        CampaignState state = script != null ? script.state() : null;
        String factionId = market.getFaction() != null
                ? market.getFaction().getId() : null;
        Mission rescue = CivilianRescueLocalMission.find(
                state, market.getId(), planet.getName(), factionId);
        if (rescue != null) return rescue;
        return SilentColonyLocalMission.find(state, market.getId(),
                planet.getName(), factionId);
    }

    /**
     * Walks {@link CampaignState}'s contracts list, finds patrons with at least
     * one player-facing row at {@code market}, and appends them as patron
     * clients. Each patron appears once even if they have multiple rows.
     */
    private static void appendPatronClients(List<Client> out, MarketAPI market, FactionAPI player) {
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) return;
        CampaignState state = script.state();
        int marketSlot = state.marketRegistry.intern(market.getId());

        Set<Long> seenPatrons = new LinkedHashSet<>();
        Set<Long> mandatoryPatrons = new LinkedHashSet<>();
        for (int i = 0; i < state.contractCount; i++) {
            if (state.contractMarketId[i] != marketSlot) continue;
            ContractState contractState = ContractState.fromByte(state.contractState[i]);
            ContractType contractType = ContractType.fromByte(state.contractType[i]);
            boolean offered = contractState == ContractState.OFFERED;
            boolean activeStationing = (contractState == ContractState.ACTIVE
                    || contractState == ContractState.IN_PROGRESS)
                    && contractType.isStationing();
            boolean assaultInProgress = contractState == ContractState.IN_PROGRESS
                    && contractType == ContractType.PLANETARY_ASSAULT;
            if (!offered && !activeStationing && !assaultInProgress) continue;
            long patronId = state.contractPatronHouseId[i];
            if (contractType == ContractType.EXTRACTION
                    || activeStationing || assaultInProgress) {
                mandatoryPatrons.add(patronId);
            }
            if (!seenPatrons.add(patronId)) continue;
        }

        for (long patronId : seenPatrons) {

            int patronRow = state.houseIndex(patronId);
            if (patronRow < 0) continue;

            String factionId = state.factionRegistry.get(state.houseFactionId[patronRow]);
            String name = state.houseDisplayName[patronRow] != null
                    ? state.houseDisplayName[patronRow]
                    : ("house#" + patronId);

            FactionAPI faction = (factionId != null && Global.getSector() != null)
                    ? Global.getSector().getFaction(factionId)
                    : null;
            String crest = faction != null ? faction.getCrest() : null;
            RepLevel rep = (faction != null && player != null)
                    ? player.getRelationshipLevel(faction.getId())
                    : RepLevel.NEUTRAL;
            boolean locked = !mandatoryPatrons.contains(patronId)
                    && (rep.ordinal() <= RepLevel.HOSTILE.ordinal()
                        || !ContractEligibility.patronEligible(state, patronId));
            String lockReason = null;
            if (locked) {
                lockReason = rep.ordinal() <= RepLevel.HOSTILE.ordinal()
                        ? "clientLockedHostile" : "clientLockedCredibility";
            }

            out.add(new Client(factionId != null ? factionId : "patron",
                    name, crest, rep, locked, lockReason, patronId));
        }
    }

    /**
     * @param alwaysOpen when true, the client is never gated by reputation
     *                   (the Independent / Pirates exception).
     */
    private static Client buildClient(FactionAPI faction, FactionAPI player, boolean alwaysOpen) {
        RepLevel rep = player != null
                ? player.getRelationshipLevel(faction.getId())
                : RepLevel.NEUTRAL;

        boolean locked = false;
        String lockReason = null;
        if (!alwaysOpen) {
            // Locked when player is HOSTILE or worse. NEUTRAL/SUSPICIOUS still work.
            if (rep.ordinal() <= RepLevel.HOSTILE.ordinal()) {
                locked = true;
                lockReason = "clientLockedHostile";
            }
        }

        return new Client(
                faction.getId(),
                faction.getDisplayName(),
                faction.getCrest(),
                rep,
                locked,
                lockReason);
    }
}

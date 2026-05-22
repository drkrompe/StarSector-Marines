package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    /** Current battle simulation — built by BriefingScreen.onAccept, read by BattleScreen. */
    private BattleSimulation battleSimulation;
    /** Frozen outcome from the most recent applied mission — read by ResultsScreen. */
    private MissionOutcome lastOutcome;
    private ScreenId currentScreen = ScreenId.MISSION_SELECT;

    /** Mission lists cached per client so positions stay stable across re-layouts. */
    private final Map<String, List<Mission>> missionsByClient = new HashMap<>();

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
        this.selectedMission = mission;
    }

    public ScreenId getCurrentScreen() {
        return currentScreen;
    }

    /** Request a screen transition; the plugin observes this and re-attaches. */
    public void goTo(ScreenId screen) {
        this.currentScreen = screen;
    }

    public String getSelectedCaptainId() {
        return selectedCaptainId;
    }

    public void setSelectedCaptainId(String captainId) {
        this.selectedCaptainId = captainId;
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
        this.battleSimulation = simulation;
    }

    public MissionOutcome getLastOutcome() {
        return lastOutcome;
    }

    public void setLastOutcome(MissionOutcome outcome) {
        this.lastOutcome = outcome;
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
        List<Mission> generated = Collections.unmodifiableList(
                MissionGenerator.generate(planet, client));
        missionsByClient.put(key, generated);
        return generated;
    }

    private static List<Client> resolveClients(PlanetAPI planet, MarketAPI market) {
        List<Client> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        // Player faction is used to look up relationships against everyone else.
        FactionAPI player = Global.getSector() != null
                ? Global.getSector().getPlayerFaction()
                : null;

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

        // 5. Campaign-tier patron houses with OFFERED contracts at this market.
        //    One Client per patron — missions come from contracts[], not the
        //    industry catalog (MissionGenerator branches on patronHouseId).
        if (market != null) {
            appendPatronClients(out, market, player);
        }

        return out;
    }

    /**
     * Walks {@link CampaignState}'s contracts list, finds patrons with at least
     * one {@code OFFERED} row at {@code market}, and appends them as patron
     * clients. Each patron appears once even if they have multiple offers.
     */
    private static void appendPatronClients(List<Client> out, MarketAPI market, FactionAPI player) {
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) return;
        CampaignState state = script.state();
        int marketSlot = state.marketRegistry.intern(market.getId());

        Set<Long> seenPatrons = new LinkedHashSet<>();
        for (int i = 0; i < state.contractCount; i++) {
            if (ContractState.fromByte(state.contractState[i]) != ContractState.OFFERED) continue;
            if (state.contractMarketId[i] != marketSlot) continue;
            long patronId = state.contractPatronHouseId[i];
            if (!seenPatrons.add(patronId)) continue;

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
            boolean locked = rep.ordinal() <= RepLevel.HOSTILE.ordinal();
            String lockReason = locked ? "clientLockedHostile" : null;

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

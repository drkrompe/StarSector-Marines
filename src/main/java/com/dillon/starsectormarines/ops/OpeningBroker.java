package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.Global;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import java.util.Comparator;
import java.util.List;

/** Authored contact and shared location policy for the opening operation sequence. */
public final class OpeningBroker {
    public static final String ID = "marines_mara_venn";
    public static final String NAME = "Mara Venn";
    public static final String PORTRAIT = "graphics/portraits/portrait31.png";
    public static final String CREST = "graphics/factions/crest_neutral_traders.png";
    public static final String INTRODUCTION = "I place ground crews for the Wayline freight cooperative. "
            + "We pay our own security bills; I don't speak for the planetary government. "
            + "Keep our militia on its feet, and there will be more work.";

    private OpeningBroker() { }

    /** Current reachable office. Reads campaign facts without creating contacts or save flags. */
    public static Destination current() {
        return resolve(Global.getSector());
    }

    static Destination resolve(SectorAPI sector) {
        if (sector == null || sector.getEconomy() == null) return null;
        FactionAPI player = sector.getPlayerFaction();
        List<MarketAPI> eligible = sector.getEconomy().getMarketsCopy().stream()
                .filter(market -> market != null && !market.isHidden() && market.getSize() > 0)
                .filter(market -> market.getPlanetEntity() != null && !market.isPlayerOwned())
                .filter(market -> market.getFaction() != null
                        && (player == null || !market.getFaction().isHostileTo(player)))
                .toList();
        MarketAPI market = eligible.stream().min(Comparator
                .comparingInt((MarketAPI candidate) -> "jangala".equals(candidate.getPlanetEntity().getId()) ? 0
                        : Factions.INDEPENDENT.equals(candidate.getFactionId()) ? 1 : 2)
                .thenComparing(MarketAPI::getId)).orElse(null);
        if (market == null) return null;
        PlanetAPI planet = market.getPlanetEntity();
        return new Destination(planet.getId(), planet.getName(),
                planet.getStarSystem() == null ? "Local space" : planet.getStarSystem().getBaseName(), planet);
    }

    public static boolean isAt(PlanetAPI planet) {
        Destination destination = current();
        return planet != null && destination != null && destination.planetId().equals(planet.getId());
    }

    static Client present(Client client, boolean atOffice) {
        if (!atOffice || client.patronHouseId != -1L || !Factions.INDEPENDENT.equals(client.factionId)) return client;
        return new Client(client.factionId, NAME, CREST, client.repLevel,
                client.locked, client.lockReason, -1L, ID);
    }

    static boolean introductionPending() {
        MarineRosterScript script = Global.getSector() == null ? null : MarineRosterScript.getInstance();
        return script != null && !script.roster().hasCompletedStory(OpeningOperationKind.COUNTERATTACK.missionId);
    }

    static String referral(Client client, MarineRoster roster, Destination destination) {
        if (client == null || isContact(client) || client.patronHouseId != -1L
                || !Factions.INDEPENDENT.equals(client.factionId) || roster == null || destination == null
                || roster.hasCompletedStory(OpeningOperationKind.COUNTERATTACK.missionId)) return "";
        return "For your first contracts, contact " + NAME + " on " + destination.label()
                + ". Open Marine operations there and choose her name. Complete her relief and depot jobs "
                + "to open repeatable militia work with other Independent brokers.";
    }

    static boolean isContact(Client client) {
        return client != null && ID.equals(client.contactId);
    }

    public record Destination(String planetId, String planetName, String systemName, PlanetAPI planet) {
        public String label() { return planetName + " / " + systemName; }
    }
}

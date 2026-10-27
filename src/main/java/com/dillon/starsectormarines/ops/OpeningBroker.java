package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import com.dillon.starsectormarines.campaign.start.OpeningPort;
import com.dillon.starsectormarines.campaign.start.OpeningPort.Use;
import com.fs.starfarer.api.Global;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

/** Authored contact and shared location policy for the opening operation sequence. */
public final class OpeningBroker {
    public static final String ID = "marines_mara_venn";
    public static final String NAME = "Zoey Venn";
    public static final String PORTRAIT = "graphics/portraits/mara-venn.png";
    public static final String CREST = "graphics/factions/crest_neutral_traders.png";

    private OpeningBroker() { }

    /**
     * Current reachable office: the company's own port (Second Wind's yard or depot) when it can be an office, else
     * the shared {@link OpeningPort} order. Reads campaign facts without creating contacts or save flags.
     */
    public static Destination current() {
        SectorAPI sector = Global.getSector();
        PlanetAPI port = CerberusCompanyStart.destination();
        if (sector != null && port != null
                && OpeningPort.eligible(port.getMarket(), sector.getPlayerFaction(), Use.OFFICE)) return of(port);
        MarketAPI market = OpeningPort.resolve(sector, Use.OFFICE, CerberusCompanyStart.homeSystem());
        return market == null ? null : of(market.getPlanetEntity());
    }

    static Destination resolve(SectorAPI sector) {
        MarketAPI market = OpeningPort.resolve(sector, Use.OFFICE);
        return market == null ? null : of(market.getPlanetEntity());
    }

    private static Destination of(PlanetAPI planet) {
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

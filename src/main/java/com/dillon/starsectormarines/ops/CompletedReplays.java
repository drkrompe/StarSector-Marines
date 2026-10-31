package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.DebugMissionCatalog.Scenario;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RepLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Replays of campaign chapters the company has already won, offered by a contact the
 * mission board shows only while its "Replay Story Missions" toggle is on.
 *
 * <p>A replay is the chapter's battle as the debug catalogue builds it, flown by the
 * real company and paid the chapter's credits on victory. A chapter replays only on the
 * planet it was fought on, so the contact lists only that planet's chapters. It never touches the arc that
 * owns the chapter: no progress, story completion, award, mark or debrief. The Khettara
 * Bloom offers its own replays and is left out.
 */
public final class CompletedReplays {
    public static final String CLIENT_FACTION_ID = "marines_replay_completed_client";
    private static final String PREFIX = "replay_completed:";
    private static final String ENABLED_KEY = "marines_replay_completed";
    private static final String ASHARU = "asharu", AGREUS = "agreus";
    /** Working Passage pays its crew in the ship rather than credits; a replay pays Cold Start's fee. */
    private static final int WORKING_PASSAGE_PAYOUT = ShepherdRecoveryProgress.COLD_PAYMENT;

    private CompletedReplays() { }

    /** Whether the board shows the replay contact; saved with the game, off by default. */
    public static boolean enabled() {
        Map<String, Object> data = persistentData();
        return data != null && Boolean.TRUE.equals(data.get(ENABLED_KEY));
    }

    public static void setEnabled(boolean enabled) {
        Map<String, Object> data = persistentData();
        if (data == null) return;
        if (enabled) data.put(ENABLED_KEY, true);
        else data.remove(ENABLED_KEY);
    }

    private static Map<String, Object> persistentData() {
        return Global.getSector() == null ? null : Global.getSector().getPersistentData();
    }

    public static Client client() {
        return new Client(CLIENT_FACTION_ID, Strings.get("replayCompletedContactName"), OpeningBroker.CREST,
                RepLevel.NEUTRAL, false, null);
    }

    public static boolean isClient(Client client) {
        return client != null && CLIENT_FACTION_ID.equals(client.factionId);
    }

    /** One replay per chapter the roster has won on {@code planet}, in campaign order. */
    public static List<Mission> offers(MarineRoster roster, PlanetAPI planet) {
        if (planet == null) return List.of();
        return offers(roster, planet.getId(), planet.getName(), CompletedReplays::siteId);
    }

    static List<Mission> offers(MarineRoster roster, String planetId, String planetName, Function<Scenario, String> site) {
        if (roster == null || planetId == null) return List.of();
        List<Mission> out = new ArrayList<>();
        for (Scenario scenario : completed(roster.completedStoryIds()))
            if (planetId.equals(site.apply(scenario))) out.add(mission(scenario, planetName));
        return List.copyOf(out);
    }

    /** Whether {@code scenario}'s chapter replays on {@code planet}. */
    public static boolean at(Scenario scenario, PlanetAPI planet) {
        return scenario != null && planet != null && planet.getId().equals(siteId(scenario));
    }

    /**
     * The planet the chapter was fought on, or null where the campaign no longer names it: the opening
     * at the broker's office port, the supply route at its frozen site, and every later arc at its fixed world.
     */
    static String siteId(Scenario scenario) {
        return switch (scenario) {
            case RELIEF, COUNTERATTACK -> {
                OpeningBroker.Destination office = OpeningBroker.current();
                yield office == null ? null : office.planetId();
            }
            case JUNCTION, THUNDER_RUN -> {
                BrokenSupplyRouteProgress route = SupplyRouteCampaign.current();
                yield route == null || !route.readable() ? null : route.planetId();
            }
            default -> fixedSiteId(scenario);
        };
    }

    /** The site of a chapter whose world never moves. */
    static String fixedSiteId(Scenario scenario) {
        if (scenario.corvus() != null) return scenario.corvus().planetId;
        return switch (scenario) {
            case SALVAGE_RIGHTS, COLD_START, WORKING_PASSAGE -> ASHARU;
            case WATER_UNDER_SEAL, LIMITED_WARRANT, YARD_OWNERS, TERMS_OF_PROTECTION, SMALL_WAR -> AGREUS;
            default -> null;
        };
    }

    /** The replayable chapters among {@code storyIds}, the roster's won story mission ids. */
    static List<Scenario> completed(Set<String> storyIds) {
        List<Scenario> out = new ArrayList<>();
        if (storyIds == null) return out;
        for (Scenario scenario : Scenario.values())
            if (storyIds.stream().anyMatch(id -> wonBy(scenario, id))) out.add(scenario);
        return out;
    }

    /** Whether the story mission {@code id} was an attempt at {@code scenario}'s chapter. */
    static boolean wonBy(Scenario scenario, String id) {
        if (id == null) return false;
        String route = BrokenSupplyRouteProgress.PREFIX;
        return switch (scenario) {
            case RELIEF -> OpeningOperationKind.RELIEF.missionId.equals(id);
            case COUNTERATTACK -> OpeningOperationKind.COUNTERATTACK.missionId.equals(id);
            case JUNCTION -> id.startsWith(route + BrokenSupplyRouteProgress.JUNCTION + "_");
            case THUNDER_RUN -> id.startsWith(route + BrokenSupplyRouteProgress.THUNDER + "_");
            case DUST_LEDGER, BLIND_WATCH, COLD_ACCOUNT ->
                    id.startsWith(CorvusWaylineProgress.PREFIX) && CorvusWaylineProgress.forMission(id) == scenario.corvus();
            case SALVAGE_RIGHTS -> id.startsWith(ShepherdRecoveryProgress.PREFIX);
            case COLD_START -> id.startsWith(ShepherdRecoveryProgress.COLD_PREFIX);
            case WORKING_PASSAGE -> id.startsWith(ShepherdRecoveryProgress.PASSAGE_PREFIX);
            case WATER_UNDER_SEAL -> id.startsWith(AgreusArcProgress.PREFIX);
            case LIMITED_WARRANT -> id.startsWith(AgreusArcProgress.WARRANT_PREFIX);
            case YARD_OWNERS -> id.startsWith(AgreusArcProgress.YARD_PREFIX);
            case TERMS_OF_PROTECTION -> id.startsWith(AgreusArcProgress.TERMS_PREFIX);
            case SMALL_WAR -> id.startsWith(AgreusArcProgress.SMALL_WAR_PREFIX);
            default -> false;
        };
    }

    /** What the chapter paid on its first victory. */
    static int payout(Scenario scenario) {
        return switch (scenario) {
            case RELIEF -> OpeningOperationKind.RELIEF.payout;
            case COUNTERATTACK -> OpeningOperationKind.COUNTERATTACK.payout;
            case JUNCTION -> SupplyRouteCampaign.CAPTURE_PAYMENT;
            case THUNDER_RUN -> SupplyRouteCampaign.THUNDER_PAYMENT;
            case DUST_LEDGER, BLIND_WATCH, COLD_ACCOUNT -> scenario.corvus().payment;
            case SALVAGE_RIGHTS -> ShepherdRecoveryProgress.PAYMENT;
            case COLD_START -> ShepherdRecoveryProgress.COLD_PAYMENT;
            case WORKING_PASSAGE -> WORKING_PASSAGE_PAYOUT;
            case WATER_UNDER_SEAL -> AgreusArcProgress.PAYMENT;
            case LIMITED_WARRANT -> AgreusArcProgress.WARRANT_PAYMENT;
            case YARD_OWNERS -> AgreusArcProgress.YARD_PAYMENT;
            case TERMS_OF_PROTECTION -> AgreusArcProgress.TERMS_PAYMENT;
            case SMALL_WAR -> AgreusArcProgress.SMALL_WAR_PAYMENT;
            default -> 0;
        };
    }

    static Mission mission(Scenario scenario, String planetName) {
        Mission.Builder builder = Mission.builder(scenario.mission())
                .id(PREFIX + scenario.name()).source(MissionSource.REPLAY).targetPlanetName(planetName)
                .name(Strings.format("replayCompletedName", Strings.resolve(scenario.title)))
                .payout(payout(scenario))
                .requirements(Strings.format("replayCompletedRequirements", scenario.squads));
        // The opening chapters fly with the employer's militia shuttle, as on first play.
        if (scenario == Scenario.RELIEF) builder.requiredDrops(1).employerShuttles(1);
        if (scenario == Scenario.COUNTERATTACK) builder.requiredDrops(3).employerShuttles(1);
        return builder.build();
    }

    /** The chapter {@code m} replays, or null for any other mission. */
    public static Scenario scenario(Mission m) {
        if (m == null || m.source != MissionSource.REPLAY || m.id == null || !m.id.startsWith(PREFIX)) return null;
        try { return Scenario.valueOf(m.id.substring(PREFIX.length())); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}

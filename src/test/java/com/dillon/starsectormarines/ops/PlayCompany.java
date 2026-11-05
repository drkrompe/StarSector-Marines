package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.backend.CampaignBackend;
import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import com.dillon.starsectormarines.campaign.start.MercenaryCompanyConversion;
import com.dillon.starsectormarines.campaign.start.OpeningPort;
import com.dillon.starsectormarines.marine.BoatDeck;
import com.dillon.starsectormarines.marine.CampaignBoat;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.OfficerInduction;
import com.dillon.starsectormarines.marine.OfficerSpecialty;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.ops.detachment.DebugCompany;
import com.dillon.starsectormarines.ops.detachment.DebugCompanyStage;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.MutableMarketStatsAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberStatusAPI;
import com.fs.starfarer.api.fleet.RepairTrackerAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.DynamicStatsAPI;
import com.fs.starfarer.api.util.MutableValue;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The campaign {@code playBattle} stands in for: a veteran company aboard the converted Second Wind, docked at one of a few worlds,
 * with a purse and
 * stores that do not run out, answered through {@link CampaignBackend}. Enough for the company's rooms to open with the
 * cheats on; it is not a simulation of the campaign, and nothing here advances. {@link PlaySaves} writes it out.
 *
 * <p>{@link #startOver} instead begins as a new game does once the Second Wind's conversion is paid: Morgan Hale alone,
 * the company's preparation lessons ahead, the opening's purse and hold, and the cheats off.
 *
 * <p>The game's types are answered with proxies, as the tests do. A question not listed answers empty: no, zero, an
 * empty list or null.
 */
final class PlayCompany {

    static final String HULL_ID = "cerberus";
    private static final String SHIP_ID = "play_second_wind";
    private static final String SHIP_NAME = "Second Wind";
    /** More than any room asks for at once, so nothing is ever short. */
    private static final float PLENTY = 99_999f;
    private static final float CREDITS = 10_000_000f;
    /** The converted Cerberus's own numbers: {@code SecondWindDressingSnapshotSuite}'s. */
    private static final float MIN_CREW = 20f;
    private static final float MAX_CREW = 68f;
    private static final float CARGO = 75f;
    private static final float READINESS = 0.7f;
    private static final String ARCADIA = "Arcadia";
    private static final Map<String, StarSystemAPI> SYSTEMS = new LinkedHashMap<>();
    private static final Map<String, List<PlanetAPI>> PLANETS = new HashMap<>();
    /** The story's worlds, by the ids the mod finds them with; Jangala first, where a run starts. */
    private static final List<PlanetAPI> WORLDS = List.of(
            newWorld(OpeningPort.JANGALA, "Jangala", OpeningPort.CORVUS, "jungle", "graphics/planets/jungle.jpg"),
            newWorld("asharu", "Asharu", OpeningPort.CORVUS, "desert", "graphics/planets/desert.jpg"),
            newWorld("corvus_IIIb", "Warion", OpeningPort.CORVUS, "barren", "graphics/planets/barren00.jpg"),
            newWorld("corvus_IIIa", "Garnir", OpeningPort.CORVUS, "cryovolcanic", "graphics/planets/cryovolcanic01.jpg"),
            newWorld("agreus", "Agreus", ARCADIA, "barren", "graphics/planets/barren02.jpg"));
    private static volatile PlanetAPI docked = WORLDS.get(0);
    private static final MutableValue HELD_CREDITS = new MutableValue(CREDITS);
    private static final List<FleetMemberAPI> MEMBERS = new ArrayList<>();
    private static MarineRoster roster;
    private static CampaignStateScript campaign;
    private static Map<String, Object> saved;
    /** What the hold has, by commodity; null is stores that do not run out. */
    private static Map<String, Float> stores;

    private PlayCompany() {
    }

    /** Leaves the company's shuttles as a battle would: one or all under repair, or every berth empty. */
    static void boats(String state) {
        BoatDeck deck = roster.boatDeck();
        ShipsBoatsAboard.reconcile(deck);
        List<String> ids = deck.boats().stream().filter(Objects::nonNull).map(CampaignBoat::id).toList();
        Map<String, Float> damage = new HashMap<>();
        switch (state) {
            case "one-repairing" -> damage.put(ids.get(0), 0.5f);
            case "all-repairing" -> ids.forEach(id -> damage.put(id, 0.5f));
            case "none" -> deck.lose(ids);
            default -> throw new IllegalArgumentException("unknown boats state: " + state);
        }
        deck.settleRecovery("play-" + state, damage, List.of(), 0f);
    }

    /** Puts a story mission behind the company, so the board offers what follows it. */
    static void storyDone(String missionId) {
        roster.markStoryComplete(missionId);
    }

    /** Installs the stand-in as the campaign backend, for the rest of the run. */
    static void install() {
        CampaignFleetAPI fleet = fleet();
        restore(veteran());
        MarineRosterScript script = new MarineRosterScript() {
            @Override public MarineRoster roster() {
                return roster;
            }
        };
        CampaignBackend.useSavedData(() -> saved);
        CampaignBackend.useCompanyFleet(() -> fleet);
        CampaignBackend.useSectorScripts(() -> List.of(script, campaign));
        CampaignBackend.useDockedPort(() -> docked.getMarket());
        CampaignBackend.useYardPort(() -> docked.getMarket());
        CampaignBackend.useStarSystems(SYSTEMS::get);
        CampaignBackend.useShipYard(PlayCompany::newShip);
        CampaignBackend.useMarkets(new CampaignBackend.Markets() {
            @Override public boolean running() {
                return true;
            }

            @Override public List<MarketAPI> all() {
                return WORLDS.stream().map(PlanetAPI::getMarket).toList();
            }

            @Override public MarketAPI byId(String id) {
                return all().stream().filter(market -> market.getId().equals(id)).findFirst().orElse(null);
            }
        });
        Map<String, FactionAPI> factions = new HashMap<>();
        CampaignBackend.useFactions(new CampaignBackend.Factions() {
            @Override public FactionAPI player() {
                return byId("player");
            }

            @Override public FactionAPI byId(String id) {
                return factions.computeIfAbsent(id, known -> faction(known, known));
            }
        });
    }

    /**
     * Starts the campaign over as a new game does, with nothing but its founding officer, as {@code ensureMarineRoster}
     * founds it. The Marine Operations panel then moves the company aboard. No dialog may still be open over the old one.
     */
    static void startOver() {
        PlaySaves.State state = campaign(new MarineRoster());
        MarineCaptain founder = new MarineCaptain("Morgan Hale", OfficerInduction.MORGAN_PORTRAIT, Rank.LIEUTENANT, 0f);
        state.roster.add(founder);
        state.roster.beginCompanyPreparation(founder);
        state.credits = CerberusCompanyStart.STARTING_CREDITS;
        state.stores = new HashMap<>();
        state.stores.put(Commodities.CREW, (float) Math.ceil(MIN_CREW) + CerberusCompanyStart.SURPLUS_CREW);
        state.stores.put(Commodities.SUPPLIES, (float) CerberusCompanyStart.TRAVEL_SUPPLIES);
        state.stores.put(Commodities.FUEL, (float) CerberusCompanyStart.STARTING_FUEL);
        restore(state);
    }

    /** The campaign a run with no save starts on. */
    private static PlaySaves.State veteran() {
        MarineRoster company = DebugCompany.seeded(DebugCompanyStage.VETERAN_COMPANY,
                DebugCompanyStage.VETERAN_COMPANY.squads, 1L);
        company.setCompanyShip(SHIP_ID, SHIP_NAME, Math.round(CARGO));
        return campaign(company);
    }

    /** A campaign around this company, docked where a run starts. */
    private static PlaySaves.State campaign(MarineRoster company) {
        PlaySaves.State state = new PlaySaves.State();
        state.roster = company;
        state.campaign = new CampaignStateScript();
        state.saved = new HashMap<>();
        state.saved.put(CerberusCompanyStart.SHIP_KEY, SHIP_ID);
        state.saved.put(CerberusCompanyStart.START_PORT_KEY, WORLDS.get(0).getMarket().getId());
        state.docked = WORLDS.get(0).getId();
        state.credits = CREDITS;
        state.awarded = List.of();
        return state;
    }

    /**
     * The founding field commander, as the game's start adds them, with the lesson already behind the company: one
     * specialty trained, the rest of the rank's points left to spend, and the line squads under their command. A debug
     * company has no officers of its own, and a save from before this has none either, so a load comes through here too.
     */
    private static void commissionFieldCommander(MarineRoster company) {
        if (company.size() > 0) return;
        DebugCompanyStage stage = DebugCompanyStage.VETERAN_COMPANY;
        MarineCaptain commander = new MarineCaptain("Morgan Hale", OfficerInduction.MORGAN_PORTRAIT, stage.officerRank, 0f);
        company.add(commander);
        company.beginOfficerInduction(commander);
        commander.train(OfficerSpecialty.TACTICAL_COVER);
        List<String> line = DebugCompany.lineSquadIds(company);
        company.assignCaptainToSquads(commander.id(),
                line.subList(0, Math.min(line.size(), stage.officerRank.squadCommandCap())));
        OfficerInduction lesson = company.officerInduction();
        lesson.introduce(commander);
        lesson.finish(company);
    }

    private static CampaignFleetAPI fleet() {
        MEMBERS.add(secondWind());
        Map<String, Object> data = new HashMap<>();
        data.put("getMembersListCopy", (Answer) args -> new ArrayList<>(MEMBERS));
        data.put("getMembersInPriorityOrder", (Answer) args -> new ArrayList<>(MEMBERS));
        data.put("addFleetMember", (Answer) args -> MEMBERS.add((FleetMemberAPI) args[0]));
        data.put("removeFleetMember", (Answer) args -> MEMBERS.remove(args[0]));
        Map<String, Object> fleet = new HashMap<>();
        fleet.put("getFleetData", stub(FleetDataAPI.class, data));
        fleet.put("getCargo", cargo());
        return stub(CampaignFleetAPI.class, fleet);
    }

    private static CargoAPI cargo() {
        Map<String, Object> cargo = new HashMap<>();
        cargo.put("getCredits", HELD_CREDITS);
        cargo.put("getMothballedShips", stub(FleetDataAPI.class, Map.of()));
        for (String plenty : List.of("getMaxCapacity", "getSpaceLeft", "getMaxFuel", "getFreeFuelSpace",
                "getMaxPersonnel", "getFreeCrewSpace")) {
            cargo.put(plenty, PLENTY);
        }
        cargo.put("getCommodityQuantity", (Answer) args -> held((String) args[0]));
        cargo.put("addCommodity", (Answer) args -> stow((String) args[0], ((Number) args[1]).floatValue()));
        cargo.put("removeCommodity", (Answer) args -> stow((String) args[0], -((Number) args[1]).floatValue()));
        Map<String, String> named = Map.of("Marines", Commodities.MARINES, "Crew", Commodities.CREW,
                "Supplies", Commodities.SUPPLIES, "Fuel", Commodities.FUEL);
        named.forEach((name, commodity) -> {
            cargo.put("get" + name, (Answer) args -> held(commodity));
            cargo.put("add" + name, (Answer) args -> stow(commodity, ((Number) args[0]).floatValue()));
            cargo.put("remove" + name, (Answer) args -> stow(commodity, -((Number) args[0]).floatValue()));
        });
        return stub(CargoAPI.class, cargo);
    }

    private static float held(String commodity) {
        return stores == null ? PLENTY : stores.getOrDefault(commodity, 0f);
    }

    /** Null, as the void calls that come through here answer. */
    private static Object stow(String commodity, float change) {
        if (stores != null) stores.put(commodity, Math.max(0f, held(commodity) + change));
        return null;
    }

    /** The campaign as it stands, to write out. */
    static PlaySaves.State snapshot() {
        PlaySaves.State state = new PlaySaves.State();
        state.roster = roster;
        state.campaign = campaign;
        state.saved = saved;
        state.docked = docked.getId();
        state.credits = HELD_CREDITS.get();
        state.stores = stores;
        state.awarded = new ArrayList<>();
        for (FleetMemberAPI member : MEMBERS.subList(1, MEMBERS.size())) {
            PlaySaves.Ship ship = new PlaySaves.Ship();
            ship.id = member.getId();
            ship.variantId = member.getVariant().getHullVariantId();
            ship.name = member.getShipName();
            state.awarded.add(ship);
        }
        return state;
    }

    /** Puts a saved campaign in place. No dialog may still be open over the old one. */
    static void restore(PlaySaves.State state) {
        roster = state.roster;
        commissionFieldCommander(roster);
        campaign = state.campaign;
        saved = state.saved;
        HELD_CREDITS.set(state.credits);
        stores = state.stores;
        docked = WORLDS.stream().filter(world -> world.getId().equals(state.docked)).findFirst().orElse(WORLDS.get(0));
        MEMBERS.subList(1, MEMBERS.size()).clear();
        for (PlaySaves.Ship ship : state.awarded) {
            FleetMemberAPI member = newShip(ship.variantId, ship.id);
            member.setShipName(ship.name);
            MEMBERS.add(member);
        }
    }

    /** The worlds a run can dock at. */
    static List<PlanetAPI> worlds() {
        return WORLDS;
    }

    /** Where the fleet is docked, whose store and yard are open. */
    static PlanetAPI docked() {
        return docked;
    }

    static void dockAt(PlanetAPI world) {
        docked = world;
    }

    private static PlanetAPI newWorld(String id, String name, String system, String type, String skin) {
        Map<String, Object> industrySpec = new HashMap<>();
        industrySpec.put("hasTag", (Answer) args -> "spaceport".equals(args[0]));
        Map<String, Object> industry = new HashMap<>();
        industry.put("getSpec", stub(IndustrySpecAPI.class, industrySpec));
        industry.put("isFunctional", true);

        Map<String, Object> port = new HashMap<>();
        port.put("getId", id + "_market");
        port.put("getName", name);
        port.put("getSize", 6);
        port.put("getFactionId", "independent");
        port.put("getFaction", faction("independent", "Independent"));
        port.put("getIndustries", List.of(stub(Industry.class, industry)));
        // What vanilla's own defender strength reads off a market, with nothing modifying it.
        Map<String, Object> dynamic = new HashMap<>();
        dynamic.put("getMod", new StatBonus());
        port.put("getStats", stub(MutableMarketStatsAPI.class, Map.of("getDynamic", stub(DynamicStatsAPI.class, dynamic))));
        port.put("getMemoryWithoutUpdate", stub(MemoryAPI.class, Map.of()));
        MarketAPI market = stub(MarketAPI.class, port);

        Map<String, Object> texture = new HashMap<>();
        texture.put("getTexture", skin);
        // Vanilla's jungle, so the Engineering backdrop has clouds and air to draw in the window.
        if ("jungle".equals(type)) {
            texture.put("getTilt", -40f);
            texture.put("getPitch", 12f);
            texture.put("getRotation", -2.6f);
            texture.put("getCloudRotation", -6f);
            texture.put("getAtmosphereThickness", 0.32f);
            texture.put("getAtmosphereColor", new Color(70, 110, 130, 235));
            texture.put("getCloudTexture", "graphics/planets/clouds_banded01.png");
            texture.put("getCloudColor", new Color(70, 115, 140, 235));
        }
        Map<String, Object> planet = new HashMap<>();
        planet.put("getId", id);
        planet.put("getName", name);
        planet.put("getTypeId", type);
        planet.put("getMarket", market);
        planet.put("getSpec", stub(PlanetSpecAPI.class, texture));
        PlanetAPI world = stub(PlanetAPI.class, planet);
        port.put("getPlanetEntity", world);
        port.put("getPrimaryEntity", world);

        List<PlanetAPI> neighbours = PLANETS.computeIfAbsent(system, known -> new ArrayList<>());
        neighbours.add(world);
        planet.put("getStarSystem", SYSTEMS.computeIfAbsent(system, known -> {
            Map<String, Object> answers = new HashMap<>();
            answers.put("getBaseName", known);
            answers.put("getName", known);
            answers.put("getPlanets", neighbours);
            return stub(StarSystemAPI.class, answers);
        }));
        return world;
    }

    private static FactionAPI faction(String id, String name) {
        Map<String, Object> faction = new HashMap<>();
        faction.put("getId", id);
        faction.put("getDisplayName", name);
        faction.put("getCrest", "graphics/factions/crest_neutral_traders.png");
        faction.put("getRelationshipLevel", RepLevel.NEUTRAL);
        return stub(FactionAPI.class, faction);
    }

    /** The hull she is, for reading her deck's outline off her file. Cerberus's data lives in warhound.ship. */
    static ShipHullSpecAPI hull() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("getHullId", HULL_ID);
        spec.put("getBaseHullId", HULL_ID);
        spec.put("getHullName", "Cerberus");
        spec.put("getDesignation", "Combat Freighter");
        spec.put("getHullSize", ShipAPI.HullSize.FRIGATE);
        spec.put("getSpriteName", "graphics/ships/cerberus/cerberus_base.png");
        spec.put("getShipFilePath", "data/hulls/warhound.ship");
        return stub(ShipHullSpecAPI.class, spec);
    }

    private static FleetMemberAPI secondWind() {
        ShipHullSpecAPI hull = hull();

        Map<String, Object> variant = new HashMap<>();
        variant.put("getHullSpec", hull);
        variant.put("hasHullMod", (Answer) args -> MercenaryCompanyConversion.ID.equals(args[0]));

        Map<String, Object> repair = new HashMap<>();
        repair.put("getCR", READINESS);
        repair.put("getMaxCR", READINESS);

        Map<String, Object> member = new HashMap<>();
        member.put("getId", SHIP_ID);
        member.put("getShipName", SHIP_NAME);
        member.put("getHullId", HULL_ID);
        member.put("getHullSpec", hull);
        member.put("getVariant", stub(ShipVariantAPI.class, variant));
        member.put("getRepairTracker", stub(RepairTrackerAPI.class, repair));
        member.put("getMinCrew", MIN_CREW);
        member.put("getMaxCrew", MAX_CREW);
        member.put("getCargoCapacity", CARGO);
        return stub(FleetMemberAPI.class, member);
    }

    /** A ship the yard hands over: whole, crewed and ready, with nothing fitted a stand-in needs to read. */
    private static FleetMemberAPI newShip(String variantId) {
        return newShip(variantId, variantId + "-" + System.nanoTime());
    }

    private static FleetMemberAPI newShip(String variantId, String id) {
        String hullId = variantId.substring(0, variantId.indexOf('_'));
        Map<String, Object> spec = new HashMap<>();
        spec.put("getHullId", hullId);
        spec.put("getBaseHullId", hullId);
        spec.put("getHullName", hullId);
        spec.put("getHullSize", ShipAPI.HullSize.DESTROYER);
        ShipHullSpecAPI hull = stub(ShipHullSpecAPI.class, spec);

        Map<String, Object> variant = new HashMap<>();
        variant.put("getHullSpec", hull);
        variant.put("getHullVariantId", variantId);

        Map<String, Object> repair = new HashMap<>();
        repair.put("getCR", READINESS);
        repair.put("getMaxCR", READINESS);

        Map<String, Object> status = new HashMap<>();
        status.put("getHullFraction", 1f);

        Map<String, Object> member = new HashMap<>();
        member.put("getId", id);
        member.put("getHullId", hullId);
        member.put("getHullSpec", hull);
        member.put("getVariant", stub(ShipVariantAPI.class, variant));
        member.put("getRepairTracker", stub(RepairTrackerAPI.class, repair));
        member.put("getStatus", stub(FleetMemberStatusAPI.class, status));
        member.put("getMinCrew", 1f);
        member.put("setShipName", (Answer) args -> member.put("getShipName", args[0]));
        return stub(FleetMemberAPI.class, member);
    }

    /** An answer worked out from the call's arguments. */
    private interface Answer {
        Object apply(Object[] args);
    }

    private static <T> T stub(Class<T> type, Map<String, Object> answers) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "stand-in " + type.getSimpleName();
                    default -> {
                        Object answer = answers.get(method.getName());
                        if (answer instanceof Answer worked) answer = worked.apply(args);
                        if (method.getReturnType() == int.class && answer instanceof Number number) yield number.intValue();
                        yield answer != null ? answer : empty(method.getReturnType());
                    }
                }));
    }

    private static Object empty(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == float.class) return 0f;
        if (type == long.class) return 0L;
        if (type == double.class) return 0d;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        if (type.isAssignableFrom(List.class)) return List.of();
        if (type == Set.class) return Set.of();
        if (type == Collection.class) return List.of();
        if (type == Map.class) return Map.of();
        return null;
    }
}

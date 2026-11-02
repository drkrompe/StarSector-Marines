package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.backend.CampaignBackend;
import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import com.dillon.starsectormarines.campaign.start.MercenaryCompanyConversion;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.ops.detachment.DebugCompany;
import com.dillon.starsectormarines.ops.detachment.DebugCompanyStage;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.RepairTrackerAPI;
import com.fs.starfarer.api.util.MutableValue;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The campaign {@code playBattle} stands in for: a veteran company aboard the converted Second Wind, docked at a port,
 * with a purse and
 * stores that do not run out, answered through {@link CampaignBackend}. Enough for the company's rooms to open with the
 * cheats on; it is not a simulation of the campaign, and nothing here advances or saves.
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

    private PlayCompany() {
    }

    /** Installs the stand-in as the campaign backend, for the rest of the run. */
    static void install() {
        MarineRoster roster = DebugCompany.seeded(DebugCompanyStage.VETERAN_COMPANY,
                DebugCompanyStage.VETERAN_COMPANY.squads, 1L);
        roster.setCompanyShip(SHIP_ID, SHIP_NAME, Math.round(CARGO));
        MarineRosterScript script = new MarineRosterScript() {
            @Override public MarineRoster roster() {
                return roster;
            }
        };
        List<EveryFrameScript> scripts = List.of(script, new CampaignStateScript());
        CampaignFleetAPI fleet = fleet();
        Map<String, Object> saved = new HashMap<>();
        saved.put(CerberusCompanyStart.SHIP_KEY, SHIP_ID);
        CampaignBackend.useSavedData(() -> saved);
        CampaignBackend.useCompanyFleet(() -> fleet);
        CampaignBackend.useSectorScripts(() -> scripts);
        MarketAPI port = port();
        CampaignBackend.useDockedPort(() -> port);
    }

    private static CampaignFleetAPI fleet() {
        FleetMemberAPI ship = secondWind();
        Map<String, Object> data = new HashMap<>();
        data.put("getMembersListCopy", List.of(ship));
        data.put("getMembersInPriorityOrder", List.of(ship));
        Map<String, Object> fleet = new HashMap<>();
        fleet.put("getFleetData", stub(FleetDataAPI.class, data));
        fleet.put("getCargo", cargo());
        return stub(CampaignFleetAPI.class, fleet);
    }

    private static CargoAPI cargo() {
        Map<String, Object> cargo = new HashMap<>();
        cargo.put("getCredits", new MutableValue(CREDITS));
        cargo.put("getMothballedShips", stub(FleetDataAPI.class, Map.of()));
        for (String plenty : List.of("getCommodityQuantity", "getMarines", "getCrew", "getSupplies", "getFuel",
                "getMaxCapacity", "getSpaceLeft", "getMaxFuel", "getFreeFuelSpace", "getMaxPersonnel",
                "getFreeCrewSpace")) {
            cargo.put(plenty, PLENTY);
        }
        return stub(CargoAPI.class, cargo);
    }

    /** A port the fleet stands docked at throughout, so the store and the yards are open. */
    private static MarketAPI port() {
        Map<String, Object> port = new HashMap<>();
        port.put("getId", "play_port");
        port.put("getName", "Stand-in Port");
        port.put("getFactionId", "independent");
        return stub(MarketAPI.class, port);
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
                        if (answer instanceof Answer worked) yield worked.apply(args);
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

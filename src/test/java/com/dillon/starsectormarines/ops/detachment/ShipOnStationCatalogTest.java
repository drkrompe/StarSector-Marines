package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.fixture.CommandPowerCommitment;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.ShipOnStation;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.battle.warship.VanillaData;
import com.dillon.starsectormarines.battle.warship.VanillaWeapons;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipOnStationCatalogTest {

    private static final Map<String, String> GUNS = Map.of("WS 001", "tpc");

    @BeforeEach
    void installWeapons() {
        VanillaData.useSource(new VanillaData.Source() {
            @Override public JSONObject json(String path) throws Exception {
                throw new java.io.FileNotFoundException(path);
            }

            @Override public List<Map<String, String>> csv(String path) {
                return List.of(
                        Map.of("id", "tpc", "damage/shot", "250", "type", "ENERGY", "range", "1000",
                                "burst size", "1", "chargedown", "0.2", "proj speed", "1000", "hints", "SYSTEM"));
            }
        });
        VanillaWeapons.clearCache();
    }

    @AfterEach
    void restore() {
        VanillaData.useSource(null);
        VanillaWeapons.clearCache();
    }

    private static FleetMemberAPI member(String baseHullId, HullSize size, String name, Map<String, String> fitted) {
        ShipHullSpecAPI spec = (ShipHullSpecAPI) Proxy.newProxyInstance(
                ShipOnStationCatalogTest.class.getClassLoader(), new Class<?>[] {ShipHullSpecAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHullId", "getBaseHullId" -> baseHullId;
                    case "getHullSize" -> size;
                    case "getBuiltInWeapons" -> new HashMap<String, String>();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        ShipVariantAPI variant = (ShipVariantAPI) Proxy.newProxyInstance(
                ShipOnStationCatalogTest.class.getClassLoader(), new Class<?>[] {ShipVariantAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHullSpec" -> spec;
                    case "getHullMods" -> Set.of();
                    case "getFittedWeaponSlots" -> fitted.keySet();
                    case "getWeaponId" -> fitted.get((String) args[0]);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (FleetMemberAPI) Proxy.newProxyInstance(
                ShipOnStationCatalogTest.class.getClassLoader(), new Class<?>[] {FleetMemberAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getVariant" -> variant;
                    case "getHullSpec" -> spec;
                    case "getShipName" -> name;
                    case "getId" -> "id-" + name;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Mission mission(List<String> employer) {
        return Mission.builder().id("ship-support-test").name("Ship Support").type(MissionType.ASSAULT)
                .source(MissionSource.GENERATED).risk(RiskLevel.LOW).employerPowerIds(employer).build();
    }

    private static ShipOnStation shipPower(List<CommandPower> powers) {
        return powers.stream().filter(p -> p instanceof ShipOnStation).map(p -> (ShipOnStation) p)
                .findFirst().orElse(null);
    }

    @Test
    void aCruiserAndACapitalEachContributeTheirOwnShipAndAFrigateDoesNot() {
        FleetMemberAPI cruiser = member("eagle", HullSize.CRUISER, "ISS Cruiser", GUNS);
        FleetMemberAPI capital = member("onslaught", HullSize.CAPITAL_SHIP, "ISS Capital", GUNS);
        FleetMemberAPI frigate = member("wolf", HullSize.FRIGATE, "ISS Frigate", GUNS);

        assertEquals("ISS Cruiser", shipPower(PowerCatalog.contributedBy(cruiser)).sources().get(0).shipName());
        assertEquals("onslaught", shipPower(PowerCatalog.contributedBy(capital)).sources().get(0).hullId());
        assertEquals("id-ISS Capital", shipPower(PowerCatalog.contributedBy(capital)).sources().get(0).memberId(),
                "a committed member is written back to by its id");
        assertNull(shipPower(PowerCatalog.contributedBy(frigate)));
    }

    @Test
    void theShepherdIsATenderSource() {
        ShipSupportSource source = PowerCatalog.shipSourceOf(member("shepherd", HullSize.FRIGATE, "Fold", Map.of()));
        assertTrue(source.tender());
    }

    @Test
    void everyCommittedShipIsItsOwnChargeOnOneCard() {
        List<CommandPower> powers = PowerCatalog.resolve(List.of(
                member("eagle", HullSize.CRUISER, "A", GUNS),
                member("wolf", HullSize.FRIGATE, "F", GUNS),
                member("onslaught", HullSize.CAPITAL_SHIP, "B", GUNS)), mission(List.of()));
        ShipOnStation power = shipPower(powers);
        assertEquals(2, power.maxCharges);
        assertEquals(List.of("A", "B"), power.sources().stream().map(ShipSupportSource::shipName).toList());
        assertEquals(1, powers.stream().filter(p -> p.id.equals(ShipOnStation.ID)).count());
    }

    @Test
    void aHeldBackMemberTakesItsChargeWithIt() {
        FleetMemberAPI a = member("eagle", HullSize.CRUISER, "A", GUNS);
        FleetMemberAPI b = member("onslaught", HullSize.CAPITAL_SHIP, "B", GUNS);
        assertEquals(1, shipPower(PowerCatalog.resolve(List.of(b), mission(List.of()))).maxCharges);
        assertNull(shipPower(PowerCatalog.resolve(List.of(), mission(List.of()))));
        assertEquals(2, shipPower(PowerCatalog.resolve(List.of(a, b), mission(List.of()))).maxCharges);
    }

    @Test
    void anEmployerMayOfferAStockHullThroughTheSameMechanism() {
        ShipOnStation power = shipPower(PowerCatalog.resolve(List.of(
                member("eagle", HullSize.CRUISER, "A", GUNS)),
                mission(List.of(ShipOnStation.EMPLOYER_PREFIX + "shepherd", "recon_ping"))));
        assertEquals(2, power.maxCharges);
        assertTrue(power.sources().get(1).tender());
    }

    @Test
    void theFrozenShipsSurviveTheFixtureCommitmentRoundTrip() {
        ShipOnStation power = new ShipOnStation(List.of(
                new ShipSupportSource("onslaught", "B", false, GUNS),
                new ShipSupportSource("shepherd", "Fold", true, Map.of())));
        CommandPowerCommitment commitment = CommandPowerCommitment.capture(power);
        ShipOnStation restored = (ShipOnStation) commitment.toPower();
        assertEquals(power.sources(), restored.sources());
        assertEquals(power.maxCharges, restored.maxCharges);
    }

    @Test
    void debugSourcesMergeIntoTheRostersPowerOrCreateIt() {
        ShipSupportSource stock = new ShipSupportSource("onslaught", "Onslaught", false, GUNS);
        List<CommandPower> created = PowerCatalog.withShipSources(List.of(), List.of(stock));
        assertEquals(1, shipPower(created).maxCharges);
        List<CommandPower> merged = PowerCatalog.withShipSources(created, List.of(stock));
        assertEquals(1, merged.size());
        assertEquals(2, shipPower(merged).maxCharges);
    }
}

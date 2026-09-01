package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a company loses with its ship.
 *
 * <p>The rule under test is that the company keeps what it <b>is</b> and loses
 * what it <b>had</b>, so these check both halves: that the designs, the squads
 * and the machines are still there afterwards, and that the people, the kit and
 * the stores are not.
 */
class ShipLossSettlementTest {

    private static final int COMPANY = 40;
    private static final int HOLD = 200;
    private static final long SEED = 918_273L;

    @Test
    @DisplayName("holding the field decides who is picked up")
    void holdingTheFieldDecidesWhoIsPickedUp() {
        ShipLossSettlement.Toll won = ShipLossSettlement.settle(
                company(), null, 0, true, SEED);
        ShipLossSettlement.Toll routed = ShipLossSettlement.settle(
                company(), null, 0, false, SEED);

        assertTrue(won.marinesSurvived() > routed.marinesSurvived(),
                "boats in the water save more than a rout does: "
                        + won.marinesSurvived() + " vs " + routed.marinesSurvived());
        assertTrue(routed.marinesLost() > 0 && won.marinesSurvived() > 0,
                "a loss should bite without ending the company");
        assertEquals(COMPANY, won.marinesLost() + won.marinesSurvived(),
                "everybody aboard is accounted for");
    }

    @Test
    @DisplayName("the roll is fixed, so reloading cannot buy a kinder one")
    void theRollIsFixedBySeed() {
        assertEquals(ShipLossSettlement.settle(company(), null, 0, true, SEED),
                ShipLossSettlement.settle(company(), null, 0, true, SEED));
    }

    @Test
    @DisplayName("the marines who did not make it are dead, and their squads are not")
    void marinesAreLostButSquadsAreNot() {
        MarineRoster roster = company();
        int squads = roster.squads().size();

        ShipLossSettlement.Toll toll =
                ShipLossSettlement.settle(roster, null, 0, false, SEED);

        assertEquals(squads, roster.squads().size(), "the squads survive her");
        int dead = 0;
        for (MarineSoldier marine : roster.soldiers()) {
            if (marine.status() == MarineSoldierStatus.KIA) dead++;
        }
        assertEquals(toll.marinesLost(), dead);
        assertTrue(dead > 0 && dead < COMPANY);
    }

    @Test
    @DisplayName("the company keeps its designs and its machines, and loses its spares")
    void designsAndMachinesSurviveTheSparesDoNot() {
        MarineRoster roster = company();
        assertNotNull(roster.mechBay().fabricateChassis(
                roster.mechBay().activeSquad().id(), MechVariant.BULWARK));
        roster.mechBay().addReplenisher(MissileReplenisherComponent.STANDARD.id(), 4);
        roster.mechBay().addReplenisher(
                MissileReplenisherComponent.ACCELERATED_FEED.id(), 1);
        int designs = roster.armory().ownedEquipmentTemplateIds().size();
        int machines = roster.mechBay().squads().get(0).mechs().size();
        int doctrines = roster.armory().weaponDoctrines().size();

        ShipLossSettlement.Toll toll =
                ShipLossSettlement.settle(roster, null, 0, true, SEED);

        assertEquals(designs, roster.armory().ownedEquipmentTemplateIds().size(),
                "equipment templates are the company's own");
        assertEquals(doctrines, roster.armory().weaponDoctrines().size(),
                "so is how it says a squad is armed");
        assertEquals(machines, roster.mechBay().squads().get(0).mechs().size(),
                "the walkers come with them");

        // The bay held five spares on its shelf and one bolted into the walker.
        assertEquals(5, toll.sparesLost());
        assertEquals(1, roster.mechBay().ownedReplenisher(
                        MissileReplenisherComponent.STANDARD.id()),
                "what is carrying the machine is carried by it");
        assertEquals(0, roster.mechBay().ownedReplenisher(
                        MissileReplenisherComponent.ACCELERATED_FEED.id()),
                "a spare nothing is holding is a spare on a shelf that sank");
    }

    @Test
    @DisplayName("what a squad is allowed to carry is not touched by a sinking")
    void equipmentTemplatesAreNotAnInventory() {
        MarineRoster roster = company();
        int templates = roster.armory().equipmentTemplateCards().size();

        ShipLossSettlement.settle(roster, null, 0, false, SEED);

        // A company's equipment is a set of designs it owns permanently, not a
        // rack it draws down; what an issue actually consumes is fleet cargo.
        assertEquals(templates, roster.armory().equipmentTemplateCards().size());
        assertTrue(roster.armory().ownsPrimaryTemplate(
                WeaponRegistry.PULSE_RIFLE_ID, EquipmentGrade.SERVICE),
                "the company still knows how to arm a rifle squad");
    }

    @Test
    @DisplayName("what was in her holds goes down, bounded by what she could carry")
    void storesSinkInProportionBoundedByHerHold() {
        Map<String, Float> holds = new LinkedHashMap<>();
        holds.put(Commodities.MARINES, 100f);
        holds.put(Commodities.HAND_WEAPONS, 100f);
        holds.put(Commodities.SUPPLIES, 100f);
        holds.put(Commodities.HEAVY_MACHINERY, 100f);

        ShipLossSettlement.Toll toll = ShipLossSettlement.settle(
                company(), cargo(holds), HOLD, true, SEED);

        assertEquals(HOLD, toll.storesLost(), "she was carrying her fill");
        for (Map.Entry<String, Float> row : holds.entrySet()) {
            assertEquals(50f, row.getValue(), 0.5f,
                    row.getKey() + " should lose its even share");
        }
    }

    @Test
    @DisplayName("a fleet carrying less than she could hold loses only what it had")
    void storesCannotSinkBelowNothing() {
        Map<String, Float> holds = new LinkedHashMap<>();
        holds.put(Commodities.SUPPLIES, 30f);

        ShipLossSettlement.Toll toll = ShipLossSettlement.settle(
                company(), cargo(holds), HOLD, true, SEED);

        assertEquals(30, toll.storesLost());
        assertEquals(0f, holds.get(Commodities.SUPPLIES), 0.5f);
    }

    /** A company of forty, quartered and equipped out of the box. */
    private static MarineRoster company() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(COMPANY);
        assertEquals(COMPANY, MarineOpsContext.companyMarines(roster).size(),
                "the fixture should put the whole company aboard");
        assertTrue(roster.squads().size() > COMPANY / MarineSquad.CAPACITY - 1);
        return roster;
    }

    private static CargoAPI cargo(Map<String, Float> holds) {
        return (CargoAPI) Proxy.newProxyInstance(CargoAPI.class.getClassLoader(),
                new Class<?>[]{CargoAPI.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getCommodityQuantity":
                            return holds.getOrDefault((String) args[0], 0f);
                        case "removeCommodity":
                            holds.merge((String) args[0],
                                    -((Number) args[1]).floatValue(), Float::sum);
                            return null;
                        default:
                            Class<?> type = method.getReturnType();
                            if (type == boolean.class) return false;
                            if (type == int.class) return 0;
                            if (type == float.class) return 0f;
                            if (type == double.class) return 0d;
                            if (type == long.class) return 0L;
                            return null;
                    }
                });
    }
}

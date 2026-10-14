package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.Armament.Delivery;
import com.dillon.starsectormarines.battle.command.FortAssault.Strikes;
import com.dillon.starsectormarines.battle.fort.FortFrame;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mortar tubes and the air as fillers of the plan's base of fire: what they rate, where they stand, what they stand in for. */
class FortAssaultFireSupportTest {

    private static final Armament RIFLE = new Armament(Delivery.DIRECT, 18f, 1, 0.5f, 6f, 2f, 0, Armament.UNLIMITED);
    private static final Armament TUBE = new Armament(Delivery.EMPLACED, 52f, 1, 4f, 64f, 16f, 40, 14);
    private static final Armament RADIO = new Armament(Delivery.CALLED, 140f, 1, 34f, 0f, 0f, 0, Armament.UNLIMITED);

    private static FortAssault.Works works() {
        return FortAssault.Works.of(new FortFrame(BatteryFort.PLAN, 100, 100, 0));
    }

    private static CapabilityProfile mortarSection() {
        return CapabilityProfile.of(List.of(RIFLE, TUBE), 4, 4);
    }

    private static CapabilityProfile observers() {
        return CapabilityProfile.of(List.of(RIFLE, RADIO), 4, 4);
    }

    private static CapabilityProfile riflemen() {
        return CapabilityProfile.of(List.of(RIFLE), 8, 8);
    }

    /** A picture in which a pass over every nest costs {@code risk} of an airframe. */
    private static AirSupportFacts air(FortAssault.Works works, int calls, float risk) {
        Map<Long, Float> byCell = new HashMap<>();
        for (int[] nest : works.nests) byCell.put(AirSupportFacts.key(nest[0], nest[1]), risk);
        return new AirSupportFacts(calls, byCell, 1.0f);
    }

    @Test
    void aServedTubeRatesAsStandoffFireAndSuppressionButNeverAsABreacher() {
        CapabilityProfile profile = mortarSection();
        assertTrue(profile.has(AssaultCapability.STANDOFF_STRUCTURE_FIRE), profile.describe());
        assertTrue(profile.has(AssaultCapability.SUPPRESSION));
        assertFalse(profile.has(AssaultCapability.DISTANCE_BREACH), "a tube cannot be aimed at a wall cell");
        assertTrue(profile.standoffFirst());
        assertEquals(52f, profile.structureFireRange(), 0.001f);
        assertFalse(riflemen().standoffFirst());
    }

    @Test
    void anObserversRadioIsTheCapabilityToDirectTheAirAndNothingElse() {
        CapabilityProfile profile = observers();
        assertTrue(profile.has(AssaultCapability.AIR_DIRECTION));
        assertFalse(profile.has(AssaultCapability.STANDOFF_STRUCTURE_FIRE));
        assertFalse(riflemen().has(AssaultCapability.AIR_DIRECTION));
    }

    @Test
    void aMortarSectionStaffsTheBaseOfFireOnItsOwn() {
        FortAssault.Works works = works();
        FortAssault.NestBelief belief = new FortAssault.NestBelief(works.nests.size());
        FortAssault.Defence defence = FortAssault.assess(works, belief, 100, 40,
                Map.of(1, riflemen(), 2, mortarSection(), 3, mortarSection(), 4, mortarSection()));
        assertEquals(List.of(2, 3, 4), defence.staffing().squads(AssaultRoles.Role.BASE_OF_FIRE).stream().sorted().toList());
    }

    @Test
    void aStrikeOnOfferStandsInForANestsShareOfTheBaseOfFire() {
        FortAssault.Works works = works();
        FortAssault.NestBelief belief = new FortAssault.NestBelief(works.nests.size());
        Map<Integer, CapabilityProfile> company = Map.of(1, riflemen(), 2, observers());
        FortAssault.Defence without = FortAssault.assess(works, belief, 100, 40, company);
        assertFalse(without.staffed(), "no gun that hurts a nest, no air");
        FortAssault.Defence with = FortAssault.assess(works, belief, 100, 40, company,
                new Strikes(air(works, works.nests.size(), 0.3f), true, 0));
        assertFalse(with.strikeNests().isEmpty());
        assertTrue(with.liveCovering() > 0);
        assertTrue(with.staffed() || with.strikeNests().size() < with.liveCovering(),
                "the strikes answer as many nests as the air offers calls for");
    }

    @Test
    void noStrikeIsCountedWithoutAnObserverOrAPassThatIsWorthIt() {
        FortAssault.Works works = works();
        FortAssault.NestBelief belief = new FortAssault.NestBelief(works.nests.size());
        FortAssault.Way way = works.ways.get(0);
        assertTrue(FortAssault.strikeable(works, belief, way, new Strikes(air(works, 4, 0.3f), false, 0)).isEmpty(),
                "nobody to direct it");
        assertTrue(FortAssault.strikeable(works, belief, way, new Strikes(air(works, 4, 1.4f), true, 0)).isEmpty(),
                "the believed anti-air makes the pass too costly");
        assertTrue(FortAssault.strikeable(works, belief, way, new Strikes(air(works, 0, 0.3f), true, 0)).isEmpty(),
                "no airframe to send");
        assertEquals(Math.min(2, way.covering().size()),
                FortAssault.strikeable(works, belief, way, new Strikes(air(works, 2, 0.3f), true, 0)).size());
    }

    @Test
    void aRefusedNestIsLeftToTheGroundUntilItsRetryTime() {
        FortAssault.Works works = works();
        FortAssault.NestBelief belief = new FortAssault.NestBelief(works.nests.size());
        FortAssault.Way way = works.ways.get(0);
        int nest = way.covering().get(0);
        belief.strikeRefused(nest, 1500);
        Strikes early = new Strikes(air(works, 8, 0.3f), true, 100);
        assertFalse(FortAssault.strikeable(works, belief, way, early).contains(nest));
        Strikes later = new Strikes(air(works, 8, 0.3f), true, 1600);
        assertTrue(FortAssault.strikeable(works, belief, way, later).contains(nest));
    }
}

package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.air.AirfieldService;
import com.dillon.starsectormarines.battle.air.AirfieldService.BerthState;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A based wing's stands: what counts as ready, the turnaround worked off by the stand's hands, and a loss replaced on its
 * stand after its kind's regeneration time, except where the hulk still stands.
 */
class HardstandAirBaseTest {

    private static final AirPoolAllowance WING = AirPoolAllowance.of(
            new AirPoolStock(1, 1, 60f), new AirPoolStock(1, 1, 45f), true);

    private static HardstandAirBase base() {
        HardstandAirBase base = new HardstandAirBase(Faction.ALLY, new AirfieldService(), WING);
        base.addStand(10, 10, AirPoolKind.BOMBER, 0f);
        base.addStand(16, 10, AirPoolKind.FIGHTER, 0f);
        return base;
    }

    @Test
    void readyCountsOnlyParkedAircraftOfTheKind() {
        HardstandAirBase base = base();
        assertEquals(1, base.ready(AirPoolKind.BOMBER));
        base.stands().get(0).berth().state = BerthState.AWAY;
        assertEquals(0, base.ready(AirPoolKind.BOMBER));
        assertEquals(1, base.ready(AirPoolKind.FIGHTER));
    }

    @Test
    void aStandsHandsWorkOffItsTurnaround() {
        HardstandAirBase base = base();
        AirfieldService.Berth berth = base.stands().get(1).berth();
        berth.state = BerthState.REFITTING;
        berth.refitWork = 10f;
        base.advanceStands(4f);
        assertEquals(10f - 4f * HardstandAirBase.GROUND_CREW_HANDS, berth.refitWork, 1e-4f);
        base.advanceStands(100f);
        assertEquals(0f, berth.refitWork);
    }

    @Test
    void anAircraftLostInTheAirIsReplacedOnItsStandAfterItsKindsTime() {
        HardstandAirBase base = base();
        AirfieldService field = new AirfieldService();
        AirfieldService.Berth berth = base.stands().get(1).berth();
        berth.hullHp = 1f;
        field.destroyed(berth);
        base.advanceStands(1f);
        assertEquals(44f, base.nextReplacementSeconds(AirPoolKind.FIGHTER), 1e-3f);
        assertEquals(-1f, base.nextReplacementSeconds(AirPoolKind.BOMBER));
        base.advanceStands(43.5f);
        assertEquals(BerthState.DESTROYED, berth.state);
        base.advanceStands(1f);
        assertEquals(BerthState.PARKED, berth.state);
        assertEquals(berth.airframe.maxHp(), berth.hullHp);
        assertEquals(1, base.ready(AirPoolKind.FIGHTER));
        assertTrue(base.nextReplacementSeconds(AirPoolKind.FIGHTER) < 0f);
    }

    @Test
    void aStandWithItsHulkOnItIsNeverReplaced() {
        HardstandAirBase base = base();
        AirfieldService.Berth berth = base.stands().get(0).berth();
        new AirfieldService().burnedOnPad(berth);
        base.advanceStands(1000f);
        assertEquals(BerthState.DESTROYED, berth.state);
        assertTrue(base.nextReplacementSeconds(AirPoolKind.BOMBER) < 0f);
    }

    @Test
    void aBasedPoolsStockIsWhatStandsReadyLessWhatCallsHaveReserved() {
        HardstandAirBase base = base();
        AirPoolService pool = new AirPoolService(AirPoolDeclaration.NONE.with(Faction.ALLY, WING))
                .basedAt(Faction.ALLY, base);
        assertEquals(1, pool.available(Faction.ALLY, AirPoolKind.BOMBER));
        base.stands().get(0).berth().state = BerthState.REFITTING;
        assertEquals(0, pool.available(Faction.ALLY, AirPoolKind.BOMBER));
        assertEquals(1, pool.available(Faction.ALLY, AirPoolKind.FIGHTER));
    }

    @Test
    void aShedKeepsItsAirframeAsAShelterReadyLikeAStand() {
        HardstandAirBase base = new HardstandAirBase(Faction.ALLY, new AirfieldService(), WING);
        HardstandAirBase.Stand shed = base.addShelter(new Gantry(30, 20, 2, 2, Gantry.Facing.of(-1, 0)), AirPoolKind.BOMBER);
        assertEquals(AirfieldService.Kind.SHELTER, shed.berth().kind);
        assertEquals(1, base.ready(AirPoolKind.BOMBER));
        shed.berth().state = BerthState.AWAY;
        assertEquals(0, base.ready(AirPoolKind.BOMBER));
    }
}

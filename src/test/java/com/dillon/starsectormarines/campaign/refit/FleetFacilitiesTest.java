package com.dillon.starsectormarines.campaign.refit;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSizing;
import com.dillon.starsectormarines.battle.world.gen.ship.FleetRooms;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipsBoats;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetFacilitiesTest {
    private static final ShipCondition FRESH = FleetFacilities.read(Map.of());

    @Test void aFreshFleetIsExactlyNeutral() {
        var neutral = ShipCondition.NONE;
        assertEquals(neutral.hullWelfare(), FRESH.hullWelfare());
        assertEquals(neutral.casualtiesSaved(), FRESH.casualtiesSaved());
        assertEquals(neutral.recruitIntake(), FRESH.recruitIntake());
        assertEquals(neutral.mechRefit(), FRESH.mechRefit());
        assertEquals(neutral.mechFieldRepair(), FRESH.mechFieldRepair());
        assertEquals(neutral.reserveCapacity(), FRESH.reserveCapacity());
    }

    @Test void onlyImprovementsAndSealedRoomsAreLeftToBuy() {
        assertEquals(RefitOrder.MESS_GALLEY, FRESH.next(RefitTrack.MESS), "no deficit to buy back");
        assertEquals(RefitOrder.POWER_OVERHAUL, FRESH.next(RefitTrack.POWER));
        assertEquals(RefitOrder.SICK_BAY_OPEN, FRESH.next(RefitTrack.SICK_BAY), "a sealed room is neutral, so its opening is bought");
        assertEquals(RefitOrder.RESERVE_HANGAR_RESTORED, FRESH.next(RefitTrack.RESERVE_HANGAR));
        assertNull(ShipRefits.refusal(RefitOrder.MESS_GALLEY, FRESH, true, true, true, true,
                RefitOrder.MESS_GALLEY.price, Map.of(), id -> id), "the cores it needs already stand restored");
    }

    @Test void fleetReceiptsCountFromTheFleetStartAndShipReceiptsDoNot() {
        Map<String, Object> data = new HashMap<>();
        data.put(FleetFacilities.RECEIPTS + RefitOrder.MESS_GALLEY.receipt, true);
        data.put(FleetFacilities.RECEIPTS + RefitOrder.SICK_BAY_SURGERY.receipt, true);
        data.put("sm_ship_restoration_swGYM_OPEN", true);
        var read = FleetFacilities.read(data);
        assertEquals(RefitBand.IMPROVED_I, read.band(RefitTrack.MESS));
        assertEquals(RefitBand.DEFICIT, read.band(RefitTrack.SICK_BAY), "surgery cannot count before the bay is opened");
        assertEquals(RefitBand.DEFICIT, read.band(RefitTrack.GYM), "a hull's receipt is not the fleet's");
    }

    @Test void aFreshFleetHasNoMechLabOneBerthAndFourBunkrooms() {
        assertEquals(new FleetRooms(0, 1, 4), FleetFacilities.rooms(FRESH));
        var bought = FRESH.with(RefitTrack.MECH_GANTRIES, RefitBand.IMPROVED_II).with(RefitTrack.BOAT_BERTHS, RefitBand.IMPROVED_I)
                .with(RefitTrack.BARRACKS, RefitBand.IMPROVED_II);
        assertEquals(new FleetRooms(4, 2, 8), FleetFacilities.rooms(bought));
        assertEquals(RefitOrder.MECH_GANTRIES_FIRST, FRESH.next(RefitTrack.MECH_GANTRIES), "the first gantry is an upgrade");
    }

    @Test void theFleetBuysItsOwnMachineShopAndTheSecondWindNeverSeesIt() {
        assertEquals(RefitBand.RESTORED, FRESH.band(RefitTrack.FABRICATION));
        assertEquals(RefitOrder.FABRICATION_TOOLING, FRESH.next(RefitTrack.FABRICATION));
        assertNull(ShipCondition.battered().with(RefitTrack.FABRICATION, RefitBand.RESTORED).next(RefitTrack.FABRICATION),
                "her fabrication is the workshop chain's");
        Map<String, Object> data = new HashMap<>();
        data.put(FleetFacilities.RECEIPTS + RefitOrder.FABRICATION_TOOLING.receipt, true);
        assertEquals(RefitBand.IMPROVED_I, FleetFacilities.read(data).band(RefitTrack.FABRICATION));
    }

    @Test void theFleetDeckOwesExactlyTheBoughtRooms() {
        var none = DeckSizing.fleetPlan(new FleetRooms(0, 1, 2), 0.45f).rooms();
        assertTrue(none.stream().noneMatch(r -> r.purpose() == RoomPurpose.VEHICLE_BAY), "no gantry, no Mech Lab");
        var full = CompanyShip.fleet(new FleetRooms(4, 3, 6));
        assertEquals(1, full.deckPlan().rooms().stream().filter(r -> r.purpose() == RoomPurpose.VEHICLE_BAY).count());
        assertEquals(6, full.deckPlan().rooms().stream().filter(r -> r.purpose() == RoomPurpose.BARRACKS).count());
        assertEquals(3, ShipsBoats.aboard(full).size(), "each berth is a boat of lift");
        assertEquals(2, CompanyShip.fleet(new FleetRooms(2, 1, 2)).restoration().workingMechBerths());
    }
}

package com.dillon.starsectormarines.campaign.refit;

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
}

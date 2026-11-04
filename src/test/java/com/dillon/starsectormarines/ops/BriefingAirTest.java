package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.PlayerFleetWings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BriefingAirTest {

    @Test
    void eachFighterTypeCountsOneAircraftABayAcrossCarriers() {
        var crafts = BriefingAir.read(List.of(
                new PlayerFleetWings.CarrierBay("One", null, List.of(FighterProfile.TALON, FighterProfile.WASP)),
                new PlayerFleetWings.CarrierBay("Two", null, List.of(FighterProfile.TALON))));
        assertEquals(List.of("briefing-air-talon", "briefing-air-wasp"),
                crafts.stream().map(BriefingAir.Craft::id).toList());
        assertEquals(List.of(2, 1), crafts.stream().map(BriefingAir.Craft::aircraft).toList());
    }

    @Test
    void noCarriersIsNoAirCover() {
        assertEquals(List.of(), BriefingAir.read(List.of()));
    }
}

package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.PlayerFleetWings;
import com.dillon.starsectormarines.battle.power.CloseAirSupport;
import com.dillon.starsectormarines.battle.power.OrbitalBarrage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BriefingAirTest {

    @Test
    void eachFighterTypeCountsOneAircraftABayAcrossCarriersThenOneGunshipAShip() {
        var crafts = BriefingAir.read(List.of(
                new PlayerFleetWings.CarrierBay("One", null, List.of(FighterProfile.TALON, FighterProfile.WASP)),
                new PlayerFleetWings.CarrierBay("Two", null, List.of(FighterProfile.TALON))),
                List.of(new OrbitalBarrage(), new CloseAirSupport()), List.of("Mudlark", "Silt"));
        assertEquals(List.of("briefing-air-talon", "briefing-air-wasp", "briefing-air-gunship"),
                crafts.stream().map(BriefingAir.Craft::id).toList());
        assertEquals(List.of(2, 1, 2), crafts.stream().map(BriefingAir.Craft::aircraft).toList());
    }

    @Test
    void noCarriersAndNoGunshipIsNoAirCover() {
        assertEquals(List.of(), BriefingAir.read(List.of(), List.of(new OrbitalBarrage()), List.of("Mudlark")));
    }
}

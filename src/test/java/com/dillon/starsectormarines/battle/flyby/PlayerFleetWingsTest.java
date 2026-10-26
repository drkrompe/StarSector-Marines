package com.dillon.starsectormarines.battle.flyby;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerFleetWingsTest {

    @Test
    void supportedVanillaWingsKeepTheirOwnAirframes() {
        assertEquals(FighterProfile.TALON, PlayerFleetWings.profileFromWingId("talon_wing"));
        assertEquals(FighterProfile.WASP, PlayerFleetWings.profileFromWingId("wasp_wing"));
        assertEquals(FighterProfile.BROADSWORD, PlayerFleetWings.profileFromWingId("broadsword_wing"));
        assertEquals(FighterProfile.THUNDER, PlayerFleetWings.profileFromWingId("thunder_wing"));
        assertEquals(FighterProfile.LONGBOW, PlayerFleetWings.profileFromWingId("longbow_wing"));
        assertEquals(FighterProfile.DAGGER, PlayerFleetWings.profileFromWingId("dagger_wing"));
        assertNull(PlayerFleetWings.profileFromWingId("trident_wing"));
        assertNull(PlayerFleetWings.profileFromWingId("unknown_mod_wing"));
        assertNull(PlayerFleetWings.profileFromWingId(null));
    }

    @Test
    void committedBomberBaysAreRenewableStaggeredBaysLikeFighters() {
        var carrier = new PlayerFleetWings.CarrierBay("Test carrier", null, List.of(
                PlayerFleetWings.profileFromWingId("longbow_wing"),
                PlayerFleetWings.profileFromWingId("dagger_wing"),
                PlayerFleetWings.profileFromWingId("talon_wing")));
        FlybyRoster roster = PlayerFleetWings.rosterFrom(List.of(carrier));
        assertEquals(3, carrier.bayCount());
        assertEquals(List.of(FighterProfile.LONGBOW, FighterProfile.DAGGER, FighterProfile.TALON),
                roster.wings.stream().map(wing -> wing.profile).toList());
        for (int i = 0; i < roster.wings.size(); i++) {
            FighterWing wing = roster.wings.get(i);
            assertEquals(Faction.MARINE, wing.side);
            assertTrue(wing.renewable(), "a carrier bay keeps its airframe on station");
            assertEquals(1, wing.sortieCount, "one airframe on station per bay");
            assertEquals(8f + i * 4f, wing.firstArrivalSec);
            assertTrue(wing.refitSec < wing.replacementSec,
                    "rearming must be quicker than replacing a lost airframe");
        }
        assertTrue(PlayerFleetWings.rosterFrom(List.of()).isEmpty(),
                "uncommitted carriers must not supply sorties");
    }
}

package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlliedGarrisonSizeTest {

    @Test
    void aBiggerMarketTurnsOutMoreOfItsOwn() {
        int previous = 0;
        for (int size = 1; size <= 10; size++) {
            int squads = AlliedGarrisonSize.squads(market(size, 6, 0));
            assertTrue(squads >= previous,
                    "squads must not fall as market size rises, at size " + size);
            previous = squads;
        }
        assertEquals(1, AlliedGarrisonSize.squads(market(3, 6, 0)));
        assertEquals(2, AlliedGarrisonSize.squads(market(4, 6, 0)));
        assertEquals(3, AlliedGarrisonSize.squads(market(5, 6, 0)));
        assertEquals(4, AlliedGarrisonSize.squads(market(6, 6, 0)));
        assertEquals(4, AlliedGarrisonSize.squads(market(10, 6, 0)));
    }

    @Test
    void aDefendedMarketFieldsOneMore() {
        int previous = 0;
        for (int defense = 0; defense <= 7; defense++) {
            int squads = AlliedGarrisonSize.squads(market(5, 6, defense));
            assertTrue(squads >= previous,
                    "squads must not fall as defence rises, at level " + defense);
            previous = squads;
        }
        assertEquals(3, AlliedGarrisonSize.squads(market(5, 6, 2)));
        assertEquals(4, AlliedGarrisonSize.squads(market(5, 6, 3)));
    }

    @Test
    void anUnstableMarketCannotTurnItsWholeGarrisonOut() {
        assertEquals(3, AlliedGarrisonSize.squads(market(5, 4, 0)));
        assertEquals(2, AlliedGarrisonSize.squads(market(5, 3, 0)));
    }

    /** No market, no garrison: nobody owns the ground, so nobody defends it. */
    @Test
    void noMarketFieldsNobody() {
        assertEquals(0, AlliedGarrisonSize.squads(TargetProfile.NEUTRAL));
        assertEquals(0, AlliedGarrisonSize.squads(null));
    }

    /** A colony with a garrison of nobody is a colony that was already taken. */
    @Test
    void everyRealMarketKeepsAtLeastOneSquad() {
        assertEquals(1, AlliedGarrisonSize.squads(market(1, 0, 0)));
        assertEquals(1, AlliedGarrisonSize.squads(market(3, 1, 0)));
    }

    private static TargetProfile market(int size, int stability, int defense) {
        return new TargetProfile(size, stability, defense, 0, "hegemony",
                EnumSet.noneOf(EconomicFunction.class),
                SurfacePalette.ROCK, SettlementLink.ROAD);
    }
}

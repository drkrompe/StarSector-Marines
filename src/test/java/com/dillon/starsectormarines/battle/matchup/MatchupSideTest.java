package com.dillon.starsectormarines.battle.matchup;

import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.marine.ArmorClass;
import com.dillon.starsectormarines.marine.LoadoutCard;
import com.dillon.starsectormarines.marine.LoadoutCards;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A matchup side is a card pairing resolved into twelve seats. */
class MatchupSideTest {

    @Test
    void starterIssuesTwelveArmoredSeats() {
        MarineLoadout[] seats = MatchupSide.STARTER.seats();
        assertEquals(12, seats.length);
        for (MarineLoadout seat : seats) {
            assertNotNull(seat.primaryWeaponId);
            assertTrue(seat.armorCapacity > 0f && seat.armorRating > 0f);
        }
    }

    @Test
    void everyCardPairsWithTheStarterIntoAFullSquad() {
        for (LoadoutCard card : LoadoutCards.all()) {
            MatchupSide side = card.kind() == LoadoutCard.Kind.WEAPON
                    ? new MatchupSide(card.definitionId(), MatchupSide.STARTER.sheetId())
                    : new MatchupSide(MatchupSide.STARTER.weaponsId(), card.definitionId());
            assertEquals(12, side.seats().length, card.id());
        }
    }

    @Test
    void aSheetIssuesItsOwnPowerSuitsToTheSeatsItNames() {
        MarineLoadout[] seats = new MatchupSide(MatchupSide.STARTER.weaponsId(),
                SquadEquipmentDoctrines.HEGEMONY_SHOCK_ARMOR).seats();
        long heavy = Arrays.stream(seats)
                .filter(seat -> seat.armorClass == ArmorClass.POWER_SUIT).count();
        assertEquals(12, heavy);
    }

    @Test
    void anExperimentKitAndMixIssueExactlyWhatTheyList() {
        MarineLoadout[] seats = MatchupSide.parse(
                "kit:field-rifle@surplus,srm-1*2,frag-grenade*3+mix:cordon-shell*10,packframe*2").seats();
        assertEquals(12, seats.length);
        assertEquals("special.srm-1", seats[1].specialEquipmentId);
        assertEquals("special.frag-grenade", seats[4].specialEquipmentId);
        assertEquals(null, seats[5].specialEquipmentId);
        assertEquals("weapon.field-rifle", seats[11].primaryWeaponId);
        assertEquals(2, Arrays.stream(seats).filter(seat -> seat.integralSystem != null).count());
        assertThrows(IllegalArgumentException.class,
                () -> MatchupSide.parse("weapons:field-security+mix:cordon-shell*11").seats());
    }

    @Test
    void parsesAndRejects() {
        MatchupSide side = MatchupSide.parse("weapons:assault+armor:recon");
        assertEquals("weapons:assault", side.weaponsId());
        assertEquals("armor:recon", side.sheetId());
        assertThrows(IllegalArgumentException.class, () -> MatchupSide.parse("weapons:assault"));
        assertThrows(IllegalArgumentException.class, () -> new MatchupSide("weapons:nope", "armor:recon").seats());
    }
}

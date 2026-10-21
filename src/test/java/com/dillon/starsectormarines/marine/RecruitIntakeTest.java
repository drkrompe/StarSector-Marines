package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** A cargo replacement's aptitude is read against the company home's intake. */
class RecruitIntakeTest {

    @Test
    void theNeutralIntakeIsTheOrdinaryDistribution() {
        Map<SoldierAptitude, Integer> counts = new EnumMap<>(SoldierAptitude.class);
        for (int roll = 0; roll < 100; roll++) counts.merge(RecruitIntake.NEUTRAL.aptitude(roll), 1, Integer::sum);
        assertEquals(Map.of(SoldierAptitude.EXCEPTIONAL, 5, SoldierAptitude.GIFTED, 20,
                SoldierAptitude.STEADY, 65, SoldierAptitude.LIMITED, 10), counts);
    }

    @Test
    void aLiftedIntakeReachesTheReplacementsDrawnIntoABillet() {
        MarineRoster plain = new MarineRoster();
        MarineRoster lifted = new MarineRoster();
        RecruitIntake lounge = new RecruitIntake(5, 25, 100);
        for (int i = 0; i < 40; i++) {
            MarineSoldier a = plain.recruitToSquad(plain.reserveSquad().id());
            MarineSoldier b = lifted.recruitToSquad(lifted.reserveSquad().id(), lounge);
            assertNotEquals(SoldierAptitude.LIMITED, b.aptitude());
            if (a.aptitude() != SoldierAptitude.LIMITED) assertEquals(a.aptitude(), b.aptitude());
        }
        assertTrue(plain.soldiers().stream().anyMatch(s -> s.aptitude() == SoldierAptitude.LIMITED),
                "the control draws a Limited hand in forty");
    }

    @Test
    void cutPointsMustRise() {
        assertThrows(IllegalArgumentException.class, () -> new RecruitIntake(30, 20, 90));
        assertThrows(IllegalArgumentException.class, () -> new RecruitIntake(5, 25, 101));
    }
}

package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.AbsentDefenceGrade.Outcome;
import com.dillon.starsectormarines.campaign.DefenceReport.Climate;
import com.dillon.starsectormarines.campaign.DefenceReport.Facts;
import com.dillon.starsectormarines.campaign.DefenceReport.Place;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefenceReportTest {

    private static final Place PLACE = new Place("Jangala", "Corvus", Climate.HEAT, "Pirate");

    @Test
    void theSameDefenceAlwaysReadsTheSame() throws Exception {
        try (HeadlessStrings ignored = new HeadlessStrings(Path.of("mod"))) {
            assertEquals(DefenceReport.compose(landed(42L), Strings::get),
                    DefenceReport.compose(landed(42L), Strings::get));
        }
    }

    @Test
    void theSpineStatesWhoWhereWhenAndWhat() throws Exception {
        try (HeadlessStrings ignored = new HeadlessStrings(Path.of("mod"))) {
            String account = DefenceReport.compose(landed(42L), Strings::get);

            assertTrue(account.contains("Pirate raiders came down on Jangala in Corvus on day 214."),
                    account);
            assertTrue(account.contains("Captain Vey stood the post with Second Section."), account);
            assertTrue(account.contains("The line held, at a price."), account);
            assertTrue(account.contains("Killed: Hale."), account);
            assertTrue(account.contains("Missing: Orsk."), account);
            assertTrue(account.contains("Wounded: 2."), account);
            assertTrue(account.contains("10 of 12 still under arms."), account);
            assertTrue(account.contains("counted for 18 of the planet's ground defence "
                    + "against a raiding strength of 40."), account);
        }
    }

    @Test
    void aRaidThatTurnedBackReportsNoLosses() throws Exception {
        try (HeadlessStrings ignored = new HeadlessStrings(Path.of("mod"))) {
            Facts facts = new Facts(7L, 30, PLACE, false, Outcome.HELD, null,
                    List.of("Second Section"), List.of(), List.of(), 0, 0, 12, 12, 18f, 0f, 0);

            String account = DefenceReport.compose(facts, Strings::get);

            assertTrue(account.contains("turned back on day 30"), account);
            assertFalse(account.contains("Killed"), account);
            assertFalse(account.contains("still under arms"), account);
        }
    }

    /**
     * Colour is framing only ({@code narrative-nouns.md} law 1). A pool line takes no
     * arguments, so it cannot name a marine, and every climate and outcome has a pool.
     */
    @Test
    void everyColourPoolExistsAndNamesNobody() throws Exception {
        try (HeadlessStrings ignored = new HeadlessStrings(Path.of("mod"))) {
            List<String> keys = new ArrayList<>();
            for (Climate climate : Climate.values()) keys.add("defenceReportColour" + climate.name());
            for (Outcome outcome : Outcome.values()) keys.add("defenceReportCloser" + outcome.name());
            keys.add("defenceReportCloserTURNED");
            for (String key : keys) {
                String pool = Strings.get(key);
                assertNotEquals(key, pool, "missing pool " + key);
                for (String line : pool.split(DefenceReport.POOL_SEPARATOR)) {
                    assertFalse(line.isBlank(), key);
                    assertFalse(line.contains("{"), key + ": " + line);
                }
            }
        }
    }

    @Test
    void climateReadsTheMostStrikingCondition() {
        assertEquals(Climate.TOXIC, Climate.from(Set.of("hot", "toxic_atmosphere")));
        assertEquals(Climate.AIRLESS, Climate.from(Set.of("no_atmosphere", "very_cold")));
        assertEquals(Climate.HEAT, Climate.from(Set.of("very_hot")));
        assertEquals(Climate.TEMPERATE, Climate.from(Set.of("habitable")));
        assertEquals(Climate.UNKNOWN, Climate.from(null));
    }

    private static Facts landed(long eventKey) {
        return new Facts(eventKey, 214, PLACE, true, Outcome.HELD_WITH_LOSSES, "Captain Vey",
                List.of("Second Section"), List.of("Hale"), List.of("Orsk"), 2, 0, 12, 10,
                18f, 40f, 0);
    }
}

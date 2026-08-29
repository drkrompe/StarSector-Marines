package com.dillon.starsectormarines.marine;

import org.json.JSONException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The role vocabulary is closed, so a catalog cannot invent a job by writing a
 * new word ({@code role-and-access.md}). Its predecessor was free text validated
 * only for being non-empty, which is why nothing noticed that two of the five
 * words in use named a price band and a manufacturer rather than a job.
 */
class ArmorRoleTest {

    @Test
    void everyDeclarableKeyParsesBackToItsOwnRole() throws JSONException {
        for (ArmorRole role : ArmorRole.values()) {
            assertSame(role, ArmorRole.parse(role.key, "armor.test"));
        }
    }

    @Test
    void aRoleOutsideTheVocabularyIsRefusedAndTaughtTheVocabulary() {
        JSONException failure = assertThrows(JSONException.class,
                () -> ArmorRole.parse("security", "armor.test"));
        assertTrue(failure.getMessage().contains("armor.test"),
                "the failure should name the entry to fix: " + failure.getMessage());
        for (ArmorRole role : ArmorRole.values()) {
            assertTrue(failure.getMessage().contains(role.key),
                    "the failure should list " + role.key + ": " + failure.getMessage());
        }
    }

    /**
     * An absent role is refused the same way an unknown one is. The catalog it
     * replaced could not tell those apart, because a missing key read as the
     * empty string and the empty string was only checked for length.
     */
    @Test
    void anAbsentRoleIsRefusedRatherThanDefaulted() {
        assertThrows(JSONException.class, () -> ArmorRole.parse(null, "armor.test"));
    }

    @Test
    void keysAreMatchedWithoutCaseOrSurroundingSpace() throws JSONException {
        assertSame(ArmorRole.ASSAULT, ArmorRole.parse("  ASSAULT  ", "armor.test"));
    }

    @Test
    void theArmoryCardShowsTheRoleTitleCased() {
        assertEquals("Assault", ArmorRole.ASSAULT.displayName());
        assertEquals("Unpowered", ArmorRole.UNPOWERED.displayName());
    }
}

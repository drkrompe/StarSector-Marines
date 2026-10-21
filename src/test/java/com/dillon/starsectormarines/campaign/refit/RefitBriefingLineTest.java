package com.dillon.starsectormarines.campaign.refit;

import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RefitBriefingLineTest {
    @Test void theBriefingNamesTheMessAndOnlyAWoundDeficit() throws Exception {
        try (var strings = new HeadlessStrings(Path.of("mod"), text -> text)) {
            assertNull(RefitText.briefingLine(ShipCondition.NONE));
            String start = RefitText.briefingLine(ShipCondition.battered());
            assertTrue(start.contains("Field rations") && start.contains("15% slower"), start);
            assertTrue(start.contains("one extra day"), start);
            String fixed = RefitText.briefingLine(ShipCondition.battered()
                    .with(RefitTrack.LIFE_SUPPORT, RefitBand.RESTORED).with(RefitTrack.MESS, RefitBand.IMPROVED_I));
            assertTrue(fixed.contains("Proper galley") && fixed.contains("15% faster"), fixed);
            assertFalse(fixed.contains("Life support"), fixed);
            assertFalse(fixed.contains("Wardroom"), fixed);
            String college = RefitText.briefingLine(ShipCondition.battered().with(RefitTrack.WARDROOM, RefitBand.IMPROVED_I));
            assertTrue(college.contains("Staff college") && college.contains("15%"), college);
        }
    }
}

package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.OfficerCommandProfile;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OfficerMissionResultWardroomTest {
    private static int xp(OfficerCommandProfile officer, boolean victory, float wardroom) {
        return OfficerMissionResult.compute(List.of(), officer, Set.of(), Set.of(), victory, 1_000,
                "job", 1f, 12, 0, wardroom).get(0).xpGained();
    }

    @Test void theWardroomScalesFieldExperienceAfterEveryOtherBonus() {
        var plain = new OfficerCommandProfile("a", "A", Rank.LIEUTENANT, 0, Status.ACTIVE, false, 0, 0, 0);
        var leader = new OfficerCommandProfile("b", "B", Rank.LIEUTENANT, 0, Status.ACTIVE, true, 0, 0, 0);
        assertEquals(100, xp(plain, true, 1f));
        assertEquals(115, xp(plain, true, 1.15f));
        assertEquals(130, xp(plain, true, 1.30f));
        assertEquals(195, xp(leader, true, 1.30f), "natural leader first, then the staff college");
        assertEquals(650, xp(plain, false, 1.30f), "a defeat still teaches, and the college still helps");
        assertEquals(xp(plain, true, 1f), OfficerMissionResult.compute(List.of(), plain, Set.of(), Set.of(), true,
                1_000, "job", 1f, 12, 0).get(0).xpGained(), "the old signature is neutral");
    }
}

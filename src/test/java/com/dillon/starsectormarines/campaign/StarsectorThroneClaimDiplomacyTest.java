package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StarsectorThroneClaimDiplomacyTest {

    @Test
    void neutralFactionsBecomeMutuallyHostile() {
        FakeAccess access = new FakeAccess();

        ThroneClaimDiplomacy.Result result = diplomacy(access).apply("source", "result");

        assertEquals(ThroneClaimDiplomacy.Result.APPLIED, result);
        assertEquals(-0.5f, access.sourceToResult);
        assertEquals(-0.5f, access.resultToSource);
        assertEquals(2, access.setCalls);
    }

    @Test
    void completedPostconditionDoesNotReplay() {
        FakeAccess access = new FakeAccess();
        access.sourceToResult = -0.75f;
        access.resultToSource = -0.6f;

        ThroneClaimDiplomacy.Result result = diplomacy(access).apply("source", "result");

        assertEquals(ThroneClaimDiplomacy.Result.ALREADY_APPLIED, result);
        assertEquals(0, access.setCalls);
    }

    @Test
    void partialApplicationRepairsOnlyMissingDirectionAndPreservesWorseStanding() {
        FakeAccess access = new FakeAccess();
        access.sourceToResult = -0.8f;

        ThroneClaimDiplomacy.Result result = diplomacy(access).apply("source", "result");

        assertEquals(ThroneClaimDiplomacy.Result.APPLIED, result);
        assertEquals(-0.8f, access.sourceToResult);
        assertEquals(-0.5f, access.resultToSource);
        assertEquals(1, access.setCalls);
    }

    @Test
    void unavailableRetriesAndInvalidIdentityRejects() {
        FakeAccess unavailable = new FakeAccess();
        unavailable.available = false;
        FakeAccess missing = new FakeAccess();
        missing.resultExists = false;

        assertEquals(ThroneClaimDiplomacy.Result.RETRY,
                diplomacy(unavailable).apply("source", "result"));
        assertEquals(ThroneClaimDiplomacy.Result.REJECTED,
                diplomacy(missing).apply("source", "result"));
        assertEquals(ThroneClaimDiplomacy.Result.REJECTED,
                diplomacy(new FakeAccess()).apply("source", "source"));
    }

    @Test
    void failedPostconditionRemainsRetryable() {
        FakeAccess access = new FakeAccess();
        access.ignoreSets = true;

        assertEquals(ThroneClaimDiplomacy.Result.RETRY,
                diplomacy(access).apply("source", "result"));
    }

    private static StarsectorThroneClaimDiplomacy diplomacy(FakeAccess access) {
        return new StarsectorThroneClaimDiplomacy(access);
    }

    private static final class FakeAccess
            implements StarsectorThroneClaimDiplomacy.SectorAccess {
        boolean available = true;
        boolean sourceExists = true;
        boolean resultExists = true;
        boolean ignoreSets;
        float sourceToResult;
        float resultToSource;
        int setCalls;

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public boolean factionExists(String factionId) {
            return "source".equals(factionId) ? sourceExists : resultExists;
        }

        @Override
        public float relationship(String fromFactionId, String toFactionId) {
            return "source".equals(fromFactionId) ? sourceToResult : resultToSource;
        }

        @Override
        public void setRelationship(String fromFactionId, String toFactionId,
                                    float value) {
            setCalls++;
            if (ignoreSets) return;
            if ("source".equals(fromFactionId)) sourceToResult = value;
            else resultToSource = value;
        }
    }
}

package com.dillon.starsectormarines.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldPresencePolicyTest {

    @Test
    void missionDefaultsRemainOverridableAndSurviveCopying() {
        Mission infiltration = Mission.builder()
                .id("s").name("Sabotage").type(MissionType.SABOTAGE)
                .risk(RiskLevel.LOW).build();
        Mission overt = Mission.builder(infiltration)
                .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED)
                .build();
        Mission copied = Mission.builder(overt).build();

        assertEquals(FieldPresencePolicy.INFILTRATION,
                infiltration.fieldPresencePolicy);
        assertEquals(FieldPresencePolicy.UNRESTRICTED, overt.fieldPresencePolicy);
        assertEquals(FieldPresencePolicy.UNRESTRICTED, copied.fieldPresencePolicy);
    }
}

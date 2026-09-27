package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile.Bucket;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MechDoctrineProfileTest {
    @Test void eachEffectiveRoleHasItsOwnExecutionBucket() {
        EnumSet<Bucket> buckets = EnumSet.noneOf(Bucket.class);
        for (MechRole role : MechRole.values()) {
            assertNotNull(ExecuteMechDoctrine.actionFor(role));
            buckets.add(ExecuteMechDoctrine.bucketFor(role));
        }
        assertEquals(EnumSet.of(Bucket.MECH_DOCTRINE_ASSAULT,
                Bucket.MECH_DOCTRINE_ARMORED_SUPPORT, Bucket.MECH_DOCTRINE_LR_SUPPORT,
                Bucket.MECH_DOCTRINE_BALANCED), buckets);
        assertEquals(MechRole.values().length, buckets.size());
    }
}

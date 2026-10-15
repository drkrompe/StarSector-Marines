package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundMissionMap;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.ops.MissionType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WholeCompoundSelectionTest {
    @Test void wholeSiteRequiresRemoteAssaultAndRoomForItsApproach() {
        assertTrue(BattleSetup.usesCompoundTemplate(MissionType.ASSAULT,MapScale.LARGE,PrecinctPlan.Sprawl.REMOTE));
        assertFalse(BattleSetup.usesCompoundTemplate(MissionType.ASSAULT,MapScale.MEDIUM,PrecinctPlan.Sprawl.REMOTE));
        assertFalse(BattleSetup.usesCompoundTemplate(MissionType.ASSAULT,MapScale.LARGE,PrecinctPlan.Sprawl.DENSE));
        assertFalse(BattleSetup.usesCompoundTemplate(MissionType.RAID,MapScale.LARGE,PrecinctPlan.Sprawl.REMOTE));
        assertTrue(CompoundMissionMap.supports(TargetProfile.NEUTRAL,Fortification.STRONGHOLD));
        assertFalse(CompoundMissionMap.supports(TargetProfile.NEUTRAL,Fortification.CITADEL));
        assertFalse(CompoundMissionMap.supports(TargetProfile.NEUTRAL.withDeclaredDefences(new GroundDefences(1,false)),Fortification.PICKET));
    }

    @Test void cardinalSitesKeepWholeFootprintAndApproachSeparateAtMinimumSize() {
        for (int turn=0;turn<4;turn++) for (boolean magazine : new boolean[]{false,true}) {
            var site=CompoundMissionMap.site(160,160,turn,magazine,4);
            for (int[] local : site.placement().layout().footprint().filled()) {
                int[] at=site.placement().cell(local[0],local[1]);
                assertTrue(at[0]>=12 && at[0]<148 && at[1]>=12 && at[1]<148);
                assertTrue(Math.abs(at[0]-site.marineX())>14 || Math.abs(at[1]-site.marineY())>14);
            }
            assertEquals(magazine ? 11 : 10,site.placement().buildings().size());
        }
        assertThrows(IllegalArgumentException.class,() -> CompoundMissionMap.select(144,80,42));
    }
}

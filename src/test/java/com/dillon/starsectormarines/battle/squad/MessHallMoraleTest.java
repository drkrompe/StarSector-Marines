package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessHallMoraleTest {
    @Test void recoveredMessImprovesRecoveryWithoutBypassingIncomingFireOrCasualtyCaps() {
        for (float multiplier : new float[]{1f, 1.25f}) {
            var squad = new Squad(1, Faction.MARINE);
            squad.moraleRecoveryMultiplier = multiplier;
            var team = squad.fireTeamMorale(0);
            team.originalSize = 4; team.aliveMembers = 2;
            team.morale = 0.1f; team.broken = true; team.timeSinceUnderFire = 0f;
            SquadMoraleSystem.updateFireTeamMorale(squad, 0.1f);
            assertEquals(0.1f, team.morale, 0.00001f, "Incoming fire still blocks recovery");
            team.timeSinceUnderFire = SquadMoraleSystem.MORALE_RECOVER_AFTER_FIRE_SECONDS;
            SquadMoraleSystem.updateFireTeamMorale(squad, 1f);
            assertEquals(0.1f + SquadMoraleSystem.MORALE_RECOVERY_RATE * 0.5f * multiplier, team.morale, 0.00001f);
            SquadMoraleSystem.updateFireTeamMorale(squad, 1f);
            assertFalse(team.broken, "Ordinary hysteresis returns the team to its squad plan");
            SquadMoraleSystem.updateFireTeamMorale(squad, 100f);
            assertEquals(0.5f, team.morale, "Crew welfare cannot erase casualties");
        }
    }
}

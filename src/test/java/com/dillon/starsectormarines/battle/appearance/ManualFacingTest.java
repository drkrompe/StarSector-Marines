package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualFacingTest {
    @Test
    void continuousMouseAimOverridesEnemyAndTravelFacingWhileKeepingAppliedGait() {
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(12, 12), null);
        long marine = roster.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE, 5, 5));
        long enemy = roster.spawn(new EntitySpec("enemy", Faction.DEFENDER, UnitType.MARINE, 2, 5));
        BattleComponents c = roster.components();
        roster.combat().setTargetId(marine, enemy);
        roster.entityWorld().setFloat(marine, c.MOVEMENT, BattleComponents.MOVEMENT_VEL_Y, 2f);
        roster.entityWorld().setFloat(marine, c.MOVEMENT, BattleComponents.MOVEMENT_GAIT_PHASE, 0.35f);

        // Both points are in the marine's cell: this must not floor the aim.
        float aimX = 5.85f;
        float aimY = 5.6f;
        new FacingSystem(roster.entityWorld(), c, roster).tick(marine, aimX, aimY);

        assertEquals(AirBody.facingToward(aimX - 5.5f, aimY - 5.5f),
                roster.entityWorld().getFloat(marine, c.LAYERED_ANIMATION,
                        BattleComponents.LAYERED_FACING_DEGREES), 0.00001f);
        assertEquals(LiveAppearance.pickFrame(LiveAppearance.Facing.EAST, true),
                roster.entityWorld().getInt(marine, c.SPRITE, BattleComponents.SPRITE_INDEX));
        assertEquals(0.35f, roster.entityWorld().getFloat(marine, c.LAYERED_ANIMATION,
                BattleComponents.LAYERED_LOCOMOTION_PHASE));
        assertEquals(LayeredAppearance.POSE_AIMED,
                roster.entityWorld().getInt(marine, c.LAYERED_ANIMATION, BattleComponents.LAYERED_WEAPON_POSE));
        int flags = roster.entityWorld().getInt(marine, c.LAYERED_ANIMATION, BattleComponents.LAYERED_FLAGS);
        assertTrue((flags & LayeredAppearance.FLAG_MOVING) != 0);
        assertEquals(0, flags & LayeredAppearance.FLAG_MUZZLE_FLASH,
                "aim alone must not invent a shot flash");
    }
}

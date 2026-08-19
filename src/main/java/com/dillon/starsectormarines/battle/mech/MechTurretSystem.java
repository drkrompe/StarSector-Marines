package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.engine.ecs.Query;

/**
 * Integrates each mech's upper-torso traverse. The resulting state is both
 * rendered and consulted by the weapon paths, so acquiring a new target no
 * longer snaps a mech's guns onto it.
 */
public final class MechTurretSystem {

    /** Maximum angular error accepted for a shot once the torso has traversed. */
    public static final float FIRE_ALIGNMENT_DEGREES = 4f;

    private final EntityWorld world;
    private final BattleComponents components;
    private final UnitRosterService roster;
    private final Query mechs;

    public MechTurretSystem(EntityWorld world, BattleComponents components,
                            UnitRosterService roster) {
        this.world = world;
        this.components = components;
        this.roster = roster;
        this.mechs = world.query(new ComponentType[]{components.MECH_LOADOUT,
                components.MECH_LOCOMOTION, components.POSITION, components.HEALTH}, null);
    }

    public void tick(float dt) {
        for (ArchetypeTable table : world.matched(mechs)) {
            boolean hasCombat = table.has(components.COMBAT);
            Object[] loadouts = table.objects(components.MECH_LOADOUT,
                    BattleComponents.MECH_LOADOUT_STATE).array();
            float[] posX = table.floats(components.POSITION,
                    BattleComponents.POSITION_X).array();
            float[] posY = table.floats(components.POSITION,
                    BattleComponents.POSITION_Y).array();
            float[] hipFacing = table.floats(components.MECH_LOCOMOTION,
                    BattleComponents.MECH_LOCOMOTION_FACING_DEGREES).array();
            long[] combatTargets = hasCombat
                    ? table.longs(components.COMBAT, BattleComponents.COMBAT_TARGET_ID).array()
                    : null;

            for (int row = 0, n = table.rowCount(); row < n; row++) {
                MechLoadoutComponent loadout = (MechLoadoutComponent) loadouts[row];
                long target = aimTarget(loadout, hasCombat ? combatTargets[row] : 0L);
                float desired = hipFacing[row];
                boolean withinTraverse = false;
                if (target != 0L && roster.isLive(target)) {
                    float dx = world.getFloat(target, components.POSITION,
                            BattleComponents.POSITION_X) - posX[row];
                    float dy = world.getFloat(target, components.POSITION,
                            BattleComponents.POSITION_Y) - posY[row];
                    if (dx != 0f || dy != 0f) {
                        float targetFacing = LayeredAppearance.wrapDegrees(AirBody.facingToward(dx, dy));
                        float twist = LayeredAppearance.wrapDegrees(targetFacing - hipFacing[row]);
                        withinTraverse = Math.abs(twist) <= LayeredMechAppearance.MAX_TORSO_TWIST_DEGREES;
                        desired = LayeredMechAppearance.torsoFacing(hipFacing[row], targetFacing);
                    }
                } else {
                    target = 0L;
                }

                float delta = LayeredAppearance.wrapDegrees(desired - loadout.torsoFacingDegrees);
                float maxStep = Math.max(0f, loadout.torsoTurnRateDegrees) * Math.max(0f, dt);
                float step = Math.max(-maxStep, Math.min(maxStep, delta));
                loadout.torsoFacingDegrees = LayeredAppearance.wrapDegrees(loadout.torsoFacingDegrees + step);
                loadout.torsoAimTargetId = target;
                loadout.torsoOnTarget = target != 0L && withinTraverse
                        && Math.abs(LayeredAppearance.wrapDegrees(targetFacing(target, posX[row], posY[row])
                        - loadout.torsoFacingDegrees)) <= FIRE_ALIGNMENT_DEGREES;
            }
        }
    }

    private long aimTarget(MechLoadoutComponent loadout, long combatTarget) {
        for (MechWeaponMount mount : loadout.mounts()) {
            if (mount != null && mount.burstRemaining > 0
                    && roster.isLive(mount.burstTargetId)) {
                return mount.burstTargetId;
            }
        }
        return combatTarget;
    }

    private float targetFacing(long target, float fromX, float fromY) {
        float dx = world.getFloat(target, components.POSITION, BattleComponents.POSITION_X) - fromX;
        float dy = world.getFloat(target, components.POSITION, BattleComponents.POSITION_Y) - fromY;
        return LayeredAppearance.wrapDegrees(AirBody.facingToward(dx, dy));
    }
}

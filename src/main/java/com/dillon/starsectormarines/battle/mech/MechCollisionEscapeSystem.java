package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.engine.ecs.Query;

/**
 * Detects mechs whose active path has made no net progress, allowing them to
 * temporarily ignore the soft separation impulse from other mechs. This is a
 * deadlock escape hatch only: terrain walkability and every non-mech collision
 * rule remain unchanged, and ordinary separation resumes on the next meaningful
 * advance toward the path destination.
 */
public final class MechCollisionEscapeSystem {

    /** Delay before a no-progress mech may pass through another mech's soft collision. */
    public static final float STALL_ESCAPE_DELAY_SECONDS = 1.5f;
    /** Required reduction in remaining destination distance to count as meaningful progress. */
    public static final float PROGRESS_DISTANCE = 0.10f;

    private final EntityWorld world;
    private final BattleComponents components;
    private final Query mechs;

    public MechCollisionEscapeSystem(EntityWorld world, BattleComponents components) {
        this.world = world;
        this.components = components;
        this.mechs = world.query(new ComponentType[]{components.MECH_LOADOUT,
                components.MOVEMENT, components.POSITION, components.HEALTH}, null);
    }

    /** Must run after separation, so the observed position includes its opposing push. */
    public void tick(float dt) {
        for (ArchetypeTable table : world.matched(mechs)) {
            Object[] loadouts = table.objects(components.MECH_LOADOUT,
                    BattleComponents.MECH_LOADOUT_STATE).array();
            Object[] paths = table.objects(components.MOVEMENT,
                    BattleComponents.MOVEMENT_PATH).array();
            int[] pathIdx = table.ints(components.MOVEMENT,
                    BattleComponents.MOVEMENT_PATH_IDX).array();
            float[] posX = table.floats(components.POSITION, BattleComponents.POSITION_X).array();
            float[] posY = table.floats(components.POSITION, BattleComponents.POSITION_Y).array();
            for (int row = 0, n = table.rowCount(); row < n; row++) {
                MechLoadoutComponent loadout = (MechLoadoutComponent) loadouts[row];
                int[] path = (int[]) paths[row];
                if (pathIdx[row] >= Paths.cellCount(path)) {
                    reset(loadout);
                    continue;
                }

                int destX = Paths.destX(path);
                int destY = Paths.destY(path);
                float dx = destX + 0.5f - posX[row];
                float dy = destY + 0.5f - posY[row];
                float remainingDistance = (float) Math.sqrt(dx * dx + dy * dy);
                if (loadout.collisionProgressDestX != destX
                        || loadout.collisionProgressDestY != destY) {
                    loadout.collisionProgressDestX = destX;
                    loadout.collisionProgressDestY = destY;
                    loadout.collisionBestRemainingDistance = remainingDistance;
                    loadout.collisionStallSeconds = 0f;
                    loadout.collisionEscapeActive = false;
                } else if (loadout.collisionBestRemainingDistance - remainingDistance
                        >= PROGRESS_DISTANCE) {
                    loadout.collisionBestRemainingDistance = remainingDistance;
                    loadout.collisionStallSeconds = 0f;
                    loadout.collisionEscapeActive = false;
                } else {
                    loadout.collisionStallSeconds += Math.max(0f, dt);
                    loadout.collisionEscapeActive = loadout.collisionStallSeconds
                            >= STALL_ESCAPE_DELAY_SECONDS;
                }
            }
        }
    }

    private static void reset(MechLoadoutComponent loadout) {
        loadout.collisionStallSeconds = 0f;
        loadout.collisionBestRemainingDistance = Float.POSITIVE_INFINITY;
        loadout.collisionProgressDestX = Integer.MIN_VALUE;
        loadout.collisionProgressDestY = Integer.MIN_VALUE;
        loadout.collisionEscapeActive = false;
    }
}

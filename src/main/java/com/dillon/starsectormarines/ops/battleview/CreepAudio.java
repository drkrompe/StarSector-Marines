package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.fs.starfarer.api.Global;
import org.lwjgl.util.vector.Vector2f;

/**
 * The creep's sound (`colony-breach-nouns.md`, Creep): a wet, slow spread bed
 * heard over living creep the player has seen. One loop, anchored on the seen
 * creep nearest the camera and as loud as the share of the ground round the
 * camera the creep has taken, so a sliver at the edge of the view is a murmur
 * and a street drowned in it is a full bed. It reads the creep field and the
 * fog and writes nothing, so nothing heard reaches the battle.
 */
public final class CreepAudio {

    static final String SPREAD = "marines_breach_creep_spread";
    /** Cells either side of the camera searched for creep. */
    static final int REACH = 14;
    /** Search stride in cells: creep comes in sheets, so every other cell is plenty. */
    static final int STRIDE = 2;
    private static final float VOLUME = 0.55f;
    /** The share of the searched ground under creep at which the bed is at full volume. */
    private static final float FULL_AT = 0.5f;
    private static final float QUIETEST = 0.25f;
    private static final Object LOOP_OWNER = new Object();

    private CreepAudio() { }

    /** Called every rendered frame: a loop must be refreshed each frame or it stops. */
    public static void play(BattleSimulation sim, float cameraCellX, float cameraCellY) {
        CreepField field = sim.creepFieldIfPresent();
        if (field == null || field.isEmpty()) return;
        Bed bed = bed((x, y) -> field.isCreeped(x, y) && sim.getFogOfWar().isCellRevealed(x, y),
                cameraCellX, cameraCellY);
        if (bed == null) return;
        Global.getSoundPlayer().playLoop(SPREAD, LOOP_OWNER, 1f, VOLUME * bed.loudness(),
                new Vector2f((bed.x() + 0.5f) * BattleShotAudio.WORLD_UNITS_PER_CELL,
                        (bed.y() + 0.5f) * BattleShotAudio.WORLD_UNITS_PER_CELL),
                new Vector2f(0f, 0f));
    }

    /** Where the bed sits and how loud it is, or null when no seen creep lies within {@link #REACH}. */
    static Bed bed(Cells creep, float cameraCellX, float cameraCellY) {
        int cx = (int) Math.floor(cameraCellX);
        int cy = (int) Math.floor(cameraCellY);
        int bestX = 0, bestY = 0, hits = 0, samples = 0;
        long best = Long.MAX_VALUE;
        for (int y = cy - REACH; y <= cy + REACH; y += STRIDE) {
            for (int x = cx - REACH; x <= cx + REACH; x += STRIDE) {
                samples++;
                if (!creep.at(x, y)) continue;
                hits++;
                long d = (long) (x - cx) * (x - cx) + (long) (y - cy) * (y - cy);
                if (d < best) {
                    best = d;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        if (hits == 0) return null;
        float share = hits / (float) samples;
        return new Bed(bestX, bestY, Math.max(QUIETEST, Math.min(1f, share / FULL_AT)));
    }

    @FunctionalInterface
    interface Cells {
        boolean at(int x, int y);
    }

    record Bed(int x, int y, float loudness) { }
}

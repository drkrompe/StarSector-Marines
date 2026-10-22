package com.dillon.starsectormarines.battle.creep;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;

import java.util.HashMap;
import java.util.Map;

/**
 * The creep digests the swarm's dead (`colony-breach-nouns.md`, Creep): a strain body lying on
 * living creep sinks into it over {@link #DIGEST_SECONDS} and is gone, so a long battle on the
 * creep does not pile up corpses. A body off the creep keeps whatever it had digested and waits
 * for the creep to reach it; a body being dragged is not lying anywhere and is left alone.
 * Marines, militia and colonists are the harvest's and are never digested.
 *
 * <p>Runs serially once per tick after the creep has spread. Progress is kept by body id and
 * read by the renderer ({@link #digested}) to draw the melt.
 */
public final class CreepDigestion {

    /** Seconds on living creep before a body is gone. */
    public static final float DIGEST_SECONDS = 25f;

    private final Map<Long, Float> progress = new HashMap<>();

    /** How far {@code body} has sunk into the creep, 0 (lying fresh) to 1 (gone). */
    public float digested(long body) {
        Float seconds = progress.get(body);
        return seconds == null ? 0f : Math.min(1f, seconds / DIGEST_SECONDS);
    }

    public void tick(BattleSimulation sim) {
        CreepField creep = sim.creepFieldIfPresent();
        if (creep == null) return;
        BattleComponents c = sim.getBattleComponents();
        for (ArchetypeTable t : sim.getEntityWorld().matched(c.corpses)) {
            Object[] types = t.objects(c.IDENTITY, BattleComponents.IDENTITY_TYPE).array();
            int[] sprite = t.ints(c.SPRITE, BattleComponents.SPRITE_INDEX).array();
            float[] x = t.floats(c.POSITION, BattleComponents.POSITION_X).array();
            float[] y = t.floats(c.POSITION, BattleComponents.POSITION_Y).array();
            for (int r = 0, n = t.rowCount(); r < n; r++) {
                if (sprite[r] < 0 || !(types[r] instanceof UnitType type) || !type.isSwarm()) continue;
                if (!creep.isCreeped((int) Math.floor(x[r]), (int) Math.floor(y[r]))) continue;
                long body = t.entityAt(r);
                if (sim.swarmHarvest().isCarried(body)) continue;
                float seconds = progress.getOrDefault(body, 0f) + BattleSimulation.TICK_DT;
                if (seconds >= DIGEST_SECONDS) {
                    // gone into the creep: no longer drawn, as a body fed into a bore is
                    sim.getEntityWorld().setInt(body, c.SPRITE, BattleComponents.SPRITE_INDEX, -1);
                    progress.remove(body);
                } else {
                    progress.put(body, seconds);
                }
            }
        }
    }
}

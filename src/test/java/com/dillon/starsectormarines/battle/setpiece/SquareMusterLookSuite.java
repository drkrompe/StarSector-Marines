package com.dillon.starsectormarines.battle.setpiece;

import com.dillon.starsectormarines.battle.evacuation.ColonyBreachObjectives;
import com.dillon.starsectormarines.battle.objective.optional.PressuredObjective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.Locale;

/** "Hold the muster" in a market square, held by a static guard against its bore's broods from the mouths. */
public final class SquareMusterLookSuite extends SetPiecePressureLook<SquareMuster> {

    @Override public String id() { return "square-muster-look"; }

    @Override public String label() { return "Market square: holding a muster under its own bore's pressure"; }

    @Override String key() { return ColonyBreachObjectives.MUSTER; }

    @Override long[] seeds() { return new long[]{2L, 3L, 4L}; }

    @Override SquareMuster task(PressuredObjective.Task task) { return task instanceof SquareMuster m ? m : null; }

    @Override float[] focus(BattleSimulation sim, SquareMuster muster) {
        return new float[]{muster.square().centreX() + .5f, muster.square().centreY() + .5f};
    }

    @Override String progress(BattleSimulation sim, SquareMuster muster) {
        return String.format(Locale.ROOT, "%.0f s held, %d/%d alive, %d mouths", muster.elapsed(), muster.alive(sim),
                muster.size(), muster.square().mouths().size());
    }
}

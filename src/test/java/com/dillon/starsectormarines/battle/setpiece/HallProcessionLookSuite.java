package com.dillon.starsectormarines.battle.setpiece;

import com.dillon.starsectormarines.battle.evacuation.ColonyBreachObjectives;
import com.dillon.starsectormarines.battle.objective.optional.PressuredObjective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.Locale;

/** "Empty the hall" down a main street, kept open by a static guard against its bore's broods from the side ways in. */
public final class HallProcessionLookSuite extends SetPiecePressureLook<HallProcession> {

    @Override public String id() { return "hall-procession-look"; }

    @Override public String label() { return "Main street: emptying the hall under its own bore's pressure"; }

    @Override String key() { return ColonyBreachObjectives.HALL; }

    @Override long[] seeds() { return new long[]{2L, 3L, 4L}; }

    @Override HallProcession task(PressuredObjective.Task task) { return task instanceof HallProcession p ? p : null; }

    @Override float[] focus(BattleSimulation sim, HallProcession procession) {
        int[] middle = HallProcession.spineMiddle(procession.street());
        return new float[]{middle[0] + .5f, middle[1] + .5f};
    }

    @Override String progress(BattleSimulation sim, HallProcession procession) {
        return String.format(Locale.ROOT, "%d out, %d at the plaza, %d alive", procession.released(),
                procession.arrived(), procession.alive(sim));
    }
}

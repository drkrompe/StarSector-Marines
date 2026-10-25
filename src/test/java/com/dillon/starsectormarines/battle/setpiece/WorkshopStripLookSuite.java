package com.dillon.starsectormarines.battle.setpiece;

import com.dillon.starsectormarines.battle.evacuation.ColonyBreachObjectives;
import com.dillon.starsectormarines.battle.objective.optional.PressuredObjective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.Locale;

/** "Strip the workshop" on a factory row, held by a static guard against its bore's broods from both gates. */
public final class WorkshopStripLookSuite extends SetPiecePressureLook<WorkshopStrip> {

    @Override public String id() { return "workshop-strip-look"; }

    @Override public String label() { return "Factory row: stripping a workshop under its own bore's pressure"; }

    @Override String key() { return ColonyBreachObjectives.STRIP; }

    @Override long[] seeds() { return new long[]{2L, 5L, 7L}; }

    @Override WorkshopStrip task(PressuredObjective.Task task) { return task instanceof WorkshopStrip s ? s : null; }

    @Override float[] focus(BattleSimulation sim, WorkshopStrip strip) {
        return new float[]{strip.stackCell()[0] + .5f, strip.stackCell()[1] + .5f};
    }

    @Override String progress(BattleSimulation sim, WorkshopStrip strip) {
        return String.format(Locale.ROOT, "%d/%d stacked, %d hands", strip.carried(), strip.target(), strip.hands(sim));
    }
}

package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.RavBruteRig;
import com.dillon.starsectormarines.battle.appearance.RavMatriarchRig;
import com.dillon.starsectormarines.battle.appearance.RavRunnerRig;
import com.dillon.starsectormarines.battle.appearance.RavSpitterRig;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.SwarmStage;

/**
 * A standing pose for a Rav body whose skeleton has not been advanced yet.
 *
 * <p>A rig is first posed by the sim's first tick after its body spawns, and a body spawned during
 * setup is drawn before that tick, for a frame or for as long as the battle stays paused. Without a
 * pose the body fell through to the old alien sheet, so a Matriarch opened its battle as a purple
 * placeholder. This poses a fresh rig of the same kind, seeded as the roster seeds it, once where
 * the body stands, and the frame draws that instead. The body's own rig is not touched, so the
 * simulation and its first tick play exactly as they did.
 */
final class RavRigStandIn {

    private RavRigStandIn() {
    }

    /** Whether {@code rig} is a Rav rig that has not been posed; anything else is left alone. */
    static boolean needed(Object rig) {
        return rig instanceof RavRunnerRig r && !r.posed()
                || rig instanceof RavBruteRig b && !b.posed()
                || rig instanceof RavMatriarchRig m && !m.posed()
                || rig instanceof RavSpitterRig s && !s.posed();
    }

    /** A rig of {@code rig}'s kind, standing at {@code (x, y)} facing {@code facing}. */
    static Object of(Object rig, long seed, float x, float y, float facing, SwarmStage stage) {
        float dt = BattleSimulation.TICK_DT;
        if (rig instanceof RavBruteRig) {
            RavBruteRig brute = new RavBruteRig(seed);
            brute.setStage(stage);
            brute.advance(x, y, facing, 0f, RavBruteRig.IDLE, false, dt);
            return brute;
        }
        if (rig instanceof RavMatriarchRig) {
            RavMatriarchRig matriarch = new RavMatriarchRig(seed);
            matriarch.setStage(stage);
            matriarch.advance(x, y, facing, RavMatriarchRig.NONE, 0f, dt);
            return matriarch;
        }
        if (rig instanceof RavSpitterRig) {
            RavSpitterRig spitter = new RavSpitterRig(seed);
            spitter.setStage(stage);
            spitter.advance(x, y, facing, 1f, false, dt);
            return spitter;
        }
        RavRunnerRig runner = new RavRunnerRig(seed);
        runner.setStage(stage);
        runner.advance(x, y, facing, 0f, false, dt);
        return runner;
    }
}

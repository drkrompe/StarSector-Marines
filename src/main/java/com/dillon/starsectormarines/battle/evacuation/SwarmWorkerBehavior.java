package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * A skitterer's legs (`colony-breach-nouns.md`, Skitterer): it walks to the cell its job names and stands there,
 * or drags its load home as a carrier does. Everything it decides and does at the end of the walk is
 * {@link SwarmWorkers}', made serially; this runs on the parallel unit update and only reads the job. It never
 * takes a target and never strikes.
 */
final class SwarmWorkerBehavior {

    /** It stops a little inside the reach at which {@link SwarmWorkers} counts it as at its work. */
    private static final float STAND = SwarmWorkers.ARRIVE * 0.8f;

    private SwarmWorkerBehavior() {}

    static void update(long worker, BattleSimulation sim) {
        sim.combat().setTargetId(worker, 0L);
        if (sim.swarmHarvest().isCarrier(worker)) {
            SwarmPressureBehavior.carryHome(worker, sim);
            return;
        }
        SwarmWorkers.Worker w = sim.swarmWorkers().of(worker);
        if (w == null) {
            sim.clearPath(worker);
            return;
        }
        int tx = w.targetX(), ty = w.targetY();
        float dx = tx + 0.5f - sim.world().x(worker), dy = ty + 0.5f - sim.world().y(worker);
        if (dx * dx + dy * dy <= STAND * STAND) {
            sim.clearPath(worker);
            return;
        }
        if (SwarmMovement.ensurePath(worker, tx, ty, w.job() == SwarmWorkers.Job.FLEE, false, sim)) {
            sim.advanceMovement(worker);
        }
    }
}

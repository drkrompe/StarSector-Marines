package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.ambient.AmbientTaskService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * Turns the hands actually at work on an apron into hull put back on the
 * aircraft standing there.
 *
 * <p>A turnaround used to be a countdown. That made a field answer its next
 * request forty-five seconds later whether or not anybody was working, so an
 * attacker who killed the ground crew denied it nothing, and one who reached the
 * apron mid-servicing found the aircraft was not even there. Counted in hands
 * instead, servicing is work: done by people who can be shot where they stand,
 * on an aircraft that is standing on its concrete while they do it.
 *
 * <p>What counts is a technician <em>at</em> a servicing point. Not standing on
 * the apron, not walking across it, and not at a stand's board, which is a job
 * the same person does on the same rotation. The ambient service says which job
 * somebody has in hand and the field says which berth each servicing cell works,
 * so the two together say who is working on what.
 *
 * <p>Work goes onto whatever is on the stand, whether or not it needs it. A
 * technician does not walk past a full airframe to find the damaged one — the
 * rotation offers a free place to work and they take it — so a field with more
 * stands than hands turns an aircraft round more slowly, and what an aircraft
 * waits for is its turn. That is a field's shape rather than a rule about it.
 *
 * <p>Follows the {@code *System} convention: stateless, reads
 * {@link AirfieldService} and the battle.
 */
public final class AirfieldCrewSystem {

    public void tick(float dt, BattleSimulation sim, AirfieldService field) {
        if (field == null || field.berths().isEmpty() || dt <= 0f) return;

        AmbientTaskService tasks = sim.ambientTasks();
        for (long actor : tasks.assigned()) {
            if (tasks.jobInHand(actor) == null) continue;
            int index = field.berthServicedFrom(
                    sim.world().cellX(actor), sim.world().cellY(actor));
            if (index < 0 || index >= field.berths().size()) continue;

            AirfieldService.Berth berth = field.berths().get(index);
            if (berth.airframeId == 0L || !sim.world().isAlive(berth.airframeId)) continue;
            // The hull first and the rest of the servicing after it, so what a
            // turnaround has left to do is legible from the outside for as long
            // as there is anything to see.
            put(sim, berth.airframeId, dt * AirfieldService.HULL_PER_HAND_SECOND);
            if (berth.state == AirfieldService.BerthState.REFITTING) {
                berth.refitWork = Math.max(0f, berth.refitWork - dt);
            }
        }
    }

    /** Put hull back on an airframe, up to what an undamaged one has. */
    private static void put(BattleSimulation sim, long airframeId, float hull) {
        float mended = Math.min(sim.world().maxHp(airframeId),
                sim.world().hp(airframeId) + hull);
        sim.world().setHp(airframeId, mended);
    }
}

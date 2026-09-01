package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.air.AirDeliveryContext;
import com.dillon.starsectormarines.battle.air.AirDeliveryPayload;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * One technician, set down to fill one billet on one posting.
 *
 * <p>The last leg of a relief flight, and the only part of it this class owns:
 * the shared shuttle state machine still flies the approach, takes the fire on
 * the way in, and departs afterwards. What arrives is an ordinary person on the
 * ground holding the billet's own rotation — the same person the walk-in
 * produces, put down somewhere else.
 *
 * <p>Deliberately a per-sortie object rather than a shared instance, unlike the
 * generic delivery recipes: the other payloads answer "what does this carrier
 * unload", which is the same every time, and this one answers "who is this
 * flight for", which is a different billet on a different posting every time.
 *
 * <p>It also lives with the works crew rather than with the aircraft, because
 * everything it knows is a works crew's — a posting, a billet, a rotation. The
 * air domain owns flying, not the errands other domains fly.
 */
final class WorksReliefPayload implements AirDeliveryPayload {

    private final BattleSimulation sim;
    private final WorksCrewService.Posting posting;
    private final int billet;
    private final AmbientTaskRoute rotation;
    private final Faction holder;
    private final int squadId;
    private final String name;

    WorksReliefPayload(BattleSimulation sim, WorksCrewService.Posting posting, int billet,
                       AmbientTaskRoute rotation, Faction holder, int squadId, String name) {
        this.sim = sim;
        this.posting = posting;
        this.billet = billet;
        this.rotation = rotation;
        this.holder = holder;
        this.squadId = squadId;
        this.name = name;
    }

    @Override
    public int unitsPerSortie(ShuttleType carrier) {
        return 1;
    }

    /**
     * Put the replacement on the ground and hand them the rotation.
     *
     * <p>The billet is filled here rather than at dispatch, so it is held by
     * the flight until the flight actually delivers. A lift shot down on the
     * way in leaves the billet empty and the posting short-handed again, which
     * is the reading that makes intercepting one worth doing.
     */
    @Override
    public boolean tryDeploy(AirDeliveryContext context) {
        int[] cell = context.findOpenDeboardCell();
        if (cell == null) return false;

        EntitySpec hand = new EntitySpec(name, holder, posting.type, cell[0], cell[1]);
        hand.squad(squadId);
        long actor = context.spawn(hand);
        sim.ambientTasks().assign(actor, rotation);
        // Clears the inbound flight as it fills the seat: the crossing is over
        // and the person is what the billet holds from here.
        posting.fill(billet, actor);
        return true;
    }
}

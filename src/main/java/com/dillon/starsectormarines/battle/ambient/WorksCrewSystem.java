package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.reinforcement.MapEntry;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.Locale;

/**
 * Sends somebody to fill a billet a works crew lost, on the account of whoever
 * holds the building.
 *
 * <p>Two things fall out of that one sentence and both are the point.
 *
 * <p><b>Killing a crew buys time rather than the building.</b> A shed whose
 * technicians are dead stops producing, and stays stopped for as long as it
 * takes somebody to walk across the map — real minutes, and minutes an attacker
 * has to spend elsewhere. It does not stop forever, because a facility that one
 * fire team could permanently switch off would be a prize nobody who took it
 * could use.
 *
 * <p><b>And a captured building is a captured building.</b> The side sent for is
 * the side holding the ground, so a motor pool taken from the defender turns out
 * marine technicians and builds marine machines. That is what makes a facility
 * worth garrisoning rather than only worth clearing.
 *
 * <p>They arrive <b>on foot from their own rear edge</b> and walk to the work.
 * Nothing here moves them: a replacement is spawned at the edge and handed the
 * billet's own rotation, and the ambient service paths them to its first stop
 * exactly as it would somebody crossing a room. The walk is long on purpose and
 * is a stretch of open ground somebody can be shot on.
 *
 * <p>Follows the {@code *System} convention: stateless, reads
 * {@link WorksCrewService} and the battle.
 */
public final class WorksCrewSystem {

    private static final Logger LOG = Global.getLogger(WorksCrewSystem.class);

    private int nextReplacement;

    public void tick(float dt, BattleSimulation sim, WorksCrewService crews) {
        if (crews == null || crews.isEmpty() || dt <= 0f) return;

        for (WorksCrewService.Posting posting : crews.postings()) {
            buryTheDead(sim, posting);
            int billet = posting.firstEmpty();
            if (billet < 0) {
                posting.filled();
                continue;
            }
            if (posting.owner == null) continue;

            posting.waited(dt);
            if (posting.shortHanded() < WorksCrewService.REPLACEMENT_SECONDS) continue;

            Faction holder = holderOf(sim, posting);
            if (holder == null) continue;
            if (sendOne(sim, crews, posting, billet, holder)) posting.filled();
        }
    }

    /**
     * Empty the billets of anybody who is no longer on the roster.
     *
     * <p>Read off liveness rather than told by a death event, because a billet
     * has exactly one question — is somebody in it — and an event stream is a
     * second place for the answer to live. A hand who left the roster any other
     * way than dying leaves the same hole.
     */
    private static void buryTheDead(BattleSimulation sim, WorksCrewService.Posting posting) {
        for (int billet = 0; billet < posting.billets(); billet++) {
            long hand = posting.hand(billet);
            if (hand != 0L && !sim.getRoster().isLive(hand)) posting.fill(billet, 0L);
        }
    }

    /**
     * Whose crew this posting's next technician is, or null while nobody's.
     *
     * <p>The compound's capture state and nothing else, on the same reading the
     * resource pools use: a contested place still supplies the side that has not
     * lost it, and a place in nobody's hands supplies nobody. A room with no
     * compound over it — a hangar in a city block rather than a garrison's own
     * field — is never refilled at all, because there is no one whose crew it
     * would be.
     */
    private static Faction holderOf(BattleSimulation sim, WorksCrewService.Posting posting) {
        CompoundService.Record record = sim.getCompoundService().getRecord(posting.owner);
        if (record == null) return null;
        return switch (record.state) {
            case DEFENDER_HELD, CONTESTED -> Faction.DEFENDER;
            case MARINE_HELD -> Faction.MARINE;
            default -> null;
        };
    }

    /**
     * Put one replacement on the map at their own side's edge, already holding
     * the billet's rotation.
     *
     * @return whether one actually went; an edge with nowhere to stand leaves
     *     the posting short-handed and asking again next tick
     */
    private boolean sendOne(BattleSimulation sim, WorksCrewService crews,
                            WorksCrewService.Posting posting, int billet, Faction holder) {
        AmbientTaskRoute rotation = posting.bill.member(billet);
        if (rotation == null) return false;

        int[] entry = MapEntry.forSide(sim, holder, crews.axis(),
                posting.towardX, posting.towardY);
        if (entry == null) return false;

        EntitySpec hand = new EntitySpec(
                "wr" + (nextReplacement++) + "-" + rotation.id(),
                holder, posting.type, entry[0], entry[1]);
        hand.squad(watchFor(sim, posting, holder));
        long actor = sim.spawn(hand);
        sim.ambientTasks().assign(actor, rotation);
        posting.fill(billet, actor);

        LOG.info("WorksCrewSystem: " + holder + " " + posting.role
                + " walking on at " + entry[0] + "," + entry[1]
                + " to fill billet " + billet + " at site " + posting.siteId);
        return true;
    }

    /**
     * The squad this side's people on this posting belong to, minting it the
     * first time that side sends anybody.
     *
     * <p>Per side rather than per posting, because a captured building's new
     * crew are not reinforcements for the old one. Claimed on the map's own
     * authority for the reason the original watch is: a works crew mission
     * command can see and has not been told is somebody else's is a works crew
     * it will send to hold a road.
     */
    private static int watchFor(BattleSimulation sim, WorksCrewService.Posting posting,
                                Faction holder) {
        int existing = posting.watchFor(holder);
        if (existing != 0) {
            Squad squad = sim.getSquad(existing);
            if (squad != null) return existing;
        }
        int squadId = sim.mintSquad(holder, posting.type);
        SquadCommandClaim.works(posting.role.name().toLowerCase(Locale.ROOT)
                + " relief at site " + posting.siteId).apply(sim, squadId);
        posting.setWatchFor(holder, squadId);
        return squadId;
    }
}

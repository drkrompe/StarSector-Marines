package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.air.AirProvider;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.reinforcement.LandingZoneScorer;
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
 * <p>They arrive <b>from their own side of the map</b> and finish the journey on
 * foot. Nothing here moves them: a replacement is put on the map holding the
 * billet's own rotation, and the ambient service paths them to its first stop
 * exactly as it would somebody crossing a room. The last stretch is open ground
 * somebody can be shot on, and that is the point of it.
 *
 * <p><b>The defender walks on; the marines fly most of the way and walk the
 * rest.</b> Not a fairness knob — it is what each side has. A garrison's rear is
 * the map edge, so its people simply come from it; an attacker has no rear on
 * this planet at all and everybody they field arrived by air. So a marine relief
 * is a sortie: it crosses on off-map, sets down a stand-off out from the work,
 * and the technician walks in from there. The flight covers the safe half of the
 * journey and can itself be intercepted; the walk is the half that is contested
 * either way.
 *
 * <p>A map with nowhere to put an aircraft down falls back to the walk rather
 * than to refusing the relief, on the same reading {@link MapEntry} takes of a
 * walled rear edge: a worse arrival, not an absent one.
 *
 * <p>Follows the {@code *System} convention: stateless, reads
 * {@link WorksCrewService} and the battle.
 */
public final class WorksCrewSystem {

    private static final Logger LOG = Global.getLogger(WorksCrewSystem.class);

    /** What the marines send one technician in on. */
    private static final ShuttleType RELIEF_TYPE = ShuttleType.AEROSHUTTLE;

    /**
     * How far short of the work a relief flight sets down, in cells.
     *
     * <p>Far enough that there is still a walk — a lift that put its passenger
     * on the shed's doorstep would be a spawner with an aircraft drawn around
     * it, and would make a facility deep in held ground exactly as easy to
     * re-crew as one on the perimeter. Near enough that the flight is plainly
     * about this building.
     */
    private static final int RELIEF_STANDOFF_CELLS = 18;

    /** How far from that stand-off point a viable pad is looked for. */
    private static final int RELIEF_LZ_SCAN = 10;

    /**
     * Open neighbours a relief pad needs. The same lenient bar the reinforcement
     * sortie uses: an aircraft should not set down in a one-cell pinch, and the
     * load-bearing test is the scorer's own viability rule.
     */
    private static final int RELIEF_MIN_CLEARANCE = 2;

    private int nextReplacement;

    public void tick(float dt, BattleSimulation sim, WorksCrewService crews) {
        if (crews == null || crews.isEmpty() || dt <= 0f) return;

        for (WorksCrewService.Posting posting : crews.postings()) {
            buryTheDead(sim, posting);
            forgetLostFlights(sim, posting);
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
     * Reopen the billets of relief flights that ended without delivering.
     *
     * <p>A lift shot down on the way in is the same hole as a technician shot at
     * the bench, and it reopens on the same cadence: the seat is empty again and
     * another goes in a minute and a half. That is what makes intercepting one
     * worth the rounds.
     *
     * <p>Read off the flight rather than off the roster, because an aircraft is
     * world-resident and never appears in the roster walk above — asking the
     * roster whether a shuttle is alive answers no on the tick it launched.
     */
    private static void forgetLostFlights(BattleSimulation sim,
                                          WorksCrewService.Posting posting) {
        for (int billet = 0; billet < posting.billets(); billet++) {
            long carrier = posting.inbound(billet);
            if (carrier == 0L) continue;
            ShuttleMission flight = sim.world().mission(carrier);
            if (flight == null || flight.state == ShuttleState.GONE) {
                posting.setInbound(billet, 0L);
            }
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

        String name = "wr" + (nextReplacement++) + "-" + rotation.id();
        if (holder == Faction.MARINE
                && flyOne(sim, crews, posting, billet, rotation, name)) {
            return true;
        }
        return walkOne(sim, crews, posting, billet, rotation, holder, name);
    }

    /** Put one replacement on their own rear edge, already holding the rotation. */
    private boolean walkOne(BattleSimulation sim, WorksCrewService crews,
                            WorksCrewService.Posting posting, int billet,
                            AmbientTaskRoute rotation, Faction holder, String name) {
        int[] entry = MapEntry.forSide(sim, holder, crews.axis(),
                posting.towardX, posting.towardY);
        if (entry == null) return false;

        EntitySpec hand = new EntitySpec(name, holder, posting.type, entry[0], entry[1]);
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
     * Send one replacement in by air, to land a stand-off out from the work.
     *
     * <p><b>The billet is answered for by the flight, not filled by it.</b>
     * Nobody is at the bench and the shed is still not working; what the
     * inbound mark buys is only that the same empty seat does not launch a
     * second lift on every tick of the crossing. The payload fills the seat on
     * touchdown, and a flight that never arrives reopens it.
     *
     * @return whether a lift actually went; false leaves the caller to walk one
     *     in instead
     */
    private boolean flyOne(BattleSimulation sim, WorksCrewService crews,
                           WorksCrewService.Posting posting, int billet,
                           AmbientTaskRoute rotation, String name) {
        // Nothing to fly on a battle whose air the host owns; the walk still
        // works, which is why this reads as "no lift went" rather than as an
        // error.
        if (sim.getAirProvider() != AirProvider.INTERNAL) return false;
        int[] lz = reliefPad(sim, crews, posting);
        if (lz == null) return false;

        float[] entry = MapEntry.airForSide(Faction.MARINE, crews.axis(), lz[0], lz[1],
                sim.getGrid().getWidth(), sim.getGrid().getHeight());
        long lift = sim.spawnShuttle(RELIEF_TYPE, Faction.MARINE, lz[0], lz[1],
                entry[0], entry[1], entry[2], entry[3], /*pendingDelay*/ 0f,
                /*seatsPerSortie*/ 1);
        ShuttleMission mission = sim.world().mission(lift);
        mission.totalCycles = 1;
        mission.payload = new WorksReliefPayload(sim, posting, billet, rotation,
                Faction.MARINE, watchFor(sim, posting, Faction.MARINE), name);
        posting.setInbound(billet, lift);

        LOG.info("WorksCrewSystem: MARINE " + posting.role
                + " flying in to " + lz[0] + "," + lz[1]
                + " to fill billet " + billet + " at site " + posting.siteId);
        return true;
    }

    /**
     * Somewhere to set a relief flight down: a stand-off out from the work,
     * along the line back to the marines' own edge.
     *
     * <p>The direction is borrowed from the on-foot answer rather than derived
     * again. Where a replacement would walk on from is already "the marine side
     * of the map"; a lift that computed its own idea of that could disagree with
     * the walk it is replacing, and one of them would then be wrong.
     */
    private static int[] reliefPad(BattleSimulation sim, WorksCrewService crews,
                                   WorksCrewService.Posting posting) {
        int[] rear = MapEntry.forSide(sim, Faction.MARINE, crews.axis(),
                posting.towardX, posting.towardY);
        if (rear == null) return null;
        float dx = rear[0] - posting.towardX;
        float dy = rear[1] - posting.towardY;
        float span = (float) Math.hypot(dx, dy);
        // The work is already close to the edge, so there is no stand-off to
        // take and nothing an aircraft adds to walking on beside it.
        if (span < RELIEF_STANDOFF_CELLS) return null;

        int hintX = Math.round(posting.towardX + dx / span * RELIEF_STANDOFF_CELLS);
        int hintY = Math.round(posting.towardY + dy / span * RELIEF_STANDOFF_CELLS);
        return new LandingZoneScorer(sim.getGrid(), sim.getTopology())
                .bestNear(hintX, hintY, RELIEF_LZ_SCAN, RELIEF_MIN_CLEARANCE);
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

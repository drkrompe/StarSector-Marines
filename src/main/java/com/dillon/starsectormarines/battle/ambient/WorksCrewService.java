package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Every watch standing on this map's worked structures, and which of their
 * billets are empty.
 *
 * <p>A manning pass hires a crew once and walks away. That is enough for a
 * building nobody ever reaches and wrong for one somebody does: kill the
 * technicians and the shed is finished for the battle, whoever ends up holding
 * it. So a posting outlives the people filling it — the room still has the work,
 * the watch bill still says how it is walked, and a billet somebody died in is
 * simply empty until it is filled again.
 *
 * <p><b>By whoever holds the place.</b> A works crew belongs to the ground it
 * stands on rather than to the side that first posted it, so a compound taken
 * from the defender is a compound whose next technicians are marines. That is
 * what makes a facility worth holding rather than only worth raiding: the
 * building does not stop being useful when it changes hands, it starts being
 * useful to somebody else.
 *
 * <p>Follows the {@code *Service} convention: state owner, advanced by
 * {@link WorksCrewSystem}.
 */
public final class WorksCrewService {

    /**
     * How long a billet stands empty before somebody is sent to fill it.
     *
     * <p>Long, and that is the whole shape of the thing. A crew replaced
     * promptly makes killing them a tax rather than a result; one never replaced
     * makes a facility a single irreversible prize and gives whoever takes it
     * nothing. Long enough that clearing a shed buys real minutes of it not
     * working, short enough that a side which holds the ground for the rest of
     * the battle gets the building back.
     *
     * <p>One replacement per expiry rather than the whole watch at once, so a
     * crew that was wiped out comes back a person at a time and a facility
     * returns to full production gradually.
     */
    public static final float REPLACEMENT_SECONDS = 90f;

    /** One trade's watch on one room, and the people filling it. */
    public static final class Posting {

        /** The room worked, which is what its claim groups are scoped to. */
        public final int siteId;
        /** The trade, which is what its rotation is. */
        public final CrewRole role;
        /** What its people turn out as. */
        public final UnitType type;
        /** The bill: where the work is and the loop each member walks. */
        public final Shift bill;
        /**
         * The compound whose capture state says who holds this place, or null
         * for a worked room that is nobody's — a hangar in a city block rather
         * than a garrison's own field. A posting with no owner is never
         * refilled, because there is nobody whose crew it would be.
         */
        public final TacticalNode owner;
        /** Somewhere to aim an arrival at, so a replacement walks on facing it. */
        public final int towardX;
        public final int towardY;

        /** Who is filling each billet, by member index; 0 where it is empty. */
        private final long[] hands;
        /**
         * The flight bringing somebody to each billet, by member index; 0 where
         * none is on the way.
         *
         * <p>Separate from {@link #hands} rather than folded into it, because a
         * lift is not a technician: nobody is at the bench yet, the shed is
         * still not working, and the aircraft is answerable to a different
         * liveness question — an air craft is world-resident and never appears
         * in the roster walk that empties a billet whose person died. Kept only
         * so the same empty seat does not launch a second lift every tick.
         */
        private final long[] inbound;
        /** One squad per side, minted when that side first sends somebody. */
        private final Map<Faction, Integer> watches = new EnumMap<>(Faction.class);
        /** Seconds this posting has stood short-handed. */
        private float shortHanded;

        Posting(int siteId, CrewRole role, UnitType type, Shift bill,
                TacticalNode owner, int towardX, int towardY, int billets) {
            this.siteId = siteId;
            this.role = role;
            this.type = type;
            this.bill = bill;
            this.owner = owner;
            this.towardX = towardX;
            this.towardY = towardY;
            this.hands = new long[billets];
            this.inbound = new long[billets];
        }

        public int billets() {
            return hands.length;
        }

        public long hand(int billet) {
            return hands[billet];
        }

        public void fill(int billet, long actor) {
            hands[billet] = actor;
            inbound[billet] = 0L;
        }

        /** The flight on its way to this billet, or 0 when none is. */
        public long inbound(int billet) {
            return inbound[billet];
        }

        public void setInbound(int billet, long carrier) {
            inbound[billet] = carrier;
        }

        /**
         * The lowest billet with nobody in it and nobody on the way, or -1 when
         * every seat is answered for.
         *
         * <p>A billet somebody is already flying to is not empty for this
         * purpose. It is still unworked — the shed does not produce until they
         * are at the bench — but sending a second lift to the same seat every
         * tick of the crossing would answer one casualty with an air bridge.
         */
        public int firstEmpty() {
            for (int billet = 0; billet < hands.length; billet++) {
                if (hands[billet] == 0L && inbound[billet] == 0L) return billet;
            }
            return -1;
        }

        /** The squad this side's people on this posting join, or 0 before there is one. */
        public int watchFor(Faction side) {
            Integer squad = watches.get(side);
            return squad == null ? 0 : squad;
        }

        public void setWatchFor(Faction side, int squadId) {
            watches.put(side, squadId);
        }

        /** How long this posting has been short-handed. */
        public float shortHanded() {
            return shortHanded;
        }

        public void waited(float seconds) {
            shortHanded += seconds;
        }

        public void filled() {
            shortHanded = 0f;
        }
    }

    private final List<Posting> postings = new ArrayList<>();
    private final TraversalAxis axis;

    /**
     * @param axis which way this battle is fought along, which is what decides
     *     the edge each side's replacements walk on at
     */
    public WorksCrewService(TraversalAxis axis) {
        this.axis = axis;
    }

    public TraversalAxis axis() {
        return axis;
    }

    /** Open a posting and answer with it, so the manning pass can fill it. */
    public Posting post(int siteId, CrewRole role, UnitType type, Shift bill,
                        TacticalNode owner, int towardX, int towardY, int billets) {
        Posting posting = new Posting(siteId, role, type, bill, owner,
                towardX, towardY, billets);
        postings.add(posting);
        return posting;
    }

    /** Every watch on this map, in the order they were posted. */
    public List<Posting> postings() {
        return List.copyOf(postings);
    }

    public boolean isEmpty() {
        return postings.isEmpty();
    }
}

package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.CoreDressing;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph.Compartment;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.campaign.refit.RefitBand;
import com.dillon.starsectormarines.campaign.refit.RefitOrder;
import com.dillon.starsectormarines.campaign.refit.RefitTrack;
import java.util.function.Supplier;

/**
 * An authorized refit order played out on the deck, so the player sees the ship change rather than
 * reading that it did: the camera flies to the room the work is in, the curtain comes down while the
 * yard works (and the order is paid for, behind it), and it lifts on the changed room.
 *
 * <p>The purchase happens at the curtain, not at the click. A deck that gains a compartment is laid
 * out again, which takes a moment and resets what was drawn; under the curtain that is the yard's
 * days of work rather than a blank frame. A refusal at that moment (the funds moved, the ship left
 * the dock) lifts the curtain on the unchanged room and says why.
 *
 * <p>An order that only clears a core's Deficit look needs no curtain: the deck is not laid out
 * again, so it is paid as the camera arrives and the change plays across the running deck as a
 * dressing wave, which the reveal waits out before showing the result. The player watches the
 * before and the after happen in one shot.
 */
final class RefitReveal {
    enum Phase { FLY, CLOSE, WORK, OPEN, SHOW, RETURN, DONE }

    /** RETURN flies the camera back to where the player had it before the tree comes back. */
    static final float FLY = 1.2f, CLOSE = 0.5f, WORK = 1.0f, OPEN = 0.7f, SHOW = 3.5f, RETURN = 1.0f;

    /** How long a dressing wave takes to cross the deck, and the least the reveal holds on it. */
    static final float WAVE = 2.5f;

    final RefitOrder order;
    /** Whether this order plays as a dressing wave instead of behind the curtain. */
    final boolean wave;
    private final Supplier<String> commit;
    private Phase phase = Phase.FLY;
    private float time;
    private String refusal;
    private boolean committed;

    RefitReveal(RefitOrder order, Supplier<String> commit) {
        this.order = order;
        this.commit = commit;
        this.wave = wave(order);
    }

    /**
     * Whether an order changes only how the running deck is dressed: a dressed core brought up from
     * Deficit. Opening a reserve lays the deck out again, so it keeps the curtain.
     */
    static boolean wave(RefitOrder order) {
        return order.track.reserve == null && order.track.core != null
                && CoreDressing.DRESSED.contains(order.track.core) && order.band == RefitBand.RESTORED;
    }

    /** Whether the order has been put to the yard yet (paid, or refused). */
    boolean paid() { return committed; }

    Phase phase() { return phase; }

    /** Seconds into the current phase. */
    float time() { return time; }

    /** Why the yard refused at the curtain, or null. */
    String refusal() { return refusal; }

    /**
     * @param deckReady whether the deck under the curtain is laid out and drawable, so the curtain
     *                  never lifts on a ship still being laid out; for a wave, whether the wave has
     *                  finished crossing the deck
     */
    void advance(float dt, boolean deckReady) {
        time += dt;
        if (wave) {
            switch (phase) {
                case FLY -> { if (time >= FLY) { pay(); next(Phase.WORK); } }
                case WORK -> { if (refusal != null) next(Phase.RETURN); else if (time >= WAVE && deckReady) next(Phase.SHOW); }
                default -> { }
            }
            if (phase == Phase.WORK || phase == Phase.FLY) return;
        }
        switch (phase) {
            case FLY -> { if (time >= FLY) next(Phase.CLOSE); }
            case CLOSE -> { if (time >= CLOSE) { pay(); next(Phase.WORK); } }
            case WORK -> { if (time >= WORK && deckReady) next(Phase.OPEN); }
            case OPEN -> { if (time >= OPEN) next(refusal == null ? Phase.SHOW : Phase.RETURN); }
            case SHOW -> { if (time >= SHOW) next(Phase.RETURN); }
            case RETURN -> { if (time >= RETURN) next(Phase.DONE); }
            case DONE -> { }
        }
    }

    /** A click or Escape: hurry to the result, never past paying for it. */
    void skip() {
        if (wave) {
            switch (phase) {
                case FLY -> { pay(); next(refusal == null ? Phase.WORK : Phase.RETURN); }
                case WORK -> next(Phase.SHOW);
                case SHOW -> next(Phase.RETURN);
                case RETURN -> next(Phase.DONE);
                default -> { }
            }
            return;
        }
        switch (phase) {
            case FLY, CLOSE -> { pay(); next(Phase.WORK); time = WORK; }
            case SHOW -> next(Phase.RETURN);
            case RETURN -> next(Phase.DONE);
            default -> { }
        }
    }

    /** How much of the stage the curtain covers, 0 to 1. */
    float curtain() {
        if (wave) return 0f;
        return switch (phase) {
            case CLOSE -> Math.min(1f, time / CLOSE);
            case WORK -> 1f;
            case OPEN -> Math.max(0f, 1f - time / OPEN);
            default -> 0f;
        };
    }

    /** The result caption's opacity: in with the curtain lifting, held, out at the end. */
    float caption() {
        return switch (phase) {
            case OPEN -> refusal == null ? Math.min(1f, time / OPEN) : 0f;
            case SHOW -> Math.min(1f, Math.min((SHOW - time) / 0.4f, wave ? time / 0.4f : 1f));
            default -> 0f;
        };
    }

    private void pay() {
        if (committed) return;
        committed = true;
        refusal = commit.get();
    }

    private void next(Phase phase) { this.phase = phase; time = 0f; }

    /**
     * The room a track's work is seen in, or null when it has none of its own. Looked up on whichever deck is drawn, because opening
     * a reserve lays the deck out again.
     */
    static Compartment room(RefitTrack track, DeckGraph graph) {
        if (track.reserve != null) {
            for (Compartment room : graph.compartments())
                if (graph.reserve(room.id()) == track.reserve) return room;
            return null;
        }
        RoomPurpose purpose = switch (track) {
            case POWER -> RoomPurpose.ENGINE_ROOM;
            case LIFE_SUPPORT -> RoomPurpose.BARRACKS;
            case COMPUTE -> RoomPurpose.BRIDGE;
            case FABRICATION -> RoomPurpose.PARTS_CAGE;
            case MESS -> RoomPurpose.MESS_HALL;
            case RESERVE_HANGAR -> RoomPurpose.HANGAR;
            default -> null;
        };
        return purpose == null ? null : graph.largest(purpose);
    }
}

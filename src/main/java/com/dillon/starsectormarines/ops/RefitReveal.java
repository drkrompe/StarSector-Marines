package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph.Compartment;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
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
 */
final class RefitReveal {
    enum Phase { FLY, CLOSE, WORK, OPEN, SHOW, RETURN, DONE }

    /** RETURN flies the camera back to where the player had it before the tree comes back. */
    static final float FLY = 1.2f, CLOSE = 0.5f, WORK = 1.0f, OPEN = 0.7f, SHOW = 3.5f, RETURN = 1.0f;

    final RefitOrder order;
    private final Supplier<String> commit;
    private Phase phase = Phase.FLY;
    private float time;
    private String refusal;
    private boolean committed;

    RefitReveal(RefitOrder order, Supplier<String> commit) {
        this.order = order;
        this.commit = commit;
    }

    Phase phase() { return phase; }

    /** Seconds into the current phase. */
    float time() { return time; }

    /** Why the yard refused at the curtain, or null. */
    String refusal() { return refusal; }

    /**
     * @param deckReady whether the deck under the curtain is laid out and drawable; the curtain
     *                  never lifts on a ship still being laid out
     */
    void advance(float dt, boolean deckReady) {
        time += dt;
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
        switch (phase) {
            case FLY, CLOSE -> { pay(); next(Phase.WORK); time = WORK; }
            case SHOW -> next(Phase.RETURN);
            case RETURN -> next(Phase.DONE);
            default -> { }
        }
    }

    /** How much of the stage the curtain covers, 0 to 1. */
    float curtain() {
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
            case SHOW -> Math.min(1f, (SHOW - time) / 0.4f);
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
     * The room a track's work is seen in, or null when it is the whole ship (her plating, grid,
     * sensors and drive are not a compartment). Looked up on whichever deck is drawn, because opening
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
            default -> null;
        };
        return purpose == null ? null : graph.largest(purpose);
    }
}

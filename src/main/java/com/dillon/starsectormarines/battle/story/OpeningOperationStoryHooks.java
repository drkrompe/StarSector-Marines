package com.dillon.starsectormarines.battle.story;

import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.dillon.starsectormarines.battle.story.StoryBeatLog.Audience;

/** Pure one-shot trigger policy over completed-tick observations. Keys are content contracts. */
public final class OpeningOperationStoryHooks {
    public enum Result { RUNNING, VICTORY, DEFEAT, RETREAT }
    public record Facts(int tick, boolean playerLanded, boolean employerLanded,
                        boolean firstPlaceReached, boolean yardReached, boolean warehouseReached,
                        int initialMilitia, int survivingMilitia, int roadArrivals,
                        boolean reserveCommitted, boolean defendersFallingBack, Result result) {}
    private final OpeningOperationKind kind;
    private final StoryBeatLog log;
    private final String prefix;
    private boolean finished;

    public OpeningOperationStoryHooks(OpeningOperationKind kind, StoryBeatLog log) {
        this.kind = kind;
        this.log = log;
        // Scenario keys also work for recurring jobs; they never imply Wayline chapter completion.
        prefix = kind == OpeningOperationKind.RELIEF ? "opening.relief." : "opening.counterattack.";
    }

    public void observe(Facts f) {
        if (finished) return;
        emit(f.tick, "started", Audience.PLAYER);
        // Retreat can happen before the first simulation tick. Do not fabricate landings.
        if (f.result == Result.RETREAT) { finish(f.tick, f.result); return; }
        if (f.playerLanded) emit(f.tick, "player_landed", Audience.PLAYER);
        if (f.employerLanded) emit(f.tick, "employer_landed", Audience.PLAYER);
        if (kind == OpeningOperationKind.RELIEF) {
            if (f.playerLanded && f.firstPlaceReached) emit(f.tick, "checkpoint_reached", Audience.PLAYER);
            if (f.initialMilitia > 0) {
                if (f.survivingMilitia == 0) emit(f.tick, "militia_line_lost", Audience.PLAYER);
                else if (f.survivingMilitia * 2 <= f.initialMilitia)
                    emit(f.tick, "militia_line_weakened", Audience.PLAYER);
            }
            // These are actual releases, not forecasts, and may be outside player vision.
            if (f.roadArrivals >= 1) emit(f.tick, "freight_wave_arrived", Audience.OBSERVER_ONLY);
            if (f.roadArrivals >= 2) emit(f.tick, "service_wave_arrived", Audience.OBSERVER_ONLY);
        } else {
            if (f.playerLanded && f.firstPlaceReached) emit(f.tick, "gate_reached", Audience.PLAYER);
            if (f.playerLanded && f.yardReached) emit(f.tick, "yard_reached", Audience.PLAYER);
            if (f.playerLanded && f.warehouseReached) emit(f.tick, "warehouse_reached", Audience.PLAYER);
            if (f.reserveCommitted) emit(f.tick, "reserve_committed", Audience.OBSERVER_ONLY);
            if (f.defendersFallingBack) emit(f.tick, "defenders_falling_back", Audience.OBSERVER_ONLY);
        }
        if (f.result != Result.RUNNING) finish(f.tick, f.result);
    }

    public void finish(int tick, Result result) {
        if (finished || result == Result.RUNNING) return;
        emit(tick, "started", Audience.PLAYER);
        emit(tick, switch (result) {
            case VICTORY -> "victory";
            case DEFEAT -> "defeat";
            case RETREAT -> "retreat";
            default -> throw new IllegalArgumentException("Not terminal");
        }, Audience.PLAYER);
        finished = true;
    }

    private void emit(int tick, String suffix, Audience audience) {
        log.emitOnce(tick, prefix + suffix, audience);
    }
}

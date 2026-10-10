package com.dillon.starsectormarines.battle.story;

import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import com.dillon.starsectormarines.battle.story.StoryBeatLog.Audience;
import java.util.List;

/** Stable content keys for confirmed Junction facts; no orders or simulation callbacks. */
public final class JunctionStoryHooks {
    public enum Result { RUNNING, VICTORY, DEFEAT, RETREAT }
    public record Facts(int tick, boolean playerLanded, boolean employerLanded,
                        boolean militiaHolding, boolean reserveCommitted,
                        List<CompoundState> states, Result result) {
        public Facts { states = List.copyOf(states); }
    }
    private static final List<String> SITES = List.of("turnout", "weigh_station", "freight_yard", "dispatch");
    private final StoryBeatLog log;
    private final boolean[] held = new boolean[4];
    private boolean finished;
    public JunctionStoryHooks(StoryBeatLog log) { this.log = log; held[0] = true; }
    public void observe(Facts f) {
        if (finished) return;
        if (f.states.size() != 4) throw new IllegalArgumentException("Junction needs four territory facts");
        emit(f.tick, "started", Audience.PLAYER);
        if (f.result == Result.RETREAT) { finish(f.tick, f.result); return; }
        if (f.playerLanded) emit(f.tick, "player_landed", Audience.PLAYER);
        if (f.employerLanded) emit(f.tick, "employer_landed", Audience.PLAYER);
        if (f.militiaHolding) emit(f.tick, "militia_hold_established", Audience.PLAYER);
        if (f.reserveCommitted) emit(f.tick, "reserve_committed", Audience.OBSERVER_ONLY);
        for (int i = 0; i < held.length; i++) {
            if (f.states.get(i) == CompoundState.MARINE_HELD) {
                if (i > 0) emit(f.tick, SITES.get(i) + "_secured", Audience.PLAYER);
                held[i] = true;
            } else if (held[i] && f.states.get(i) == CompoundState.DEFENDER_HELD) {
                emit(f.tick, SITES.get(i) + "_retaken", Audience.PLAYER);
            }
        }
        finish(f.tick, f.result);
    }
    public void finish(int tick, Result result) {
        if (finished || result == Result.RUNNING) return;
        emit(tick, "started", Audience.PLAYER);
        emit(tick, result == Result.VICTORY ? "victory" : result == Result.RETREAT ? "retreat" : "defeat", Audience.PLAYER);
        finished = true;
    }
    private void emit(int tick, String suffix, Audience audience) { log.emitOnce(tick, "supply_route.junction." + suffix, audience); }
}

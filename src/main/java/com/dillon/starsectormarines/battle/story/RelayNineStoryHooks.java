package com.dillon.starsectormarines.battle.story;

import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import com.dillon.starsectormarines.battle.story.StoryBeatLog.Audience;
import java.util.List;

/** Stable content keys for confirmed RelayNine facts; no orders or simulation callbacks. */
public final class RelayNineStoryHooks {
    public enum Result { RUNNING, VICTORY, DEFEAT, RETREAT }
    public record Facts(int tick, boolean playerLanded, boolean employerLanded,
                        List<Integer> militiaHolds, boolean reserveArrived,
                        List<CompoundState> states, Result result) {
        public Facts { states = List.copyOf(states); militiaHolds = List.copyOf(militiaHolds); }
    }
    private static final List<String> SITES = List.of("apron", "power_yard", "freight_gate", "relay_hall");
    private final StoryBeatLog log;
    private final boolean[] held = new boolean[4];
    private final boolean[] retaken = new boolean[4];
    private boolean finished;
    public RelayNineStoryHooks(StoryBeatLog log) { this.log = log; held[0] = true; }
    public void observe(Facts f) {
        if (finished) return;
        if (f.states.size() != 4) throw new IllegalArgumentException("RelayNine needs four territory facts");
        emit(f.tick, "started", Audience.PLAYER);
        if (f.result == Result.RETREAT) { finish(f.tick, f.result); return; }
        if (f.playerLanded) emit(f.tick, "player_landed", Audience.PLAYER);
        if (f.employerLanded) emit(f.tick, "employer_landed", Audience.PLAYER);
        for (int site : f.militiaHolds) if (site > 0 && site < 4 && f.states.get(site) == CompoundState.MARINE_HELD)
            emit(f.tick, SITES.get(site) + "_militia_holding", Audience.PLAYER);
        if (f.reserveArrived) emit(f.tick, "road_reserve_arrived", Audience.OBSERVER_ONLY);
        for (int i = 0; i < held.length; i++) {
            if (f.states.get(i) == CompoundState.MARINE_HELD) {
                if (i > 0) emit(f.tick, SITES.get(i) + "_secured", Audience.PLAYER);
                if (retaken[i]) emit(f.tick, SITES.get(i) + "_restored", Audience.PLAYER);
                held[i] = true;
            } else if (held[i] && f.states.get(i) == CompoundState.DEFENDER_HELD) {
                emit(f.tick, SITES.get(i) + "_retaken", Audience.PLAYER);
                retaken[i] = true;
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
    private void emit(int tick, String suffix, Audience audience) { log.emitOnce(tick, "supply_route.relay_nine." + suffix, audience); }
}

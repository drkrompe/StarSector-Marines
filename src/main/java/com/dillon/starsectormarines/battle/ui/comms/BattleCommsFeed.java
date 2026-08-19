package com.dillon.starsectormarines.battle.ui.comms;

/**
 * Small player-facing battle dispatch surface. Producers publish a concise
 * officer read; the HUD presents the newest dispatch for its real-time
 * lifetime. New dispatches replace stale ones immediately so an outcome can
 * never sit behind an obsolete warning.
 */
public final class BattleCommsFeed {

    public enum Tone { WARNING, DANGER, GOOD_NEWS, STATUS }

    public record Notice(String heading, String body, Tone tone, float durationSec) {
        public Notice {
            if (heading == null || heading.isBlank()) throw new IllegalArgumentException("heading");
            if (body == null || body.isBlank()) throw new IllegalArgumentException("body");
            if (tone == null) throw new IllegalArgumentException("tone");
            if (!(durationSec > 0f)) throw new IllegalArgumentException("durationSec");
        }
    }

    private Notice active;
    private float remainingSec;

    public void post(Notice notice) {
        if (notice == null) throw new IllegalArgumentException("notice");
        active = notice;
        remainingSec = notice.durationSec();
    }

    public void update(float dt) {
        if (active == null || dt <= 0f) return;
        remainingSec -= dt;
        if (remainingSec <= 0f) {
            active = null;
            remainingSec = 0f;
        }
    }

    public Notice activeNotice() {
        return active;
    }

    public float remainingSec() {
        return remainingSec;
    }
}

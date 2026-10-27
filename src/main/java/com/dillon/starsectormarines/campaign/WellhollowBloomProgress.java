package com.dillon.starsectormarines.campaign;

import java.io.Serializable;

/**
 * Save-owned ledger for *The Wellhollow Bloom*, the authored Rav Bloom arc on Asharu
 * ({@code wellhollow-bloom-arc.md}). Its five chapters unlock in order, each on the previous
 * chapter's victory; a chapter already won stays offered as a replay that pays half its fee and
 * never moves the arc. Every attempt carries a fresh identity; only the active attempt settles,
 * and only once.
 */
public final class WellhollowBloomProgress implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String PREFIX = "wellhollow_bloom_v1_";
    private static final String REPLAY = "replay_";

    /** The arc's chapters in their prescribed order, each one shipped Rav mission. */
    public enum Chapter {
        COLONY_BREACH("breach", 20_000),
        CORDON_LINE("line", 26_000),
        CORDON_ROAD("road", 28_000),
        THE_BURN("burn", 32_000),
        BROAD_FRONT("front", 40_000);

        public final String code;
        public final int fee;
        Chapter(String code, int fee) { this.code = code; this.fee = fee; }
        public int replayFee() { return fee / 2; }
        /** One-based place in the arc. */
        public int number() { return ordinal() + 1; }
    }

    private int won;
    private int attempt = 1;
    private String activeId;
    private int replaysWon, losses, paid;
    /** The story chapter whose last attempt was lost, so its retry briefs as one; null otherwise. */
    private Chapter lastLost;

    public static boolean owns(String id) { return id != null && id.startsWith(PREFIX); }
    public static boolean replay(String id) { return owns(id) && id.startsWith(PREFIX + REPLAY); }
    /** The chapter an identity was issued for, or null for any other identity. */
    public static Chapter chapterOf(String id) {
        if (!owns(id)) return null;
        String rest = id.substring(PREFIX.length() + (replay(id) ? REPLAY.length() : 0));
        for (Chapter chapter : Chapter.values()) if (rest.startsWith(chapter.code + "_")) return chapter;
        return null;
    }

    /** Chapters won in order; also the index of the chapter now on offer. */
    public int won() { return won; }
    public int attempt() { return attempt; }
    public int replaysWon() { return replaysWon; }
    public int losses() { return losses; }
    public int paid() { return paid; }
    public boolean lastLost(Chapter chapter) { return chapter != null && chapter == lastLost; }
    public boolean active() { return activeId != null; }
    public boolean complete() { return won >= Chapter.values().length; }
    /** The chapter the story offers next, or null once the arc is complete. */
    public Chapter current() { return complete() ? null : Chapter.values()[won]; }
    public boolean replayable(Chapter chapter) { return chapter != null && chapter.ordinal() < won; }

    public String missionId(Chapter chapter, boolean replay) {
        return PREFIX + (replay ? REPLAY : "") + chapter.code + "_" + attempt;
    }
    public static int fee(String id) {
        Chapter chapter = chapterOf(id);
        return chapter == null ? -1 : replay(id) ? chapter.replayFee() : chapter.fee;
    }
    /** Whether {@code id} is an attempt this ledger offers now: the current chapter, or a replay of one won. */
    public boolean matches(String id) {
        Chapter chapter = chapterOf(id);
        if (chapter == null) return false;
        boolean replay = replay(id);
        return id.equals(missionId(chapter, replay)) && (replay ? replayable(chapter) : chapter == current());
    }

    public boolean begin(String id) {
        if (!matches(id) || activeId != null) return false;
        activeId = id;
        return true;
    }
    /** Closes an unsettled launch as if it never happened: the same attempt can launch again. */
    public boolean abandon(String id) {
        if (id == null || !id.equals(activeId)) return false;
        activeId = null;
        return true;
    }

    /**
     * Accepts only the active attempt's receipt. Returns the fee (zero on a defeat), or -1 when
     * the outcome is stale, duplicate, foreign or pays the wrong amount, writing nothing.
     */
    public int settle(String id, boolean victory, int payment) {
        if (id == null || !id.equals(activeId) || !matches(id) || payment != (victory ? fee(id) : 0)) return -1;
        boolean replay = replay(id);
        if (!victory) {
            losses++;
            if (!replay) lastLost = chapterOf(id);
        } else if (replay) replaysWon++;
        else {
            won++;
            lastLost = null;
        }
        paid += payment;
        attempt++;
        activeId = null;
        return payment;
    }
}

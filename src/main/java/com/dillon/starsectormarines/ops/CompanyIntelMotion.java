package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.CompanyHqViewModel.NewsPost;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** View-local instrument timing. Carrier animation never manufactures received reports. */
final class CompanyIntelMotion {
    static final int COMPANY = 0;
    static final int CONFIRMED = 1;
    static final int RUMOR = 2;
    private static final double CLOCK_CYCLE = 1440d;
    private static final float REVEAL_SECONDS = 0.85f;
    private static final float ARRIVAL_SECONDS = 3.2f;

    private final Set<String> observedIds = new HashSet<>();
    private final int[] counts = new int[3];
    private final float[] arrivalAges = {ARRIVAL_SECONDS, ARRIVAL_SECONDS, ARRIVAL_SECONDS};
    private boolean initialized;
    private String selectedId;
    private int selectedChannel = -1;
    private float revealAge = REVEAL_SECONDS;
    private double clock;

    void observe(List<NewsPost> posts, NewsPost selected) {
        counts[0] = counts[1] = counts[2] = 0;
        for (NewsPost post : posts) {
            int channel = channel(post);
            counts[channel]++;
            if (observedIds.add(post.id()) && initialized) arrivalAges[channel] = 0f;
        }
        initialized = true;
        String nextId = selected == null ? null : selected.id();
        if (!Objects.equals(selectedId, nextId)) {
            selectedId = nextId;
            revealAge = 0f;
        }
        selectedChannel = selected == null ? -1 : channel(selected);
    }

    void advance(float seconds) {
        if (!Float.isFinite(seconds) || seconds <= 0f) return;
        clock = (clock + seconds) % CLOCK_CYCLE;
        revealAge = Math.min(REVEAL_SECONDS, revealAge + seconds);
        for (int i = 0; i < arrivalAges.length; i++) {
            arrivalAges[i] = Math.min(ARRIVAL_SECONDS, arrivalAges[i] + seconds);
        }
    }

    float phase(float period, float offset) {
        if (!Float.isFinite(period) || period <= 0f || !Float.isFinite(offset)) {
            throw new IllegalArgumentException("finite period and offset required");
        }
        double cycles = clock / period + offset;
        return (float) (cycles - Math.floor(cycles));
    }

    float selectionReveal() {
        float value = revealAge / REVEAL_SECONDS;
        return value * value * (3f - 2f * value);
    }

    float arrivalStrength(int channel) {
        float remaining = 1f - arrivalAges[channel] / ARRIVAL_SECONDS;
        return remaining * remaining;
    }

    int count(int channel) { return counts[channel]; }
    int selectedChannel() { return selectedChannel; }
    boolean hasSelection() { return selectedId != null; }
    float timeSeconds() { return (float) clock; }

    static int channel(NewsPost post) {
        return post.companyReport() ? COMPANY : post.rumor() ? RUMOR : CONFIRMED;
    }
}

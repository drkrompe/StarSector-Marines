package com.dillon.starsectormarines.battle.salvage;

/** One finite find. Only local, uncontested infantry presence advances recovery. */
public final class SalvageCache {
    public static final float SECURE_SECONDS = 5f;
    public record Recovered(String id, SalvageResource resource, int quantity) {
        public Recovered {
            if (id == null || id.isBlank() || resource == null || quantity <= 0)
                throw new IllegalArgumentException("Invalid salvage find");
        }
    }

    public final String id;
    public final int cellX, cellY;
    public final SalvageResource resource;
    public final int quantity;
    private boolean discovered, secured, contested;
    private float progress;

    public SalvageCache(String id, int cellX, int cellY, SalvageResource resource, int quantity) {
        new Recovered(id, resource, quantity);
        this.id = id; this.cellX = cellX; this.cellY = cellY;
        this.resource = resource; this.quantity = quantity;
    }

    public boolean discovered() { return discovered; }
    public boolean secured() { return secured; }
    public boolean contested() { return contested; }
    public float fraction() { return Math.min(1f, progress / SECURE_SECONDS); }
    public Recovered recovered() { return new Recovered(id, resource, quantity); }

    public void advance(float seconds, boolean visible, boolean infantryPresent, boolean hostilePresent) {
        if (secured) return;
        discovered |= visible;
        contested = discovered && visible && hostilePresent;
        if (!visible || !infantryPresent || hostilePresent) { progress = 0; return; }
        progress += Math.max(0, seconds);
        if (progress + .0001f >= SECURE_SECONDS) { secured = true; contested = false; }
    }
}

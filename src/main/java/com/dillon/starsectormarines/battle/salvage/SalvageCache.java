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
    /** The room a find inside it is held from (left, bottom, right, top), or null for a find in the open. */
    private int[] room;

    public SalvageCache(String id, int cellX, int cellY, SalvageResource resource, int quantity) {
        new Recovered(id, resource, quantity);
        this.id = id; this.cellX = cellX; this.cellY = cellY;
        this.resource = resource; this.quantity = quantity;
    }

    /**
     * Makes this a find inside a room ({@code ruins-works.md}, sealed stores): infantry anywhere in the room, or within
     * {@code margin} cells of its walls, are present at it and see it. A squad told to a small room holds it from its
     * places, which need not be inside, so a find in the room is recovered by holding the room.
     */
    public SalvageCache heldFromRoom(int left, int bottom, int right, int top, int margin) {
        room = new int[]{left - margin, bottom - margin, right + 1 + margin, top + 1 + margin};
        return this;
    }

    /** Whether this find stands in a room it is held from. */
    public boolean inRoom() { return room != null; }

    /** Whether a body at {@code (x, y)} holds this find's room; false for a find in the open. */
    public boolean holds(float x, float y) {
        return room != null && x >= room[0] && x < room[2] && y >= room[1] && y < room[3];
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

package com.dillon.starsectormarines.battle.salvage;

import com.dillon.starsectormarines.i18n.Strings;

/** One finite find. Only local, uncontested infantry presence advances recovery. */
public final class SalvageCache {
    public static final float SECURE_SECONDS = 5f;

    /**
     * A find brought home.
     *
     * @param tech whether it is a ruins tech cache ({@code ruins-expedition.md}, The vault and its prize): settled as a
     *             roll of the game's own equipment, never as its resource's commodity
     */
    public record Recovered(String id, SalvageResource resource, int quantity, boolean tech) {
        public Recovered {
            if (id == null || id.isBlank() || resource == null || quantity <= 0)
                throw new IllegalArgumentException("Invalid salvage find");
        }

        /** An ordinary find of field cargo. */
        public Recovered(String id, SalvageResource resource, int quantity) {
            this(id, resource, quantity, false);
        }

        /** What the find is called where it is listed: a tech cache, or its quantity of its resource. */
        public String label() { return SalvageCache.label(resource, quantity, tech); }
    }

    /** A find's listed name: a tech cache's own, or {@code "<quantity> <resource>"}. */
    static String label(SalvageResource resource, int quantity, boolean tech) {
        return tech ? Strings.get("fieldFindTechCache") : quantity + " " + resource.displayName;
    }

    public final String id;
    public final int cellX, cellY;
    public final SalvageResource resource;
    public final int quantity;
    private boolean discovered, secured, contested;
    private float progress;
    /** The room a find inside it is held from (left, bottom, right, top), or null for a find in the open. */
    private int[] room;
    private boolean tech;

    public SalvageCache(String id, int cellX, int cellY, SalvageResource resource, int quantity) {
        new Recovered(id, resource, quantity);
        this.id = id; this.cellX = cellX; this.cellY = cellY;
        this.resource = resource; this.quantity = quantity;
    }

    /**
     * Makes this a find inside a room ({@code ruins-expedition.md}, Works): infantry anywhere in the room, or within
     * {@code margin} cells of its walls, are present at it and see it. A squad told to a small room holds it from its
     * places, which need not be inside, so a find in the room is recovered by holding the room.
     */
    public SalvageCache heldFromRoom(int left, int bottom, int right, int top, int margin) {
        room = new int[]{left - margin, bottom - margin, right + 1 + margin, top + 1 + margin};
        return this;
    }

    /**
     * Makes this a ruins tech cache ({@code RuinsFindTier.TECH}): a find in a place the works or a guardian keep,
     * which holds the game's own equipment and settles as a roll of it rather than as its resource.
     */
    public SalvageCache asTechCache() {
        tech = true;
        return this;
    }

    /** Whether this is a ruins tech cache. */
    public boolean tech() { return tech; }

    /** What the find is called where it is listed. */
    public String label() { return label(resource, quantity, tech); }

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
    public Recovered recovered() { return new Recovered(id, resource, quantity, tech); }

    public void advance(float seconds, boolean visible, boolean infantryPresent, boolean hostilePresent) {
        if (secured) return;
        discovered |= visible;
        contested = discovered && visible && hostilePresent;
        if (!visible || !infantryPresent || hostilePresent) { progress = 0; return; }
        progress += Math.max(0, seconds);
        if (progress + .0001f >= SECURE_SECONDS) { secured = true; contested = false; }
    }
}

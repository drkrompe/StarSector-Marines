package com.dillon.starsectormarines.battle.salvage;

import com.dillon.starsectormarines.i18n.Strings;

/** One finite find. Only local, uncontested infantry presence advances recovery. */
public final class SalvageCache {
    public static final float SECURE_SECONDS = 5f;

    /**
     * A find brought home.
     *
     * @param tech      whether it is a ruins tech cache ({@code ruins-expedition.md}, The vault and its prize): settled as a
     *                  roll of the game's own equipment, never as its resource's commodity
     * @param commodity the commodity it settles as: its resource's, or a plant store's own goods
     *                  ({@code ruins-expedition.md}, Finds lie where the plant kept its goods)
     * @param goodsName what the goods are called where the find is listed
     */
    public record Recovered(String id, SalvageResource resource, int quantity, boolean tech, String commodity,
                            String goodsName) {
        public Recovered {
            if (id == null || id.isBlank() || resource == null || quantity <= 0)
                throw new IllegalArgumentException("Invalid salvage find");
            if (commodity == null) commodity = resource.commodityId;
            if (goodsName == null) goodsName = resource.displayName;
        }

        /** The commodity it settles as; its resource's for a find saved before finds carried goods. */
        @Override public String commodity() { return commodity != null ? commodity : resource.commodityId; }

        /** What its goods are called; its resource's for a find saved before finds carried goods. */
        @Override public String goodsName() { return goodsName != null ? goodsName : resource.displayName; }

        /** A find of its resource's own commodity. */
        public Recovered(String id, SalvageResource resource, int quantity, boolean tech) {
            this(id, resource, quantity, tech, null, null);
        }

        /** An ordinary find of field cargo. */
        public Recovered(String id, SalvageResource resource, int quantity) {
            this(id, resource, quantity, false);
        }

        /** What the find is called where it is listed: a tech cache, or its quantity of its goods. */
        public String label() { return SalvageCache.label(goodsName(), quantity, tech); }
    }

    /** A find's listed name: a tech cache's own, or {@code "<quantity> <goods>"}. */
    static String label(String goodsName, int quantity, boolean tech) {
        return tech ? Strings.get("fieldFindTechCache") : quantity + " " + goodsName;
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
    private String goods;
    private String goodsName;

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

    /**
     * Makes this a load of a plant store's own goods ({@code ruins-expedition.md}, Finds lie where the plant kept its
     * goods): it settles as {@code commodity}, listed as {@code name}, and stands on its own load's art rather than a
     * field-salvage bundle.
     */
    public SalvageCache ofGoods(String commodity, String name) {
        if (commodity == null || commodity.isBlank() || name == null || name.isBlank())
            throw new IllegalArgumentException("A store's goods are named");
        goods = commodity;
        goodsName = name;
        return this;
    }

    /** The plant store's commodity this find is, or null for a field-salvage bundle of its resource. */
    public String goods() { return goods; }

    /** Whether this is a ruins tech cache. */
    public boolean tech() { return tech; }

    /** What the find is called where it is listed. */
    public String label() { return label(goodsName != null ? goodsName : resource.displayName, quantity, tech); }

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
    public Recovered recovered() { return new Recovered(id, resource, quantity, tech, goods, goodsName); }

    public void advance(float seconds, boolean visible, boolean infantryPresent, boolean hostilePresent) {
        if (secured) return;
        discovered |= visible;
        contested = discovered && visible && hostilePresent;
        if (!visible || !infantryPresent || hostilePresent) { progress = 0; return; }
        progress += Math.max(0, seconds);
        if (progress + .0001f >= SECURE_SECONDS) { secured = true; contested = false; }
    }
}

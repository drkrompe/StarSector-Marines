package com.dillon.starsectormarines.battle.derelict;

/**
 * Metal on the claim a stripper may cut (`fallen-hull-nouns.md`, Stripper): a structure the ground laid, or a wreck,
 * the watch's own or the company's and the garrison's. Cut down to nothing it is gone; a wreck of the watch's cut down
 * is never raised. A wreck the company scrapped is denied (`derelict-watch.md`, Denial): it is no salvage.
 */
public final class Salvage {

    public enum Kind { STRUCTURE, WRECK }

    /** The metal in a walker's wreck, the watch's or anyone's. UNTUNED. */
    public static final float WRECK_METAL = 120f;
    /** The metal in a lighter wreck: a vehicle, an emplacement. UNTUNED. */
    public static final float LIGHT_WRECK_METAL = 60f;

    public final long id;
    public final Kind kind;
    public final float x;
    public final float y;
    /** The watch's wreck this is, or null. */
    public final Wreck wreck;

    private float metal;

    public Salvage(long id, Kind kind, float x, float y, float metal, Wreck wreck) {
        if (metal < 0f) throw new IllegalArgumentException("Salvage holds no less than nothing");
        this.id = id;
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.metal = metal;
        this.wreck = wreck;
    }

    public float metal() {
        return metal;
    }

    /** Whether a stripper may still cut it: metal left, and not a wreck raised or scrapped by the company. */
    public boolean cuttable() {
        if (metal <= 0f) return false;
        return wreck == null || wreck.raisable();
    }

    /**
     * A stripper cuts up to {@code wanted} from it; a wreck of the watch's cut to nothing is stripped and never raised.
     *
     * @return the metal cut
     */
    public float cut(float wanted) {
        if (!cuttable()) return 0f;
        float taken = Math.max(0f, Math.min(metal, wanted));
        metal -= taken;
        if (metal <= 0f && wreck != null) wreck.strip();
        return taken;
    }
}

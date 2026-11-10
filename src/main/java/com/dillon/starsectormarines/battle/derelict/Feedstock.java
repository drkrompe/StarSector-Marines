package com.dillon.starsectormarines.battle.derelict;

import java.util.List;

/**
 * A store of feedstock (`fallen-hull-nouns.md`, Vocabulary): metal, the autoforge's one input, held at the hull and at
 * each established stake. Haulers put metal in; the forge draws it out. Never people.
 *
 * <p>A store's metal reaches the forge along the relay chain, abstracted: the forge draws from the hull's store and
 * every linked established stake's as one pool ({@link #draw}).
 */
public final class Feedstock {

    private float metal;

    public Feedstock() {
        this(0f);
    }

    public Feedstock(float metal) {
        if (metal < 0f) throw new IllegalArgumentException("A store never holds less than nothing");
        this.metal = metal;
    }

    public float metal() {
        return metal;
    }

    /** A hauler unloads {@code amount} here. */
    public void deposit(float amount) {
        if (amount > 0f) metal += amount;
    }

    /** Takes up to {@code wanted} out; returns what it gave. */
    public float take(float wanted) {
        float given = Math.max(0f, Math.min(metal, wanted));
        metal -= given;
        return given;
    }

    /** The metal held across {@code stores}. */
    public static float total(List<Feedstock> stores) {
        float sum = 0f;
        for (Feedstock store : stores) sum += store.metal;
        return sum;
    }

    /**
     * Draws {@code cost} from {@code stores}, in order (the hull's first), only when they hold it all between them.
     *
     * @return whether the cost was drawn; on false nothing is taken
     */
    public static boolean draw(List<Feedstock> stores, float cost) {
        if (total(stores) + 1e-4f < cost) return false;
        float left = cost;
        for (Feedstock store : stores) {
            if (left <= 0f) break;
            left -= store.take(left);
        }
        return true;
    }
}

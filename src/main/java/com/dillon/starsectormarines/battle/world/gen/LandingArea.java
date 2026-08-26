package com.dillon.starsectormarines.battle.world.gen;

import java.util.List;
import java.util.Objects;

/**
 * One authored arrival area containing the two distinct shuttle berths that
 * may deliver a paired squad lift. The area is immutable map geometry: battle
 * setup may choose whether to use it, but may not reinterpret its footprint or
 * approach side.
 */
public final class LandingArea {

    public static final int BERTH_COUNT = 2;

    /** Stable within a generated map and derived from authored coordinates. */
    public final String id;
    /** Inclusive bounds covering both berths and the open ground between them. */
    public final int left, bottom, right, top;
    /** Shared direction from the area toward open approach airspace. */
    public final LandingPad.Approach approach;

    private final List<LandingPad> berths;

    public LandingArea(String id, LandingPad first, LandingPad second) {
        this.id = Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("landing-area id must not be blank");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first.centerX == second.centerX && first.centerY == second.centerY) {
            throw new IllegalArgumentException("landing-area berths must have distinct centers");
        }
        if (first.approach != second.approach) {
            throw new IllegalArgumentException("landing-area berths must share one approach");
        }
        this.approach = first.approach;
        this.left = Math.min(first.left(), second.left());
        this.bottom = Math.min(first.bottom(), second.bottom());
        this.right = Math.max(first.right(), second.right());
        this.top = Math.max(first.top(), second.top());
        this.berths = List.of(first, second);
    }

    /** Exactly two berths in deterministic approach-frontage order. */
    public List<LandingPad> berths() {
        return berths;
    }

    public LandingPad berth(int index) {
        return berths.get(index);
    }

    public boolean contains(int x, int y) {
        return x >= left && x <= right && y >= bottom && y <= top;
    }
}

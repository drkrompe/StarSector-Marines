package com.dillon.starsectormarines.battle.world.gen.layout;

/**
 * Ground reserved by name (inclusive world cells), for a {@link SitePurpose}: an
 * open site is kept clear of landscape so a later set piece, or a battle event,
 * has it to stand on; a ground site is held for authored terrain. A site stated
 * in the layout itself (not resolved by a {@link Deferred}) is known before the
 * places grow, and nothing grows on it.
 */
public record Site(String name, int x0, int y0, int x1, int y1, SitePurpose purpose, boolean required)
        implements Piece {

    public Site {
        if (purpose == null) purpose = SitePurpose.OPEN;
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("a site needs a name");
        if (x1 < x0 || y1 < y0) throw new IllegalArgumentException("an empty site: " + name);
    }
}

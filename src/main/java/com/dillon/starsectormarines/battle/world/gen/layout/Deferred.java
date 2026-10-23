package com.dillon.starsectormarines.battle.world.gen.layout;

import java.util.List;
import java.util.function.Function;

/**
 * Pieces a mission can only state once the ground exists — a line from one
 * place's claimed edge to the next's, say. Resolved at its {@link Phase} from a
 * read-only {@link LayoutGround}; what it returns is laid and reported under
 * the returned pieces' own names.
 */
public record Deferred(String name, Phase phase, Function<LayoutGround, List<Piece>> resolve, boolean required)
        implements Piece {

    public Deferred {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("a deferred piece needs a name");
        if (phase == null || resolve == null) throw new IllegalArgumentException("a deferred piece needs a phase and a resolver: " + name);
    }
}

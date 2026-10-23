package com.dillon.starsectormarines.battle.world.gen.layout;

/** What a {@link Block} is built of. */
public enum BlockKind {
    /** Hedgehogs and crate stacks: closed to movement, open to sight, broken by fire, pushed by a dozer blade. */
    BARRICADE,
    /**
     * A wreck across a road: closed to movement until a dozer clears it. There is no wreck art yet, so it is laid
     * as crate stacks, which a blade pushes the same way (placeholder).
     */
    WRECK,
    /** Sandbag positions facing {@link Block#facing}: knee-high cover on walkable ground. */
    SANDBAG
}

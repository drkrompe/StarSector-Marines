package com.dillon.starsectormarines.battle.drone;

/**
 * How a {@link Flock} flies: the weights of its members' four steering terms and the distances they read
 * ({@link FlockSteering}). Data, so a tight darting flock and a loose wheeling one are tuning, not code.
 *
 * @param separation       weight of the push away from neighbours closer than {@code separationCells}
 * @param alignment        weight of matching the neighbours' mean velocity
 * @param cohesion         weight of closing on the neighbours' centre
 * @param anchor           weight of the pull along the member's own route to the anchor
 * @param separationCells  neighbours closer than this push apart, in cells
 * @param neighbourCells   members within this distance are a member's neighbours, in cells
 * @param arriveCells      the anchor pull fades to nothing over this last stretch, so members settle round it
 * @param maxSpeed         the fastest a member is asked to fly, cells per second
 */
public record FlockWeights(float separation, float alignment, float cohesion, float anchor,
                           float separationCells, float neighbourCells, float arriveCells, float maxSpeed) {

    /**
     * The derelict security flock (owner's recommended pick, `derelict-swarm-size`): tight and darting. A strong
     * separation at a short distance keeps the members a body-width or two apart, a light alignment lets them turn
     * quickly rather than wheel as one sheet, and a firm anchor pull keeps the body on the network's point.
     */
    public static final FlockWeights WASP = new FlockWeights(1.8f, 0.6f, 0.7f, 1.2f, 1.6f, 5f, 3f, 5.5f);

    /** A loose wheeling flock: wide spacing, strong alignment, weak anchor. Tuning only; nothing ships it yet. */
    public static final FlockWeights MURMURATION = new FlockWeights(1.2f, 1.2f, 0.9f, 0.7f, 2.6f, 8f, 5f, 4.5f);

    public FlockWeights {
        if (separationCells <= 0f || neighbourCells <= 0f || arriveCells <= 0f || maxSpeed <= 0f) {
            throw new IllegalArgumentException("flock distances and speed must be positive");
        }
    }
}

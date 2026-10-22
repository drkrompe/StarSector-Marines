package com.dillon.starsectormarines.battle.cordon;

/**
 * Which of the Cordon's two battles this is ({@code cordon-nouns.md}): the
 * company as the mobile reserve behind the redoubts while the region behind
 * them is evacuated, or as the guard of the relief road through a gap in the
 * line to the town cut off beyond it. The campaign picks it; the plan, the
 * allies and the swarm are shared.
 */
public enum CordonVariant {
    /** The mobile reserve behind the redoubts; refugees walk out behind the line. */
    LINE,
    /** The relief road through the gap to the cut-off town; convoys bring its people out. */
    ROAD
}

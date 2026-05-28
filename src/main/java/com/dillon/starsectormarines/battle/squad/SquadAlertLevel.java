package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.unit.Unit;

/**
 * Squad-scoped awareness state. Drives the idle vs. engaged branch in
 * {@link com.dillon.starsectormarines.battle.ai.goap.actions.HoldPost} and
 * {@link com.dillon.starsectormarines.battle.ai.goap.actions.PatrolRoute}:
 * a UNAWARE squad sticks to its assigned routine (hold the node / walk the
 * patrol route), an ENGAGED squad falls through to
 * {@link com.dillon.starsectormarines.battle.ai.CombatantBehavior}
 * so members pick targets and fight normally.
 *
 * <p>Promotion happens in {@code SquadAlertSystem.tick}, which runs once per
 * tick and walks each squad. If any living squadmate has
 * line-of-sight to an alive enemy combatant the squad goes ENGAGED; if a
 * squadmate has been hit recently (fall-back timer running) but no LOS, the
 * squad goes SUSPICIOUS so it can converge on the last-known enemy cell.
 * After {@link Squad#ENGAGED_DECAY_SECONDS} of no contact the squad drops
 * back to SUSPICIOUS, then to UNAWARE so patrols resume their routes.
 *
 * <p>Per-unit fall-back is independent of squad alert — a marine who eats a
 * rocket still breaks contact via
 * {@link com.dillon.starsectormarines.battle.decision.FallbackBehavior} even if the
 * squad is UNAWARE the moment before. The alert level only governs idle behavior.
 *
 * <p>Squads with {@link Unit#NO_SQUAD} members (solo defenders, civilians,
 * pre-shuttle marines) skip this state machine — they run their
 * {@link com.dillon.starsectormarines.battle.unit.UnitRole} behavior directly.
 */
public enum SquadAlertLevel {
    /** No squadmate has spotted an enemy or taken fire recently. Idle routines run. */
    UNAWARE,
    /** A squadmate took fire or saw movement, but no current LOS to a target. Converge on last known position. */
    SUSPICIOUS,
    /** A squadmate has LOS on an enemy right now. Combat dispatch active. */
    ENGAGED
}

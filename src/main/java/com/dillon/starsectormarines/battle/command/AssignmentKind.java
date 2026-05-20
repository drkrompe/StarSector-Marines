package com.dillon.starsectormarines.battle.command;

/**
 * The kind of strategic task a {@link MissionCommand} hands down to a squad.
 * Each value implies which MISSION-priority goal becomes relevant on the
 * squad once {@link ObjectiveAssignment#kind()} is set to it; see
 * {@code roadmap/ai/12-squad-of-squads.md} for the layer's design.
 *
 * <p>Stage 1 kinds:
 * <ul>
 *   <li>{@link #CLEAR_ZONE} — push into the named zone and eliminate hostiles
 *       inside it. Conquest signature — spreads marine squads across charge-
 *       site zones instead of dogpiling the nearest contact.</li>
 *   <li>{@link #HOLD_NODE} — anchor on a tactical node and defend it. Pairs
 *       with Story H's last-stand {@code HoldPosition} when that ships.</li>
 *   <li>{@link #RUSH_OBJECTIVE} — close on a specific mission objective and
 *       execute it (planter cordon, extract, etc.). Composes with Story J's
 *       {@code CordonForPlant}.</li>
 *   <li>{@link #SUPPORT} — fallback when no objective-specific kind fits:
 *       patrol toward a contested zone, hold a flank, back a friendly squad.
 *       The commander uses this for surplus squads.</li>
 * </ul>
 *
 * <p>Additions ({@code SWEEP_SECTOR}, {@code CONVERGE_ON_CONTACT}) queue
 * behind the Assault commander shape — separate kinds from the Conquest set
 * because the per-squad goal mapping differs.
 */
public enum AssignmentKind {
    CLEAR_ZONE,
    HOLD_NODE,
    RUSH_OBJECTIVE,
    SUPPORT
}

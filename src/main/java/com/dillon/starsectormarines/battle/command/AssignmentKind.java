package com.dillon.starsectormarines.battle.command;

/**
 * The kind of strategic task a {@link MissionCommand} hands down to a squad.
 * Each value implies which MISSION-priority goal becomes relevant on the
 * squad once {@link ObjectiveAssignment#kind()} is set to it; see
 * {@code ai-nouns.md} for the layer's design.
 *
 * <p>Stage 1 kinds:
 * <ul>
 *   <li>{@link #CLEAR_ZONE} — push into the named zone and eliminate hostiles
 *       inside it. Conquest signature — spreads marine squads across charge-
 *       site zones instead of dogpiling the nearest contact.</li>
 *   <li>{@link #SWEEP_SECTOR} — search an Assault sector via a commander-
 *       selected waypoint until the squad acquires a contact. The assignment
 *       carries an exact cell, but no enemy identity or position.</li>
 *   <li>{@link #DEFEND_TRACK} — move to a coarse, commander-selected Conquest
 *       track rally and hold for contact. The assignment carries no hostile
 *       identity or reported position.</li>
 *   <li>{@link #DEFEND_SITE} — move to an authored Sabotage installation and
 *       hold routine security or alarm response. The assignment names only
 *       defender-owned geometry, never a hostile identity or position.</li>
 *   <li>{@link #DEFEND_AREA} — move to a walkable rally within a coarse
 *       Assault security area and hold for locally acquired contact. The
 *       assignment carries defender-owned geometry, never hostile position.</li>
 *   <li>{@link #ADVANCE_TRACK} — move an attacking Conquest squad to a safe
 *       staging cell behind its believed lane front. The assignment carries
 *       an own-force destination, never a hostile identity.</li>
 *   <li>{@link #SECURE_COMPOUND} — push into a compound's zone, clear it, then
 *       hold until the compound's capture timer completes. Issued by
 *       {@code ConquestCommand} for zones containing uncaptured compounds
 *       that the squad's forward position has reached or passed.</li>
 *   <li>{@link #HOLD_NODE} — anchor on a tactical node and defend it. Pairs
 *       with Story H's last-stand {@code HoldPosition} when that ships.</li>
 *   <li>{@link #RUSH_OBJECTIVE} — close on a specific mission objective and
 *       execute it (planter cordon, extract, etc.). Composes with Story J's
 *       {@code CordonForPlant}.</li>
 *   <li>{@link #ESCORT} — advance to a mission-authored rally cell, then
 *       maintain a protective ring as that cell follows a moving payload.</li>
 *   <li>{@link #SUPPORT} — fallback when no objective-specific kind fits:
 *       patrol toward a contested zone, hold a flank, back a friendly squad.
 *       The commander uses this for surplus squads.</li>
 * </ul>
 *
 * <p>{@code CONVERGE_ON_CONTACT} remains queued behind the richer Assault
 * commander shape.
 */
public enum AssignmentKind {
    CLEAR_ZONE,
    SWEEP_SECTOR,
    DEFEND_TRACK,
    /** Hold an authored Sabotage installation cell for routine security or alarm response. */
    DEFEND_SITE,
    /** Hold a coarse Assault security area at a commander-selected walkable rally. */
    DEFEND_AREA,
    ADVANCE_TRACK,
    SECURE_COMPOUND,
    HOLD_NODE,
    RUSH_OBJECTIVE,
    SUPPORT,
    /** Advance to a rally cell, then remain within a protective leash of a moving mission payload. */
    ESCORT
}

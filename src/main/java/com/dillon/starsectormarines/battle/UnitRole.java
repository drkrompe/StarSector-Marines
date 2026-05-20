package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.turret.MapTurret;

/**
 * What a {@link Unit} is doing this battle. Drives the dispatch in
 * {@code BattleSimulation.updateUnit}.
 *
 * <ul>
 *   <li>{@link #COMBATANT} — default. Engage the nearest enemy, fall back when
 *       shot, repeat. The behavior every unit had before the role system landed.</li>
 *   <li>{@link #PLANTER} — heads to an assigned objective cell and channels a
 *       timed action there (charge plant, hack, breach). Falls back to combatant
 *       behavior while in transit if it picks up a closer target opportunistically.</li>
 *   <li>{@link #OBJECTIVE_CAMPER} — combatant variant that prefers firing
 *       positions near an assigned objective rather than the closest enemy.
 *       Used by defenders who need to hold a position even if marines are
 *       trying to draw them away.</li>
 *   <li>{@link #VIP} — non-combatant. Cowers in cover until a friendly marine
 *       is in LOS, then follows them toward an exfil objective. Killable but
 *       deals no damage.</li>
 *   <li>{@link #KIT_RETRIEVER} — combatant who's been tasked to pick up a
 *       dropped equipment kit ({@link EquipmentDrop}). Paths to the drop cell;
 *       on contact the sim promotes them to {@link #PLANTER} with the kit's
 *       objective. Fires opportunistically en route.</li>
 *   <li>{@link #FLEE} — non-combatant who panics when combatants get close.
 *       Used by ambient civilians (CIVILIAN/ENGINEER/SCIENTIST types) — they
 *       wander between random nearby cells with brief dwell pauses until
 *       gunfire enters their perception, then path away from the threat
 *       toward the nearest map edge. Distinct from VIP, which is a
 *       mission-controlled non-combatant with an exfil objective.</li>
 *   <li>{@link #TURRET} — static defense. Immobile; tracks the nearest visible
 *       enemy combatant within range, rotates toward it, and fires when the
 *       barrel is aligned. Assigned to {@link MapTurret} units stamped by
 *       {@link BattleSetup}; routed to {@code TurretBehavior} for per-tick
 *       update.</li>
 *   <li>{@link #GARRISON} — squad-cohesion role for defenders pegged to a
 *       {@link com.dillon.starsectormarines.battle.tactical.TacticalNode}.
 *       Holds a firing position near the node's anchor until any squadmate
 *       sees an enemy, at which point the squad's
 *       {@link com.dillon.starsectormarines.battle.ai.SquadAlertLevel}
 *       transitions to ENGAGED and members fall through to combatant-style
 *       engagement. Replaces "everyone clusters at the defender spawn anchor"
 *       for conquest-mode maps with a tactical layer.</li>
 *   <li>{@link #PATROL} — squad walks a route between random nearby
 *       {@link com.dillon.starsectormarines.battle.tactical.TacticalNode}s in
 *       their district while UNAWARE. On enemy contact the squad bumps to
 *       ENGAGED and members shift to combatant engagement; on SUSPICIOUS
 *       (a squadmate took fire but no LOS) the squad converges on the last
 *       known enemy cell. Distinct from {@link #GARRISON} only in idle
 *       movement — the combat path is identical.</li>
 * </ul>
 *
 * <p>Mission setup assigns roles when populating the simulation. Roles can
 * change at runtime if a future mission wants it (e.g., capture → switch from
 * COMBATANT to OBJECTIVE_CAMPER once a flag is taken), but nothing in the
 * current code paths writes to {@code Unit.role} after spawn.
 */
public enum UnitRole {
    COMBATANT,
    PLANTER,
    OBJECTIVE_CAMPER,
    VIP,
    KIT_RETRIEVER,
    FLEE,
    TURRET,
    GARRISON,
    PATROL
}

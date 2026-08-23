package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * One entity's finished combat record — the plain-data form of its
 * {@code TELEMETRY} component, snapshotted at the end of a battle.
 *
 * <p>Immutable and free of entity handles by design. The battle world is
 * ephemeral and never serializes
 * ({@code progression-nouns.md}), so this
 * is what crosses to the campaign: rows whose {@link #campaignSoldierId} is
 * non-null belong to marines the campaign roster knows and accumulate into a
 * career record; every other row (defenders, employer militia, turrets) is a
 * balance artifact only and is read for the debug report and then dropped.
 *
 * @param entityId          battle-local entity id, valid only for the battle that produced it
 * @param name              the greppable unit name, for the debug report
 * @param faction           which side this entity fought for
 * @param type              what it was
 * @param campaignSoldierId the campaign roster key, or {@code null} for anyone the campaign does not track
 * @param survived          false if the entity was a corpse when the battle ended
 */
public record CombatTelemetryRow(long entityId,
                                 String name,
                                 Faction faction,
                                 UnitType type,
                                 String campaignSoldierId,
                                 boolean survived,
                                 int roundsFired,
                                 int roundsHit,
                                 float damageDealt,
                                 float friendlyFireDamage,
                                 float damageTaken,
                                 int kills,
                                 int secondaryUsed) {

    /**
     * True if this entity did anything at all worth printing. A battle spawns
     * plenty of turrets nothing ever walked past; listing them buries the rows
     * that matter.
     */
    public boolean sawAction() {
        return roundsFired > 0 || secondaryUsed > 0 || kills > 0
                || damageDealt > 0f || friendlyFireDamage > 0f || damageTaken > 0f;
    }

    /** Landed fraction, or {@code 0} for an entity that never fired. */
    public float landedFraction() {
        return roundsFired > 0 ? (float) roundsHit / roundsFired : 0f;
    }
}

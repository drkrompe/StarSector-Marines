package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stateless consumer of the {@code TELEMETRY} component: turns the live
 * columns into a snapshot every consumer downstream can hold
 * ({@link CombatTelemetryRow}), and formats that snapshot as a readable
 * table.
 *
 * <p>Two consumers, one gather. The campaign keeps only the rows it
 * recognizes, keyed by {@link CombatTelemetryRow#campaignSoldierId}; the
 * debug readout keeps all of them, because "what did the defenders' rifles
 * actually do" is the question a synthetic two-unit harness cannot answer
 * ({@code s3-per-soldier-telemetry.md}).
 *
 * <p><b>Order is spawn order</b>, recovered by sorting on entity id: the
 * archetype tables are grouped by membership, so a walk over them interleaves
 * marines, turrets and corpses in an order that shifts every time an entity
 * gains or loses a component. Sorting also makes the report reproducible,
 * which is what lets two runs of the same scenario be diffed.
 *
 * <p>Safe to call after the battle has released every dead entity —
 * {@code TELEMETRY} and {@code IDENTITY} both ride the corpse transmute, so
 * the fallen appear in the gather with {@code survived = false}. This is a
 * column walk, not a roster walk, so it is not exposed to the dense
 * registry's swap-and-pop.
 */
public final class CombatTelemetryReport {

    private CombatTelemetryReport() {}

    /**
     * Snapshots every recorded entity, live or dead, in ascending entity-id
     * (spawn) order.
     */
    public static List<CombatTelemetryRow> gather(EntityWorld world, BattleComponents components) {
        List<CombatTelemetryRow> rows = new ArrayList<>();
        for (ArchetypeTable t : world.matched(components.telemetryRecords)) {
            boolean survived = !t.has(components.CORPSE);
            Object[] types = t.objects(components.IDENTITY, BattleComponents.IDENTITY_TYPE).array();
            Object[] factions = t.objects(components.IDENTITY, BattleComponents.IDENTITY_FACTION).array();
            Object[] names = t.objects(components.IDENTITY, BattleComponents.IDENTITY_NAME).array();
            Object[] soldierIds = t.objects(components.IDENTITY,
                    BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID).array();
            int[] fired = t.ints(components.TELEMETRY, BattleComponents.TELEMETRY_ROUNDS_FIRED).array();
            int[] hits = t.ints(components.TELEMETRY, BattleComponents.TELEMETRY_ROUNDS_HIT).array();
            float[] dealt = t.floats(components.TELEMETRY, BattleComponents.TELEMETRY_DAMAGE_DEALT).array();
            float[] friendly = t.floats(components.TELEMETRY,
                    BattleComponents.TELEMETRY_FRIENDLY_FIRE_DAMAGE).array();
            float[] taken = t.floats(components.TELEMETRY, BattleComponents.TELEMETRY_DAMAGE_TAKEN).array();
            int[] kills = t.ints(components.TELEMETRY, BattleComponents.TELEMETRY_KILLS).array();
            int[] secondary = t.ints(components.TELEMETRY, BattleComponents.TELEMETRY_SECONDARY_USED).array();
            for (int r = 0, n = t.rowCount(); r < n; r++) {
                rows.add(new CombatTelemetryRow(
                        t.entityAt(r),
                        (String) names[r],
                        (Faction) factions[r],
                        (UnitType) types[r],
                        (String) soldierIds[r],
                        survived,
                        fired[r], hits[r], dealt[r], friendly[r], taken[r],
                        kills[r], secondary[r]));
            }
        }
        rows.sort(Comparator.comparingLong(CombatTelemetryRow::entityId));
        return rows;
    }

    /** Convenience overload over a running sim. */
    public static List<CombatTelemetryRow> gather(BattleSimulation sim) {
        return gather(sim.getEntityWorld(), sim.getBattleComponents());
    }

    /**
     * Renders {@code rows} as a fixed-width table with a per-faction summary.
     * Rows with nothing on them are omitted; see
     * {@link CombatTelemetryRow#sawAction()}.
     */
    public static String format(List<CombatTelemetryRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-22s %-10s %6s %6s %6s %9s %9s %9s %5s %4s%n",
                "unit", "faction", "fired", "hit", "acc%", "dealt", "ff", "taken", "kills", "sec"));
        for (CombatTelemetryRow row : rows) {
            if (!row.sawAction()) continue;
            sb.append(String.format("%-22s %-10s %6d %6d %5.0f%% %9.1f %9.1f %9.1f %5d %4d%s%n",
                    abbreviate(row.name(), 22),
                    row.faction(),
                    row.roundsFired(), row.roundsHit(), row.landedFraction() * 100f,
                    row.damageDealt(), row.friendlyFireDamage(), row.damageTaken(),
                    row.kills(), row.secondaryUsed(),
                    row.survived() ? "" : "  KIA"));
        }
        for (Faction faction : Faction.values()) {
            int fired = 0;
            int hit = 0;
            int kills = 0;
            float dealt = 0f;
            for (CombatTelemetryRow row : rows) {
                if (row.faction() != faction) continue;
                fired += row.roundsFired();
                hit += row.roundsHit();
                kills += row.kills();
                dealt += row.damageDealt();
            }
            if (fired == 0 && kills == 0) continue;
            sb.append(String.format("%-22s %-10s %6d %6d %5.0f%% %9.1f %9s %9s %5d%n",
                    "TOTAL", faction, fired, hit,
                    fired > 0 ? 100f * hit / fired : 0f, dealt, "", "", kills));
        }
        return sb.toString();
    }

    private static String abbreviate(String name, int max) {
        if (name == null) return "?";
        return name.length() <= max ? name : name.substring(0, max);
    }
}

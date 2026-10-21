package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Who hit whom: one row per resolved hit, recorded only while switched on.
 *
 * <p>{@link CombatTelemetryService} keeps running totals per shooter, which answer "how much did this gun deal" and
 * never "what killed that tank". This is the other half: the hit itself, with the plate it met and where the damage
 * went. Both damage resolvers write it at the point they already credit telemetry, after the armour law has spoken,
 * so a row is what the hit actually did.
 *
 * <p>It is an observer. Recording reads state and draws nothing from any random stream, so a battle played with it
 * on is the battle played with it off; it can therefore be switched on at any tick of a replay to look at what
 * follows without changing it. Off, the resolvers pay one boolean read per hit.
 *
 * <p>Damage application is serial ({@code DamageService}), so the row list needs no locking.
 */
public final class HitLog {

    /** Rows kept before the log stops recording, so a forgotten log cannot take the heap. */
    public static final int DEFAULT_CAPACITY = 2_000_000;

    /**
     * One resolved hit. {@code damage} is the round as fired, {@code postCover} what reached the body after cover
     * and the target's damage multiplier; {@code rating} the plate rating the round was read against, after any arc;
     * {@code arc} and {@code part} are null where the target has none. {@code hpAfter} and {@code armorAfter} are
     * the target's columns once the hit was written.
     */
    public record Hit(int tick, long target, String targetLabel, long attacker, String attackerLabel,
                      float damage, float postCover, float penetration, float rating, String arc, String part,
                      float mitigated, float armorDamage, float structureDamage,
                      float hpAfter, float armorAfter, boolean died) {
        public float applied() { return armorDamage + structureDamage; }
    }

    private final List<Hit> hits = new ArrayList<>();
    private IntSupplier clock = () -> -1;
    private boolean recording;
    private int capacity = DEFAULT_CAPACITY;
    private boolean overflowed;

    /** The simulation's tick, stamped on every row. */
    public void setClock(IntSupplier clock) {
        this.clock = clock;
    }

    public void start() {
        recording = true;
    }

    public void stop() {
        recording = false;
    }

    public void clear() {
        hits.clear();
        overflowed = false;
    }

    public void setCapacity(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    /** Whether rows are being taken; the resolvers read this before building one. */
    public boolean recording() {
        return recording;
    }

    /** True once the log filled and stopped taking rows. */
    public boolean overflowed() {
        return overflowed;
    }

    public List<Hit> hits() {
        return Collections.unmodifiableList(hits);
    }

    public int tick() {
        return clock.getAsInt();
    }

    public void record(Hit hit) {
        if (!recording) return;
        if (hits.size() >= capacity) {
            overflowed = true;
            recording = false;
            return;
        }
        hits.add(hit);
    }

    /**
     * A readable name for any body the battle knows: {@code FACTION:TYPE} for a roster unit,
     * {@code FACTION:VEHICLE_TYPE} for a ground hull, {@code FACTION:AIRFRAME} for a craft, {@code none} for an
     * unattributed hit. Reads only.
     */
    public static String label(UnitRosterService roster, long id) {
        if (id == CombatTelemetryService.NO_ATTACKER) return "none";
        ConvoyService convoy = roster.convoy();
        if (convoy.isVehicle(id)) return convoy.faction(id) + ":" + convoy.vehicleType(id);
        IdentityService identity = roster.identity();
        if (identity.has(id)) return identity.faction(id) + ":" + identity.type(id);
        World world = roster.world();
        if (world.airframe(id) != null) return world.airFaction(id) + ":" + world.airframe(id);
        return "entity";
    }
}

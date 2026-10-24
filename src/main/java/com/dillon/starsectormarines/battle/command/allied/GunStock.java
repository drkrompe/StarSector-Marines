package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.deployable.FieldMortarService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * The ammunition a place's guns have on hand (`allied-tools.md`, The delivery hook): a redoubt's gun towers, a picket's
 * nests, a base's turrets, any gun that fires on a cooldown, plus the field mortar tubes standing there. Each shot
 * spends a round from the gun's stock; a gun with none left fires rationed, waiting {@link #RATION} times its own
 * cooldown between shots, as a crew scraping the last belts together does. An ammunition truck's arrival refills the
 * lot ({@link #recipient}). A tube keeps its own count (`FieldMortarService`) and is refilled to its capacity.
 *
 * <p>Mission-neutral: what a place is, how many rounds a gun holds and when a truck is asked for are the mission's.
 */
public final class GunStock {

    /** A dry gun waits this many times its own cooldown between shots. */
    public static final float RATION = 4f;

    private final String name;
    private final int roundsPerGun;
    private final Map<Long, int[]> rounds = new LinkedHashMap<>();
    private final Map<Long, Float> lastTimer = new LinkedHashMap<>();
    private final List<FieldMortarService.Tube> tubes = new ArrayList<>();
    private int refills;

    /** @param roundsPerGun rounds a gun holds full ({@code cordon-allied-forces.md} and the like set it) */
    public GunStock(String name, int roundsPerGun) {
        this.name = name;
        this.roundsPerGun = roundsPerGun;
    }

    public String name() { return name; }

    /** Adds a gun, full. */
    public GunStock add(long gun) {
        rounds.putIfAbsent(gun, new int[]{roundsPerGun});
        return this;
    }

    /** Adds a field mortar tube standing at this place; a truck refills it to its capacity. */
    public GunStock addTube(FieldMortarService.Tube tube) {
        if (!tubes.contains(tube)) tubes.add(tube);
        return this;
    }

    /** Empties every gun (a scripted shortage, the Cordon's L2). */
    public void drain() {
        for (int[] r : rounds.values()) r[0] = 0;
    }

    /** Rounds the guns hold together now, over what they hold full; {@code 1} with no guns. */
    public float share() {
        if (rounds.isEmpty()) return 1f;
        long left = 0;
        for (int[] r : rounds.values()) left += r[0];
        return left / (float) ((long) roundsPerGun * rounds.size());
    }

    /** Guns with no rounds left. */
    public int dry() {
        int n = 0;
        for (int[] r : rounds.values()) if (r[0] <= 0) n++;
        return n;
    }

    /** Trucks that have refilled this place. */
    public int refills() { return refills; }

    /** Refills every gun and every live tube here. */
    public void refill(BattleSimulation sim) {
        for (int[] r : rounds.values()) r[0] = roundsPerGun;
        if (sim != null) for (FieldMortarService.Tube t : tubes) sim.fieldMortars().rearm(t);
        refills++;
    }

    /**
     * One tick: every gun that fired since the last spends a round, or, dry, waits its rationed time before the
     * next. Reads a shot off the gun's cooldown timer jumping up, so it needs no hook in the firing code.
     */
    public void advance(BattleSimulation sim) {
        for (Map.Entry<Long, int[]> e : rounds.entrySet()) {
            long gun = e.getKey();
            if (sim.resolveUnit(gun) == 0L || !sim.world().isAlive(gun)) continue;
            float timer = sim.world().cooldownTimer(gun);
            Float last = lastTimer.get(gun);
            if (last != null && timer > last + 1e-4f) {
                if (e.getValue()[0] > 0) {
                    e.getValue()[0]--;
                } else {
                    timer = sim.combat().attackCooldown(gun) * RATION;
                    sim.world().setCooldownTimer(gun, timer);
                }
            }
            lastTimer.put(gun, timer);
        }
    }

    /** The recipient a truck bound here drives to: arrival refills the place. Gone when {@code there} says so. */
    public SupplyDelivery.Recipient recipient(int[] cell, BooleanSupplier there) {
        GunStock stock = this;
        return new SupplyDelivery.Recipient() {
            @Override public String name() { return stock.name; }
            @Override public int[] where(BattleSimulation sim) { return there.getAsBoolean() ? cell : null; }
            @Override public void arrived(BattleSimulation sim) { stock.refill(sim); }
        };
    }
}

package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;

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
 * lot ({@link #recipient}). A mortar team's tubes keep their own count (`FieldMortarService`), standing or packed, and
 * are refilled to their capacity.
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
    private final List<Integer> mortarSquads = new ArrayList<>();
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

    /** Adds a mortar team holding this place; a truck refills its tubes, standing or packed, to their capacity. */
    public GunStock addMortarSquad(int squad) {
        if (!mortarSquads.contains(squad)) mortarSquads.add(squad);
        return this;
    }

    /** Empties every gun (a scripted shortage). */
    public void drain() {
        for (int[] r : rounds.values()) r[0] = 0;
    }

    /** Empties these guns, where they are this place's (a scripted shortage in some of its guns, the Cordon's L2). */
    public void drain(List<Long> guns) {
        for (long gun : guns) {
            int[] r = rounds.get(gun);
            if (r != null) r[0] = 0;
        }
    }

    /** Rounds the guns hold together now, over what they hold full; {@code 1} with no guns. */
    public float share() {
        if (rounds.isEmpty()) return 1f;
        long left = 0;
        for (int[] r : rounds.values()) left += r[0];
        return left / (float) ((long) roundsPerGun * rounds.size());
    }

    /**
     * Rounds the guns and the mortar tubes hold together, over what they hold full: one pool, so a team's few tube
     * rounds weigh as rounds, not as a share of their own. {@code 1} with nothing here.
     */
    public float pooledShare(BattleView sim) {
        long left = 0, full = (long) roundsPerGun * rounds.size();
        for (int[] r : rounds.values()) left += r[0];
        for (int squad : mortarSquads) {
            int[] t = sim.fieldMortars().squadRounds(squad, sim);
            left += t[0];
            full += t[1];
        }
        return full == 0 ? 1f : left / (float) full;
    }

    /** Rounds one gun has left, or {@code -1} for a gun not stocked here. */
    public int rounds(long gun) {
        int[] r = rounds.get(gun);
        return r == null ? -1 : r[0];
    }

    /** Guns with no rounds left. */
    public int dry() {
        int n = 0;
        for (int[] r : rounds.values()) if (r[0] <= 0) n++;
        return n;
    }

    /**
     * Spends one of the gun's rounds for a shot fired outside its own cooldown (a barrage's shell); false when it has
     * none left. A gun not stocked here fires freely.
     */
    public boolean spend(long gun) {
        int[] r = rounds.get(gun);
        if (r == null) return true;
        if (r[0] <= 0) return false;
        r[0]--;
        return true;
    }

    /** Trucks that have refilled this place. */
    public int refills() { return refills; }

    /** Refills every gun and every mortar team's tubes here. */
    public void refill(BattleSimulation sim) {
        for (int[] r : rounds.values()) r[0] = roundsPerGun;
        if (sim != null) for (int squad : mortarSquads) sim.fieldMortars().restockSquad(squad, sim);
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

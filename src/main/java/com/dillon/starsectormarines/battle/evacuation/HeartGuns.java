package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.command.allied.AlliedCommand;
import com.dillon.starsectormarines.battle.command.allied.AlliedMakers;
import com.dillon.starsectormarines.battle.command.allied.AlliedRequest;
import com.dillon.starsectormarines.battle.command.allied.AlliedTool;
import com.dillon.starsectormarines.battle.command.allied.AlliedToolShed;
import com.dillon.starsectormarines.battle.command.allied.GunStock;
import com.dillon.starsectormarines.battle.command.allied.SupplyDelivery;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The heart's guns and the trucks that keep them firing (`heart-allied-forces.md`, the ammunition trucks): the
 * Burn's battery, and the flak pits a beat digs, each place a {@link GunStock}. A gun spends a round a shot and fires
 * rationed when it has none; a place below {@link #CALL_SHARE} of its rounds asks the vehicle park's tool shed for an
 * ammunition truck, never with one of its own still on the road, and the truck's arrival refills the place.
 * The owner asked for the trucks to rearm the bases' guns (2026-10-23).
 */
public final class HeartGuns {

    /** A place this short of its rounds calls a truck. */
    static final float CALL_SHARE = 0.5f;
    /** Ticks between the guns' calls. */
    static final int CALL_TICKS = 60;
    /**
     * Shells a battery gun holds full. The barrage fires two and a half a second across four guns, about two hundred a
     * gun over its plan, so a gun runs low near the third line and dry near the fifth with no truck.
     */
    public static final int BATTERY_SHELLS = 120;
    /** Bursts a flak pit holds full. */
    public static final int FLAK_ROUNDS = 120;
    /** The place the Burn's battery stands. */
    public static final String BATTERY = "battery";
    /** The flak pit dug by the battery. */
    public static final String BATTERY_FLAK = "battery-flak";
    /** The place the ammunition dump's flak stands. */
    public static final String DUMP = "dump";
    /** The place the vehicle park's flak stands. */
    public static final String PARK_FLAK = "park-flak";

    private static final String ISSUER = "heart guns";

    private record Place(GunStock stock, int[] cell, List<Long> guns) { }

    private final Map<String, Place> places = new LinkedHashMap<>();
    /** Beats' own errands by place: where their trucks go and what arrival does. */
    private final Map<String, SupplyDelivery.Recipient> errands = new LinkedHashMap<>();
    private final AlliedCommand command = new AlliedCommand(ISSUER);
    private AlliedToolShed shed;
    private boolean advancesShed;
    private int called;

    /** A place's guns, full, around {@code cell}: where its trucks drive to. */
    public HeartGuns place(String name, int roundsPerGun, int[] cell) {
        places.putIfAbsent(name, new Place(new GunStock(name, roundsPerGun), cell, new ArrayList<>()));
        return this;
    }

    /** Adds a gun to a place laid with {@link #place}; a gun at a place not laid is left unstocked. */
    public HeartGuns add(String name, long gun) {
        Place p = places.get(name);
        if (p == null || gun == 0L || p.guns().contains(gun)) return this;
        p.stock().add(gun);
        p.guns().add(gun);
        return this;
    }

    /**
     * The guns call their trucks from {@code shed}, stocked with {@link #truckMaker()}. {@code advance} says whether
     * these guns advance the shed each tick: true where nothing else does (the Burn), false where the lanes do.
     */
    public HeartGuns calledFrom(AlliedToolShed shed, boolean advance) {
        this.shed = shed;
        this.advancesShed = advance;
        return this;
    }

    /**
     * Sends a truck from the shed on a beat's errand: to {@code to}, under {@code place}, a name of the errand's own.
     * Returns false when there is no shed, no truck in stock, the depot cannot send or the recipient is gone.
     */
    public boolean send(BattleSimulation sim, String place, SupplyDelivery.Recipient to, String reason) {
        if (shed == null) return false;
        int[] at = to.where(sim);
        if (at == null) return false;
        errands.put(place, to);
        if (shed.request(sim, new AlliedRequest(AlliedTool.AMMO_TRUCK, place, at[0], at[1], reason))) return true;
        errands.remove(place);
        return false;
    }

    /** Whether trucks can be sent at all: the guns have a shed to call them from. */
    public boolean sends() { return shed != null; }

    /** Trucks bound for {@code place} still on the road. */
    public int onRoad(String place) {
        return shed == null ? 0 : shed.onRoad(AlliedTool.AMMO_TRUCK, place);
    }

    /** An ammunition truck to the place a request names, refilling it on arrival; none for a place with no gun standing. */
    public AlliedToolShed.Maker truckMaker() {
        return AlliedMakers.ammunition(command, request -> {
            SupplyDelivery.Recipient errand = errands.get(request.toPlace());
            if (errand != null) return errand;
            Place p = places.get(request.toPlace());
            return p == null ? null : p.stock().recipient(p.cell(), () -> true);
        });
    }

    /** The place's stock, or {@code null}. */
    public GunStock stock(String name) {
        Place p = places.get(name);
        return p == null ? null : p.stock();
    }

    /** One tick: rounds spent, trucks driven, and every {@link #CALL_TICKS} a truck called to a place running low. */
    public void tick(BattleSimulation sim, List<String> beats) {
        for (Place p : places.values()) p.stock().advance(sim);
        command.advance(sim);
        if (shed == null) return;
        if (advancesShed) shed.advance(sim);
        if (sim.getSimTickIndex() % CALL_TICKS != 0) return;
        for (Map.Entry<String, Place> e : places.entrySet()) {
            Place p = e.getValue();
            if (!calls(p.stock().share(), standing(sim, p), shed.onRoad(AlliedTool.AMMO_TRUCK, e.getKey()))) continue;
            String reason = p.stock().dry() > 0 ? "dry" : "low";
            if (!shed.request(sim, new AlliedRequest(AlliedTool.AMMO_TRUCK, e.getKey(), p.cell()[0], p.cell()[1], reason))) continue;
            called++;
            beats.add("ammunition_called_" + e.getKey() + "_" + called);
        }
    }

    /** The rule: a place with a gun standing, under {@link #CALL_SHARE} of its rounds and no truck of its own on the road, calls one. */
    static boolean calls(float share, boolean standing, int onRoad) {
        return standing && onRoad == 0 && share < CALL_SHARE;
    }

    private static boolean standing(BattleSimulation sim, Place p) {
        for (long gun : p.guns()) if (sim.resolveUnit(gun) != 0L && sim.world().isAlive(gun)) return true;
        return false;
    }
}

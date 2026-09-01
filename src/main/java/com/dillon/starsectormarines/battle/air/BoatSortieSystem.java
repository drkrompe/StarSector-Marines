package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

/**
 * Sends a ship's boats out of her bays and brings them home again.
 *
 * <p><b>A real sortie rather than a countdown, and that is the whole point of
 * it.</b> A bay could have been given a timer that emptied a berth for a while
 * and put a boat back looking serviced, and from a deck screen it would be very
 * nearly indistinguishable. What it would not be is the same machinery: the
 * berth would empty without an aircraft existing, the hull that came back would
 * not be the hull that left, a boat could not be lost, and every consequence a
 * garrison field already has would have to be written a second time for ships.
 * So a boat flies — an air entity, on a mission, through the ordinary state
 * machine — and everything downstream of that is free.
 *
 * <p><b>Where it goes is deliberately nowhere.</b> Out through the bay's door to
 * a point off the hull, and back. On a deck view there is no elsewhere to fly to
 * and inventing one would be a second world nobody is looking at; what the
 * flight is for here is the time the berth is empty, the hull it comes home
 * with, and the crew having something to turn round. A mission that means
 * something — a lift to the surface, a boarding run — replaces the destination
 * and keeps everything else.
 *
 * <p>Follows the {@code *System} convention and is the sibling of
 * {@link AirStrikeSystem}: a garrison field decides to put an aircraft over the
 * battle, and this decides to put a boat off the ship. Both spend a berth, both
 * hand the airframe back through {@code AirSystem}, and neither owns any state
 * but its own cadence.
 */
public final class BoatSortieSystem {

    /**
     * Sim-seconds before the first boat goes out.
     *
     * <p>Long enough that a screen opened on the deck shows the bay as it
     * stands rather than mid-launch. A player's first look at their own boat
     * deck should be the boats.
     */
    private static final float FIRST_SORTIE_SEC = 60f;

    /**
     * Sim-seconds between one boat leaving and the next going out.
     *
     * <p>A ship's boat traffic, not an airfield's cadence. A bay is not
     * answering an assault; it is running the errands a ship in company has,
     * and those are occasional. Short enough that a player watching the deck
     * for a minute or two sees one go.
     */
    private static final float SORTIE_INTERVAL_SEC = 45f;

    /**
     * How many of a ship's boats may be off at once.
     *
     * <p>One. A ship in company is not flying an air operation, and a bay with
     * every berth empty is a bay that cannot answer anything — the same reason a
     * garrison field keeps half its sheds back, arrived at from a much smaller
     * establishment.
     */
    private static final int MAX_AWAY = 1;

    /** How long a retry waits when the bay wanted to send one and could not. */
    private static final float RETRY_SEC = 10f;

    private float nextSortieIn = FIRST_SORTIE_SEC;

    /** Advance the cadence and send a boat out when the bay has one to send. */
    public void tick(float dt, BattleSimulation sim, AirfieldService field) {
        if (field == null || field.berths().isEmpty()) return;
        nextSortieIn -= dt;
        if (nextSortieIn > 0f) return;

        if (away(field) >= MAX_AWAY) {
            nextSortieIn = RETRY_SEC;
            return;
        }
        AirfieldService.Berth boat = readyBoat(field);
        if (boat == null) {
            nextSortieIn = RETRY_SEC;
            return;
        }
        send(sim, field, boat);
        nextSortieIn = SORTIE_INTERVAL_SEC;
    }

    /**
     * Puts one boat off the ship.
     *
     * <p>Its own berth is both the entry and the exit, because a boat leaves
     * from where it is kept and comes home to the same place — the shape a
     * based sortie already has. What differs from a garrison's is only which
     * end is the errand: an aircraft off a field flies to somewhere on this
     * map, and a boat off a ship flies to the one place that is not on it.
     *
     * <p>It starts climbing off the stand rather than already under way.
     * Without that the craft is at cruise altitude on the first tick of its
     * flight leg, which reads as the boat vanishing off the deck rather than
     * lifting from it — the same discontinuity the settle onto a pad exists to
     * remove, the other way round.
     */
    private static void send(BattleSimulation sim, AirfieldService field,
                             AirfieldService.Berth boat) {
        float padX = boat.centerX + 0.5f;
        float padY = boat.centerY + 0.5f;
        float[] out = boat.offship;
        long craft = sim.spawnShuttle((ShuttleType) boat.airframe, field.owner(),
                out[0], out[1], padX, padY, padX, padY, 0f);
        ShuttleMission mission = sim.world().mission(craft);
        // The hull that leaves is the hull that was on the stand, and it owes
        // itself back to that stand: AirSystem hands it over on every ending,
        // recovered or not.
        sim.world().setHp(craft, field.launch(boat));
        mission.homeBerth = boat;
        mission.totalCycles = 1;
        sim.world().kinematics(craft).teleport(padX, padY, boat.facingDegrees);
        mission.padPhaseElapsed = 0f;
        mission.state = ShuttleState.PAD_ASCENT;
    }

    /**
     * The first boat that could go now.
     *
     * <p>A berth with somewhere off this map to be. That gate is what keeps a
     * garrison's hardstands out of it — an aircraft on a lot has nowhere to fly
     * that is not the battle, so this finds nothing on a ground map and does
     * nothing there, without needing to be told which kind of place it is on.
     *
     * <p>And something that flies as a boat. A berth holds an {@link Airframe},
     * which is what a thing is; the air spawn takes a {@link ShuttleType}, which
     * is what it flies as. A bay stocked with something that is not one holds a
     * boat that never leaves rather than crashing the tick that tried to send
     * it.
     */
    private static AirfieldService.Berth readyBoat(AirfieldService field) {
        for (AirfieldService.Berth berth : field.berths()) {
            if (!berth.airworthy() || !berth.hasOffship()) continue;
            if (berth.airframe instanceof ShuttleType) return berth;
        }
        return null;
    }

    /** How many of this bay's boats are off the ship. */
    private static int away(AirfieldService field) {
        int out = 0;
        for (AirfieldService.Berth berth : field.berths()) {
            if (berth.hasOffship()
                    && berth.state == AirfieldService.BerthState.AWAY) {
                out++;
            }
        }
        return out;
    }

}

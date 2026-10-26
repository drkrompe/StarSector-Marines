package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The player's one queue of ferry sorties ({@code air-nouns.md}, Lift).
 *
 * <p>The opening squad loads stand at its head: each ferry craft's own sorties were queued
 * when the battle was set up, bound to that craft, so a craft flies every one of them
 * before anything ordered later. Behind them come the machine drops the player orders, one
 * machine each, first in first out. A craft that has flown its own loads takes the first
 * queued drop its lift rating admits ({@link LiftAdmission}); one that admits nothing
 * leaves when its loads are done, as before. A sortie carries its squad load or exactly one
 * machine, never both.
 *
 * <p>A queued drop can be cancelled until a craft takes it; once taken it is in the air and
 * is the craft's ({@link ShuttleMission#machineDropId}). The queue holds only what is
 * waiting; nothing here spends lift, which the drop power does when it orders.
 */
public final class LiftQueue {

    /**
     * One machine waiting for a ferry craft.
     *
     * @param id            stable handle, for a cancel and for the craft that flies it
     * @param powerId       the drop power that ordered it, and refunds a cancel
     * @param manifestIndex the machine's place in that power's bay manifest
     * @param route         {@code {entryX, entryY, exitX, exitY}} of its sortie
     * @param claim         who the landed machine's squad belongs to
     */
    public record MachineDrop(long id, String powerId, int manifestIndex, ArmorLoad load,
                              float lzX, float lzY, float[] route, SquadCommandClaim claim) {

        public LiftClass liftClass() {
            return load.liftClass();
        }
    }

    private final List<MachineDrop> queued = new ArrayList<>();
    private long nextId = 1L;
    private boolean active;

    /**
     * Whether the player has a drop power, so a lift-rated ferry craft waits on standby after
     * its own loads instead of leaving. Off, the ferry behaves exactly as it always has.
     */
    public boolean active() {
        return active;
    }

    /** Marks the queue as serving a drop power for the rest of the battle. */
    public void activate() {
        active = true;
    }

    /** Appends one machine to the queue and returns its job. */
    public MachineDrop enqueue(String powerId, int manifestIndex, ArmorLoad load,
                               float lzX, float lzY, float[] route, SquadCommandClaim claim) {
        if (load == null) throw new IllegalArgumentException("a drop needs a machine");
        MachineDrop job = new MachineDrop(nextId++, powerId, manifestIndex, load, lzX, lzY,
                route.clone(), claim);
        queued.add(job);
        return job;
    }

    /** Removes a job that no craft has taken yet; {@code null} when it is not waiting. */
    public MachineDrop cancel(long id) {
        for (int i = 0; i < queued.size(); i++) {
            if (queued.get(i).id() == id) return queued.remove(i);
        }
        return null;
    }

    /** The waiting job with {@code id}, or {@code null}. */
    public MachineDrop find(long id) {
        for (MachineDrop job : queued) if (job.id() == id) return job;
        return null;
    }

    /** Whether {@code powerId}'s manifest entry {@code index} is waiting for a craft. */
    public boolean isQueued(String powerId, int index) {
        for (MachineDrop job : queued) {
            if (job.manifestIndex() == index && job.powerId().equals(powerId)) return true;
        }
        return false;
    }

    /** Waiting jobs, oldest first. */
    public List<MachineDrop> queued() {
        return Collections.unmodifiableList(queued);
    }

    /**
     * Hands {@code frame} the oldest waiting job it can lift, removing it from the queue;
     * {@code null} when nothing waiting fits.
     */
    public MachineDrop takeFor(Airframe frame) {
        for (int i = 0; i < queued.size(); i++) {
            if (LiftAdmission.check(queued.get(i).liftClass(), frame).admitted()) {
                return queued.remove(i);
            }
        }
        return null;
    }

    /**
     * Whether a craft is one of the player's transports landing marines: the ferry. Gunships,
     * strikes, pool sorties, pickups, rescue flights and armour carriers are not.
     */
    public static boolean ferryCandidate(ShuttleMission mission) {
        return mission != null && !mission.destroyed && !mission.gunship && !mission.strikeSortie
                && !mission.poolSortie && mission.cover == null && mission.pickup == null
                && !mission.awaitingEvacuees && !mission.rescueMilitiaTransport
                && !mission.rescuePickupMechTransport
                && (mission.payload == null || mission.payload == InfantryPayload.INSTANCE);
    }

    /** Marks every marine transport in the air now as ferry. Idempotent. */
    public static void enlist(World world, long[] air) {
        for (long id : air) {
            if (world.airFaction(id) != Faction.MARINE) continue;
            ShuttleMission mission = world.mission(id);
            if (ferryCandidate(mission) && mission.state != ShuttleState.GONE) mission.ferry = true;
        }
    }

    /** Whether a live ferry craft among {@code air} has a lift rating that admits {@code payload}. */
    public static boolean ferryAdmits(World world, long[] air, LiftClass payload) {
        for (long id : air) {
            ShuttleMission mission = world.mission(id);
            if (mission == null || !mission.ferry || mission.destroyed
                    || mission.state == ShuttleState.GONE) continue;
            if (LiftAdmission.check(payload, world.airframe(id)).admitted()) return true;
        }
        return false;
    }

    /** Whether a live ferry craft among {@code air} lifts at least {@code rating}. */
    public static boolean ferryRated(World world, long[] air, LiftRating rating) {
        for (long id : air) {
            ShuttleMission mission = world.mission(id);
            if (mission == null || !mission.ferry || mission.destroyed
                    || mission.state == ShuttleState.GONE) continue;
            Airframe frame = world.airframe(id);
            if (frame != null && frame.liftRating().ordinal() >= rating.ordinal()) return true;
        }
        return false;
    }

    /**
     * Puts a ferry craft with nothing of its own left on standby: invisible and waiting in
     * {@link ShuttleState#PENDING} for a job, re-armed after {@code rearmSeconds}, holding no
     * passenger and no machine.
     */
    public static void standBy(ShuttleMission mission, float rearmSeconds) {
        mission.standby = true;
        mission.state = ShuttleState.PENDING;
        mission.pendingDelay = rearmSeconds;
        mission.marinesRemaining = 0;
        mission.deboardedThisSortie = 0;
        mission.marineLoadout = null;
        mission.armorDrop = null;
        mission.mechDeployments = null;
        mission.mechVariants = null;
        mission.mechVariant = null;
        mission.vehicleDeployment = null;
        mission.machineDropId = 0L;
        mission.squadId = Squad.NO_SQUAD;
        mission.totalCycles = mission.currentCycle + 1;
        mission.fieldPresenceAdmitted = false;
    }

    /**
     * Loads {@code job} onto a standby ferry craft: one machine, its landing ground and route,
     * and the claim its squad lands under. The craft flies it as one sortie, then stands by
     * again.
     */
    public static void loadMachine(ShuttleMission mission, MachineDrop job) {
        mission.standby = false;
        mission.machineDropId = job.id();
        mission.payload = ArmorDropPayload.INSTANCE;
        mission.armorDrop = new ArmorLoad[]{job.load()};
        mission.vehicleDeployment = null;
        mission.mechDeployments = null;
        mission.mechVariants = null;
        mission.mechVariant = null;
        // Threat pictures read these to know a carrier holds a mech or a vehicle.
        if (job.load().isVehicle()) {
            mission.vehicleDeployment = job.load().vehicle();
        } else {
            MechDeploymentSpec mech = job.load().mech();
            mission.mechDeployments = new MechDeploymentSpec[]{mech};
            mission.mechVariants = new MechVariant[]{mech.variant()};
            mission.mechVariant = mech.variant();
        }
        mission.marinesRemaining = 1;
        mission.deboardedThisSortie = 0;
        mission.marineLoadout = null;
        mission.squadId = Squad.NO_SQUAD;
        mission.commandClaim = job.claim();
        mission.arrivalGroupId = -1;
        mission.expectedArrivalStrength = 0;
        mission.lzX = job.lzX();
        mission.lzY = job.lzY();
        mission.entryX = job.route()[0];
        mission.entryY = job.route()[1];
        mission.exitX = job.route()[2];
        mission.exitY = job.route()[3];
        mission.unloadStalledFor = 0f;
    }
}

package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirSteeringSystem;
import com.dillon.starsectormarines.battle.air.SteeringMode;
import com.dillon.starsectormarines.battle.drone.Drone;
import com.dillon.starsectormarines.battle.drone.DroneLook;
import com.dillon.starsectormarines.battle.drone.DroneSeparation;
import com.dillon.starsectormarines.battle.drone.SortieFlight;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretAim;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.Warship;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runs every tender over the battle: launches and recovers their drones, flies
 * them to a site, has their builders work it, and stands the finished structure
 * up.
 *
 * <p>The hull itself is flown by {@code WarshipSystem}; a tender asks it for a
 * station like any scripted ship. The defence drones are ordinary
 * {@code DRONE_PATROL} units whose squad is anchored to the tender, so the shipped
 * swarm action screens and engages around it unchanged. The builders are the same
 * drone, unarmed and steered here, because what they do (fly to a point, orbit
 * it, come home) has no use for the swarm's target search.
 *
 * <p>Serial: it spawns and removes units, so it runs in the serial part of the
 * tick beside the other deployable services and never from the parallel unit
 * dispatch.
 */
public final class TenderSystem {

    /** How far short of a site, in cells, the tender holds station, so the builders visibly fly out to it. */
    static final float STANDOFF_CELLS = 5f;
    /** Within this of its station, and slower than {@link #ARRIVE_SPEED}, the tender is on station. */
    static final float ARRIVE_CELLS = 2.5f;
    static final float ARRIVE_SPEED = 1.0f;
    /**
     * A piloted tender does not station itself: its builders launch once it is within this many
     * cells of the site, and the work stops (builders home) while the pilot is farther than
     * {@link #PILOT_LEASH_CELLS}.
     */
    public static final float PILOT_REACH_CELLS = 16f;
    /** How far a piloted tender may be from a working site before its builders are called home. */
    public static final float PILOT_LEASH_CELLS = 26f;
    /** A builder this close to the site is working it. */
    static final float WORK_RADIUS = 3.2f;
    /**
     * Hostile units the tender's side knows of ({@link TenderThreat}) this close to a site
     * stop the work on it and send the builders home. Wider than any rifle's reach (a DMR is 32), because a builder that
     * waits to be shot at from outside the old radius is a builder lost for
     * nothing; the screen's job is to clear the ground so the work can resume.
     */
    static final float THREAT_CELLS = 30f;
    /** Seconds between one defence drone leaving the bay and the next. */
    static final float LAUNCH_INTERVAL = 0.8f;
    /** Seconds between one builder leaving the bay and the next. */
    static final float BUILDER_LAUNCH_INTERVAL = 0.55f;
    /** Seconds between one defence drone being released to fly home and the next. */
    static final float RECALL_GAP = 0.4f;
    /** Seconds between one builder being released to fly home and the next. */
    static final float BUILDER_RECALL_GAP = 0.7f;
    /** Builders closer than this push each other apart, in cells. */
    static final float BUILDER_SEPARATION = 1.5f;
    /** A builder this close to its station turns to face the site. */
    static final float STATION_FACE_CELLS = 0.8f;
    static final float FACE_DEG_PER_SEC = 240f;
    private static final float DESTROY = 1.0e6f;

    private final List<Tender> tenders = new ArrayList<>();
    private float clock;
    private int nextDrone = 1;
    private int nextSite = 1;
    private final TenderCues cues = new TenderCues();

    /** Puts a tender over the battle: its hull is flown by the warship system, its drones launch from the next tick. */
    public void add(BattleSimulation sim, Tender tender) {
        track(tender);
        sim.addWarship(tender.ship());
    }

    /**
     * Lists a tender without registering its hull with a simulation, for a caller that flies the
     * hull itself (a session test, a scene). {@link #add} is the way a battle gets one.
     */
    public void track(Tender tender) {
        tenders.add(tender);
    }

    /** Tenders over the battle, for presentation and evidence. */
    public List<Tender> tenders() {
        return Collections.unmodifiableList(tenders);
    }

    /** The tender whose hull is {@code ship}, or null. */
    public Tender of(Warship ship) {
        for (Tender t : tenders) if (t.ship() == ship) return t;
        return null;
    }

    /** The presentation events the builders have asked for; see {@link TenderCues}. */
    public TenderCues cues() {
        return cues;
    }

    public void tick(float dt, BattleSimulation sim) {
        if (tenders.isEmpty()) return;
        clock += dt;
        cues.prune(sim.getSimTickIndex());
        for (int i = tenders.size() - 1; i >= 0; i--) {
            Tender t = tenders.get(i);
            if (!t.anchorAlive()) {
                collapse(t, sim);
                tenders.remove(i);
                continue;
            }
            tickDefence(t, dt, sim);
            t.pointDefence().tick(dt, sim, t);
            TenderDirector.direct(t, sim, dt);
            tickOrders(t, dt, sim);
            tickBuilders(t, dt, sim);
        }
    }

    /** The hull is gone: its orders fail, and the drones that were out go with it. */
    private void collapse(Tender t, BattleSimulation sim) {
        for (ConstructionOrder o : t.orders()) t.retire(o, "the tender was lost");
        t.sweep();
        boolean wreck = t.ship().destroyed();
        for (Long id : new ArrayList<>(t.defenders)) release(t, id, wreck, sim);
        for (Long id : new ArrayList<>(t.builders)) release(t, id, wreck, sim);
    }

    private void release(Tender t, long id, boolean wreck, BattleSimulation sim) {
        if (sim.resolveUnit(id) != 0L) {
            if (wreck) sim.applyExternalDamage(id, DESTROY, DESTROY);
            else sim.takeOffTheField(id);
        }
        t.lostDefender(id);
        t.lostBuilder(id);
    }

    // ---- defence -----------------------------------------------------------------------------

    private void tickDefence(Tender t, float dt, BattleSimulation sim) {
        TenderFlight flight = t.flight;
        for (Long id : new ArrayList<>(t.defenders)) {
            if (sim.resolveUnit(id) == 0L) t.lostDefender(id);
        }
        flight.scan(sim, t.faction(), t.anchorX(), t.anchorY());
        World world = sim.world();
        flight.publishEscort(t.defenders, world);
        flight.trackBay(t.bayX(), t.bayY(), dt);
        int called = 0;
        for (Long id : new ArrayList<>(t.defenders)) {
            TenderFlight.Sortie sortie = flight.of(id);
            AirBody body = world.kinematics(id);
            if (sortie == null || body == null) continue;
            if (!t.defenceOut()) {
                flight.recall(sortie, clock + SortieFlight.stagger(called++, RECALL_GAP), false);
            } else if (sortie.leg != TenderFlight.Leg.RECOVER && world.hp(id) < world.maxHp(id) * TenderFlight.RETREAT_FRACTION) {
                flight.recall(sortie, clock, true);
            } else {
                flight.resume(sortie);
            }
            if (!flight.step(sortie, body.x, body.y, t.bayX(), t.bayY(), t.bayMouthDegrees(), clock, dt)) continue;
            if (sortie.damaged) flight.repairing(clock);
            sim.takeOffTheField(id);
            t.dockedDefender(id);
        }
        flight.publishEscort(t.defenders, world);
        t.launchClock += dt;
        if (!t.defenceOut() || t.launchClock < LAUNCH_INTERVAL) return;
        if (t.defenceReserve() - flight.underRepair(clock) <= 0) return;
        t.launchClock = 0f;
        Squad squad = defenceSquad(t, sim);
        long id = launch(t, sim, true, squad.id);
        if (id == 0L) return;
        flight.begin(id, true, t.defenders.size());
        t.launchedDefender(id);
        flight.publishEscort(t.defenders, world);
        if (sim.resolveUnit(squad.leaderId) == 0L) squad.leaderId = id;
    }

    private Squad defenceSquad(Tender t, BattleSimulation sim) {
        if (t.defenceSquadId < 0) {
            int id = sim.mintSquad(t.faction(), UnitType.DRONE);
            sim.getSquad(id).droneAnchor = t;
            t.defenceSquadId = id;
        }
        return sim.getSquad(t.defenceSquadId);
    }

    /** Spawns one drone in the bay's mouth: an armed screening drone in {@code squadId}, or an unarmed builder when {@code armed} is false. */
    private long launch(Tender t, BattleSimulation sim, boolean armed, int squadId) {
        float bx = t.bayX();
        float by = t.bayY();
        int[] cell = openCell(sim.getGrid(), bx, by);
        String name = (armed ? "tender-defence-" : "tender-builder-") + nextDrone++;
        EntitySpec spec = Drone.create(name, t.faction(), cell[0], cell[1], 0L, DroneLook.TENDER);
        if (armed) spec.squad(squadId);
        else spec.role(UnitRole.STRUCTURE);
        long id = sim.spawn(spec);
        if (id == 0L) return 0L;
        AirBody body = sim.world().kinematics(id);
        if (body != null) {
            body.teleport(bx, by, t.bayMouthDegrees());
            sim.world().setPos(id, bx, by);
        }
        return id;
    }

    private static int[] openCell(NavigationGrid grid, float x, float y) {
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        for (int r = 0; r <= 4; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (grid.inBounds(cx + dx, cy + dy) && grid.isWalkable(cx + dx, cy + dy)) {
                        return new int[]{cx + dx, cy + dy};
                    }
                }
            }
        }
        return new int[]{Math.max(0, Math.min(grid.getWidth() - 1, cx)),
                Math.max(0, Math.min(grid.getHeight() - 1, cy))};
    }

    // ---- orders ------------------------------------------------------------------------------

    private void tickOrders(Tender t, float dt, BattleSimulation sim) {
        t.blockedSeconds = Math.max(0f, t.blockedSeconds - dt);
        t.sweep();
        ConstructionOrder o = t.current();
        if (o == null) return;
        NavigationGrid grid = sim.getGrid();
        if (!grid.isWalkable((int) Math.floor(o.siteX()), (int) Math.floor(o.siteY()))) {
            t.retire(o, "the site is no longer open ground");
            return;
        }
        switch (o.phase()) {
            case QUEUED -> {
                o.begin();
                if (t.piloted()) {
                    o.standoff(t.ship().x, t.ship().y);
                    return;
                }
                float dx = t.ship().x - o.siteX();
                float dy = t.ship().y - o.siteY();
                float len = (float) Math.hypot(dx, dy);
                float ux = len < 0.01f ? 0f : dx / len;
                float uy = len < 0.01f ? -1f : dy / len;
                o.standoff(o.siteX() + ux * STANDOFF_CELLS, o.siteY() + uy * STANDOFF_CELLS);
                t.ship().flyRoute(null, false);
                t.ship().stationTo(o.standoffX(), o.standoffY());
            }
            case TRANSIT -> {
                Warship ship = t.ship();
                boolean there = t.piloted()
                        ? Math.hypot(ship.x - o.siteX(), ship.y - o.siteY()) <= PILOT_REACH_CELLS
                        : Math.hypot(ship.x - o.standoffX(), ship.y - o.standoffY()) <= ARRIVE_CELLS
                                && ship.speed() <= ARRIVE_SPEED;
                if (there) {
                    o.startWork();
                    t.builderClock = BUILDER_LAUNCH_INTERVAL;
                    deployBuilders(t, 0f, sim);
                }
            }
            case WORKING -> work(t, o, dt, sim);
            default -> { }
        }
    }

    /** Launches the next builder once the gap since the last has passed, so they leave the bay in a line. */
    private void deployBuilders(Tender t, float dt, BattleSimulation sim) {
        t.builderClock += dt;
        if (t.builderReserve() <= 0 || t.builderClock < BUILDER_LAUNCH_INTERVAL) return;
        t.builderClock = 0f;
        long id = launch(t, sim, false, -1);
        if (id == 0L) return;
        t.flight.begin(id, false, t.flight.freeStation(t.builders, t.builderComplement()));
        t.launchedBuilder(id);
    }

    private void work(Tender t, ConstructionOrder o, float dt, BattleSimulation sim) {
        for (Long id : new ArrayList<>(t.builders)) {
            if (sim.resolveUnit(id) == 0L) t.lostBuilder(id);
        }
        World world = sim.world();
        int onSite = 0;
        for (Long id : t.builders) {
            if (Math.hypot(world.x(id) - o.siteX(), world.y(id) - o.siteY()) <= WORK_RADIUS) onSite++;
        }
        if (t.builders.isEmpty() && t.builderReserve() == 0) {
            t.retire(o, "no builders left");
            return;
        }
        int tick = sim.getSimTickIndex();
        List<Long> struck = new ArrayList<>();
        boolean hit = TenderThreat.hits(t.builders, world, t.builderHp, struck);
        for (Long id : struck) cues.stun(t.faction(), world.x(id), world.y(id), id, tick);
        if (hit | t.builderLostSinceLastLook()) {
            if (!t.underFire()) cues.alert(t.faction(), o.siteX(), o.siteY(), tick);
            t.blockedSeconds = TenderThreat.BLOCK_SECONDS;
        }
        o.underFire(t.underFire());
        float[] known = TenderThreat.known(sim, t);
        boolean contested = t.underFire() || TenderThreat.near(known, o.siteX(), o.siteY(), THREAT_CELLS)
                || outOfReach(t, o);
        // Builders stood down for a threat come back out once the ground is clear.
        if (!contested) deployBuilders(t, dt, sim);
        int crew = t.builderComplement();
        boolean done = o.work(dt, onSite, crew, contested);
        if (!done) {
            for (Long id : t.builders) {
                if (t.welding(id, o, world.x(id), world.y(id)) && BuilderStations.torchLit(id, tick)) {
                    cues.weld(o, t.faction(), tick);
                    break;
                }
            }
            return;
        }
        cues.complete(t.faction(), o.siteX(), o.siteY(), tick);
        String name = "tender-turret-" + nextSite++;
        sim.spawn(MapTurret.create(name, t.faction(), o.option().structureId(),
                (int) Math.floor(o.siteX()), (int) Math.floor(o.siteY())));
    }

    /** Whether a pilot has flown the tender too far from {@code o}'s site for its builders to keep working. */
    public static boolean outOfReach(Tender t, ConstructionOrder o) {
        return t.piloted() && Math.hypot(t.ship().x - o.siteX(), t.ship().y - o.siteY()) > PILOT_LEASH_CELLS;
    }


    // ---- builders ----------------------------------------------------------------------------

    /**
     * Steers every builder through its sortie: out through the bay's mouth, to its own
     * station on the site's ring while there is work, and, when the work stops, home in
     * the order it left, each docking only on arrival.
     */
    private void tickBuilders(Tender t, float dt, BattleSimulation sim) {
        TenderFlight flight = t.flight;
        ConstructionOrder o = t.current();
        boolean working = o != null && o.phase() == ConstructionOrder.Phase.WORKING && !o.contested();
        World world = sim.world();
        List<Long> ids = new ArrayList<>(t.builders);
        int n = ids.size();
        long[] mateIds = new long[n];
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            mateIds[i] = ids.get(i);
            xs[i] = world.x(mateIds[i]);
            ys[i] = world.y(mateIds[i]);
        }
        float towardTender = o == null ? 0f
                : AirBody.facingToward(t.ship().x - o.siteX(), t.ship().y - o.siteY());
        int released = 0;
        for (int i = 0; i < n; i++) {
            long id = ids.get(i);
            if (sim.resolveUnit(id) == 0L) {
                t.lostBuilder(id);
                continue;
            }
            AirBody body = world.kinematics(id);
            TenderFlight.Sortie sortie = flight.of(id);
            if (body == null || sortie == null) continue;
            if (!working) flight.recall(sortie, clock + SortieFlight.stagger(released++, BUILDER_RECALL_GAP), false);
            else flight.resume(sortie);
            if (flight.step(sortie, body.x, body.y, t.bayX(), t.bayY(), t.bayMouthDegrees(), clock, dt)) {
                sim.takeOffTheField(id);
                t.dockedBuilder(id);
                continue;
            }
            float[] push = DroneSeparation.push(id, body.x, body.y, mateIds, xs, ys, n, BUILDER_SEPARATION);
            float[] goal = sortie.goal;
            if (goal != null) {
                AirSteeringSystem.steer(body, goal[0] + push[0], goal[1] + push[1], SteeringMode.CRUISE,
                        Drone.HANDLING, dt);
            } else if (o != null) {
                float[] station = BuilderStations.station(o.siteX(), o.siteY(), towardTender, sortie.slot,
                        t.builderComplement(), clock, sortie.phase);
                AirSteeringSystem.steer(body, station[0] + push[0], station[1] + push[1],
                        SteeringMode.BRAKE_TO_STATION, Drone.HANDLING, dt);
                if (body.distanceTo(station[0], station[1]) < STATION_FACE_CELLS) {
                    float toSite = AirBody.facingToward(o.siteX() - body.x, o.siteY() - body.y);
                    body.facingDegrees = TurretAim.slewToward(body.facingDegrees, toSite, FACE_DEG_PER_SEC * dt);
                }
            } else {
                AirSteeringSystem.steer(body, body.x, body.y, SteeringMode.STATION, Drone.HANDLING, dt);
            }
            world.setPos(id, body.x, body.y);
        }
    }
}

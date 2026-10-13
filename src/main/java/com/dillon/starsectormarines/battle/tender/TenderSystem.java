package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirSteeringSystem;
import com.dillon.starsectormarines.battle.air.SteeringMode;
import com.dillon.starsectormarines.battle.drone.Drone;
import com.dillon.starsectormarines.battle.drone.DroneLook;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
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
    /** A builder this close to the site is working it. */
    static final float WORK_RADIUS = 3.2f;
    /** Radius, in cells, of the ring a builder works on. */
    static final float ORBIT_RADIUS = 1.4f;
    static final float ORBIT_DEG_PER_SEC = 70f;
    /**
     * Hostile units this close to a site stop the work on it and send the builders
     * home. Wider than any rifle's reach (a DMR is 32), because a builder that
     * waits to be shot at from outside the old radius is a builder lost for
     * nothing; the screen's job is to clear the ground so the work can resume.
     */
    static final float THREAT_CELLS = 30f;
    /** A returning drone this close to the bay is in it. */
    static final float DOCK_RADIUS = 1.8f;
    /** Seconds between one defence drone leaving the bay and the next. */
    static final float LAUNCH_INTERVAL = 0.8f;
    private static final float DESTROY = 1.0e6f;

    private final List<Tender> tenders = new ArrayList<>();
    private float clock;
    private int nextDrone = 1;
    private int nextSite = 1;

    /** Puts a tender over the battle: its hull is flown by the warship system, its drones launch from the next tick. */
    public void add(BattleSimulation sim, Tender tender) {
        tenders.add(tender);
        sim.addWarship(tender.ship());
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

    public void tick(float dt, BattleSimulation sim) {
        if (tenders.isEmpty()) return;
        clock += dt;
        for (int i = tenders.size() - 1; i >= 0; i--) {
            Tender t = tenders.get(i);
            if (!t.anchorAlive()) {
                collapse(t, sim);
                tenders.remove(i);
                continue;
            }
            tickDefence(t, dt, sim);
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
        for (Long id : new ArrayList<>(t.defenders)) {
            if (sim.resolveUnit(id) == 0L) t.lostDefender(id);
        }
        if (!t.defenceOut()) {
            for (Long id : new ArrayList<>(t.defenders)) {
                sim.takeOffTheField(id);
                t.dockedDefender(id);
            }
            return;
        }
        t.launchClock += dt;
        if (t.defenceReserve() <= 0 || t.launchClock < LAUNCH_INTERVAL) return;
        t.launchClock = 0f;
        Squad squad = defenceSquad(t, sim);
        long id = launch(t, sim, true, squad.id);
        if (id == 0L) return;
        t.launchedDefender(id);
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

    /** Spawns one drone at the bay: an armed screening drone in {@code squadId}, or an unarmed builder when {@code armed} is false. */
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
            body.teleport(bx, by, t.ship().facingDegrees);
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
                boolean there = Math.hypot(ship.x - o.standoffX(), ship.y - o.standoffY()) <= ARRIVE_CELLS
                        && ship.speed() <= ARRIVE_SPEED;
                if (there) {
                    o.startWork();
                    deployBuilders(t, sim);
                }
            }
            case WORKING -> work(t, o, dt, sim);
            default -> { }
        }
    }

    private void deployBuilders(Tender t, BattleSimulation sim) {
        while (t.builderReserve() > 0) {
            long id = launch(t, sim, false, -1);
            if (id == 0L) return;
            t.launchedBuilder(id);
        }
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
        boolean contested = hostileNear(sim, t.faction(), o.siteX(), o.siteY());
        // Builders stood down for a threat come back out once the ground is clear.
        if (!contested) deployBuilders(t, sim);
        int crew = t.builderComplement();
        if (!o.work(dt, onSite, crew, contested)) return;
        String name = "tender-turret-" + nextSite++;
        sim.spawn(MapTurret.create(name, t.faction(), o.option().structureId(),
                (int) Math.floor(o.siteX()), (int) Math.floor(o.siteY())));
    }

    private static boolean hostileNear(BattleSimulation sim, Faction side, float x, float y) {
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        long[] dense = roster.denseArray();
        float r2 = THREAT_CELLS * THREAT_CELLS;
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long u = dense[i];
            if (!side.hostileTo(roster.identity().faction(u))) continue;
            float dx = world.x(u) - x;
            float dy = world.y(u) - y;
            if (dx * dx + dy * dy <= r2) return true;
        }
        return false;
    }

    // ---- builders ----------------------------------------------------------------------------

    /** Steers every builder: onto the working site's ring while there is work, otherwise home to the bay. */
    private void tickBuilders(Tender t, float dt, BattleSimulation sim) {
        ConstructionOrder o = t.current();
        boolean working = o != null && o.phase() == ConstructionOrder.Phase.WORKING && !o.contested();
        World world = sim.world();
        List<Long> ids = new ArrayList<>(t.builders);
        int n = ids.size();
        for (int i = 0; i < n; i++) {
            long id = ids.get(i);
            if (sim.resolveUnit(id) == 0L) {
                t.lostBuilder(id);
                continue;
            }
            AirBody body = world.kinematics(id);
            if (body == null) continue;
            float gx;
            float gy;
            if (working) {
                double a = Math.toRadians(clock * ORBIT_DEG_PER_SEC + 360f * i / Math.max(1, n));
                gx = o.siteX() + ORBIT_RADIUS * (float) Math.cos(a);
                gy = o.siteY() + ORBIT_RADIUS * (float) Math.sin(a);
            } else {
                gx = t.bayX();
                gy = t.bayY();
                if (body.distanceTo(gx, gy) <= DOCK_RADIUS) {
                    sim.takeOffTheField(id);
                    t.dockedBuilder(id);
                    continue;
                }
            }
            AirSteeringSystem.steer(body, gx, gy, SteeringMode.CRUISE, Drone.HANDLING, dt);
            world.setPos(id, body.x, body.y);
        }
    }
}

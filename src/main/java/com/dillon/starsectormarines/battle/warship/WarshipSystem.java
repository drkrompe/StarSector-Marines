package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Ticks the warships over the battle: each mount picks the nearest enemy it can
 * reach and traverse to, then fires its weapon's burst at that ground, every
 * round a real detonation that lands after the flight time its own speed
 * implies.
 *
 * <p>The shells are not a second damage model. A round becomes an ordinary
 * {@link PendingDetonation}, so splash, line of sight, wall breach, roof loss
 * and friendly fire come from the pipeline that already owns them, at the
 * figures the weapon itself carries. Friendly fire is unconditional, as it is
 * for every detonation.
 */
public final class WarshipSystem {

    /** Penetration by the game's damage type — what armour has to stop. */
    private static final float PENETRATION_KINETIC = 12f;
    private static final float PENETRATION_EXPLOSIVE = 8f;
    private static final float PENETRATION_ENERGY = 10f;
    private static final float PENETRATION_OTHER = 4f;

    private final List<Warship> ships = new ArrayList<>();
    private final List<Warship> shipsView = Collections.unmodifiableList(ships);
    private final List<WarshipShell> shells = new ArrayList<>();
    private final List<WarshipShell> shellsView = Collections.unmodifiableList(shells);
    private final List<WarheadStrike> strikes = new ArrayList<>();
    private final List<WarheadStrike> strikesView = Collections.unmodifiableList(strikes);
    private final Random random = new Random(20261011L);
    private final WarshipPointDefence pointDefence = new WarshipPointDefence();

    /** A round this heavy lands as a heavy blast; lighter ones are only a detonation. */
    private static final float HEAVY_IMPACT_DAMAGE = 100f;

    /**
     * Seconds between two fire cues from one mount. A Devastator fires twelve
     * rounds a second and a cue per round would be static, so a burst is heard
     * as one report per this interval; a single shot always sounds.
     */
    private static final float FIRE_CUE_GAP_SECONDS = 0.3f;
    /** Seconds between two landing cues for one weapon, for the same reason. */
    private static final float IMPACT_CUE_GAP_SECONDS = 0.2f;
    /** A carrier splits no nearer than this share of its way to the ground, so a close target still gives its warheads room to fan. */
    private static final float SPLIT_MIN_SHARE = 0.35f;
    /** Warheads detonate over this extra window after their travel, so a sheaf walks across the ground rather than landing as one. */
    private static final float WARHEAD_STAGGER_SECONDS = 0.8f;

    private final List<WarshipSoundCue> cues = new ArrayList<>();
    private final List<WarshipSoundCue> cuesView = Collections.unmodifiableList(cues);
    private final Map<Object, Float> lastCueAt = new IdentityHashMap<>();
    private final Set<Warship.Mount> litBeams = Collections.newSetFromMap(new IdentityHashMap<>());
    private float clock;

    /** Sounds asked for since the last {@link #beginFrame()}, for whoever presents the battle. */
    public List<WarshipSoundCue> cuesThisFrame() {
        return cuesView;
    }

    /** Forgets last frame's cues; called once per simulation advance so a paused caller does not replay them. */
    public void beginFrame() {
        cues.clear();
    }

    private boolean cueAllowed(Object key, float gap) {
        Float last = lastCueAt.get(key);
        if (last != null && clock - last < gap) return false;
        lastCueAt.put(key, clock);
        return true;
    }

    private void cue(WarshipSoundCue.Kind kind, String soundId, float x, float y) {
        if (soundId != null) cues.add(new WarshipSoundCue(kind, soundId, x, y, 1f));
    }

    /** Puts a ship over the battle. */
    public void add(Warship ship) {
        ships.add(ship);
    }

    public List<Warship> ships() {
        return shipsView;
    }

    /** The ships' point defence, for presentation and evidence. */
    public WarshipPointDefence pointDefence() {
        return pointDefence;
    }

    /** Rounds in flight, for presentation and evidence. */
    public List<WarshipShell> shells() {
        return shellsView;
    }

    /** Warheads a carrier has released that have not yet detonated, for presentation and evidence. */
    public List<WarheadStrike> strikes() {
        return strikesView;
    }

    /** A turret fires once its barrel is this close to the bearing of what it has chosen. */
    private static final float ALIGNED_DEGREES = 2f;
    /** A beam this strong, in damage per second, is a lance that cuts ground; weaker beams are anti-ship and silent here. */
    private static final float LANCE_MIN_DPS = 500f;
    private static final float LANCE_TELEGRAPH_SECONDS = 1.5f;
    private static final float LANCE_PENETRATION = 10f;
    private static final int LANCE_WALL_DAMAGE = 100;
    private static final float LANCE_MIN_HALF_WIDTH = 1.2f;
    private static final float LANCE_MIN_SWEEP_RATE = 8f;
    private static final float LANCE_MAX_HALF_ANGLE = 1.2f;
    private static final float LANCE_REST_SECONDS = 2f;

    public void tick(float dt, BattleSimulation sim) {
        clock += dt;
        landShells(dt, sim);
        landStrikes(dt, sim);
        ships.removeIf(Warship::destroyed);
        for (Warship ship : ships) {
            depart(dt, sim, ship);
            direct(dt, sim, ship);
            ship.fluxPool().update(dt);
            moveHull(dt, ship);
            turnHull(dt, ship);
            for (Warship.Mount mount : ship.mounts()) {
                if (mount.pointDefence()) {
                    pointDefence.tickMount(dt, sim, ship, mount, shells);
                } else {
                    tickMount(dt, sim, ship, mount);
                }
            }
        }
        ships.removeIf(Warship::departed);
        pointDefence.ageMarks(dt);
    }

    /**
     * Counts a bounded ship's station time down and, when it is spent, sends it
     * away: it hovers off the map along the line from the map's centre through
     * where it stands (its nose direction if it is at the centre) at its own speed,
     * and is removed once it has cleared the edge or, failing that, after
     * {@link Warship#DEPARTURE_LIMIT_SECONDS}.
     */
    private static void depart(float dt, BattleSimulation sim, Warship ship) {
        if (ship.spendStationTime(dt) && !ship.departing()) {
            ship.beginDeparture();
            float w = sim.getGrid().getWidth();
            float h = sim.getGrid().getHeight();
            float dx = ship.x - w * 0.5f;
            float dy = ship.y - h * 0.5f;
            float len = (float) Math.hypot(dx, dy);
            if (len < 0.01f) {
                double nose = Math.toRadians(ship.facingDegrees + 90f);
                dx = (float) Math.cos(nose);
                dy = (float) Math.sin(nose);
                len = 1f;
            }
            float reach = (float) Math.hypot(w, h) + ship.lengthCells;
            ship.stationTo(ship.x + dx / len * reach, ship.y + dy / len * reach);
        }
        if (!ship.departing()) return;
        ship.addDepartingSeconds(dt);
        float margin = ship.lengthCells * 0.5f;
        boolean offMap = ship.x < -margin || ship.y < -margin
                || ship.x > sim.getGrid().getWidth() + margin || ship.y > sim.getGrid().getHeight() + margin;
        if (offMap || ship.departingSeconds() > Warship.DEPARTURE_LIMIT_SECONDS) ship.markDeparted();
    }

    /** How long a directed ship fires on ground that holds no hostile before it chooses again. */
    private static final float EMPTY_AREA_SECONDS = 2f;
    private static final float RETRY_SECONDS = 1f;
    /** A ship that has moved this far since choosing its heading chooses it again. */
    private static final float REHEADING_DISTANCE = 3f;
    /** A scripted flight counts a waypoint as passed within this many cells, and flies on without braking. */
    private static final float ROUTE_PASS_RADIUS = 3f;

    /**
     * A directed ship holds up to its limit of areas at once. Each is dropped once
     * it has held no hostile for a few seconds; a free slot is filled, densest
     * ground first, as soon as one is dropped and otherwise looked for once a
     * second. A ship under a manual order holds only that ground until it is empty.
     */
    private void direct(float dt, BattleSimulation sim, Warship ship) {
        if (!ship.isDirected()) return;
        List<Warship.Held> current = ship.held();
        List<Warship.Held> kept = new ArrayList<>(current.size());
        for (Warship.Held h : current) {
            int count = hostilesWithin(sim, ship, h.area);
            if (count > 0) {
                h.emptySeconds = 0f;
                h.weight = count;
            } else {
                h.emptySeconds += dt;
            }
            if (h.emptySeconds < EMPTY_AREA_SECONDS) kept.add(h);
        }
        boolean dropped = kept.size() != current.size();
        if (dropped) ship.setDirectedAreas(kept);
        if (ship.manualOrder() || kept.size() >= ship.directorMaxAreas()) {
            ship.resortByWeight();
            return;
        }
        // With ground to spare it looks again once a second, not every tick.
        ship.retrySeconds += dt;
        if (!dropped && ship.retrySeconds < RETRY_SECONDS) {
            ship.resortByWeight();
            return;
        }
        ship.retrySeconds = 0f;
        List<WarshipOrder.FireOnArea> avoid = new ArrayList<>(ship.areas());
        List<Warship.Held> fresh = WarshipDirector.fill(sim, ship, ships, avoid, ship.directorMaxAreas() - kept.size());
        if (!fresh.isEmpty()) {
            kept.addAll(fresh);
            ship.setDirectedAreas(kept);
        }
        ship.resortByWeight();
    }

    private static int hostilesWithin(BattleSimulation sim, Warship ship, WarshipOrder.FireOnArea area) {
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        long[] dense = roster.denseArray();
        int count = 0;
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long u = dense[i];
            if (ship.faction.hostileTo(roster.identity().faction(u)) && area.contains(world.x(u), world.y(u))) {
                count++;
            }
        }
        return count;
    }

    /**
     * Hovers the hull toward its station at its own speed: it accelerates, cruises
     * and brakes in time to stop on the spot rather than drifting through it.
     */
    private static void moveHull(float dt, Warship ship) {
        if (!ship.hasStation()) return;
        ship.advanceRoute(ROUTE_PASS_RADIUS);
        float dx = ship.stationX() - ship.x;
        float dy = ship.stationY() - ship.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float speed = ship.isPassingThrough() ? ship.maxSpeed
                : Math.min(ship.maxSpeed, (float) Math.sqrt(2f * ship.deceleration * dist));
        float wantX = dist > 0f ? dx / dist * speed : 0f;
        float wantY = dist > 0f ? dy / dist * speed : 0f;
        float ax = wantX - ship.velocityX;
        float ay = wantY - ship.velocityY;
        float change = (float) Math.sqrt(ax * ax + ay * ay);
        float allowed = ship.acceleration * dt;
        if (change > allowed && change > 0f) {
            ax *= allowed / change;
            ay *= allowed / change;
        }
        ship.velocityX += ax;
        ship.velocityY += ay;
        float stepX = ship.velocityX * dt;
        float stepY = ship.velocityY * dt;
        if (dist <= (float) Math.sqrt(stepX * stepX + stepY * stepY) && speed < allowed * 2f) {
            ship.x = ship.stationX();
            ship.y = ship.stationY();
            ship.velocityX = 0f;
            ship.velocityY = 0f;
        } else {
            ship.x += stepX;
            ship.y += stepY;
        }
    }

    /**
     * Turns the hull toward the heading its order wants, at the hull's own rate:
     * it accelerates toward the turn, cruises at its maximum and brakes in time
     * to stop on the heading rather than swinging through it.
     */
    private static void turnHull(float dt, Warship ship) {
        List<Warship.Held> held = ship.held();
        boolean bring = !held.isEmpty() && held.get(0).area.bringGunsToBear();
        if (bring && (!ship.headingChosen() || Math.hypot(ship.x - ship.headingChosenAtX,
                        ship.y - ship.headingChosenAtY) > REHEADING_DISTANCE)) {
            int k = held.size();
            float[] tx = new float[k];
            float[] ty = new float[k];
            float[] weight = new float[k];
            for (int i = 0; i < k; i++) {
                tx[i] = held.get(i).area.x();
                ty[i] = held.get(i).area.y();
                weight[i] = Math.max(1, held.get(i).weight);
            }
            ship.desiredHeading = WarshipFacing.bestHeading(ship, tx, ty, weight);
            ship.markHeadingChosen();
        } else if (ship.isPassingThrough() && !bring
                && Math.hypot(ship.velocityX, ship.velocityY) > 0.05f) {
            // Flying a route with no instruction to bring guns to bear, it points along its travel.
            ship.desiredHeading = AirBody.facingToward(ship.velocityX, ship.velocityY);
        }
        float error = WarshipFacing.wrap(ship.desiredHeading - ship.facingDegrees);
        float wanted = Math.signum(error)
                * Math.min(ship.maxTurnDegPerSec, (float) Math.sqrt(2f * ship.turnAccelDegPerSec2 * Math.abs(error)));
        float step = ship.turnAccelDegPerSec2 * dt;
        ship.turnVelocity += Math.max(-step, Math.min(step, wanted - ship.turnVelocity));
        float move = ship.turnVelocity * dt;
        if (Math.abs(error) <= Math.abs(move) && Math.abs(ship.turnVelocity) < step * 2f) {
            ship.facingDegrees = ship.desiredHeading;
            ship.turnVelocity = 0f;
        } else {
            ship.facingDegrees = WarshipFacing.wrap(ship.facingDegrees + move);
        }
    }

    /**
     * Lets a landing detonation hurt any hostile hull whose footprint its blast
     * reaches, through the hull's armour rule. A ship's own side never hurts it.
     * A hull taken to nothing goes up in a heavy blast and is removed from the
     * battle on the next tick, so it stops firing at once.
     */
    public void onDetonation(PendingDetonation det, BattleSimulation sim) {
        for (Warship ship : ships) {
            if (ship.destroyed() || !ship.faction.hostileTo(det.shooterFaction)) continue;
            if (ship.distanceTo(det.endpointX, det.endpointY) > det.aoeRadius) continue;
            ship.takeDamage(det.damage, det.penetration);
            if (ship.destroyed()) {
                sim.spawnHeavyImpact(ship.x, ship.y, Math.max(6f, ship.lengthCells * 0.5f));
            }
        }
    }

    /** Advances every round in flight; one that arrives lands as a detonation and, if heavy, a heavy blast. */
    private void landShells(float dt, BattleSimulation sim) {
        for (int i = shells.size() - 1; i >= 0; i--) {
            WarshipShell shell = shells.get(i);
            if (shell.intercepted) {
                shells.remove(i);
                continue;
            }
            if (!shell.advance(dt)) continue;
            shells.remove(i);
            if (shell.landing() == null) {
                release(shell);
                continue;
            }
            sim.detonateNow(shell.landing());
            if (cueAllowed(shell.weapon, IMPACT_CUE_GAP_SECONDS)) {
                cue(WarshipSoundCue.Kind.IMPACT, shell.weapon.impactSoundId(), shell.toX, shell.toY);
            }
            if (shell.weapon.damagePerShot() >= HEAVY_IMPACT_DAMAGE) {
                sim.spawnHeavyImpact(shell.toX, shell.toY, shell.blastCells, shell.weapon.explosionColor());
            }
        }
    }

    /** A carrier has reached its split point: its warheads leave it, each already committed to its own landing. */
    private void release(WarshipShell shell) {
        float rx = shell.path().landX();
        float ry = shell.path().landY();
        for (WarshipShell.Warhead warhead : shell.warheads()) {
            strikes.add(new WarheadStrike(shell.weapon, shell.faction, rx, ry, warhead));
        }
    }

    /** Counts released warheads down; one that arrives detonates as an ordinary detonation and, if heavy, a heavy blast. */
    private void landStrikes(float dt, BattleSimulation sim) {
        for (int i = strikes.size() - 1; i >= 0; i--) {
            WarheadStrike strike = strikes.get(i);
            if (!strike.advance(dt)) continue;
            strikes.remove(i);
            PendingDetonation det = strike.detonation();
            sim.detonateNow(det);
            if (cueAllowed(strike.weapon, IMPACT_CUE_GAP_SECONDS)) {
                cue(WarshipSoundCue.Kind.IMPACT, strike.weapon.impactSoundId(), strike.aimX, strike.aimY);
            }
            if (det.damage >= HEAVY_IMPACT_DAMAGE) {
                sim.spawnHeavyImpact(strike.aimX, strike.aimY, strike.visualCells, strike.weapon.explosionColor());
            }
        }
    }

    private void tickMount(float dt, BattleSimulation sim, Warship ship, Warship.Mount m) {
        m.secondsSinceFired += dt;
        m.cooldown -= dt;
        VanillaWeapon w = m.weapon;
        if (w.beam()) {
            tickBeam(dt, sim, ship, m);
            return;
        }
        if (ship.overloaded()) {
            // An overloaded ship is silent: the burst under way is abandoned where it stands.
            m.burstRemaining = 0;
            m.aimed = false;
            traverse(dt, ship, m, ship.facingDegrees + m.slot.angleDegrees());
            return;
        }
        if (m.burstRemaining > 0) {
            traverse(dt, ship, m, AirBody.facingToward(m.aimX - ship.mountX(m), m.aimY - ship.mountY(m)));
            m.burstTimer -= dt;
            if (m.burstTimer <= 0f) {
                if (!ship.hasOrdnance(m)) {
                    // Out of rounds mid-burst: the mount falls silent where it stands.
                    m.burstRemaining = 0;
                    m.aimed = false;
                    return;
                }
                fire(sim, ship, m);
                m.burstRemaining--;
                m.burstTimer = w.burstDelaySeconds();
                if (m.burstRemaining == 0) {
                    m.cooldown = Math.max(w.cooldownSeconds(), 0.05f);
                    m.aimed = false;
                }
            }
            return;
        }
        float rest = ship.facingDegrees + m.slot.angleDegrees();
        // A choice is kept until the mount has fired on it, so a turret can finish
        // swinging; it is dropped if its area was dropped or the hull has turned it out of arc.
        if (m.aimed && (!ship.holdsGround(m.aimX, m.aimY) || !Warship.bears(
                m, ship.facingDegrees, ship.mountX(m), ship.mountY(m), m.aimX, m.aimY))) {
            m.aimed = false;
        }
        if (!m.aimed && !ship.areas().isEmpty()) m.aimed = pickTarget(sim, ship, m) != null;
        if (!m.aimed) {
            traverse(dt, ship, m, rest);
            return;
        }
        float bearing = AirBody.facingToward(m.aimX - ship.mountX(m), m.aimY - ship.mountY(m));
        traverse(dt, ship, m, bearing);
        if (m.cooldown > 0f) return;
        if (Math.abs(WarshipFacing.wrap(bearing - m.headingDegrees)) > ALIGNED_DEGREES) return;
        if (!ship.hasOrdnance(m)) {
            m.aimed = false;
            return;
        }
        // Venting: the barrel stays on its choice and waits for the pool to drain.
        float cost = w.fluxPerShot() * w.burstSize();
        if (!ship.fluxPool().canStart(cost)) return;
        ship.fluxPool().spend(cost);
        m.burstRemaining = w.burstSize();
        m.burstTimer = 0f;
    }

    /**
     * A beam mount with an area to cut lays one sweep across the area's width as
     * seen from the mount, walked at the weapon's own traverse rate and kept inside
     * the slot's arc. Once laid the sweep is committed, like any lance: the
     * barrel follows it but does not re-aim it.
     */
    private void tickBeam(float dt, BattleSimulation sim, Warship ship, Warship.Mount m) {
        VanillaWeapon w = m.weapon;
        if (m.lance != null) {
            if (sim.lanceSweeps().active().contains(m.lance)) {
                m.headingDegrees = (float) Math.toDegrees(m.lance.angle()) - 90f;
                if (m.lance.isBurning()) {
                    m.secondsSinceFired = 0f;
                    if (litBeams.add(m)) {
                        cue(WarshipSoundCue.Kind.BEAM_LIT, w.fireSoundId(), ship.mountX(m), ship.mountY(m));
                    }
                    LanceSweep s = m.lance.sweep;
                    float angle = m.lance.angle();
                    cue(WarshipSoundCue.Kind.BEAM_BURNING, w.loopSoundId(),
                            s.originX() + (float) Math.cos(angle) * s.reach(),
                            s.originY() + (float) Math.sin(angle) * s.reach());
                }
                return;
            }
            litBeams.remove(m);
            m.lance = null;
        }
        float rest = ship.facingDegrees + m.slot.angleDegrees();
        WarshipOrder.FireOnArea area = null;
        if (ship.areas().isEmpty() || m.cooldown > 0f || w.damagePerSecond() < LANCE_MIN_DPS
                || !ship.hasOrdnance(m) || !ship.fluxPool().canStartFire() || (area = pickTarget(sim, ship, m)) == null) {
            traverse(dt, ship, m, rest);
            return;
        }
        float mx = ship.mountX(m);
        float my = ship.mountY(m);
        float dx = area.x() - mx;
        float dy = area.y() - my;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float centre = (float) Math.atan2(dy, dx);
        float half = dist > area.radius()
                ? Math.min(LANCE_MAX_HALF_ANGLE, (float) Math.asin(area.radius() / dist)) : LANCE_MAX_HALF_ANGLE;
        // Kept inside the slot's own arc, measured about the slot's direction.
        float slotWorld = (float) Math.toRadians(rest + 90f);
        float halfArc = (float) Math.toRadians(Math.max(m.slot.arcDegrees(), 1f) * 0.5f);
        float start = Math.max(-halfArc, wrapRadians(centre - half - slotWorld));
        float end = Math.min(halfArc, wrapRadians(centre + half - slotWorld));
        if (end - start < 0.05f) {
            traverse(dt, ship, m, rest);
            return;
        }
        float rate = Math.max(LANCE_MIN_SWEEP_RATE, w.turnRateDegPerSec());
        float burn = Math.max(1f, (float) Math.toDegrees(end - start) / rate);
        if (!ship.fluxPool().canStart(w.fluxPerSecond() * burn)) {
            traverse(dt, ship, m, rest);
            return;
        }
        LanceSweep sweep = new LanceSweep(0L, ship.faction, mx, my,
                slotWorld + start, slotWorld + end,
                WarshipScale.cells(w.rangePx()),
                Math.max(LANCE_MIN_HALF_WIDTH, WarshipScale.cells(w.beamWidthPx())),
                LANCE_TELEGRAPH_SECONDS, burn, w.damagePerSecond(),
                LANCE_PENETRATION, LANCE_WALL_DAMAGE, w.id());
        m.lance = sim.startLanceSweep(sweep);
        ship.fluxPool().spend(w.fluxPerSecond() * burn);
        m.shotsFired++;
        ship.spendOrdnance(m);
        m.cooldown = sweep.totalSeconds() + Math.max(w.cooldownSeconds(), LANCE_REST_SECONDS);
    }

    private static float wrapRadians(float r) {
        float d = (float) (r % (2 * Math.PI));
        if (d > Math.PI) d -= (float) (2 * Math.PI);
        if (d < -Math.PI) d += (float) (2 * Math.PI);
        return d;
    }

    /**
     * Swings the barrel toward {@code bearing} at the weapon's own traverse rate.
     * A fixed mount, a hardpoint or a weapon with no traverse, points where the
     * hull points it.
     */
    static void traverse(float dt, Warship ship, Warship.Mount m, float bearing) {
        float rest = ship.facingDegrees + m.slot.angleDegrees();
        float rate = m.weapon.turnRateDegPerSec();
        if (m.slot.hardpoint() || rate <= 0f) {
            m.headingDegrees = rest;
            return;
        }
        float step = rate * dt;
        float error = WarshipFacing.wrap(bearing - m.headingDegrees);
        m.headingDegrees = Math.abs(error) <= step
                ? bearing : WarshipFacing.wrap(m.headingDegrees + Math.signum(error) * step);
    }

    /**
     * Deals the mount one of the ship's areas and a target in it. Areas are dealt
     * in rotation across every mount, the densest twice per round, so a battery
     * spreads over all the ground it holds rather than all hammering one cluster;
     * an area the mount cannot reach is skipped for the next. Returns the area
     * chosen, with the mount's aim set, or {@code null} when none holds a target it
     * can bear on.
     */
    private WarshipOrder.FireOnArea pickTarget(BattleSimulation sim, Warship ship, Warship.Mount m) {
        List<WarshipOrder.FireOnArea> areas = ship.areas();
        int k = areas.size();
        int cycle = k + 1;
        int start = ship.dealCounter % cycle;
        boolean[] tried = new boolean[k];
        for (int step = 0; step < cycle; step++) {
            int position = (start + step) % cycle;
            int index = position < k ? position : 0;
            if (tried[index]) continue;
            tried[index] = true;
            if (acquire(sim, ship, m, areas.get(index))) {
                ship.dealCounter = start + step + 1;
                return areas.get(index);
            }
        }
        return null;
    }

    /**
     * Chooses, at random among the hostile units inside the ordered area that this
     * mount can reach, the one to fire on. Random rather than nearest so a
     * battery spreads over the ground it was given instead of piling every round
     * on the nearest man.
     */
    private boolean acquire(BattleSimulation sim, Warship ship, Warship.Mount m, WarshipOrder.FireOnArea area) {
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        float mx = ship.mountX(m);
        float my = ship.mountY(m);
        int seen = 0;
        if (area.bombardStructures()) {
            int left = Math.max(0, (int) Math.floor(area.x() - area.radius()));
            int top = Math.max(0, (int) Math.floor(area.y() - area.radius()));
            int right = Math.min(sim.getGrid().getWidth() - 1, (int) Math.ceil(area.x() + area.radius()));
            int bottom = Math.min(sim.getGrid().getHeight() - 1, (int) Math.ceil(area.y() + area.radius()));
            for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++) {
                if (!sim.getTopology().isWall(x, y) || !area.contains(x + .5f, y + .5f)
                        || !Warship.bears(m, ship.facingDegrees, mx, my, x + .5f, y + .5f)) continue;
                if (random.nextInt(++seen) == 0) { m.aimX = x + .5f; m.aimY = y + .5f; }
            }
            return seen > 0;
        }
        long[] dense = roster.denseArray();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long u = dense[i];
            Faction f = roster.identity().faction(u);
            if (!ship.faction.hostileTo(f)) continue;
            float ux = world.x(u);
            float uy = world.y(u);
            if (!area.contains(ux, uy) || !Warship.bears(m, ship.facingDegrees, mx, my, ux, uy)) continue;
            seen++;
            if (random.nextInt(seen) == 0) {
                m.aimX = ux;
                m.aimY = uy;
            }
        }
        return seen > 0;
    }

    private void fire(BattleSimulation sim, Warship ship, Warship.Mount m) {
        VanillaWeapon w = m.weapon;
        m.shotsFired++;
        ship.spendOrdnance(m);
        m.secondsSinceFired = 0f;
        float mx = ship.mountX(m);
        float my = ship.mountY(m);
        if (cueAllowed(m, FIRE_CUE_GAP_SECONDS)) cue(WarshipSoundCue.Kind.FIRE, w.fireSoundId(), mx, my);
        float dx = m.aimX - mx;
        float dy = m.aimY - my;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        // The weapon's own cone, as a lateral miss at the target's range.
        float miss = dist * (float) Math.tan(Math.toRadians(w.spreadDegrees() * 0.5f))
                * (random.nextFloat() - 0.5f) * 2f;
        float nx = dist > 0f ? -dy / dist : 0f;
        float ny = dist > 0f ? dx / dist : 0f;
        float lx = m.aimX + nx * miss;
        float ly = m.aimY + ny * miss;
        // The fireball drawn is the spec's visual figure; what the blast hurts is its damage footprint.
        float visual = Math.max(1f, WarshipScale.cells(w.explosionRadiusPx()));
        float blast = Math.max(1f, WarshipScale.cells(w.damageRadiusPx()));
        MissileFlightSpec flight = w.missile() ? w.flight() : null;
        if (flight == null) {
            float speed = Math.max(1f, WarshipScale.cells(w.projectileSpeedPx()));
            MissilePath path = MissilePath.straight(mx, my, lx, ly, speed);
            shells.add(new WarshipShell(w, ship.faction, mx, my, lx, ly, path, visual,
                    detonation(ship, lx, ly, w.damagePerShot(), penetration(w.damageType()), blast), null));
            return;
        }
        // The round's flight is a pure function of this seed, taken from who fired what, where and
        // when rather than from the shared stream, so a missile never shifts another gun's draws.
        long seed = roundSeed(m.slot.id(), m.shotsFired, ship.faction, lx, ly);
        if (flight.mirv() != null) {
            fireCarrier(sim, ship, w, flight, mx, my, lx, ly, seed);
            return;
        }
        MissilePath path = MissilePath.plan(flight, mx, my, lx, ly, seed);
        shells.add(new WarshipShell(w, ship.faction, mx, my, path.landX(), path.landY(), path, visual,
                detonation(ship, path.landX(), path.landY(), w.damagePerShot(), penetration(w.damageType()), blast),
                null));
    }

    /**
     * A multi-warhead missile: it flies as a carrier to a split point short of the
     * ground it was aimed at, and its warheads, each carrying the spec's own damage
     * and blast, are spread over that ground at random. Every landing is chosen
     * here, at fire time; the carrier's arrival only starts their clocks.
     */
    private void fireCarrier(BattleSimulation sim, Warship ship, VanillaWeapon w, MissileFlightSpec flight,
                             float mx, float my, float lx, float ly, long seed) {
        MissileFlightSpec.Mirv mirv = flight.mirv();
        float dx = lx - mx;
        float dy = ly - my;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float splitRange = WarshipScale.cells(mirv.splitRangePx());
        float along = Math.max(dist - splitRange, SPLIT_MIN_SHARE * dist);
        float ux = dist > 0f ? dx / dist : 0f;
        float uy = dist > 0f ? dy / dist : 1f;
        MissilePath carrier = MissilePath.plan(flight, mx, my, mx + ux * along, my + uy * along, seed);
        float releaseX = carrier.landX();
        float releaseY = carrier.landY();
        float blast = Math.max(1f, WarshipScale.cells(mirv.damageRadiusPx()));
        float visual = Math.max(1f, WarshipScale.cells(mirv.visualRadiusPx()));
        float warheadSpeed = Math.max(1f, WarshipScale.cells(mirv.warheadSpeedPx()));
        float[] aim = WarheadScatter.points(seed, mirv.count(), lx, ly, WarheadScatter.spreadRadius(mirv.count(), blast));
        float maxX = sim.getGrid().getWidth() - 0.5f;
        float maxY = sim.getGrid().getHeight() - 0.5f;
        List<WarshipShell.Warhead> warheads = new ArrayList<>(mirv.count());
        for (int i = 0; i < mirv.count(); i++) {
            float ax = Math.max(0.5f, Math.min(maxX, aim[2 * i]));
            float ay = Math.max(0.5f, Math.min(maxY, aim[2 * i + 1]));
            float travel = (float) Math.hypot(ax - releaseX, ay - releaseY) / warheadSpeed;
            float delay = travel + WARHEAD_STAGGER_SECONDS * MissilePath.unit(seed, 100 + i);
            warheads.add(new WarshipShell.Warhead(
                    detonation(ship, ax, ay, mirv.damage(), penetration(mirv.damageType()), blast),
                    delay, visual, ax, ay));
        }
        shells.add(new WarshipShell(w, ship.faction, mx, my, lx, ly, carrier, visual, null, warheads));
    }

    /** A stateless seed for one round: the mount, how many it has fired, the side and the ground it was aimed at. */
    private static long roundSeed(String slotId, int shotNumber, Faction faction, float x, float y) {
        long h = 0x9E3779B97F4A7C15L;
        h = (h ^ slotId.hashCode()) * 0xBF58476D1CE4E5B9L;
        h = (h ^ shotNumber) * 0x94D049BB133111EBL;
        h = (h ^ faction.ordinal()) * 0xBF58476D1CE4E5B9L;
        h = (h ^ Float.floatToIntBits(x)) * 0x94D049BB133111EBL;
        h = (h ^ Float.floatToIntBits(y)) * 0xBF58476D1CE4E5B9L;
        return h ^ (h >>> 31);
    }

    private static PendingDetonation detonation(Warship ship, float x, float y,
                                                float damage, float penetration, float blastCells) {
        // A heavy round's own blast is the authored aftermath; the generic smoke
        // plume belongs to the lighter ones that have no blast of their own.
        boolean heavy = damage >= HEAVY_IMPACT_DAMAGE;
        return new PendingDetonation(
                0L, x, y, 0f,
                blastCells, damage, penetration,
                Math.round(damage), ship.faction,
                /*aerialDelivery*/ false,
                /*wallDamageRadius*/ blastCells,
                /*spawnDustOnWallBreak*/ true,
                /*friendlyFireImmune*/ false,
                /*authoredAftermath*/ heavy);
    }

    private static float penetration(String damageType) {
        return switch (damageType) {
            case "KINETIC" -> PENETRATION_KINETIC;
            case "HIGH_EXPLOSIVE" -> PENETRATION_EXPLOSIVE;
            case "ENERGY" -> PENETRATION_ENERGY;
            default -> PENETRATION_OTHER;
        };
    }
}

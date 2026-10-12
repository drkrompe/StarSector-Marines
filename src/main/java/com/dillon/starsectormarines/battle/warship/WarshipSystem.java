package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
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
        // Doctrine passes are staggered by add order, so a fleet does not all look at once.
        ship.doctrineClock = DOCTRINE_PERIOD_SECONDS - 0.1f * (ships.size() % 10);
        ships.add(ship);
    }

    /** Seconds between two doctrine passes for one ship. */
    static final float DOCTRINE_PERIOD_SECONDS = 1f;
    /** Cells beyond a hazard's radius, and beyond half the hull, a standing-off ship keeps. */
    private static final float STANDOFF_MARGIN_CELLS = 5f;
    /** Seconds ahead of touchdown a denial round should land, so the first one is already falling when the boat arrives. */
    private static final float DENIAL_LEAD_SECONDS = 0.5f;
    /** Seconds before the first gun may fire at which a zone is taken, so the hull can swing and the turrets traverse onto it. */
    private static final float DENIAL_PREPARE_SECONDS = 10f;

    /**
     * Ground a planetary defence (or any mission-declared threat) makes dangerous
     * to a capital of the factions hostile to {@code owner}. It does no damage:
     * nothing in the ground model hurts a capital yet. It is only a declared
     * input to a posture that keeps its distance.
     */
    public record CapitalHazard(Faction owner, float x, float y, float radius) {}

    private final List<CapitalHazard> hazards = new ArrayList<>();

    public void declareCapitalHazard(Faction owner, float x, float y, float radius) {
        if (!(radius > 0f)) throw new IllegalArgumentException("a hazard needs a radius");
        hazards.add(new CapitalHazard(owner, x, y, radius));
    }

    public void clearCapitalHazards() {
        hazards.clear();
    }

    public List<CapitalHazard> capitalHazards() {
        return Collections.unmodifiableList(hazards);
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
    static final float LANCE_MIN_DPS = 500f;
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
            doctrine(dt, sim, ship);
            depart(dt, sim, ship);
            direct(dt, sim, ship);
            tickShield(dt, sim, ship);
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
        boolean weighted = ship.profile().weighted() && !ship.manualOrder();
        WarshipDirector.Friendlies friendlies = weighted ? WarshipDirector.friendliesOf(sim, ship) : null;
        List<Warship.Held> current = ship.held();
        List<Warship.Held> kept = new ArrayList<>(current.size());
        boolean droppedForFriendlies = false;
        for (Warship.Held h : current) {
            if (h.denial) {
                // A denied landing zone is managed below from the inbound drops, not from who stands on it.
                kept.add(h);
                continue;
            }
            int count = hostilesWithin(sim, ship, h.area);
            if (count > 0) {
                h.emptySeconds = 0f;
                h.weight = count;
            } else {
                h.emptySeconds += dt;
            }
            if (h.emptySeconds >= EMPTY_AREA_SECONDS) continue;
            if (weighted && !ship.dangerClose() && endangersFriendlies(sim, ship, h.area, friendlies)) {
                // Friendlies walked into ground that was clean when it was chosen.
                droppedForFriendlies = true;
                continue;
            }
            kept.add(h);
        }
        boolean denialsChanged = ship.profile().denyLandings() && !ship.manualOrder()
                && updateDenials(sim, ship, kept, friendlies);
        boolean dropped = kept.size() != current.size() || denialsChanged;
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
        int slots = ship.directorMaxAreas() - kept.size();
        List<Warship.Held> fresh;
        if (weighted) {
            WarshipDirector.Selection picked = WarshipDirector.fillWeighted(sim, ship, ships, avoid, slots,
                    friendlies, ObjectiveBias.focus(sim, ship));
            fresh = picked.areas();
            ship.noteFriendlyPass(picked.refusedForFriendlies()
                    || (droppedForFriendlies && fresh.isEmpty() && kept.isEmpty()));
        } else {
            fresh = WarshipDirector.fill(sim, ship, ships, avoid, slots);
            ship.noteFriendlyPass(false);
        }
        if (!fresh.isEmpty()) {
            kept.addAll(fresh);
            ship.setDirectedAreas(kept);
        }
        ship.resortByWeight();
    }

    /** Whether a round aimed at any hostile in {@code area} would reach a friendly within the blast and its margin. */
    private static boolean endangersFriendlies(BattleSimulation sim, Warship ship, WarshipOrder.FireOnArea area,
                                               WarshipDirector.Friendlies friendlies) {
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        long[] dense = roster.denseArray();
        int n = roster.liveCount();
        float[] xs = new float[n];
        float[] ys = new float[n];
        int hostiles = 0;
        for (int i = 0; i < n; i++) {
            long u = dense[i];
            if (!ship.faction.hostileTo(roster.identity().faction(u))) continue;
            float ux = world.x(u);
            float uy = world.y(u);
            if (!area.contains(ux, uy)) continue;
            xs[hostiles] = ux;
            ys[hostiles] = uy;
            hostiles++;
        }
        float danger = WarshipDirector.blastCells(ship, area.radius()) + WarshipDirector.SAFETY_MARGIN_CELLS;
        return WarshipDirector.exposure(xs, ys, hostiles, area.x(), area.y(), area.radius(), friendlies, danger) > 0f;
    }

    /** Radius of a denied landing zone, as a share of the director's area radius, never under {@link #DENIAL_MIN_RADIUS}. */
    private static final float DENIAL_RADIUS_SHARE = 0.6f;
    private static final float DENIAL_MIN_RADIUS = 3f;
    private static final int DENIAL_AIM_ATTEMPTS = 6;

    /**
     * Keeps the ship's denied landing zones equal to the inbound hostile drops
     * worth firing on: the most valuable ones first (people and machines per
     * second to touchdown), each from the moment a round fired now would land at
     * touchdown until the boat has put everyone on the ground, and none that is
     * out of reach or has a friendly in the blast and its margin. {@code kept}
     * is rewritten in place; returns whether the set of denied zones changed.
     */
    private boolean updateDenials(BattleSimulation sim, Warship ship, List<Warship.Held> kept,
                                  WarshipDirector.Friendlies friendlies) {
        float reach = WarshipDirector.reach(ship);
        List<AirThreatPicture.InboundDrop> drops = new ArrayList<>(
                AirThreatPicture.take(sim, ship, ships).drops());
        drops.sort((a, b) -> {
            int byValue = Float.compare(b.value(), a.value());
            return byValue != 0 ? byValue : Long.compare(a.craftId(), b.craftId());
        });
        float radius = Math.max(DENIAL_MIN_RADIUS, ship.directorRadius() * DENIAL_RADIUS_SHARE);
        float danger = WarshipDirector.blastCells(ship, radius) + WarshipDirector.SAFETY_MARGIN_CELLS;
        List<Warship.Held> wanted = new ArrayList<>();
        int room = ship.directorMaxAreas();
        for (AirThreatPicture.InboundDrop drop : drops) {
            if (wanted.size() >= room) break;
            float gap = (float) Math.hypot(drop.lzX() - ship.x, drop.lzY() - ship.y);
            if (gap > reach) continue;
            float slowest = slowestFall(ship, drop.lzX(), drop.lzY());
            if (slowest < 0f || drop.etaSeconds() > slowest + DENIAL_LEAD_SECONDS + DENIAL_PREPARE_SECONDS) continue;
            if (!ship.dangerClose() && friendlies != null && WarshipDirector.exposure(
                    new float[]{drop.lzX()}, new float[]{drop.lzY()}, 1, drop.lzX(), drop.lzY(), radius,
                    friendlies, danger) > 0f) {
                continue;
            }
            Warship.Held existing = null;
            for (Warship.Held h : kept) {
                if (h.denial && h.area.x() == drop.lzX() && h.area.y() == drop.lzY()) existing = h;
            }
            if (existing == null) {
                existing = new Warship.Held(new WarshipOrder.FireOnArea(drop.lzX(), drop.lzY(), radius,
                        ship.directorBringsGunsToBear()), Math.max(1, drop.marines()));
                existing.denial = true;
            }
            existing.weight = Math.max(1, drop.marines());
            existing.denialEta = drop.etaSeconds();
            wanted.add(existing);
        }
        List<Warship.Held> before = new ArrayList<>();
        for (Warship.Held h : kept) if (h.denial) before.add(h);
        boolean same = before.size() == wanted.size() && before.containsAll(wanted);
        kept.removeIf(h -> h.denial);
        kept.addAll(0, wanted);
        return !same;
    }

    /**
     * The longest fall time, in seconds, among the shell mounts that can reach
     * {@code (lx, ly)}: the lead a zone's window must open with so every gun's
     * first round can be in the air by touchdown. {@code -1} when none reaches.
     */
    private static float slowestFall(Warship ship, float lx, float ly) {
        float slowest = -1f;
        for (Warship.Mount m : ship.mounts()) {
            if (m.pointDefence() || m.weapon.beam()) continue;
            float dist = (float) Math.hypot(lx - ship.mountX(m), ly - ship.mountY(m));
            if (dist > WarshipScale.cells(m.weapon.rangePx())) continue;
            slowest = Math.max(slowest, dist / Math.max(1f, WarshipScale.cells(m.weapon.projectileSpeedPx())));
        }
        return slowest;
    }

    /**
     * Runs the ship's doctrine once a second: reads the threat picture, asks the
     * doctrine for a posture (unless a mission pinned one), takes it, and keeps
     * the station clear of declared capital hazards while the posture asks for
     * standoff.
     */
    private void doctrine(float dt, BattleSimulation sim, Warship ship) {
        ship.ageDoctrine(dt);
        ship.doctrineClock += dt;
        if (ship.doctrineClock < DOCTRINE_PERIOD_SECONDS) return;
        float elapsed = ship.doctrineClock;
        ship.doctrineClock = 0f;
        WarshipDoctrine doctrine = ship.doctrine();
        if (ship.pinnedPosture() == null && doctrine != null) {
            AirThreatPicture picture = AirThreatPicture.take(sim, ship, ships);
            PostureInputs inputs = new PostureInputs(ship.hullFraction(), ship.fluxFraction(), ship.overloaded(),
                    ship.hasShield() && !ship.shieldUp(), picture.score(), picture.describe(),
                    picture.ownAir(), ship.stance());
            WarshipDoctrine.Decision decision = doctrine.assess(inputs, elapsed);
            ship.setPosture(decision.posture(), decision.reason());
        }
        if (ship.profile().standoff()) {
            float[] station = standoffStation(ship);
            if (station != null) ship.standoffTo(station[0], station[1]);
        } else {
            ship.endStandoff();
        }
    }

    /** Where to hover to be clear of every hazard hostile to the ship, or {@code null} when the station already is. */
    private float[] standoffStation(Warship ship) {
        float sx = ship.hasStation() ? ship.stationX() : ship.x;
        float sy = ship.hasStation() ? ship.stationY() : ship.y;
        boolean moved = false;
        float clear = STANDOFF_MARGIN_CELLS + ship.lengthCells * 0.5f;
        for (int pass = 0; pass < 3; pass++) {
            boolean pushed = false;
            for (CapitalHazard hazard : hazards) {
                if (!hazard.owner().hostileTo(ship.faction)) continue;
                float dx = sx - hazard.x();
                float dy = sy - hazard.y();
                float len = (float) Math.hypot(dx, dy);
                float need = hazard.radius() + clear;
                if (len >= need) continue;
                if (len < 0.01f) {
                    dx = 1f;
                    dy = 0f;
                    len = 1f;
                }
                sx = hazard.x() + dx / len * need;
                sy = hazard.y() + dy / len * need;
                pushed = true;
                moved = true;
            }
            if (!pushed) break;
        }
        return moved ? new float[]{sx, sy} : null;
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
            float gap = ship.distanceTo(det.endpointX, det.endpointY);
            if (ship.hasShield() && gap <= det.aoeRadius + WarshipShield.THREAT_MARGIN_CELLS
                    && threatens(ship, det.damage, det.penetration, det.endpointX, det.endpointY)) {
                // A landing this close is itself the warning: the shield rises for the next one.
                ship.shieldModel().noteThreat();
            }
            if (gap > det.aoeRadius) continue;
            if (ship.shieldCovers(det.endpointX, det.endpointY)) {
                // The shield takes the hit as flux; the hull takes nothing.
                ship.shieldModel().absorb(det.damage, ship.fluxPool());
                continue;
            }
            ship.takeDamage(det.damage, det.penetration);
            if (ship.destroyed()) {
                sim.spawnHeavyImpact(ship.x, ship.y, Math.max(6f, ship.lengthCells * 0.5f));
            }
        }
    }

    /** Whether a round of this strength landing at {@code (px, py)} would hurt the hull and comes from inside the shield's arc. */
    private static boolean threatens(Warship ship, float damage, float penetration, float px, float py) {
        return Warship.effectiveDamage(damage, penetration, ship.armour) > 0f && ship.arcCovers(px, py);
    }

    /**
     * Advances the ship's shield. An automatic shield rises when a hostile round
     * that would hurt the hull is about to land near it from inside the arc: a
     * detonation in the queue, a projectile in flight or a hostile warship's shell.
     */
    private void tickShield(float dt, BattleSimulation sim, Warship ship) {
        boolean threat = ship.hasShield() && ship.shieldAuto() && sensesThreat(sim, ship);
        ship.shieldModel().update(dt, ship.fluxPool(), threat);
    }

    private boolean sensesThreat(BattleSimulation sim, Warship ship) {
        for (PendingDetonation d : sim.getInflightDetonations()) {
            if (d == null || !ship.faction.hostileTo(d.shooterFaction)) continue;
            if (nearHull(ship, d.endpointX, d.endpointY, d.aoeRadius)
                    && threatens(ship, d.damage, d.penetration, d.endpointX, d.endpointY)) return true;
        }
        for (Projectile p : sim.getActiveProjectiles()) {
            PendingDetonation d = p.onArrival;
            if (p.intercepted || d == null || !ship.faction.hostileTo(p.shooterFaction)) continue;
            if (nearHull(ship, p.toX, p.toY, d.aoeRadius)
                    && threatens(ship, d.damage, d.penetration, p.toX, p.toY)) return true;
        }
        for (WarshipShell s : shells) {
            if (s.intercepted || !ship.faction.hostileTo(s.faction)) continue;
            PendingDetonation d = s.landing();
            if (nearHull(ship, s.toX, s.toY, s.blastCells)
                    && threatens(ship, d.damage, d.penetration, s.toX, s.toY)) return true;
        }
        return false;
    }

    private static boolean nearHull(Warship ship, float px, float py, float blast) {
        return ship.distanceTo(px, py) <= blast + WarshipShield.THREAT_MARGIN_CELLS;
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
        if (m.burstRemaining > 0 && beyondOpportunity(ship, m)) {
            // The hull has drawn away from what this burst was aimed at: it falls silent rather than fire past the posture's range.
            m.burstRemaining = 0;
            m.aimed = false;
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
                m, ship.facingDegrees, ship.mountX(m), ship.mountY(m), m.aimX, m.aimY,
                ship.groundRangeMultiplier(m.aimX, m.aimY)) || beyondOpportunity(ship, m))) {
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
        if (denialNotYet(ship, m)) return;
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
     * Whether the mount is aimed at a denied landing zone whose boat is still too
     * far from touchdown for a round fired now to land with it: the barrel stays
     * on the zone and waits, so the first round falls as the boat settles.
     */
    private static boolean denialNotYet(Warship ship, Warship.Mount m) {
        Warship.Held zone = ship.denialHeldAt(m.aimX, m.aimY);
        if (zone == null) return false;
        float fall = (float) Math.hypot(m.aimX - ship.mountX(m), m.aimY - ship.mountY(m))
                / Math.max(1f, WarshipScale.cells(m.weapon.projectileSpeedPx()));
        return zone.denialEta > fall + DENIAL_LEAD_SECONDS;
    }

    /**
     * Whether the mount's aim has drifted past the posture's opportunity range
     * (the hull moved, or the posture changed). Never true for a manual order
     * or when the posture sets no range.
     */
    private static boolean beyondOpportunity(Warship ship, Warship.Mount m) {
        float range = ship.opportunityRange();
        if (range == Float.POSITIVE_INFINITY || ship.manualOrder()) return false;
        return Math.hypot(m.aimX - ship.mountX(m), m.aimY - ship.mountY(m)) > range;
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
        Warship.Held denial = ship.denialHeld(area);
        if (denial != null) {
            // A landing zone nobody stands on yet: the mount aims at open ground inside the zone; tickMount
            // holds the trigger until a round fired now would land as the boat touches down.
            if (m.weapon.beam()) return false;
            for (int attempt = 0; attempt < DENIAL_AIM_ATTEMPTS; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                float r = area.radius() * (float) Math.sqrt(random.nextDouble());
                float px = area.x() + (float) Math.cos(angle) * r;
                float py = area.y() + (float) Math.sin(angle) * r;
                if (Warship.bears(m, ship.facingDegrees, mx, my, px, py)) {
                    m.aimX = px;
                    m.aimY = py;
                    return true;
                }
            }
            return false;
        }
        // A posture's opportunity range and battery limit directed fire; a manual order is never limited.
        if (!ship.manualOrder() && !ship.mayFireAtGround(m)) return false;
        float opportunity = ship.manualOrder() ? Float.POSITIVE_INFINITY : ship.opportunityRange();
        if (area.bombardStructures()) {
            int left = Math.max(0, (int) Math.floor(area.x() - area.radius()));
            int top = Math.max(0, (int) Math.floor(area.y() - area.radius()));
            int right = Math.min(sim.getGrid().getWidth() - 1, (int) Math.ceil(area.x() + area.radius()));
            int bottom = Math.min(sim.getGrid().getHeight() - 1, (int) Math.ceil(area.y() + area.radius()));
            for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++) {
                if (!sim.getTopology().isWall(x, y) || !area.contains(x + .5f, y + .5f)
                        || !Warship.bears(m, ship.facingDegrees, mx, my, x + .5f, y + .5f, Warship.STRUCTURAL_RANGE_MULTIPLIER)
                        || Math.hypot(x + .5f - mx, y + .5f - my) > opportunity) continue;
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
            if (!area.contains(ux, uy) || !Warship.bears(m, ship.facingDegrees, mx, my, ux, uy)
                    || Math.hypot(ux - mx, uy - my) > opportunity) continue;
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

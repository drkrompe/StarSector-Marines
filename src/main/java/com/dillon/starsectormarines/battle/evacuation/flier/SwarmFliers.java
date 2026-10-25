package com.dillon.starsectormarines.battle.evacuation.flier;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.combat.StaggerService;
import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.evacuation.BruteChargeSystem;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretRole;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The battle's Harrier packs: releases them, flies them once a tick in pack order, lands their strikes, and
 * lets the fallen fall ({@link HarrierFall}) before laying their bodies where they land.
 *
 * <p>Serial, after the swarm's ground packs, and deterministic: packs are visited in id order, members in slot
 * order, and nothing here draws a random number. A Harrier is a roster unit (it is shot, takes damage and dies as
 * any body does) whose {@code SWARM_FLIER} role has no per-unit behaviour: everything it does is decided here, by
 * its pack ({@link HarrierPack}).
 */
public final class SwarmFliers {

    /** Ticks between prey re-picks while a pack loiters. */
    static final int REPICK_TICKS = 15;
    /** A friend inside this of a body on foot keeps it from counting as alone. */
    static final float ALONE_RADIUS = 5f;


    /** Something a pack did, kept for evidence. */
    public record Event(int tick, Kind kind, int pack, long harrier, long target) {
        public enum Kind { COMMIT, STRIKE, LIFT, PULL_UP_DONE, SPLIT, ESCORT_LOST, LANDED }
    }

    private final List<HarrierPack> packs = new ArrayList<>();
    private final List<HarrierFall> falls = new ArrayList<>();
    private final List<HarrierLift> lifts = new ArrayList<>();
    private final List<Stain> stains = new ArrayList<>();
    /** The share of strikes on a marine that lift it ({@link HarrierRules#LIFT_CHANCE}); a review raises it. */
    float liftChance = HarrierRules.LIFT_CHANCE;
    private final List<Event> events = new ArrayList<>();
    private final List<HarrierTargeting.Candidate> candidates = new ArrayList<>();
    private final List<float[]> airGuns = new ArrayList<>();
    private int nextPackId = 1;
    private int released;

    public List<HarrierPack> packs() { return Collections.unmodifiableList(packs); }

    /** How many Harriers the battle has released, in every pack. */
    public int released() { return released; }
    public List<HarrierFall> falls() { return Collections.unmodifiableList(falls); }
    public List<HarrierLift> lifts() { return Collections.unmodifiableList(lifts); }

    /**
     * Blood a bite left on the ground, at ({@code x},{@code y}) along the stroke ({@code facing}), from {@code tick}.
     * Drawn as a spatter thrown along the stroke that spreads a little and then fades
     * ({@link HarrierRules#STAIN_SECONDS}). Presentation only.
     */
    public record Stain(float x, float y, float facing, int tick) {}

    /** The stains still on the ground. */
    public List<Stain> stains() { return Collections.unmodifiableList(stains); }

    private void stain(float x, float y, float dx, float dy, int tick) {
        if (stains.size() >= HarrierRules.MAX_STAINS) stains.remove(0);
        stains.add(new Stain(x, y, (float) Math.toDegrees(Math.atan2(dy, dx)), tick));
    }

    /** The throw {@code body} is in the air on, or {@code null} when it is on the ground. */
    public HarrierLift lift(long body) {
        for (HarrierLift l : lifts) if (l.body == body) return l;
        return null;
    }

    /** Whether a killed Harrier is still falling: its body is not on the ground yet, and is not drawn there. */
    public boolean falling(long harrier) {
        for (HarrierFall f : falls) if (f.id == harrier) return true;
        return false;
    }
    public List<Event> events() { return Collections.unmodifiableList(events); }

    /** The pack a live Harrier flies with, or {@code null}. */
    public HarrierPack packOf(long harrier) {
        for (HarrierPack p : packs) for (HarrierFlight m : p.members) if (m.id == harrier) return p;
        return null;
    }

    /** A live Harrier's flight, or {@code null}. */
    public HarrierFlight flight(long harrier) {
        for (HarrierPack p : packs) for (HarrierFlight m : p.members) if (m.id == harrier) return m;
        return null;
    }

    /**
     * Whether the side the swarm fights has anti-air on the map: an anti-air emplacement, or a hull with a mount
     * that engages air, on the marines' side or an ally's. A pack may only be released over a map that has it
     * ({@link #releaseUnderCover}), so the flier is never a threat the player has nothing to answer with.
     */
    public static boolean hasAirCover(BattleSimulation sim) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            if (!preySide(sim.identity().faction(u)) || !sim.turretState().isTurret(u)) continue;
            if (sim.turretState().structure(u).targetRole == TurretRole.AA) return true;
        }
        ConvoyService convoy = sim.getRoster().convoy();
        for (int i = 0, n = sim.bodies().bodyCount(); i < n; i++) {
            long b = sim.bodies().bodyAt(i);
            if (!convoy.isVehicle(b) || !preySide(convoy.faction(b))) continue;
            StructureDef mount = convoy.vehicleType(b).turretStructure();
            if (mount != null && mount.mount.engagesAir) return true;
        }
        return false;
    }

    /**
     * {@link #release} for a mission: refuses a map with no anti-air on the side the pack hunts
     * ({@link #hasAirCover}). A scene's control may release without it; a mission may not.
     */
    public HarrierPack releaseUnderCover(BattleSimulation sim, HarrierLook look, float x, float y, int count,
                                         long escortId) {
        return hatchUnderCover(sim, look, escortId, x, y, x, y, count, escortId);
    }

    /**
     * {@link #hatch} for a mission: refuses a map with no anti-air on the side the pack hunts ({@link #hasAirCover}).
     * With emergence off ({@code -Dbattle.swarm.emergence=false}) the pack appears whole on its ring instead.
     */
    public HarrierPack hatchUnderCover(BattleSimulation sim, HarrierLook look, long source, float fromX, float fromY,
                                       float anchorX, float anchorY, int count, long escortId) {
        if (!hasAirCover(sim)) {
            throw new IllegalStateException("A Harrier pack needs anti-air on the map to answer it");
        }
        if (!sim.swarmEmergence().enabled()) return release(sim, look, anchorX, anchorY, count, escortId);
        return hatch(sim, look, source, fromX, fromY, anchorX, anchorY, count, escortId);
    }

    /**
     * Hatches a pack of {@code count} (at most {@link HarrierRules#MAX_PACK}) out of {@code source} (a brood spire's
     * crown, a Matriarch's back, a bore; 0 for a place) at ({@code fromX}, {@code fromY}), to loiter at
     * ({@code anchorX}, {@code anchorY}): one Harrier climbs out now and the rest one by one, each
     * {@link HarrierRules#HATCH_MIN_SECONDS} to {@link HarrierRules#HATCH_MAX_SECONDS} after the last, low and
     * clustered, and gather on the ring (`colony-breach-nouns.md`, Emergence). The pack commits only once the last is
     * out. A source that dies stops the hatch; a source that moves (the Matriarch) carries the hatch with it.
     */
    public HarrierPack hatch(BattleSimulation sim, HarrierLook look, long source, float fromX, float fromY,
                             float anchorX, float anchorY, int count, long escortId) {
        HarrierPack pack = new HarrierPack(nextPackId++, anchorX, anchorY);
        pack.escortId = escortId;
        pack.unhatched = Math.max(1, Math.min(HarrierRules.MAX_PACK, count));
        pack.hatchSource = source;
        pack.hatchX = fromX;
        pack.hatchY = fromY;
        pack.hatchLook = look;
        pack.nextHatchTick = sim.getSimTickIndex();
        packs.add(pack);
        hatchDue(sim, pack, sim.getSimTickIndex());
        return pack;
    }

    /**
     * Puts a patrol of {@code count} up off a brood spire ({@code spire}, its crown at ({@code x}, {@code y})) to fly
     * {@code route} ({@link HarrierPatrol#route}) and land back on it, hatching from the crown as any spire's pack
     * does, under the same law ({@link #hatchUnderCover}).
     */
    public HarrierPack patrolUnderCover(BattleSimulation sim, HarrierLook look, long spire, float x, float y,
                                        float[] route, int count) {
        HarrierPack pack = hatchUnderCover(sim, look, spire, x, y, x, y, count, 0L);
        if (!sim.swarmEmergence().enabled()) risingFrom(sim, pack, x, y);
        pack.patrol = new HarrierPatrol(route, x, y, Math.max(1, Math.min(HarrierRules.MAX_PACK, count)));
        return pack;
    }

    /** Lets the next Harrier of a hatching pack out when its turn has come. */
    private void hatchDue(BattleSimulation sim, HarrierPack pack, int tick) {
        if (pack.unhatched <= 0) return;
        World world = sim.world();
        if (pack.hatchSource != 0L) {
            if (sim.resolveUnit(pack.hatchSource) == 0L || !world.isAlive(pack.hatchSource)) {
                pack.unhatched = 0;
                return;
            }
            pack.hatchX = world.x(pack.hatchSource);
            pack.hatchY = world.y(pack.hatchSource);
        }
        if (tick < pack.nextHatchTick) return;
        int index = pack.hatched++;
        long h = (pack.id * 0x9E3779B97F4A7C15L + index) * 0xBF58476D1CE4E5B9L;
        double a = 2 * Math.PI * ((h >>> 40) % 1000) / 1000.0;
        float spread = HarrierRules.ROOST_SPREAD * ((h >>> 20) % 1000) / 1000f;
        NavigationGrid grid = sim.getGrid();
        float px = clamp(pack.hatchX + (float) Math.cos(a) * spread, grid.getWidth());
        float py = clamp(pack.hatchY + (float) Math.sin(a) * spread, grid.getHeight());
        HarrierFlight m = spawnMember(sim, pack.hatchLook, px, py, (float) Math.toDegrees(a));
        m.altitude = m.shownAltitude = HarrierRules.ROOST_ALTITUDE;
        m.shownRate = 0f;
        pack.members.add(m);
        pack.reslot();
        pack.unhatched--;
        float gap = HarrierRules.HATCH_MIN_SECONDS
                + (HarrierRules.HATCH_MAX_SECONDS - HarrierRules.HATCH_MIN_SECONDS) * ((h >>> 4) % 1000) / 1000f;
        pack.nextHatchTick = tick + Math.max(1, Math.round(gap / BattleSimulation.TICK_DT));
        // The pod splits as it climbs out: a little dust off it, drifting out with the flier.
        float ux = (float) Math.cos(a), uy = (float) Math.sin(a);
        sim.emitGroundDust(px, py, ux * 0.8f, uy * 0.8f, 0.3f, EffectsService.DUST_WHEEL);
    }

    /**
     * Releases a pack of {@code count} (at most {@link HarrierRules#MAX_PACK}) at ({@code x}, {@code y}),
     * escorting {@code escortId} (its Matriarch) or 0. Members start loitering at height on a ring there.
     */
    public HarrierPack release(BattleSimulation sim, HarrierLook look, float x, float y, int count, long escortId) {
        int n = Math.max(1, Math.min(HarrierRules.MAX_PACK, count));
        HarrierPack pack = new HarrierPack(nextPackId++, x, y);
        pack.escortId = escortId;
        NavigationGrid grid = sim.getGrid();
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            float px = clamp(x + (float) Math.cos(a) * HarrierRules.LOITER_RADIUS, grid.getWidth());
            float py = clamp(y + (float) Math.sin(a) * HarrierRules.LOITER_RADIUS, grid.getHeight());
            HarrierFlight flight = spawnMember(sim, look, px, py, (float) Math.toDegrees(a));
            flight.slot = i;
            pack.members.add(flight);
        }
        packs.add(pack);
        return pack;
    }

    /** One Harrier at ({@code px}, {@code py}) facing {@code facing}, on the roster, at loiter height. */
    private HarrierFlight spawnMember(BattleSimulation sim, HarrierLook look, float px, float py, float facing) {
        AirBody body = new AirBody();
        body.teleport(px, py, facing);
        EntitySpec spec = new EntitySpec("harrier-" + (++released), Faction.DEFENDER, UnitType.SWARM_HARRIER,
                (int) px, (int) py)
                .atPosition(px, py)
                .role(UnitRole.SWARM_FLIER)
                .health(HarrierRules.HP)
                .attackDamage(HarrierRules.BITE_DAMAGE)
                .attackRange(HarrierRules.STRIKE_REACH)
                .moveSpeed(0f)
                .visionRange(HarrierRules.SENSE_RANGE)
                .airLosRadius(HarrierRules.AIR_LOS_RADIUS)
                .body(body);
        long id = sim.spawn(spec);
        HarrierFlight flight = new HarrierFlight(id, body, look);
        // Seeded off the id, so a replay flaps the same and a pack never flaps in step.
        flight.wingClock = (float) ((id * 0x9E3779B97F4A7C15L >>> 40) % 1000) / 1000f;
        return flight;
    }

    /**
     * Sets a pack just released off a roost (a brood spire's crown at {@code (x, y)}) to rise from it: every member
     * clustered within {@link HarrierRules#ROOST_SPREAD} of the crown, low, facing outward, so the pack is seen
     * climbing out and spreading to its loiter ring rather than appearing on it.
     */
    public void risingFrom(BattleSimulation sim, HarrierPack pack, float x, float y) {
        int n = pack.members.size();
        NavigationGrid grid = sim.getGrid();
        for (int i = 0; i < n; i++) {
            HarrierFlight m = pack.members.get(i);
            double a = 2 * Math.PI * i / n;
            float px = clamp(x + (float) Math.cos(a) * HarrierRules.ROOST_SPREAD, grid.getWidth());
            float py = clamp(y + (float) Math.sin(a) * HarrierRules.ROOST_SPREAD, grid.getHeight());
            m.body.teleport(px, py, (float) Math.toDegrees(a));
            m.altitude = m.shownAltitude = HarrierRules.ROOST_ALTITUDE;
            m.shownRate = 0f;
            sim.world().setPos(m.id, px, py);
        }
    }

    /** One tick: every pack's prey, flight and strikes, then any splits. */
    public void tick(BattleSimulation sim) {
        ageFalls(sim);
        ageLifts(sim);
        int stale = sim.getSimTickIndex() - Math.round(HarrierRules.STAIN_SECONDS / BattleSimulation.TICK_DT);
        while (!stains.isEmpty() && stains.get(0).tick() < stale) stains.remove(0);
        if (packs.isEmpty()) return;
        int tick = sim.getSimTickIndex();
        World world = sim.world();
        boolean candidatesBuilt = false;
        List<HarrierPack> splitting = null;
        for (int p = 0; p < packs.size(); p++) {
            HarrierPack pack = packs.get(p);
            hatchDue(sim, pack, tick);
            dropDead(sim, pack);
            if (pack.members.isEmpty()) {
                // Every Harrier out so far may have been shot off its roost while the next is still to come.
                if (pack.unhatched <= 0) packs.remove(p--);
                continue;
            }
            if (pack.escortId != 0L) {
                if (!world.isAlive(pack.escortId)) {
                    events.add(new Event(tick, Event.Kind.ESCORT_LOST, pack.id, 0L, 0L));
                    pack.escortId = 0L;
                    pack.splitDue = false;
                    scatter(pack);
                } else {
                    pack.anchorAt(world.x(pack.escortId), world.y(pack.escortId));
                }
            }
            long prey = pack.target;
            boolean alive = prey != 0L && preyStands(sim, prey);
            if (pack.phase == HarrierPack.Phase.LOITER && (!alive || tick % REPICK_TICKS == pack.id % REPICK_TICKS)) {
                if (!candidatesBuilt) {
                    buildCandidates(sim);
                    candidatesBuilt = true;
                }
                // A patrol on its route only spots what comes near; once it hunts it senses as any pack.
                float range = pack.patrol != null && !alive ? HarrierPatrol.SPOT_RANGE : HarrierRules.SENSE_RANGE;
                prey = HarrierTargeting.pick(candidates, pack.centreX(), pack.centreY(), alive ? prey : 0L, range);
                alive = prey != 0L;
            }
            if (alive) pack.prey(prey, world.x(prey), world.y(prey), sim.physicalRadius(prey));
            else pack.prey(0L, 0f, 0f, 0f);
            if (pack.phase == HarrierPack.Phase.DIVE) followPrey(sim, pack);
            if (pack.patrol != null && !alive && pack.phase == HarrierPack.Phase.LOITER && pack.unhatched <= 0) {
                pack.patrol.step(BattleSimulation.TICK_DT);
                pack.anchorAt(pack.patrol.x(), pack.patrol.y());
            }
            HarrierPack.Phase before = pack.phase;
            pack.advance(BattleSimulation.TICK_DT, (h, t) -> {
                sim.applyDamage(t, h, HarrierRules.BITE_DAMAGE, HarrierRules.BITE_PENETRATION,
                        HarrierRules.STRIKE_MORALE);
                events.add(new Event(tick, Event.Kind.STRIKE, pack.id, h, t));
                strikeLooks(sim, pack, h, t, tick);
            });
            if (before == HarrierPack.Phase.LOITER && pack.phase == HarrierPack.Phase.DIVE) {
                if (!candidatesBuilt) {
                    buildCandidates(sim);
                    candidatesBuilt = true;
                }
                spread(sim, pack);
                events.add(new Event(tick, Event.Kind.COMMIT, pack.id, 0L, pack.target));
            } else if (before == HarrierPack.Phase.PULL_UP && pack.phase == HarrierPack.Phase.LOITER) {
                events.add(new Event(tick, Event.Kind.PULL_UP_DONE, pack.id, 0L, pack.target));
            }
            for (HarrierFlight m : pack.landedNow) {
                // Back on its hooks: off the field, no body, and the spire's roost holds it again.
                sim.takeOffTheField(m.id);
                events.add(new Event(tick, Event.Kind.LANDED, pack.id, m.id, 0L));
            }
            pack.landedNow.clear();
            NavigationGrid grid = sim.getGrid();
            for (HarrierFlight m : pack.members) {
                m.body.x = clamp(m.body.x, grid.getWidth());
                m.body.y = clamp(m.body.y, grid.getHeight());
                m.body.previousAltitude = m.altitude * HarrierRules.LOITER_HEIGHT_CELLS;
                world.setPos(m.id, m.body.x, m.body.y);
            }
            if (pack.shouldSplit()) {
                if (splitting == null) splitting = new ArrayList<>();
                splitting.add(pack);
            }
        }
        if (splitting != null) {
            for (HarrierPack pack : splitting) {
                List<HarrierFlight> half = pack.takeSplit();
                adopt(half, pack.escortId);
                events.add(new Event(tick, Event.Kind.SPLIT, pack.id, 0L, 0L));
            }
        }
    }

    /** Every member of a pack that lost its escort flies on as a pack of one: the split pack that bleeds a line. */
    private void scatter(HarrierPack pack) {
        while (pack.members.size() > 1) {
            HarrierFlight last = pack.members.remove(pack.members.size() - 1);
            List<HarrierFlight> one = new ArrayList<>();
            one.add(last);
            adopt(one, 0L);
        }
        pack.reslot();
    }

    private void adopt(List<HarrierFlight> members, long escortId) {
        if (members.isEmpty()) return;
        float sx = 0f, sy = 0f;
        for (HarrierFlight m : members) { sx += m.body.x; sy += m.body.y; }
        HarrierPack pack = new HarrierPack(nextPackId++, sx / members.size(), sy / members.size());
        pack.escortId = escortId;
        pack.members.addAll(members);
        pack.reslot();
        packs.add(pack);
    }

    private void dropDead(BattleSimulation sim, HarrierPack pack) {
        boolean lost = false;
        for (int i = pack.members.size() - 1; i >= 0; i--) {
            HarrierFlight m = pack.members.get(i);
            if (sim.world().isAlive(m.id)) continue;
            falls.add(new HarrierFall(m.id, m.look, m.body, m.shownAltitude));
            pack.members.remove(i);
            lost = true;
        }
        if (lost) pack.reslot();
    }

    /**
     * Steps every fall. A Harrier's corpse is made where it was shot, when its death is drained; it is kept off
     * the ground (not drawn, not counted by the body pile) while the fall plays, and laid where the fall lands,
     * at the tumble's last facing, as one of its look's corpse drawings. From then it is a body like any other
     * Rav's: it pools, melts into the creep and is retired by the pile cap on the same rules.
     */
    /**
     * What a strike looks like on the body it landed on: dust kicked off the ground, sparks off its plate and, on
     * anything that bleeds, blood thrown along the stroke. And, rarely, a marine snatched off its feet and thrown
     * ({@link HarrierLift}): only infantry that lived through the claws and is not already in the air.
     */
    private void strikeLooks(BattleSimulation sim, HarrierPack pack, long harrier, long target, int tick) {
        HarrierFlight m = null;
        for (HarrierFlight f : pack.members) if (f.id == harrier) m = f;
        if (m == null) return;
        double rad = Math.toRadians(m.body.facingDegrees + 90f);
        float dx = (float) Math.cos(rad), dy = (float) Math.sin(rad);
        World world = sim.world();
        float tx = world.x(target), ty = world.y(target);
        boolean infantry = BruteChargeSystem.isInfantry(target, sim);
        sim.emitClawStrike(tx, ty, dx, dy, infantry ? 1f : 0f);
        if (infantry) stain(tx + dx * .3f, ty + dy * .3f, dx, dy, tick);
        if (!infantry || !sim.getRoster().isAliveById(target) || lift(target) != null
                || !HarrierLift.rolls(harrier, target, tick, liftChance)) return;
        NavigationGrid grid = sim.getGrid();
        // Thrown on along the dive, as far as open ground allows; a body against a wall is not lifted at all.
        for (float cells = HarrierRules.LIFT_THROW_CELLS; cells >= 1f; cells -= .4f) {
            float lx = tx + dx * cells, ly = ty + dy * cells;
            if (!ManualTerrainMotion.canStand(grid, lx, ly, sim.getRoster().radius(target))) continue;
            lifts.add(new HarrierLift(harrier, target, tx, ty, lx, ly));
            events.add(new Event(tick, Event.Kind.LIFT, pack.id, harrier, target));
            sim.stagger().stagger(target, Math.round(HarrierRules.LIFT_SECONDS / BattleSimulation.TICK_DT) + 1,
                    StaggerService.Cause.KNOCKED_DOWN);
            sim.clearPath(target);
            return;
        }
    }

    /**
     * Carries each thrown marine along its arc, and lands it: the ground hurts it, and it is knocked flat and
     * skids on as a body a Brute threw does ({@code BruteChargeSystem.toss}). One that died in the air drops where
     * it is.
     */
    private void ageLifts(BattleSimulation sim) {
        if (lifts.isEmpty()) return;
        NavigationGrid grid = sim.getGrid();
        for (int i = lifts.size() - 1; i >= 0; i--) {
            HarrierLift l = lifts.get(i);
            if (!sim.getRoster().isAliveById(l.body)) {
                lifts.remove(i);
                continue;
            }
            l.advance(BattleSimulation.TICK_DT);
            sim.world().setPos(l.body, clamp(l.x(), grid.getWidth()), clamp(l.y(), grid.getHeight()));
            if (!l.landed()) continue;
            lifts.remove(i);
            sim.applyDamage(l.body, l.harrier, HarrierRules.LIFT_LANDING_DAMAGE, 0f, HarrierRules.LIFT_LANDING_MORALE);
            float dx = l.x1 - l.x0, dy = l.y1 - l.y0;
            float d = (float) Math.hypot(dx, dy);
            if (d > 1e-4f) sim.bruteCharges().toss(l.harrier, l.body, dx / d, dy / d);
        }
    }

    private void ageFalls(BattleSimulation sim) {
        if (falls.isEmpty()) return;
        BattleComponents c = sim.getBattleComponents();
        EntityWorld world = sim.getEntityWorld();
        NavigationGrid grid = sim.getGrid();
        for (int i = falls.size() - 1; i >= 0; i--) {
            HarrierFall f = falls.get(i);
            f.advance(BattleSimulation.TICK_DT);
            boolean corpse = world.has(f.id, c.CORPSE) && world.has(f.id, c.SPRITE);
            if (!f.landed()) {
                if (corpse) world.setInt(f.id, c.SPRITE, BattleComponents.SPRITE_INDEX, -1);
                continue;
            }
            if (corpse) {
                sim.world().setPos(f.id, clamp(f.x(), grid.getWidth()), clamp(f.y(), grid.getHeight()));
                world.setFloat(f.id, c.CORPSE, BattleComponents.CORPSE_FACING_DEGREES, f.facing());
                world.setInt(f.id, c.CORPSE, BattleComponents.CORPSE_DEATH_TICK, sim.getSimTickIndex());
                world.setInt(f.id, c.SPRITE, BattleComponents.SPRITE_INDEX, f.look.corpseIndex(f.corpseVariant()));
            }
            falls.remove(i);
        }
    }
    /**
     * Gives each member of a pack that has just committed its own prey near the body the pack chose
     * ({@link HarrierTargeting#assign}), in slot order, so the pack spreads over the place it dives on rather than
     * stacking on one body.
     */
    private void spread(BattleSimulation sim, HarrierPack pack) {
        int n = pack.members.size();
        float[] xs = new float[n], ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = pack.members.get(i).body.x;
            ys[i] = pack.members.get(i).body.y;
        }
        long[] picks = HarrierTargeting.assign(candidates, pack.targetX, pack.targetY, xs, ys);
        World world = sim.world();
        for (int i = 0; i < n; i++) {
            HarrierFlight m = pack.members.get(i);
            long prey = picks[i];
            if (prey == 0L || !preyStands(sim, prey)) continue;
            m.prey = prey;
            m.preyX = world.x(prey);
            m.preyY = world.y(prey);
            m.preyRadius = sim.physicalRadius(prey);
        }
    }

    /** Keeps each diver on its own prey as the prey moves; a diver whose prey is gone gives the dive up. */
    private void followPrey(BattleSimulation sim, HarrierPack pack) {
        World world = sim.world();
        for (HarrierFlight m : pack.members) {
            if (m.struck || m.prey == 0L) continue;
            if (!preyStands(sim, m.prey)) {
                m.struck = true;
                continue;
            }
            m.preyX = world.x(m.prey);
            m.preyY = world.y(m.prey);
        }
    }

    private boolean preyStands(BattleSimulation sim, long id) {
        if (sim.getRoster().convoy().isVehicle(id)) return sim.bodies().isTargetable(id);
        return sim.world().isAlive(id) && !sim.isRiding(id);
    }

    /** The bodies a pack may dive on this tick, each with what it is and how many air guns reach it. */
    private void buildCandidates(BattleSimulation sim) {
        candidates.clear();
        airGuns.clear();
        World world = sim.world();
        ConvoyService convoy = sim.getRoster().convoy();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            if (!preySide(sim.identity().faction(u)) || !sim.turretState().isTurret(u)) continue;
            StructureDef s = sim.turretState().structure(u);
            WeaponDef w = s.mount.weapon;
            if (s.targetRole == TurretRole.AA || w != null && w.tracksFliers()) {
                airGuns.add(new float[]{world.x(u), world.y(u), w != null ? w.range : 0f});
            }
        }
        for (int i = 0, n = sim.bodies().bodyCount(); i < n; i++) {
            long b = sim.bodies().bodyAt(i);
            if (!convoy.isVehicle(b) || !preySide(convoy.faction(b)) || !sim.bodies().isTargetable(b)) continue;
            StructureDef mount = convoy.vehicleType(b).turretStructure();
            if (mount != null && (mount.mount.engagesAir || mount.mount.weapon.tracksFliers())) {
                airGuns.add(new float[]{world.x(b), world.y(b), mount.mount.weapon.range});
            }
        }
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            Faction f = sim.identity().faction(u);
            if (!preySide(f) || sim.isRiding(u)) continue;
            UnitType type = sim.identity().type(u);
            if (!type.combatant || type.isDrone() || type.isFlier()) continue;
            HarrierTargeting.Prey prey;
            if (sim.turretState().isTurret(u)) prey = HarrierTargeting.Prey.GUN;
            else if (type.isStatic()) continue;
            else if (type.isMech() || type.isMachineFrame()) prey = HarrierTargeting.Prey.ARMOUR;
            else if (isMedic(sim, u)) prey = HarrierTargeting.Prey.MEDIC;
            else prey = alone(sim, u, f) ? HarrierTargeting.Prey.ISOLATED : HarrierTargeting.Prey.INFANTRY;
            candidates.add(new HarrierTargeting.Candidate(u, world.x(u), world.y(u), prey, cover(world.x(u), world.y(u))));
        }
        for (int i = 0, n = sim.bodies().bodyCount(); i < n; i++) {
            long b = sim.bodies().bodyAt(i);
            if (!convoy.isVehicle(b) || !preySide(convoy.faction(b)) || !sim.bodies().isTargetable(b)) continue;
            VehicleType vt = convoy.vehicleType(b);
            HarrierTargeting.Prey prey = vt.hasTurretWeapon() ? HarrierTargeting.Prey.ARMOUR
                    : HarrierTargeting.Prey.LOGISTICS;
            candidates.add(new HarrierTargeting.Candidate(b, world.x(b), world.y(b), prey, cover(world.x(b), world.y(b))));
        }
    }

    private int cover(float x, float y) {
        int n = 0;
        for (float[] g : airGuns) {
            float dx = g[0] - x, dy = g[1] - y;
            if (dx * dx + dy * dy <= g[2] * g[2]) n++;
        }
        return n;
    }

    private static boolean isMedic(BattleSimulation sim, long u) {
        if (!sim.squad().hasSquad(u)) return false;
        Squad squad = sim.getSquad(sim.squad().squadId(u));
        return squad != null && squad.medic != null && squad.medic.medicId() == u;
    }

    private static boolean alone(BattleSimulation sim, long u, Faction f) {
        World world = sim.world();
        float x = world.x(u), y = world.y(u);
        float r2 = ALONE_RADIUS * ALONE_RADIUS;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long o = sim.liveUnitAt(i);
            if (o == u || sim.identity().faction(o) != f) continue;
            float dx = world.x(o) - x, dy = world.y(o) - y;
            if (dx * dx + dy * dy <= r2) return false;
        }
        return true;
    }

    /** The sides a Harrier hunts: the marines and those fighting beside them. */
    static boolean preySide(Faction f) {
        return f == Faction.MARINE || f == Faction.ALLY;
    }

    private static float clamp(float v, int size) {
        return Math.max(0.5f, Math.min(size - 0.5f, v));
    }
}

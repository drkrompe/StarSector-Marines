package com.dillon.starsectormarines.battle.evacuation.flier;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.component.BattleComponents;
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
        public enum Kind { COMMIT, STRIKE, PULL_UP_DONE, SPLIT, ESCORT_LOST }
    }

    private final List<HarrierPack> packs = new ArrayList<>();
    private final List<HarrierFall> falls = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();
    private final List<HarrierTargeting.Candidate> candidates = new ArrayList<>();
    private final List<float[]> airGuns = new ArrayList<>();
    private int nextPackId = 1;
    private int released;

    public List<HarrierPack> packs() { return Collections.unmodifiableList(packs); }
    public List<HarrierFall> falls() { return Collections.unmodifiableList(falls); }

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
        if (!hasAirCover(sim)) {
            throw new IllegalStateException("A Harrier pack needs anti-air on the map to answer it");
        }
        return release(sim, look, x, y, count, escortId);
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
            AirBody body = new AirBody();
            body.teleport(px, py, (float) Math.toDegrees(a));
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
            flight.slot = i;
            // Seeded off the id, so a replay flaps the same and a pack never flaps in step.
            flight.wingClock = (float) ((id * 0x9E3779B97F4A7C15L >>> 40) % 1000) / 1000f;
            pack.members.add(flight);
        }
        packs.add(pack);
        return pack;
    }

    /** One tick: every pack's prey, flight and strikes, then any splits. */
    public void tick(BattleSimulation sim) {
        ageFalls(sim);
        if (packs.isEmpty()) return;
        int tick = sim.getSimTickIndex();
        World world = sim.world();
        boolean candidatesBuilt = false;
        List<HarrierPack> splitting = null;
        for (int p = 0; p < packs.size(); p++) {
            HarrierPack pack = packs.get(p);
            dropDead(sim, pack);
            if (pack.members.isEmpty()) {
                packs.remove(p--);
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
                prey = HarrierTargeting.pick(candidates, pack.centreX(), pack.centreY(), alive ? prey : 0L);
                alive = prey != 0L;
            }
            if (alive) pack.prey(prey, world.x(prey), world.y(prey), sim.physicalRadius(prey));
            else pack.prey(0L, 0f, 0f, 0f);
            if (pack.phase == HarrierPack.Phase.DIVE) followPrey(sim, pack);
            HarrierPack.Phase before = pack.phase;
            pack.advance(BattleSimulation.TICK_DT, (h, t) -> {
                sim.applyDamage(t, h, HarrierRules.BITE_DAMAGE, HarrierRules.BITE_PENETRATION,
                        HarrierRules.STRIKE_MORALE);
                events.add(new Event(tick, Event.Kind.STRIKE, pack.id, h, t));
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

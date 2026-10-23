package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.combat.StaggerService.Cause;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.HullRam;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.engine.ecs.Query;

import java.util.HashSet;
import java.util.Set;

/**
 * A walking mech rams the other side as a hull does ({@link HullRam}, {@code convoy-nouns.md}): on the first tick a
 * moving mech touches a hostile body, the two exchange one impulse at the mech's closing speed and each is hurt,
 * knocked down or staggered by the velocity change it could not shed. Against another mech both machines' own
 * walking counts; against a creature or a man only the mech's does, as a hull's does. Friends, allies and
 * civilians are shoved by {@link SeparationSystem}, weighed in the same tonnes, and never hurt. Hulls are not
 * here: a mech and a hull meet in {@link VehicleRepulsionSystem}.
 *
 * <p>Serial; runs in the separation slot after the shoves, so it reads the contacts the shoves left.
 */
public final class MechRamSystem {

    /** A mech slower than this is standing, and rams nothing. */
    static final float MOVING_SPEED = .15f;
    /** Gap counted as touching beyond the two bodies' radii, cells. */
    static final float TOUCH = .05f;

    private final UnitRosterService roster;
    private final World world;
    private final EntityWorld entityWorld;
    private final BattleComponents components;
    private final UnitSpatialIndex unitIndex;
    private final Query mechs;
    private final LongBucket scratch = new LongBucket();
    private Set<Long> touching = new HashSet<>(), touched = new HashSet<>();
    private long rams;
    private float damageDealt, damageTaken;

    public MechRamSystem(UnitRosterService roster, UnitSpatialIndex unitIndex) {
        this.roster = roster;
        this.world = roster.world();
        this.entityWorld = roster.entityWorld();
        this.components = roster.components();
        this.unitIndex = unitIndex;
        this.mechs = entityWorld.query(new ComponentType[]{components.MECH_LOADOUT,
                components.MOVEMENT, components.POSITION, components.HEALTH}, null);
    }

    /** Rams a mech landed since the battle began, what they dealt and what the mechs took back; evidence. */
    public long rams() { return rams; }
    public float damageDealt() { return damageDealt; }
    public float damageTaken() { return damageTaken; }

    public void tick() {
        for (ArchetypeTable table : entityWorld.matched(mechs)) {
            for (int row = 0, n = table.rowCount(); row < n; row++) {
                long mech = table.entityAt(row);
                if (roster.isAliveById(mech)) strike(mech);
            }
        }
        Set<Long> swap = touched;
        touched = touching;
        touching = swap;
        touching.clear();
    }

    private void strike(long mech) {
        float vx = velX(mech), vy = velY(mech);
        if (Math.hypot(vx, vy) < MOVING_SPEED) return;
        Faction side = roster.identity().faction(mech);
        if (side == null) return;
        float mx = world.x(mech), my = world.y(mech), radius = roster.radius(mech);
        unitIndex.gather(mx, my, radius + 2f, scratch);
        for (int k = 0, n = scratch.size; k < n; k++) {
            long body = scratch.ids[k];
            if (body == mech || roster.convoy().isVehicle(body) || !roster.isAliveById(body)
                    || !entityWorld.has(body, components.POSITION) || !entityWorld.has(body, components.MOVEMENT)
                    || world.hasKinematics(body)) continue;
            UnitType type = roster.identity().type(body);
            if (type == null || type.isStatic() || type == UnitType.GROUND_VEHICLE
                    || entityWorld.getInt(body, components.ROLE, BattleComponents.ROLE_ORDINAL)
                    == UnitRole.STRUCTURE.ordinal()) continue;
            if (!side.hostileTo(roster.identity().faction(body))) continue;
            float dx = world.x(body) - mx, dy = world.y(body) - my;
            float dist = (float) Math.hypot(dx, dy);
            if (dist > radius + roster.radius(body) + TOUCH || dist < 1e-4f) continue;
            long pair = mech < body ? mech * 0x9E3779B97F4A7C15L ^ body : body * 0x9E3779B97F4A7C15L ^ mech;
            boolean first = touching.add(pair) && !touched.contains(pair);
            if (!first) continue;
            float nx = dx / dist, ny = dy / dist;
            float toward = vx * nx + vy * ny;
            float away = velX(body) * nx + velY(body) * ny;
            // Two machines both drive into the blow; a man or a creature running at the mech adds nothing.
            float closing = type.isMech() ? HullRam.machineClosing(toward, away) : HullRam.closing(toward, away);
            HullRam.Exchange hit = HullRam.exchange(closing, tonnes(mech), tonnes(body));
            if (hit.impulse() <= 0f) continue;
            rams++;
            if (hit.bodyDamage() > 0f) {
                damageDealt += hit.bodyDamage();
                roster.damageService().applyDamage(body, mech, hit.bodyDamage(), HullRam.PENETRATION, 1f);
            }
            if (hit.hullDamage() > 0f) {
                damageTaken += hit.hullDamage();
                roster.damageService().applyDamage(mech, body, hit.hullDamage(), HullRam.PENETRATION, 0f);
            }
            if (hit.bodyDv() >= HullRam.KNOCKDOWN_DV && roster.isAliveById(body)) {
                roster.stagger().stagger(body, ticks(HullRam.KNOCKDOWN_SECONDS),
                        type.isMech() ? Cause.STAGGERED : Cause.KNOCKED_DOWN);
            }
            if (hit.hullDv() >= HullRam.STALL_DV && roster.isAliveById(mech)) {
                roster.stagger().stagger(mech, ticks(HullRam.STALL_SECONDS), Cause.STAGGERED);
            }
            if (!roster.isAliveById(mech)) return;
        }
    }

    private float tonnes(long id) {
        UnitType type = roster.identity().type(id);
        MechLoadoutComponent loadout = type.isMech() ? world.mechLoadout(id) : null;
        return HullRam.bodyTonnes(type, roster.identity().swarmStage(id),
                loadout == null ? 1f : loadout.variant.relativeMass);
    }

    private float velX(long id) {
        return entityWorld.getFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_VEL_X);
    }

    private float velY(long id) {
        return entityWorld.getFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_VEL_Y);
    }

    private static int ticks(float seconds) {
        return Math.max(1, Math.round(seconds / BattleSimulation.TICK_DT));
    }
}

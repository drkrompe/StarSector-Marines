package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.combat.BodyDamageResolver;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import org.junit.jupiter.api.Test;

import java.util.function.LongConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The collapse itself: a kind of body nobody has written yet reaches every
 * consumer by registering, and by registering only.
 *
 * <p>The fixture carrier here is a stand-in for the fourth kind — it is not a
 * chassis and not an aircraft, and nothing in the battle has heard of it. If a
 * future carrier is added the old way, by teaching each consumer what it is,
 * this test is what fails: it never mentions vehicles or aircraft, so it can
 * only pass while the shared surface is genuinely the route.
 */
public class BodyCarrierRegistryTest {

    private static final float RADIUS = 2.75f;
    private static final float HALF_HEIGHT = 1.25f;
    private static final float WEAPON_RANGE = 17f;

    /**
     * A fourth carrier, implemented once. It holds one body, answers the shared
     * questions about it, and records the kill it is told about.
     */
    private static final class SentryPost implements BodyCarrier {
        private final long id;
        private boolean reachable = true;
        private boolean destroyed;

        SentryPost(long id) {
            this.id = id;
        }

        @Override public boolean owns(long candidate) { return candidate == id; }
        @Override public boolean isTargetable(long candidate) { return candidate == id && reachable; }
        @Override public Faction faction(long candidate) { return Faction.DEFENDER; }
        @Override public float velocityX(long candidate) { return 0f; }
        @Override public float velocityY(long candidate) { return 0f; }
        @Override public float targetRadius(long candidate) { return RADIUS; }
        @Override public float hitHalfHeight(long candidate) { return HALF_HEIGHT; }
        @Override public float weaponRange(long candidate) { return WEAPON_RANGE; }
        @Override public void destroy(long candidate) { destroyed = true; }
        @Override public void forEachBody(LongConsumer visitor) { visitor.accept(id); }
    }

    /**
     * Mints a world entity carrying the trio every off-roster body carries and
     * nothing else — no {@code POSITION}, {@code COMBAT}, {@code MOVEMENT} or
     * {@code ROLE}, which is the membership-narrowing the collapse must not
     * undo.
     */
    private static long mintBody(UnitRosterService roster, float x, float y, float hp) {
        BattleComponents c = roster.components();
        long id = roster.allocateAir(new ComponentType[]{
                c.IDENTITY, c.HEALTH, c.ARMOR, c.KINEMATICS});
        roster.entityWorld().setObject(id, c.IDENTITY,
                BattleComponents.IDENTITY_TYPE, UnitType.GROUND_VEHICLE);
        roster.entityWorld().setObject(id, c.IDENTITY,
                BattleComponents.IDENTITY_FACTION, Faction.DEFENDER);
        roster.entityWorld().setObject(id, c.IDENTITY,
                BattleComponents.IDENTITY_NAME, "sentry");
        roster.world().setMaxHp(id, hp);
        roster.world().setHp(id, hp);
        roster.entityWorld().setFloat(id, c.HEALTH,
                BattleComponents.HEALTH_DAMAGE_TAKEN_MULT, 1f);
        roster.world().setMaxArmor(id, 0f);
        roster.world().setArmor(id, 0f);
        roster.world().setArmorRating(id, 0f);
        // Which kinematic body carries the position is the carrier's business
        // and nothing the shared surface asks about; this one just needs to be
        // somewhere.
        AirBody body = new AirBody();
        body.teleport(x, y, 0f);
        roster.world().setKinematics(id, body);
        return id;
    }

    private static boolean contains(LongBucket out, long id) {
        for (int i = 0; i < out.size; i++) if (out.ids[i] == id) return true;
        return false;
    }

    @Test
    public void registeringACarrierIsEverythingANewKindOfBodyHasToDo() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        long sentry = mintBody(roster, 20.5f, 20.5f, 100f);
        SentryPost carrier = new SentryPost(sentry);
        roster.bodies().register(carrier);

        index.rebuild(roster);

        assertEquals(carrier, roster.bodies().carrierOf(sentry),
                "the registry routes the id to the carrier that claimed it");
        assertTrue(roster.bodies().isTargetable(sentry),
                "the liveness gate every held reference and squad belief goes through");
        assertEquals(RADIUS, roster.radius(sentry), 1e-4f,
                "ballistics, blast catch and separation all size the body through here");
        assertEquals(HALF_HEIGHT, roster.hitHalfHeight(sentry), 1e-4f);
        assertEquals(WEAPON_RANGE, roster.threatRange(sentry), 1e-4f,
                "how far it can shoot, without a COMBAT component to read");

        LongBucket out = new LongBucket();
        index.gather(20.5f, 20.5f, 4f, out);
        assertTrue(contains(out, sentry),
                "one admission path, so a new carrier is in the index by construction");
    }

    @Test
    public void aCarrierOutOfReachIsNotAdmittedAndNotResolvable() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        long sentry = mintBody(roster, 20.5f, 20.5f, 100f);
        SentryPost carrier = new SentryPost(sentry);
        roster.bodies().register(carrier);
        carrier.reachable = false;

        index.rebuild(roster);

        assertFalse(roster.bodies().isTargetable(sentry));
        LongBucket out = new LongBucket();
        index.gather(20.5f, 20.5f, 4f, out);
        assertFalse(contains(out, sentry),
                "the reachability gate is the carrier's, not the index's");
    }

    @Test
    public void damageRunsTheSharedLawAndTheCarrierOwnsTheDeath() {
        UnitSpatialIndex index = new UnitSpatialIndex(64, 64);
        UnitRosterService roster = new UnitRosterService(index, null);
        long sentry = mintBody(roster, 20.5f, 20.5f, 30f);
        SentryPost carrier = new SentryPost(sentry);
        roster.bodies().register(carrier);
        BodyDamageResolver resolver = new BodyDamageResolver(roster);

        resolver.resolve(carrier, sentry, 0L, 10f, 0f);
        assertEquals(20f, roster.world().hp(sentry), 1e-4f,
                "one route applies the shared durability law to any body");
        assertFalse(carrier.destroyed);

        resolver.resolve(carrier, sentry, 0L, 40f, 0f);
        assertEquals(0f, roster.world().hp(sentry), 1e-4f);
        assertTrue(carrier.destroyed,
                "common route, per-carrier sink — the carrier decides what dying means");
    }
}

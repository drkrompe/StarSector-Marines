package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises only the resolver's grid, body index, and roster; no battle tick. */
class BallisticPointAimTest {
    private static final float EPS = 1e-4f;
    private static final float RANGE = 14f;
    private static final float VELOCITY = 10f;
    private final NavigationGrid grid = new NavigationGrid(32, 16);
    private final UnitSpatialIndex index = new UnitSpatialIndex(32, 16);
    private final UnitRosterService roster = new UnitRosterService(index, null);
    private final DoodadService doodads = new DoodadService(grid);
    private final BallisticResolver resolver = new BallisticResolver(grid, doodads, index, roster);
    private final BallisticResolver.Source source =
            new BallisticResolver.Source(0L, 2.5f, 5.5f, 0f, Faction.MARINE);

    BallisticPointAimTest() {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 32; x++) grid.setWalkableFloor(x, y);
        }
    }

    @Test
    void emptyGroundNeedsNoTargetAndDoesNotTerminateAtTheCursor() {
        BallisticResolver.Resolution near = point(3.5f, 5.5f, 1f, new QueueRandom(0f, 0f));
        BallisticResolver.Resolution far = point(25.5f, 5.5f, 1f, new QueueRandom(0f, 0f));

        assertEquals(BallisticResolver.StopKind.OVERSHOOT, near.kind());
        assertEquals(23.5f, near.endX(), EPS);
        assertEquals(2.1f, near.flightTime(), EPS);
        assertEquals(near, far);
        assertFalse(near.impacts());
        assertFalse(near.hitIntended());
        assertTrue(near.bodyHits().isEmpty());
    }

    @Test
    void wallStopsBeforeABodyAndDeterminesArrivalTime() {
        grid.setWalkable(6, 5, false);
        spawn(Faction.DEFENDER, 10, 5);
        BallisticResolver.Resolution result = point(10.5f, 5.5f, 1f,
                new QueueRandom(0f, 0f));

        assertEquals(BallisticResolver.StopKind.WALL, result.kind());
        assertEquals(6f, result.endX(), EPS);
        assertEquals(0.35f, result.flightTime(), EPS);
        assertEquals(0L, result.victimId());
    }

    @Test
    void crossedCoverUsesTheSharedCatchBeforeAFartherBody() {
        doodads.addDoodad(new Doodad(8, 5, new TileManifest.TileFrame(0, 0),
                false, Doodad.COVER_MED));
        spawn(Faction.DEFENDER, 10, 5);
        BallisticResolver.Resolution result = point(10.5f, 5.5f, 1f,
                new QueueRandom(0f, 0f, 0f));

        assertEquals(BallisticResolver.StopKind.DOODAD_BLOCK, result.kind());
        assertEquals(8.5f, result.endX(), EPS);
        assertEquals(0L, result.victimId());
    }

    @Test
    void cursorOnAMovingBodyDoesNotSupplyAutomaticLead() {
        long body = spawn(Faction.DEFENDER, 10, 5);
        velocity(body, 0f, 2f);
        BallisticResolver.Resolution result = point(10.5f, 5.5f, 1f,
                new QueueRandom(0f, 0f));

        assertEquals(BallisticResolver.StopKind.OVERSHOOT, result.kind());
        assertEquals(0L, result.victimId());
    }

    @Test
    void movingInterposerCanEnterTheCommittedRay() {
        long body = spawn(Faction.DEFENDER, 8, 7);
        velocity(body, 0f, -10f / 3f);
        BallisticResolver.Resolution result = point(12.5f, 5.5f, 1f,
                new QueueRandom(0f, 0f, 0f));

        assertEquals(BallisticResolver.StopKind.UNIT_HIT, result.kind());
        assertEquals(body, result.victimId());
        assertTrue(result.flightTime() > 0f && result.flightTime() < 0.6f);
        assertFalse(result.hitIntended());
    }

    @Test
    void pointShotsKeepFriendlyCatchProbabilityAndMuzzleProtection() {
        spawn(Faction.MARINE, 3, 5);
        long friendly = spawn(Faction.MARINE, 12, 5);
        BallisticResolver.Resolution caught = point(15.5f, 5.5f, 1f,
                new QueueRandom(0f, 0f, 0.3f));
        BallisticResolver.Resolution passed = point(15.5f, 5.5f, 1f,
                new QueueRandom(0f, 0f, 0.4f));

        assertEquals(friendly, caught.victimId());
        assertTrue(caught.friendlyHit());
        assertEquals(BallisticResolver.StopKind.OVERSHOOT, passed.kind());
    }

    @Test
    void poorAccuracyAndSmokeCanMissABodyUnderTheCursor() {
        long body = spawn(Faction.DEFENDER, 10, 5);
        BallisticResolver.Resolution accurate = point(10.5f, 5.5f, 1f,
                new QueueRandom(0.99f, 0f, 0f));
        BallisticResolver.Resolution poor = point(10.5f, 5.5f, 0f,
                new QueueRandom(0.99f, 0f));
        for (int x = 3; x <= 9; x++) grid.addTransientOpacityAt(grid.index(x, 5));
        BallisticResolver.Resolution smoked = point(10.5f, 5.5f, 1f,
                new QueueRandom(0.99f, 0f));
        BallisticResolver.Resolution nearCursorInSmoke = point(3.5f, 5.5f, 1f,
                new QueueRandom(0.99f, 0f));

        assertEquals(body, accurate.victimId());
        assertEquals(BallisticResolver.StopKind.OVERSHOOT, poor.kind());
        assertEquals(BallisticResolver.StopKind.OVERSHOOT, smoked.kind());
        assertEquals(smoked, nearCursorInSmoke,
                "a nearby cursor cannot evade obscuration down the same firing lane");
        assertTrue(smoked.endY() > accurate.endY());
    }

    @Test
    void explicitMuzzleAndPenetrationRetainOrderedContacts() {
        long ownBody = spawn(Faction.MARINE, 4, 5);
        long first = spawn(Faction.DEFENDER, 8, 5);
        long second = spawn(Faction.DEFENDER, 12, 5);
        BallisticResolver.Source muzzle = new BallisticResolver.Source(
                ownBody, 4.25f, 5.5f, 0f, Faction.DEFENDER);
        BallisticResolver.Resolution result = resolver.resolvePoint(muzzle,
                15.5f, 5.5f, 1f, 0f, VELOCITY, RANGE, 1,
                new QueueRandom(0f, 0f, 0f, 0f));

        assertEquals(second, result.victimId());
        assertEquals(2, result.bodyHits().size());
        assertEquals(first, result.bodyHits().get(0).victimId());
        assertEquals(second, result.bodyHits().get(1).victimId());
        assertEquals((result.endX() - muzzle.x()) / VELOCITY, result.flightTime(), EPS);
        assertFalse(result.hitIntended());
    }

    @Test
    void degenerateAimIsRejectedBeforeAnyRandomDraw() {
        assertThrows(IllegalArgumentException.class,
                () -> point(2.5f, 5.5f, 1f, new QueueRandom()));
        assertThrows(IllegalArgumentException.class,
                () -> point(Float.NaN, 5.5f, 1f, new QueueRandom()));
    }

    private BallisticResolver.Resolution point(float x, float y, float accuracy, Random rng) {
        return resolver.resolvePoint(source, x, y, accuracy, 0f, VELOCITY, RANGE, rng);
    }

    private long spawn(Faction faction, int x, int y) {
        return roster.spawn(new EntitySpec("body", faction, UnitType.MARINE, x, y));
    }

    private void velocity(long id, float x, float y) {
        BattleComponents c = roster.components();
        roster.entityWorld().setFloat(id, c.MOVEMENT, BattleComponents.MOVEMENT_VEL_X, x);
        roster.entityWorld().setFloat(id, c.MOVEMENT, BattleComponents.MOVEMENT_VEL_Y, y);
    }

    private static final class QueueRandom extends Random {
        private final ArrayDeque<Float> values = new ArrayDeque<>();

        QueueRandom(float... values) {
            for (float value : values) this.values.add(value);
        }

        @Override
        public float nextFloat() {
            if (values.isEmpty()) throw new IllegalStateException("QueueRandom exhausted");
            return values.remove();
        }
    }
}

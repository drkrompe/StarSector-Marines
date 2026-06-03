package com.dillon.starsectormarines.engine.ecs;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for the deferred {@link CommandBuffer}: structural changes queued
 * during a {@link Query} walk and applied at {@link EntityWorld#flush()} without
 * corrupting the in-progress iteration (the swap-pop trap), plus apply-time guards
 * (skip add/remove on an already-destroyed entity, no-op double-destroy) and FIFO
 * ordering. Synthetic, game-agnostic components.
 */
public class CommandBufferTest {

    private EntityWorld world;
    private ComponentType POSITION; // INT x
    private ComponentType HEALTH;   // FLOAT hp
    private ComponentType TARGET;   // LONG
    private ComponentType CORPSE;   // tag

    private EntityWorld fresh() {
        EntityWorld w = new EntityWorld();
        POSITION = w.register(0, "Position", FieldKind.INT);
        HEALTH   = w.register(1, "Health", FieldKind.FLOAT);
        TARGET   = w.register(2, "Target", FieldKind.LONG);
        CORPSE   = w.register(3, "Corpse");
        return w;
    }

    @Test
    public void deferredDestroyDuringWalkIsSafe() {
        world = fresh();
        long[] es = new long[6];
        for (int i = 0; i < es.length; i++) {
            es[i] = world.createEntity(POSITION);
            world.setInt(es[i], POSITION, 0, i);
        }

        // Walk the table and queue every even-valued row for destruction mid-walk.
        Query q = world.query(new ComponentType[]{POSITION}, null);
        for (ArchetypeTable t : world.matched(q)) {
            int[] xs = t.ints(POSITION, 0).array();
            for (int r = 0; r < t.rowCount(); r++) {
                if (xs[r] % 2 == 0) world.cmd().destroy(t.entityAt(r));
            }
        }
        assertEquals(6, world.entityCount(), "nothing removed until flush");

        world.flush();

        assertEquals(3, world.entityCount());
        for (int i = 0; i < es.length; i++) {
            assertEquals(i % 2 != 0, world.isAlive(es[i]), "odd survive, even destroyed");
        }
        // Surviving rows still resolve to their original values after the swap-pops.
        List<Integer> survivors = new ArrayList<>();
        for (ArchetypeTable t : world.matched(q)) {
            int[] xs = t.ints(POSITION, 0).array();
            for (int r = 0; r < t.rowCount(); r++) survivors.add(xs[r]);
        }
        survivors.sort(null);
        assertEquals(List.of(1, 3, 5), survivors);
    }

    @Test
    public void deferredDeathTransitionDuringWalk() {
        // The canonical death path: walk live {Position,Health}, and for the dead
        // ones queue remove(Health)+add(Corpse). Flush turns them into corpses that
        // keep their cell, without disturbing the live walk.
        world = fresh();
        long a = world.createEntity(POSITION, HEALTH);
        long b = world.createEntity(POSITION, HEALTH);
        long c = world.createEntity(POSITION, HEALTH);
        world.setInt(a, POSITION, 0, 10); world.setFloat(a, HEALTH, 0, 5f);
        world.setInt(b, POSITION, 0, 20); world.setFloat(b, HEALTH, 0, 0f);   // dead
        world.setInt(c, POSITION, 0, 30); world.setFloat(c, HEALTH, 0, 7f);

        Query live = world.query(new ComponentType[]{POSITION, HEALTH}, null);
        for (ArchetypeTable t : world.matched(live)) {
            float[] hp = t.floats(HEALTH, 0).array();
            for (int r = 0; r < t.rowCount(); r++) {
                if (hp[r] <= 0f) {
                    long e = t.entityAt(r);
                    world.cmd().remove(e, HEALTH).add(e, CORPSE);
                }
            }
        }
        world.flush();

        assertTrue(world.isAlive(b));
        assertFalse(world.has(b, HEALTH));
        assertTrue(world.has(b, CORPSE));
        assertEquals(20, world.getInt(b, POSITION, 0), "corpse keeps its cell");

        assertEquals(2, rows(live), "two still live");
        Query corpses = world.query(new ComponentType[]{POSITION, CORPSE}, null);
        assertEquals(1, rows(corpses));
    }

    @Test
    public void addOnEntityDestroyedEarlierInSameFlushIsSkipped() {
        world = fresh();
        long e = world.createEntity(POSITION);
        world.cmd().destroy(e).add(e, TARGET);   // destroy wins; the add is moot

        world.flush();   // must not throw

        assertFalse(world.isAlive(e));
    }

    @Test
    public void doubleDestroyIsNoOp() {
        world = fresh();
        long e = world.createEntity(POSITION);
        world.cmd().destroy(e).destroy(e);

        world.flush();

        assertFalse(world.isAlive(e));
        assertEquals(0, world.entityCount());
    }

    @Test
    public void flushClearsBufferAndIsIdempotent() {
        world = fresh();
        long keep = world.createEntity(POSITION);
        long drop = world.createEntity(POSITION);
        world.cmd().destroy(drop);

        world.flush();
        assertTrue(world.cmd().isEmpty(), "buffer drained after flush");
        assertEquals(1, world.entityCount());

        world.flush();   // re-flush applies nothing
        assertEquals(1, world.entityCount());
        assertTrue(world.isAlive(keep));
    }

    @Test
    public void createDuringWalkIsImmediateAndSafe() {
        // Creating into the SAME table being walked must not corrupt the walk: the
        // new row is invisible this pass, existing rows still read correctly.
        world = fresh();
        long a = world.createEntity(POSITION);
        long b = world.createEntity(POSITION);
        world.setInt(a, POSITION, 0, 1);
        world.setInt(b, POSITION, 0, 2);

        Query q = world.query(new ComponentType[]{POSITION}, null);
        int visited = 0, sum = 0;
        for (ArchetypeTable t : world.matched(q)) {
            int[] xs = t.ints(POSITION, 0).array();
            int count = t.rowCount();
            for (int r = 0; r < count; r++) {
                sum += xs[r];
                if (r == 0) world.createEntity(POSITION);   // spawn mid-walk
                visited++;
            }
        }
        assertEquals(2, visited, "spawned row not visited this pass");
        assertEquals(3, sum, "existing rows read intact across the spawn");
        assertEquals(3, world.entityCount(), "spawn took effect immediately");
    }

    private int rows(Query q) {
        int n = 0;
        for (ArchetypeTable t : world.matched(q)) n += t.rowCount();
        return n;
    }
}

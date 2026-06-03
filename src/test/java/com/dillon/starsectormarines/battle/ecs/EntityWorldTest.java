package com.dillon.starsectormarines.battle.ecs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engine-layer contract tests for {@link EntityWorld}, exercised with synthetic
 * (game-agnostic) components: structural add/remove moves, swap-and-pop location
 * fixup, query matching/reactivity + exclude masks, presence tags, column
 * iteration, and capacity shrink. No persistence test — marine-ops battles are
 * transient (no save/load), so the world is ephemeral and carries no Serializable
 * contract.
 */
public class EntityWorldTest {

    private EntityWorld world;
    private ComponentType POSITION; // INT x, INT y
    private ComponentType HEALTH;   // FLOAT hp
    private ComponentType TARGET;   // LONG targetId
    private ComponentType NAME;     // OBJECT
    private ComponentType CORPSE;   // tag (zero fields)

    private EntityWorld fresh() {
        EntityWorld w = new EntityWorld();
        POSITION = w.register(0, "Position", FieldKind.INT, FieldKind.INT);
        HEALTH   = w.register(1, "Health", FieldKind.FLOAT);
        TARGET   = w.register(2, "Target", FieldKind.LONG);
        NAME     = w.register(3, "Name", FieldKind.OBJECT);
        CORPSE   = w.register(4, "Corpse");
        return w;
    }

    @Test
    public void createReadWrite() {
        world = fresh();
        long e = world.createEntity(POSITION, HEALTH);
        world.setInt(e, POSITION, 0, 3);
        world.setInt(e, POSITION, 1, 7);
        world.setFloat(e, HEALTH, 0, 42.5f);

        assertTrue(world.isAlive(e));
        assertTrue(world.has(e, POSITION));
        assertTrue(world.has(e, HEALTH));
        assertFalse(world.has(e, TARGET));
        assertEquals(3, world.getInt(e, POSITION, 0));
        assertEquals(7, world.getInt(e, POSITION, 1));
        assertEquals(42.5f, world.getFloat(e, HEALTH, 0));
    }

    @Test
    public void addComponentPreservesSharedDataAndDefaultsNew() {
        world = fresh();
        long e = world.createEntity(POSITION, HEALTH);
        world.setInt(e, POSITION, 0, 5);
        world.setFloat(e, HEALTH, 0, 10f);

        world.addComponent(e, TARGET);

        assertTrue(world.has(e, TARGET));
        assertEquals(5, world.getInt(e, POSITION, 0), "shared field survives the archetype move");
        assertEquals(10f, world.getFloat(e, HEALTH, 0));
        assertEquals(0L, world.getLong(e, TARGET, 0), "new component starts at default");
    }

    @Test
    public void removeComponentDropsItKeepsRest() {
        world = fresh();
        long e = world.createEntity(POSITION, HEALTH, TARGET);
        world.setInt(e, POSITION, 0, 9);

        world.removeComponent(e, HEALTH);

        assertFalse(world.has(e, HEALTH));
        assertTrue(world.has(e, POSITION));
        assertTrue(world.has(e, TARGET));
        assertEquals(9, world.getInt(e, POSITION, 0));
    }

    @Test
    public void deathTransitionKeepsPosition() {
        // live -> corpse: drop Health, add the Corpse tag; Position (cell) survives.
        world = fresh();
        long e = world.createEntity(POSITION, HEALTH);
        world.setInt(e, POSITION, 0, 12);
        world.setInt(e, POSITION, 1, 34);

        world.removeComponent(e, HEALTH);
        world.addComponent(e, CORPSE);

        assertFalse(world.has(e, HEALTH));
        assertTrue(world.has(e, CORPSE));
        assertEquals(12, world.getInt(e, POSITION, 0), "corpse keeps its cell");
        assertEquals(34, world.getInt(e, POSITION, 1));
    }

    @Test
    public void swapPopFixesMovedEntityLocation() {
        world = fresh();
        long a = world.createEntity(POSITION);
        long b = world.createEntity(POSITION);
        long c = world.createEntity(POSITION);
        world.setInt(a, POSITION, 0, 1);
        world.setInt(b, POSITION, 0, 2);
        world.setInt(c, POSITION, 0, 3);

        world.destroy(b);   // tail c swaps into b's row; c must still resolve

        assertFalse(world.isAlive(b));
        assertEquals(1, world.getInt(a, POSITION, 0));
        assertEquals(3, world.getInt(c, POSITION, 0), "tail entity readable after swap-pop");
        assertEquals(2, world.entityCount());
    }

    @Test
    public void objectColumnRoundTripsAndClearsOnReuse() {
        world = fresh();
        long e = world.createEntity(NAME);
        world.setObject(e, NAME, 0, "hello");
        assertEquals("hello", world.getObject(e, NAME, 0));

        world.destroy(e);
        long e2 = world.createEntity(NAME);   // reuses the vacated slot
        assertNull(world.getObject(e2, NAME, 0), "reused slot starts null, no stale ref");
    }

    @Test
    public void tagComponentHasNoColumns() {
        world = fresh();
        long e = world.createEntity(CORPSE);
        assertTrue(world.has(e, CORPSE));
        assertEquals(0, CORPSE.fieldCount());
    }

    @Test
    public void queryReactsToStructuralChange() {
        world = fresh();
        long a = world.createEntity(POSITION, HEALTH);
        world.createEntity(POSITION);   // b: no health
        long b = world.createEntity(POSITION);

        Query liveDamageable = world.query(new ComponentType[]{POSITION, HEALTH}, null);
        assertEquals(1, rows(liveDamageable));

        world.addComponent(b, HEALTH);
        assertEquals(2, rows(liveDamageable));

        world.destroy(a);
        assertEquals(1, rows(liveDamageable));
    }

    @Test
    public void queryExcludeMask() {
        world = fresh();
        world.createEntity(POSITION, HEALTH);             // live
        long corpse = world.createEntity(POSITION, CORPSE);

        Query corpses = world.query(new ComponentType[]{POSITION}, new ComponentType[]{HEALTH});
        int seen = 0;
        long seenId = -1;
        for (ArchetypeTable t : world.matched(corpses)) {
            for (int r = 0; r < t.rowCount(); r++) { seen++; seenId = t.entityAt(r); }
        }
        assertEquals(1, seen);
        assertEquals(corpse, seenId);
    }

    @Test
    public void columnIterationSeesAllRows() {
        world = fresh();
        long e1 = world.createEntity(POSITION);
        long e2 = world.createEntity(POSITION);
        world.setInt(e1, POSITION, 0, 100);
        world.setInt(e2, POSITION, 0, 200);

        Query q = world.query(new ComponentType[]{POSITION}, null);
        int sum = 0;
        for (ArchetypeTable t : world.matched(q)) {
            int[] xs = t.ints(POSITION, 0).array();
            for (int r = 0; r < t.rowCount(); r++) sum += xs[r];
        }
        assertEquals(300, sum);
    }

    @Test
    public void shrinksAfterMassDestroy() {
        world = fresh();
        long[] es = new long[100];
        for (int i = 0; i < es.length; i++) es[i] = world.createEntity(POSITION);
        int grown = world.tableOf(es[0]).capacity();
        assertTrue(grown >= 100);

        for (int i = 1; i < es.length; i++) world.destroy(es[i]);   // leave es[0]

        assertTrue(world.tableOf(es[0]).capacity() < grown, "table trims after mass destroy");
        assertEquals(1, world.entityCount());
        assertTrue(world.isAlive(es[0]));
        assertEquals(0, world.getInt(es[0], POSITION, 0));
    }

    private int rows(Query q) {
        int n = 0;
        for (ArchetypeTable t : world.matched(q)) n += t.rowCount();
        return n;
    }
}

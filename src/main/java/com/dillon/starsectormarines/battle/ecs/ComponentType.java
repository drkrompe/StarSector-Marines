package com.dillon.starsectormarines.battle.ecs;

/**
 * Identity + schema of a component: a stable {@code id} (its bit index in the
 * archetype mask, {@code 0..63}), a name, and the ordered {@link FieldKind}s of
 * its fields. A zero-field component is a pure-presence <b>tag</b> (a mask bit
 * with no columns). Registered in code via {@link EntityWorld#register} — never by
 * reflection. Everything keys off {@link #id} (an {@code int}), never object
 * identity.
 */
public final class ComponentType {

    public final int id;
    public final String name;
    private final FieldKind[] fields;

    ComponentType(int id, String name, FieldKind[] fields) {
        if (id < 0 || id >= 64) throw new IllegalArgumentException("component id out of [0,64): " + id);
        this.id = id;
        this.name = name;
        this.fields = fields.clone();
    }

    /** The archetype-mask bit for this component. */
    public long bit() { return 1L << id; }

    public int fieldCount() { return fields.length; }

    public FieldKind fieldKind(int field) { return fields[field]; }

    @Override public String toString() { return "ComponentType(" + id + ":" + name + ")"; }
}

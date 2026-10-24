package com.dillon.starsectormarines.ops.battleview;

/** Lets a look study outside this package draw the hardpoint capacitors in each motif ({@link TurretFluxCapacitors}). */
public final class CapacitorMotifs {

    private CapacitorMotifs() {}

    /** The motifs by name, lower case. */
    public static String[] names() {
        TurretFluxCapacitors.Motif[] motifs = TurretFluxCapacitors.Motif.values();
        String[] names = new String[motifs.length];
        for (int i = 0; i < motifs.length; i++) names[i] = motifs[i].name().toLowerCase();
        return names;
    }

    /** Draws every hardpoint's capacitors in {@code name}'s motif from now on; returns the one it replaced. */
    public static String use(String name) {
        String was = TurretFluxCapacitors.motif.name().toLowerCase();
        TurretFluxCapacitors.motif = TurretFluxCapacitors.Motif.valueOf(name.toUpperCase());
        return was;
    }
}

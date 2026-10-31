package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * The allied bases' rocket artillery (`heart-nouns.md`, Logistics base): a Locust emplacement in every pit the layout
 * laid at a base ({@link HeartLayout#LOCUST_PIT}), the logistics base's on both forms and each airbase's on Broad Front.
 * Each is a support gun ({@code SupportFire}): it fires for the side on a swarm mass somebody of the side has in
 * sight, never danger close. A base's guns are one place on the heart's stock ({@link HeartGuns}), so a base running
 * low calls an ammunition truck as the flak does. The guns are the military's ({@code ALLY}) and never end the battle.
 */
public final class HeartBaseGuns {

    private HeartBaseGuns() {}

    /** Salvos a base Locust holds full: some ten minutes of steady fire at its ten-second cycle. */
    public static final int LOCUST_SALVOS = 60;
    /** The heart-guns place of a base's Locusts: {@code locusts-<base>}. */
    static final String PLACE = "locusts-";

    /** Seats every laid pit's Locust and stocks it; returns the guns seated. Call once at setup. */
    public static List<Long> man(BattleSimulation sim, LaidLayout layout, HeartGuns guns) {
        List<Long> out = new ArrayList<>();
        if (layout == null) return out;
        for (Laid laid : layout.pieces()) {
            if (!laid.isLaid() || !laid.name().startsWith(HeartLayout.LOCUST_PIT) || laid.cells().size() < 2) continue;
            int[] a = laid.cells().get(0), b = laid.cells().get(1);
            int[] cell = {(a[0] + b[0]) / 2, (a[1] + b[1]) / 2};
            if (!sim.getGrid().inBounds(cell[0], cell[1]) || !sim.getGrid().isWalkable(cell[0], cell[1])) continue;
            long gun = ColonyMilitia.seatEmplacement(sim, cell, "Base Locust " + laid.name().substring(HeartLayout.LOCUST_PIT.length()),
                    TurretCatalogRegistry.LOCUST_STRUCTURE_ID);
            if (gun == 0L) continue;
            String place = place(laid.name());
            guns.place(place, LOCUST_SALVOS, cell).add(place, gun);
            sim.supportFire().enrol(gun);
            out.add(gun);
        }
        return out;
    }

    /** The place a pit's gun stocks under: its base, the pit's name without its number. */
    static String place(String pit) {
        String base = pit.substring(HeartLayout.LOCUST_PIT.length());
        int dash = base.lastIndexOf('-');
        return PLACE + (dash < 0 ? base : base.substring(0, dash));
    }
}

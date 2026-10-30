package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.gen.forest.Undergrowth;
import com.dillon.starsectormarines.battle.world.gen.nature.MineralFormation;
import com.dillon.starsectormarines.battle.world.gen.nature.NaturalPropShape;
import com.dillon.starsectormarines.battle.world.model.CoverPropDamage;
import com.dillon.starsectormarines.battle.world.model.PropDamage;
import com.dillon.starsectormarines.battle.world.model.RockDamage;
import com.dillon.starsectormarines.battle.world.model.TileManifest;

import java.util.Set;

/**
 * What a piece of the environment is made of, as far as a round is concerned: one
 * <b>material rating</b> per family, read against a round's penetration through
 * {@link DurabilityModel#materialEfficiency}. This is the one table; every
 * damageable prop family, shared-edge barrier kind and structural wall is keyed
 * here and nowhere else.
 *
 * <p>The ratings are set against the weapon catalog's penetration. Small arms run
 * from 2 (SMG) through 5 (pulse rifle, squad automatic) and 7 (field rifle) to 11
 * (DMR) and the anti-materiel rifle is 18. So wood and
 * wire fall to anything, sandbags to field rifles slowly and to pulse fire not at all, machinery,
 * timber and stone only from the DMR up, and masonry only to anti-materiel
 * rounds. Blast weapons do their structural damage through their own detonation's
 * authored wall damage, not through this table.
 *
 * <p>A material says nothing about how much a feature can take — that remains the
 * feature's own structure pool, authored with it — only how much of a round's
 * payload reaches that pool.
 */
public enum EnvironmentMaterial {
    /** Crates, storage boxes and furniture. */
    WOOD(1.5f),
    /** Industrial mesh fencing on rails. */
    WIRE(3f),
    /** Steel drum clusters. */
    DRUM(8f),
    /** Filled sacks: sandbag walls, fort sandbags, revetments. */
    SANDBAG(10f),
    /** Generators, consoles, tanks, machine tools. */
    MACHINERY(14f),
    /** Standing trees. */
    TIMBER(16f),
    /** Rocks, boulders and ice formations. */
    STONE(20f),
    /** Structural walls, including a window's sill. */
    MASONRY(22f);

    /** Registered props that are furniture rather than machinery. */
    private static final Set<String> FURNITURE = Set.of(
            "doodad.industrial-cable-reel", "doodad.industrial-pallet-stack",
            "doodad.military-bunk", "doodad.residential-bed-h", "doodad.residential-bed-head-e",
            "doodad.residential-bed-v", "doodad.residential-bed-head-n",
            "doodad.desk-1", "doodad.desk-2",
            "doodad.shelf-empty", "doodad.shelf-1", "doodad.shelf-2", "doodad.shelf-3",
            "main-stores.pallet", "main-stores.cartons", "sickbay-clinic.linen-high",
            "sickbay-clinic.linen-low", "mess-galley.stool");

    private final float rating;

    EnvironmentMaterial(float rating) {
        this.rating = rating;
    }

    /** The material rating a round's penetration is read against. */
    public float rating() {
        return rating;
    }

    /** Share of a round's payload damage this material takes; see {@link DurabilityModel#materialEfficiency}. */
    public float efficiency(float penetration) {
        return DurabilityModel.materialEfficiency(penetration, rating);
    }

    /**
     * Whole structure points a round of {@code damage} and {@code penetration} removes,
     * rounded to the nearest point: feature structure is kept in whole points.
     */
    public int structuralDamage(float damage, float penetration) {
        return structuralDamage(damage, penetration, false);
    }

    /**
     * As {@link #structuralDamage(float, float)}, for a round that may {@code burn}: a beam or an energy bolt chars
     * what burns (crates, furniture and standing trees) with its whole payload, whatever its penetration, where
     * every other material reads it as an ordinary round.
     */
    public int structuralDamage(float damage, float penetration, boolean burn) {
        if (!(damage > 0f)) return 0;
        return Math.round(damage * (burn && burns() ? 1f : efficiency(penetration)));
    }

    /** Whether a burning round takes this material whatever its penetration: wood and timber. */
    public boolean burns() {
        return this == WOOD || this == TIMBER;
    }

    /** The material of a destructible prop by its definition id, or null for one rounds cannot wear. */
    public static EnvironmentMaterial forProp(String definitionId) {
        if (definitionId == null) return null;
        if (CoverPropDamage.CRATES.contains(definitionId)) return WOOD;
        if (definitionId.startsWith("doodad.industrial-fence-")) return WIRE;
        if (definitionId.startsWith("doodad.sandbag-") || definitionId.startsWith("doodad.fort-sandbag-")) {
            return SANDBAG;
        }
        if (definitionId.startsWith(TileManifest.EMPLACEMENT_PIECE_PREFIX)) return MASONRY;
        if (PropDamage.BARRELS.contains(definitionId)) return DRUM;
        if (PropDamage.EQUIPMENT.containsKey(definitionId) || PropDamage.DECORATIONS.contains(definitionId)) {
            return FURNITURE.contains(definitionId) ? WOOD : MACHINERY;
        }
        if (RockDamage.IDS.contains(definitionId)) return STONE;
        NaturalPropShape shape = NaturalPropShape.find(definitionId);
        if (shape instanceof TreeCanopy) return TIMBER;
        if (shape instanceof Undergrowth) return WOOD;
        if (shape instanceof MineralFormation) return STONE;
        return null;
    }

    /** The material of a shared-edge feature. */
    public static EnvironmentMaterial forBarrier(SharedEdgeBarrier.Kind kind) {
        return switch (kind) {
            case WINDOW -> MASONRY;
            case REVETMENT -> SANDBAG;
        };
    }
}

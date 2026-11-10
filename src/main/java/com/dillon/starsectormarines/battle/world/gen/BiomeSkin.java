package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.NaturalRise;
import com.dillon.starsectormarines.battle.world.model.NaturalVoid;
import com.dillon.starsectormarines.battle.world.model.SurfaceSignature;

/**
 * How one world dresses an {@link OwnedMap} ({@code owned-maps.md}, The biome skin): a deterministic reading of the
 * world's palette, living world, signature and climate that answers every question the map leaves open (what the
 * ground is, how a drop looks, how a settlement is built to live there) and none that it answers. The layout never
 * moves under a skin: no wall, door, road, pad, site, drop or rise cell is the skin's to place. Game-API-free, so
 * {@link OwnedMapRecipe} and its tests run headless.
 *
 * @param palette   what the ground is made of
 * @param living    which living world a verdant palette is; {@link LivingWorld#TEMPERATE} otherwise
 * @param signature the planet type's detail over its palette
 * @param climate   what the air does; {@link Climate#NONE} for a world nobody described
 */
public record BiomeSkin(SurfacePalette palette, LivingWorld living, SurfaceSignature signature, Climate climate) {

    public BiomeSkin {
        if (palette == null) palette = SurfacePalette.ROCK;
        if (living == null || palette != SurfacePalette.VERDANT) living = LivingWorld.TEMPERATE;
        if (signature == null) signature = SurfaceSignature.NONE;
        if (climate == null) climate = Climate.NONE;
    }

    /** The skin of a mission's target world; bare rock with no air hazard for none. */
    public static BiomeSkin of(TargetProfile profile) {
        if (profile == null) return new BiomeSkin(SurfacePalette.ROCK, null, null, null);
        return new BiomeSkin(profile.surface(), profile.living(), profile.signature(), profile.climate());
    }

    /** A skin from a vanilla planet type and the world's climate, as the campaign bridge reads them. */
    public static BiomeSkin forPlanetType(String planetTypeId, Climate climate) {
        return new BiomeSkin(SurfaceZoning.forPlanetType(planetTypeId), SurfaceZoning.livingWorld(planetTypeId),
                SurfaceZoning.signatureFor(planetTypeId), climate);
    }

    /** The wild ground every cell starts as: lawn on a living world, the palette's own ground everywhere else. */
    public GroundKind wild() {
        return palette.inlandGround();
    }

    /**
     * The drop a drop of this role is drawn as. A volcanic world's watercourse is the shallow lava river and its rift
     * the deep one; on every other world both roles are the palette's one drop ({@link NaturalVoidZoning#forPalette}).
     */
    public NaturalVoidZoning.Drop drop(OwnedMap.DropRole role) {
        if (palette == SurfacePalette.VOLCANIC) {
            return role == OwnedMap.DropRole.RIVER ? NaturalVoidZoning.LAVA_RIVER : NaturalVoidZoning.LAVA_RIFT;
        }
        return NaturalVoidZoning.forPalette(palette);
    }

    /**
     * How deep a drop's floor lies at a share of its band, in metres: the band's own metres where the drop varies,
     * and its steps' floor everywhere where it does not (a style with one depth keeps it whatever the map asks).
     */
    public float floorMeters(OwnedMap.DropRole role, float share) {
        NaturalVoidZoning.Drop drop = drop(role);
        if (!drop.varies()) return drop.steps().get(drop.steps().size() - 1).depthMeters();
        float s = Math.max(0f, Math.min(1f, share));
        return drop.shallowest() + (drop.deepest() - drop.shallowest()) * s;
    }

    /** Whether this world's drops can hold a terraced town in their banks: a crevasse in the ice. */
    public boolean dropsTakeTerraces(OwnedMap.DropRole role) {
        return drop(role).style() == NaturalVoid.Style.ICE;
    }

    /** The rise style a bank is drawn in: ice banks on a frozen world, rock strata everywhere else. */
    public NaturalRise.Style riseStyle() {
        return palette == SurfacePalette.FROZEN ? NaturalRise.Style.ICE_WALL : NaturalRise.Style.RAVINE_WALL;
    }

    /**
     * How the world's buildings are made ({@link Habitat}): shielded under hard radiation, sealed where the air
     * kills or is not there, insulated on a frozen world, open elsewhere. The palette answers when the climate is
     * silent (a story map, a fixture), so an irradiated or toxic world with no market behind it still adapts.
     */
    public Habitat habitat() {
        if (climate.has(Climate.Hazard.RADIATION) || palette == SurfacePalette.IRRADIATED) return Habitat.SHIELDED;
        if (climate.has(Climate.Hazard.TOXIC) || climate.has(Climate.Hazard.AIRLESS)
                || palette == SurfacePalette.TOXIC) return Habitat.SEALED;
        return HabitatZoning.forWorld(palette, climate);
    }

    /** How a settlement is adapted to its world ({@code owned-maps.md}, Adaptation). */
    public enum Adaptation {
        /** Built in the open: breathable air, nothing to keep out. */
        OPEN,
        /** Heated, compact brick and steel on a frozen world; its people still walk between buildings. */
        INSULATED,
        /** Sealed or shielded buildings behind a perimeter whose only openings are locks at its gates. */
        SEALED_PERIMETER,
        /** Terraced into the bank of the drop the map lets it use, out of the wind. */
        CREVASSE_TERRACE
    }

    /**
     * The adaptation of {@code settlement} on {@code map}: a perimeter wherever the air must be kept out, which wins
     * over everything; a terraced town where the map lets the settlement use a drop and this world's drop takes
     * terraces; insulated on a frozen world; open otherwise.
     */
    public Adaptation adaptation(OwnedMap map, OwnedMap.Settlement settlement) {
        Habitat habitat = habitat();
        if (habitat.enclosed()) return Adaptation.SEALED_PERIMETER;
        OwnedMap.Drop bank = settlement.terraceInto() == null ? null : map.drop(settlement.terraceInto());
        if (bank != null && dropsTakeTerraces(bank.role())) return Adaptation.CREVASSE_TERRACE;
        if (habitat == Habitat.INSULATED) return Adaptation.INSULATED;
        return Adaptation.OPEN;
    }
}

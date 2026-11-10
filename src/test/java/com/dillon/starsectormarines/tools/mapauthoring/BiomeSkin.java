package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.world.gen.ClimateZoning;
import com.dillon.starsectormarines.battle.world.gen.Habitat;
import com.dillon.starsectormarines.battle.world.gen.HabitatZoning;
import com.dillon.starsectormarines.battle.world.gen.NaturalVoidZoning;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SurfaceZoning;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.NaturalRise;
import com.dillon.starsectormarines.battle.world.model.SurfaceSignature;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Program;

import java.util.List;

/**
 * One world a map is checked on: a vanilla planet type, read through the same zoning the campaign uses, so the
 * editor's switch draws what that world would get. Its palette ({@link SurfaceZoning#forPlanetType}), its signature,
 * its drop ({@link NaturalVoidZoning#forPalette}) and the habitat its settlements are built as
 * ({@link HabitatZoning#forWorld}) under the conditions such a world usually has.
 *
 * <p>A skin repaints one layout; it never re-lays it. Living-world variety that changes a layout (a jungle's canopy, an
 * ocean's islands) is the owned map's skin pass ({@code owned-maps}), not this.
 */
public enum BiomeSkin {
    ROCK("Rock", "barren", "no_atmosphere"),
    ARID("Arid", "desert"),
    VERDANT("Verdant", "terran"),
    JUNGLE("Jungle", "jungle"),
    TUNDRA("Tundra", "tundra"),
    OCEAN("Ocean", "water"),
    FROZEN("Frozen", "frozen", "very_cold"),
    VOLCANIC("Volcanic", "lava", "very_hot", "tectonic_activity"),
    IRRADIATED("Irradiated", "irradiated", "irradiated"),
    TOXIC("Toxic", "toxic", "toxic_atmosphere");

    private final String label, planetType;
    private final List<String> conditions;

    BiomeSkin(String label, String planetType, String... conditions) {
        this.label = label;
        this.planetType = planetType;
        this.conditions = List.of(conditions);
    }

    public String label() { return label; }
    public String planetType() { return planetType; }
    public SurfacePalette palette() { return SurfaceZoning.forPlanetType(planetType); }
    public SurfaceSignature signature() { return SurfaceZoning.signatureFor(planetType); }
    public NaturalVoidZoning.Drop drop() { return NaturalVoidZoning.forPalette(palette()); }

    public Habitat habitat() {
        SurfacePalette p = palette();
        return HabitatZoning.forWorld(p, ClimateZoning.forConditions(conditions, p));
    }

    /** What a settlement of this program becomes on this world, in the editor's words. */
    public String adaptation(Program program) {
        String built = switch (habitat()) {
            case OPEN -> "open";
            case INSULATED -> "insulated";
            case SEALED -> "sealed perimeter";
            case SHIELDED -> "shielded, sealed complex";
        };
        return program == Program.CREVASSE_TOWN ? "crevasse town, " + built : built;
    }

    /** The rise a world of this skin stands up in: ice banks on a frozen world, rock banks in its drop's wall elsewhere. */
    public NaturalRise.Style riseStyle() {
        return palette() == SurfacePalette.FROZEN ? NaturalRise.Style.ICE_WALL : NaturalRise.Style.RAVINE_WALL;
    }

    /** The skin a palette is shown under when nothing else names one. */
    public static BiomeSkin of(SurfacePalette palette) {
        for (BiomeSkin skin : values()) if (skin.palette() == palette) return skin;
        return ROCK;
    }

    /**
     * Repaints a map laid out on {@code builtAs} as this world: the ground palette and signature, wild ground in this
     * world's own kinds, and town walls rebuilt for its habitat. Walkability and every edge are untouched: a skin
     * changes nothing a unit stands on or sees through.
     */
    public void repaint(CellTopology topology, SurfacePalette builtAs) {
        SurfacePalette to = palette();
        topology.setGroundPalette(to);
        topology.setSignature(signature());
        GroundKind fromInland = builtAs.inlandGround(), fromApproach = builtAs.approachGround();
        GroundKind toInland = to.inlandGround(), toApproach = to.approachGround();
        Habitat habitat = habitat();
        for (int y = 0; y < topology.getHeight(); y++) for (int x = 0; x < topology.getWidth(); x++) {
            GroundKind kind = topology.getGroundKind(x, y);
            if (kind == fromInland && fromInland != toInland) topology.setGroundKind(x, y, toInland);
            else if (kind == fromApproach && fromApproach != toApproach) topology.setGroundKind(x, y, toApproach);
            // Mirrors HabitatStage's wall rebuild, on a finished map rather than a generation context.
            String id = topology.surfaceId(topology.getSurface(x, y));
            String wall = habitat.wallFor(id);
            if (wall != null && !wall.equals(id)) topology.setSurface(x, y, topology.surfaceIndex(wall));
        }
    }
}

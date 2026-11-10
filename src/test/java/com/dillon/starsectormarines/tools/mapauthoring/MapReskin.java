package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.BiomeSkin;
import com.dillon.starsectormarines.battle.world.gen.Habitat;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.SurfaceSignature;

/**
 * A finished map, generated once on its home palette, dressed as another world for the page's switch and sheet: the
 * ground palette and signature, wild ground in the world's own kinds, and walls rebuilt for its habitat. It never
 * touches walkability, edges, buildings or doodads. It stands in for the owned-maps planet skin pass (a post-generation
 * re-dress, {@code owned-maps.md}) until that lands, and is replaced by it then; land use (fields on a frozen world)
 * is that pass's, not this.
 */
final class MapReskin {

    private final BattleSimulation sim;
    private final CellTopology topology;
    private final SurfacePalette home, homeSurface, homeGround;
    private final SurfaceSignature homeSignature;
    private final GroundKind[] kinds;
    private final int[] surfaces;

    MapReskin(BattleSimulation sim, SurfacePalette home) {
        this.sim = sim;
        this.topology = sim.getTopology();
        this.home = home;
        this.homeSurface = sim.surface();
        this.homeGround = topology.groundPalette();
        this.homeSignature = topology.signature();
        int w = topology.getWidth(), h = topology.getHeight();
        kinds = new GroundKind[w * h];
        surfaces = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            kinds[y * w + x] = topology.getGroundKind(x, y);
            surfaces[y * w + x] = topology.getSurface(x, y);
        }
    }

    /** Dresses the map as {@code world}, from the home dress each time so worlds never compound. */
    void apply(MapWorld world) {
        restore();
        BiomeSkin skin = world.skin();
        SurfacePalette to = skin.palette();
        if (to == home && skin.signature() == homeSignature) return;
        topology.setGroundPalette(to);
        topology.setSignature(skin.signature());
        sim.setSurface(to);
        GroundKind fromInland = home.inlandGround(), fromApproach = home.approachGround();
        GroundKind toInland = to.inlandGround(), toApproach = to.approachGround();
        Habitat habitat = skin.habitat();
        int w = topology.getWidth();
        for (int y = 0; y < topology.getHeight(); y++) for (int x = 0; x < w; x++) {
            GroundKind kind = kinds[y * w + x];
            if (kind == fromInland && fromInland != toInland) topology.setGroundKind(x, y, toInland);
            else if (kind == fromApproach && fromApproach != toApproach) topology.setGroundKind(x, y, toApproach);
            String id = topology.surfaceId(surfaces[y * w + x]);
            String wall = id == null ? null : habitat.wallFor(id);
            if (wall != null && !wall.equals(id)) topology.setSurface(x, y, topology.surfaceIndex(wall));
        }
    }

    /** The map as it was generated. */
    void restore() {
        if (homeSurface != null) sim.setSurface(homeSurface);
        topology.setGroundPalette(homeGround);
        topology.setSignature(homeSignature);
        int w = topology.getWidth();
        for (int y = 0; y < topology.getHeight(); y++) for (int x = 0; x < w; x++) {
            if (topology.getGroundKind(x, y) != kinds[y * w + x]) topology.setGroundKind(x, y, kinds[y * w + x]);
            if (topology.getSurface(x, y) != surfaces[y * w + x]) topology.setSurface(x, y, surfaces[y * w + x]);
        }
    }
}

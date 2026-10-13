package com.dillon.starsectormarines.battle.world.gen.nature;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.PatchField;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.forest.LandscapeAccess;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainSurface;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Generation-time mineral fields. Ground, roads, cliff faces and finished access retain ownership. */
public final class FormationBrush {
    private FormationBrush() {}
    public record Placement(int x,int y,MineralFormation shape,int turns) {
        public int width() { return shape.width(turns); }
        public int height() { return shape.height(turns); }
        public boolean blocks(int x,int y) { return shape.blocks(x-this.x,y-this.y,turns); }
    }

    public static void scatter(GenContext c, SurfacePalette surface, boolean[] reserved) {
        if(surface!=SurfacePalette.ROCK && surface!=SurfacePalette.FROZEN) return;
        boolean ice=surface==SurfacePalette.FROZEN;
        MineralFormation[] pool=ice?new MineralFormation[]{MineralFormation.ICE_BLOCKS,MineralFormation.ICE_PINNACLES}
                :new MineralFormation[]{MineralFormation.BOULDERS,MineralFormation.SPIRES};
        Random random=new Random(c.seed^0x4d494e4552414cL);
        PatchField patches=new PatchField(c.seed^0x535049524553L,17);
        List<int[]> candidates=new ArrayList<>();
        for(int y=2;y<c.height-2;y+=3) for(int x=2;x<c.width-2;x+=3) candidates.add(new int[]{x,y});
        Collections.shuffle(candidates,random);
        boolean[] closed=new boolean[c.width*c.height];
        List<Placement> accepted=new ArrayList<>();
        for(int[] cell:candidates) {
            int turns=random.nextInt(4);
            MineralFormation shape=pool[random.nextInt(pool.length)];
            Placement p=new Placement(cell[0]-shape.width(turns)/2,cell[1]-shape.height(turns)/2,shape,turns);
            if(p.x()<0 || p.y()<0 || p.x()+p.width()>c.width || p.y()+p.height()>c.height) continue;
            boolean fits=true;
            for(int y=p.y();y<p.y()+p.height() && fits;y++) for(int x=p.x();x<p.x()+p.width();x++) {
                if(!p.blocks(x,y))continue;
                boolean material=ice?c.topology.getGroundKind(x,y)==CellTopology.GroundKind.SNOW
                        :switch(c.topology.getGroundKind(x,y)) {case STONE,DIRT -> true;default -> false;};
                if(!material || patches.sample(x,y)>.52f || closed[y*c.width+x]
                        || !eligible(c.grid,c.topology,reserved,x,y)) {fits=false;break;}
            }
            if(!fits || !LandscapeAccess.canClose(c.grid,closed,p.x(),p.y(),p.x()+p.width()-1,p.y()+p.height()-1,p::blocks))continue;
            accepted.add(p);
            for(int y=p.y();y<p.y()+p.height();y++)for(int x=p.x();x<p.x()+p.width();x++)
                if(p.blocks(x,y))closed[y*c.width+x]=true;
        }
        stamp(accepted,c.grid,c.topology,c.doodads,reserved);
    }

    /** All-or-nothing placement, also available to authored maps without procedural scatter. */
    public static void stamp(List<Placement> placements, NavigationGrid grid, CellTopology topology,
                             List<Doodad> props, boolean[] reserved) {
        int w=grid.getWidth(),h=grid.getHeight();
        if(topology.getWidth()!=w || topology.getHeight()!=h || (reserved!=null && reserved.length!=w*h))
            throw new IllegalArgumentException("Formation and map dimensions differ");
        if (placements.isEmpty()) return;
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) throw new IllegalStateException("Formation tilesets have not been installed");
        List<Doodad> additions=new ArrayList<>();boolean[] blocked=new boolean[w*h];
        for(var p:placements) {
            if(p.x()<0 || p.y()<0 || p.x()+p.width()>w || p.y()+p.height()>h)
                throw new IllegalArgumentException("Formation extends beyond map");
            var def=registry.doodad(p.shape().id());
            if(def==null || def.footprintCellsX!=p.shape().width() || def.footprintCellsY!=p.shape().height())
                throw new IllegalStateException("Missing or mismatched formation "+p.shape().id());
            additions.add(new Doodad(p.x(),p.y(),def,p.turns()));
            for(int y=p.y();y<p.y()+p.height();y++)for(int x=p.x();x<p.x()+p.width();x++)if(p.blocks(x,y)) {
                if(!eligible(grid,topology,reserved,x,y))throw new IllegalArgumentException("Formation overlaps protected ground at "+x+","+y);
                blocked[y*w+x]=true;
            }
        }
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(blocked[y*w+x]) {
            grid.setWalkable(x,y,false);grid.setSeeThrough(x,y,false);grid.setTag(x,y,CellTag.NATURAL_SOLID,true);
            grid.setWallHp(x,y,0);topology.setFixture(x,y,true);topology.setNatureOverlayIndex(x,y,-1);
        }
        props.addAll(additions);
    }

    private static boolean eligible(NavigationGrid grid,CellTopology topology,boolean[] reserved,int x,int y) {
        boolean natural=switch(topology.getGroundKind(x,y)){case STONE,DIRT,SNOW,SAND,GRASS -> true;default -> false;};
        if(!natural || !grid.isWalkable(x,y) || grid.isDoorway(x,y) || topology.isWall(x,y) || topology.isFixture(x,y)
                || topology.getBuildingId(x,y)!=0 || topology.getSurface(x,y)!=0
                || (reserved!=null && reserved[y*grid.getWidth()+x])
                || (topology.roadSurface()!=null && topology.roadSurface().underlay(x,y)!=null))return false;
        if(topology.terrainSurface()!=null)for(int layer=0;layer<TerrainSurface.LAYERS;layer++)
            if(topology.terrainSurface().patch(x,y,layer)!=null)return false;
        return true;
    }
}

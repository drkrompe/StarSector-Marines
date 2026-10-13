package com.dillon.starsectormarines.battle.world.gen.terrain;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/** Generation-time placement. Never call on a running battle; authoring rebuilds from clean ground. */
public final class TerrainBrush {
    private TerrainBrush() {}
    public static TerrainField stamp(TerrainPlan plan,NavigationGrid grid,CellTopology topology) {
        return stamp(plan,grid,topology,null);
    }
    /** Reserved circulation/objective cells are optional host facts, never erased by the brush. */
    public static TerrainField stamp(TerrainPlan plan,NavigationGrid grid,CellTopology topology,boolean[] reserved) {
        if(grid.getWidth()!=plan.width() || grid.getHeight()!=plan.height()
                || topology.getWidth()!=plan.width() || topology.getHeight()!=plan.height()
                || (reserved!=null && reserved.length!=plan.width()*plan.height()))
            throw new IllegalArgumentException("Terrain and map dimensions differ");
        if(topology.terrainSurface()!=null)throw new IllegalArgumentException("Terrain already stamped; rebuild the map to edit it");
        TerrainField field=TerrainField.build(plan);
        // Preflight the complete visible apron too. Refusal must leave both grids untouched.
        for(int y=0;y<plan.height();y++)for(int x=0;x<plan.width();x++) {
            boolean raised=field.maximum(x,y)>=-.65, excavated=field.excavationMaximum(x,y)>=-.65;
            if (raised && excavated)
                throw new IllegalArgumentException("Raised and excavated terrain overlap at "+x+","+y+"; erase before changing relief");
            if (!raised && !excavated) continue;
            boolean natural=switch(topology.getGroundKind(x,y)) {case GRASS,DIRT,STONE,SAND,SNOW -> true;default -> false;};
            if(!natural || !grid.isWalkable(x,y) || grid.isDoorway(x,y) || topology.isWall(x,y) || topology.isFixture(x,y)
                    || topology.getBuildingId(x,y)!=0 || topology.getSurface(x,y)!=0
                    || (topology.roadSurface()!=null && topology.roadSurface().underlay(x,y)!=null)
                    || (reserved!=null && reserved[y*plan.width()+x]))
                throw new IllegalArgumentException("Terrain overlaps protected ground at "+x+","+y+"; erase or move this stroke");
        }
        TileRegistry tiles=TileRegistry.installed();
        String material=plan.finish().materialPrefix();
        if(tiles==null || tiles.block(material+"talus")==null || tiles.block(material+"rock")==null)throw new IllegalStateException("Terrain materials are not installed: "+material);
        for(int i=0;i<7;i++)
            if(tiles.block(material+"face."+i)==null)throw new IllegalStateException("Missing terrain face material "+material+i);
        TerrainSurface surface=TerrainSurface.build(plan,field,topology);
        for(int y=0;y<plan.height();y++)for(int x=0;x<plan.width();x++) {
            if(field.maximum(x,y)>=-.65 || field.excavationMaximum(x,y)>=-.65)topology.setNatureOverlayIndex(x,y,-1);
            if(!field.blocked(x,y))continue;
            grid.setWalkable(x,y,false);grid.setSeeThrough(x,y,field.excavated(x,y));grid.setTag(x,y,CellTag.NATURAL_SOLID,true);
            if (field.excavated(x,y)) grid.setEdgeCoverSuppressed(x,y,true);
            grid.setWallHp(x,y,0);topology.setFixture(x,y,true);
        }
        topology.setTerrainSurface(surface);
        // Add only the new cliff-facing cover; unrelated authored cover is untouched.
        int[] dx={0,1,0,-1},dy={-1,0,1,0};
        for(int y=0;y<plan.height();y++)for(int x=0;x<plan.width();x++)if(grid.isWalkable(x,y))
            for(int facing=0;facing<4;facing++) {
                int nx=x+dx[facing],ny=y+dy[facing];
                if(grid.inBounds(nx,ny) && field.raised(nx,ny) && grid.getCoverAtFacing(x,y,facing)<1)
                    grid.setCoverAtFacing(x,y,facing,1);
            }
        return field;
    }
}

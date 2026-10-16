package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import java.util.Random;

/** Required domestic rooms and compact public/service divisions, sized before furniture. */
final class NeighborhoodPartitionStrategy implements PartitionStrategy {
    static final NeighborhoodPartitionStrategy HOME = new NeighborhoodPartitionStrategy(true, false);
    static final NeighborhoodPartitionStrategy WORKSHOP = new NeighborhoodPartitionStrategy(false, false);
    static final NeighborhoodPartitionStrategy OFFICE = new NeighborhoodPartitionStrategy(false, true);
    private final boolean home, office;
    private NeighborhoodPartitionStrategy(boolean home, boolean office) { this.home=home; this.office=office; }
    public PartitionLayout partition(NavigationGrid g, CellTopology t, int l,int b,int r,int top, Random rng, GroundKind ground) {
        return partition(g,t,l,b,r,top,rng,ground,BuildingPlacement.DEFAULT);
    }
    public PartitionLayout partition(NavigationGrid g, CellTopology t, int l,int b,int r,int top,
                                     Random rng, GroundKind ground, BuildingPlacement placement) {
        boolean vertical=placement.frontage==BuildingPlacement.Side.TOP || placement.frontage==BuildingPlacement.Side.BOTTOM
                || (placement.frontage==null && top-b>=r-l);
        int across=vertical?r-l+1:top-b+1, depth=vertical?top-b+1:r-l+1;
        if (across<8 || depth<8) return narrow(g,t,l,b,r,top,ground,placement);
        boolean reverse=placement.frontage==BuildingPlacement.Side.BOTTOM || placement.frontage==BuildingPlacement.Side.RIGHT;
        int split=depth/2, middle=across/2;
        boolean frontRooms=home && across>=9 && depth>=9;
        boolean twoBeds=home && across>=13 && depth>=11;
        for(int v=1;v<depth-1;v++) for(int u=1;u<across-1;u++) {
            int x=vertical?l+u:l+(reverse?depth-1-v:v), y=vertical?b+(reverse?depth-1-v:v):b+u;
            boolean wall=v==split || (frontRooms && u==middle && v<split) || (twoBeds && u==middle && v>split);
            boolean door=(v==split && (u==middle+2 || (twoBeds && u==middle-2)))
                    || (frontRooms && u==middle && v==Math.max(2,split/2));
            if(wall && !door) { g.setWalkable(x,y,false); continue; }
            if(door) { BinaryPartitionStrategy.openInteriorDoorway(g,t,x,y,ground); continue; }
            RoomPurpose purpose=home ? (v>split?RoomPurpose.BEDROOM:
                    u<middle?RoomPurpose.HOME_KITCHEN:RoomPurpose.APARTMENT_LIVING)
                    : office ? (v<split?RoomPurpose.CIVIC_RECEPTION:RoomPurpose.CIVIC_OFFICE)
                    : (v<split?RoomPurpose.PRODUCTION_FLOOR:RoomPurpose.STOCKROOM);
            t.setRoomPurpose(x,y,purpose);
        }
        int cross=vertical?l+middle+2:b+middle+2;
        // An axis across depth makes the default shell entrances follow the plan's front/rear.
        int wall=vertical?b+(reverse?depth-1-split:split):l+(reverse?depth-1-split:split);
        return new PartitionLayout(vertical?PartitionLayout.Orient.HORIZONTAL:PartitionLayout.Orient.VERTICAL,
                new int[]{wall},false,false,true,cross);
    }
    private PartitionLayout narrow(NavigationGrid g,CellTopology t,int l,int b,int r,int top,
                                   GroundKind ground,BuildingPlacement placement) {
        boolean alongX=r-l>=top-b;
        int length=alongX?r-l+1:top-b+1, width=alongX?top-b+1:r-l+1;
        if(length<9||width<6)return PartitionLayout.NONE;
        int count=home&&length>=14?3:2;
        int[] axes=new int[count-1];
        for(int i=1;i<count;i++)axes[i-1]=(alongX?l:b)+(length-1)*i/count;
        for(int y=b+1;y<top;y++)for(int x=l+1;x<r;x++) {
            int along=alongX?x:y, cross=alongX?y-b:x-l;
            int room=0;while(room<axes.length&&along>axes[room])room++;
            if(room<axes.length&&along==axes[room]) {
                if(cross==width/2)BinaryPartitionStrategy.openInteriorDoorway(g,t,x,y,ground);
                else g.setWalkable(x,y,false);
                continue;
            }
            RoomPurpose p=home?(room==0?RoomPurpose.BEDROOM:room==count-1?RoomPurpose.APARTMENT_LIVING:RoomPurpose.HOME_KITCHEN)
                    :office?(room==0?RoomPurpose.CIVIC_RECEPTION:RoomPurpose.CIVIC_OFFICE)
                    :(room==0?RoomPurpose.PRODUCTION_FLOOR:RoomPurpose.STOCKROOM);
            t.setRoomPurpose(x,y,p);
        }
        return new PartitionLayout(alongX?PartitionLayout.Orient.VERTICAL:PartitionLayout.Orient.HORIZONTAL,axes,false,true);
    }

}

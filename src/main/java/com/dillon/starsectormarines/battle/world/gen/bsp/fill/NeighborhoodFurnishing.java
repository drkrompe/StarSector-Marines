package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingWorkGroups.Group;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingWorkGroups.Part;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Furniture belongs to each connected room, with circulation reserved by whole activity groups. */
final class NeighborhoodFurnishing {
    private NeighborhoodFurnishing() {}
    record Room(int left,int bottom,int right,int top,RoomPurpose purpose,int area) {}
    /** A real-length (three-cell) bed with a cell clear at its foot, and a locker beside its head. */
    static Group sleeping() { return new Group(4,2,List.of(new Part("doodad.residential-bed-h",0,0,true),
            new Part("colony-furnishings.locker",0,1,true))); }
    static Group kitchen() { return new Group(2,2,List.of(new Part("colony-furnishings.galley",0,0,true),
            new Part("colony-furnishings.locker",1,0,true))); }
    static Group dining(boolean full) {
        return new Group(full?4:3,3,List.of(new Part(full?"mess-galley.table":"crew-lounge.coffee",0,1,true),
                new Part("mess-galley.stool",0,0,false),new Part("mess-galley.stool",1,2,false)));
    }
    static Group lounge() { return new Group(3,3,List.of(new Part("doodad.residential-sofa-h",0,2,true),
            new Part("crew-lounge.coffee",0,0,true))); }
    static Group desk() { return new Group(2,3,List.of(new Part("colony-furnishings.desk",0,0,true),
            new Part("mess-galley.stool",0,1,false))); }
    static Group sofa() { return new Group(2,2,List.of(new Part("doodad.residential-sofa-h",0,1,true))); }

    static boolean furnish(NavigationGrid grid, CellTopology topology,int l,int b,int r,int top,List<Doodad> props) {
        var groups=new BuildingWorkGroups(grid,topology,props);
        boolean furnished=false;
        List<Room> rooms=rooms(grid,topology,l,b,r,top);
        boolean sharedHall=rooms.stream().anyMatch(room->room.purpose==RoomPurpose.RESIDENTIAL_HALL);
        boolean separateKitchen=rooms.stream().anyMatch(room->room.purpose==RoomPurpose.HOME_KITCHEN);
        for(Room room:rooms) {
            RoomPurpose p=room.purpose;
            if(p==null) continue;
            switch(p) {
                case BEDROOM -> {
                    if(fit(groups,room,sleeping(),1)==0)
                        fit(groups,room,new Group(4,1,List.of(new Part("doodad.residential-bed-h",0,0,true))),1);
                    if(room.area>=24) fit(groups,room,desk(),1);
                    if(room.area>=36) fit(groups,room,CompactBuildingFurnishing.pair("locker","locker"),1);
                }
                case HOME_KITCHEN -> {
                    fit(groups,room,kitchen(),1);
                    if(fit(groups,room,dining(true),1)==0) fit(groups,room,dining(false),1);
                }
                case APARTMENT_LIVING -> {
                    if(!separateKitchen&&!sharedHall)fit(groups,room,kitchen(),1);
                    if(fit(groups,room,lounge(),1)==0) fit(groups,room,sofa(),1);
                    if(separateKitchen) {
                        fit(groups,room,new Group(4,2,List.of(new Part("crew-lounge.media",0,0,true))),1);
                        if(room.area>=30)fit(groups,room,desk(),1);
                    } else {
                        // Existing apartment plans combine cooking and living in one common room.
                        if(sharedHall)fit(groups,room,kitchen(),1);
                        if(fit(groups,room,dining(true),1)==0) fit(groups,room,dining(false),1);
                    }
                }
                case APARTMENT_LOBBY -> fit(groups,room,desk(),1);
                case CIVIC_RECEPTION -> {
                    fit(groups,room,desk(),1);
                    fit(groups,room,dining(false),Math.max(1,room.area/30));
                    fit(groups,room,sofa(),2);
                }
                case CIVIC_OFFICE -> {
                    fit(groups,room,desk(),Math.max(1,room.area/15));
                    fit(groups,room,CompactBuildingFurnishing.pair("shelf","locker"),1);
                    if(room.area>=30) fit(groups,room,dining(true),1);
                }
                case CONFERENCE_ROOM -> {
                    if(fit(groups,room,dining(true),1)==0) fit(groups,room,dining(false),1);
                    fit(groups,room,CompactBuildingFurnishing.pair("desk","locker"),1);
                }
                case SERVER_ROOM -> {
                    fit(groups,room,new Group(2,3,List.of(
                            new Part("ship-utility.server",0,0,true,false),
                            new Part("ship-utility.server",1,0,true,false))),Math.max(1,room.area/12));
                }
                case PRODUCTION_FLOOR -> {
                    // Compact repair bench, spare parts and its operator's stool.
                    fit(groups,room,BuildingWorkGroups.cargoDesk(true),1);
                    fit(groups,room,CompactBuildingFurnishing.pair("utility","desk"),1);
                    fit(groups,room,CompactBuildingFurnishing.pair("case","shelf"),1);
                }
                case STOCKROOM -> {
                    fit(groups,room,BuildingWorkGroups.cargoDesk(false),1);
                    fit(groups,room,BuildingWorkGroups.pallets(1,"cartons"),1);
                    if(groups.fitRackRows(room.left-1,room.bottom-1,room.right+1,room.top+1,room.purpose)==0)
                        fit(groups,room,CompactBuildingFurnishing.pair("shelf","shelf"),Math.max(1,room.area/16));
                }
                default -> { continue; }
            }
            furnished=true;
        }
        return furnished;
    }
    private static int fit(BuildingWorkGroups groups,Room room,Group group,int count) {
        return groups.fit(room.left-1,room.bottom-1,room.right+1,room.top+1,room.purpose,group,count);
    }
    static List<Room> rooms(NavigationGrid g,CellTopology t,int l,int b,int r,int top) {
        List<Room> rooms=new ArrayList<>();boolean[][] seen=new boolean[g.getWidth()][g.getHeight()];
        for(int y=b+1;y<top;y++) for(int x=l+1;x<r;x++) {
            if(seen[x][y]||!g.isWalkable(x,y)||g.isDoorway(x,y)) continue;
            RoomPurpose purpose=t.getRoomPurpose(x,y);
            int minX=x,maxX=x,minY=y,maxY=y,area=0;
            var queue=new ArrayDeque<int[]>();queue.add(new int[]{x,y});seen[x][y]=true;
            while(!queue.isEmpty()) {
                int[] at=queue.removeFirst();area++;
                minX=Math.min(minX,at[0]);maxX=Math.max(maxX,at[0]);minY=Math.min(minY,at[1]);maxY=Math.max(maxY,at[1]);
                for(int[] d:STEPS) {
                    int nx=at[0]+d[0],ny=at[1]+d[1];
                    if(nx<=l||nx>=r||ny<=b||ny>=top||seen[nx][ny]||!g.isWalkable(nx,ny)||g.isDoorway(nx,ny)
                            ||t.getRoomPurpose(nx,ny)!=purpose) continue;
                    seen[nx][ny]=true;queue.add(new int[]{nx,ny});
                }
            }
            rooms.add(new Room(minX,minY,maxX,maxY,purpose,area));
        }
        return rooms;
    }
    private static final int[][] STEPS={{1,0},{-1,0},{0,1},{0,-1}};
}

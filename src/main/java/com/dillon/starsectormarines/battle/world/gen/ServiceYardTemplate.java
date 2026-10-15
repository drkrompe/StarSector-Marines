package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.List;

/** Complete campaign-derived yards, shared by authored maps and claimed procedural parcels. */
public enum ServiceYardTemplate {
    FREIGHT_MARSHALLING(27, 19), MAINTENANCE(19, 20), POWER_COURT(25, 14);

    public final int width, height;
    private record Part(String id, int x, int y, int turns, boolean solid) {}
    private record Choice(ServiceYardTemplate template, int x, int y, int turn) {}

    ServiceYardTemplate(int width, int height) { this.width = width; this.height = height; }
    public int width(int turn) { return (turn & 1) == 0 ? width : height; }
    public int height(int turn) { return (turn & 1) == 0 ? height : width; }

    public static boolean fitsParcel(int width, int height) {
        for (ServiceYardTemplate template : values()) for (int turn=0;turn<2;turn++)
            if (template.width(turn)+2<=width && template.height(turn)+2<=height
                    && (template.width+2)*(template.height+2)*2>=width*height) return true;
        return false;
    }

    /** Fit only inside the supplied parcel, leaving a circulation margin outside all four gates. */
    public static boolean tryFit(BlockLeaf parcel, GenContext c) {
        List<Choice> choices = new ArrayList<>();
        for (ServiceYardTemplate template : values()) for (int turn = 0; turn < 4; turn++) {
            if (template.width(turn) + 2 > parcel.width() || template.height(turn) + 2 > parcel.height()) continue;
            int x = parcel.left + (parcel.width() - template.width(turn)) / 2;
            int y = parcel.top + (parcel.height() - template.height(turn)) / 2;
            if (template.canPlace(c, x, y, turn)) choices.add(new Choice(template, x, y, turn));
        }
        if (choices.isEmpty()) return false;
        Choice choice = choices.get(c.rng.nextInt(choices.size()));
        choice.template.place(c, choice.x, choice.y, choice.turn);
        return true;
    }

    /** Refusal is read-only, including the host's layout random stream. */
    public boolean canPlace(GenContext c, int left, int bottom, int turn) {
        new RoomPose(turn, false);
        int right = left + width(turn) - 1, top = bottom + height(turn) - 1;
        if (left < 1 || bottom < 1 || right >= c.width - 1 || top >= c.height - 1) return false;
        // A connected, open margin lets existing traffic pass the new perimeter on every side.
        for (int x=left-1;x<=right+1;x++) {
            if (!c.grid.isWalkable(x,bottom-1) || !c.grid.isWalkable(x,top+1)) return false;
            if (x<=right && (!c.grid.canTraverseCellStep(x,bottom-1,x+1,bottom-1)
                    || !c.grid.canTraverseCellStep(x,top+1,x+1,top+1))) return false;
        }
        for (int y=bottom-1;y<=top+1;y++) {
            if (!c.grid.isWalkable(left-1,y) || !c.grid.isWalkable(right+1,y)) return false;
            if (y<=top && (!c.grid.canTraverseCellStep(left-1,y,left-1,y+1)
                    || !c.grid.canTraverseCellStep(right+1,y,right+1,y+1))) return false;
        }
        boolean[][] reserved = c.get(BspKeys.ROAD_RESERVATION);
        for (int y = bottom; y <= top; y++) for (int x = left; x <= right; x++) {
            if (!c.grid.isWalkable(x,y) || c.topology.isFixture(x,y) || c.topology.hasBuildingKindHint(x,y)
                    || c.topology.getGroundKind(x,y) == GroundKind.STREET
                    || c.topology.getGroundKind(x,y) == GroundKind.STRIPED
                    || (reserved != null && reserved[x][y])) return false;
            for (int yy = y-2; yy <= y+2; yy++) for (int xx = x-2; xx <= x+2; xx++)
                if (c.grid.inBounds(xx,yy) && c.grid.isDoorway(xx,yy)) return false;
        }
        for (LandingPad pad : c.landingPads)
            if (left <= pad.right()+2 && right >= pad.left()-2 && bottom <= pad.top()+2 && top >= pad.bottom()-2) return false;
        for (Doodad prop : c.doodads)
            if (left < prop.cellX+prop.footprintCellsX && right >= prop.cellX
                    && bottom < prop.cellY+prop.footprintCellsY && top >= prop.cellY) return false;
        // An existing closed edge is occupied topology even when both adjacent cells are standable.
        for (int y = bottom; y <= top; y++) for (int x = left; x <= right; x++) {
            if (x < right && !c.grid.canTraverseCellStep(x,y,x+1,y)) return false;
            if (y < top && !c.grid.canTraverseCellStep(x,y,x,y+1)) return false;
        }
        return true;
    }

    /** Place the whole footprint or fail before mutation. Mission objectives and forces stay with the host. */
    public void place(GenContext c, int left, int bottom, int turn) {
        if (!canPlace(c,left,bottom,turn)) throw new IllegalArgumentException(name()+" yard overlaps occupied or reserved ground");
        RoomPose pose = new RoomPose(turn,false);
        List<Doodad> props = new ArrayList<>();
        List<Part> parts = parts();
        for (Part part : parts) {
            var def = TileRegistry.installed().doodad("doodad."+part.id);
            if (def == null) throw new IllegalStateException("Missing yard asset: "+part.id);
            Doodad local = new Doodad(part.x,part.y,def,part.turns);
            if (part.x < 0 || part.y < 0 || part.x+local.footprintCellsX > width || part.y+local.footprintCellsY > height)
                throw new IllegalStateException("Yard asset outside template: "+part.id);
            int[] a = pose.map(part.x,part.y,width,height);
            int[] b = pose.map(part.x+local.footprintCellsX-1,part.y+local.footprintCellsY-1,width,height);
            props.add(new Doodad(left+Math.min(a[0],b[0]),bottom+Math.min(a[1],b[1]),def,(part.turns+turn)%4));
        }
        new StoryServiceYard(c).gravel(left,bottom,left+width(turn)-1,bottom+height(turn)-1);
        for (int i=0;i<props.size();i++) {
            Doodad prop = props.get(i);
            if (parts.get(i).solid) for (int y=prop.cellY;y<prop.cellY+prop.footprintCellsY;y++)
                for (int x=prop.cellX;x<prop.cellX+prop.footprintCellsX;x++) {
                    c.grid.setWalkable(x,y,false); c.grid.setSeeThrough(x,y,true); c.topology.setFixture(x,y,true);
                }
            c.doodads.add(prop);
        }
    }

    private List<Part> parts() {
        Builder b = new Builder();
        int mx=(width-1)/2, my=(height-1)/2;
        for (int y=0;y<height;y++) for (int x=0;x<width;x++) {
            if (x!=0 && x!=width-1 && y!=0 && y!=height-1) continue;
            if ((y==0 || y==height-1) && Math.abs(x-mx)<=2) continue;
            if ((x==0 || x==width-1) && Math.abs(y-my)<=2) continue;
            String piece = x==0 && y==0 ? "corner-sw" : x==width-1 && y==0 ? "corner-se"
                    : x==0 && y==height-1 ? "corner-nw" : x==width-1 && y==height-1 ? "corner-ne"
                    : y==0 || y==height-1 ? "straight-h" : "straight-v";
            b.prop(x,y,"industrial-fence-"+piece,0);
        }
        switch (this) {
            case FREIGHT_MARSHALLING -> {
                for (int x : new int[]{3,11,19}) {
                    b.bay(x,3,x+5,9);
                    b.prop(x+1,6,x==11 ? "parked-flatbed-truck" : "parked-cargo-truck",x==11 ? 0 : 3);
                    b.cargo(x,13);
                }
            }
            case MAINTENANCE -> {
                b.bay(3,2,8,10); b.prop(4,6,"parked-utility-truck",3);
                b.bay(11,2,16,10); b.prop(12,6,"parked-flatbed-truck",0);
                b.prop(5,11,"parked-tanker-truck",2); b.prop(3,15,"industrial-turbine-set",0);
                b.prop(11,15,"industrial-machine-tool",0); b.prop(14,15,"industrial-pipe-bundle",0);
            }
            case POWER_COURT -> {
                for (int x : new int[]{3,13}) {
                    b.prop(x,3,"industrial-turbine-set",0); b.prop(x,9,"industrial-reactor-housing",0);
                    b.prop(x+4,10,"industrial-switchboard",0);
                }
                b.prop(21,3,"industrial-fluid-tank",0); b.prop(21,9,"industrial-pump-set",1);
            }
        }
        return b.parts;
    }

    private static final class Builder {
        final List<Part> parts = new ArrayList<>();
        void prop(int x,int y,String id,int turn) { parts.add(new Part(id,x,y,turn,true)); }
        void bay(int l,int b,int r,int t) {
            for (int x : new int[]{l,r}) for (int y : new int[]{b,t})
                parts.add(new Part("fl-striped-yellow",x,y,y==t ? 0 : 1,false));
        }
        void cargo(int x,int y) {
            for (int dy=0;dy<2;dy++) for (int dx=0;dx<3;dx++) prop(x+dx,y+dy,"industrial-crate-stack",0);
            prop(x+4,y,"industrial-pallet-stack",0);
        }
    }
}

package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.ServiceYardTemplate;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingStamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Reserve up to two campaign-sized industrial parcels before claims, road graphs and fills.
 * Only complete industrial leaves and their internal minor streets may be joined. A complete
 * exterior road ring preserves every neighboring frontage; trunks and compound seeds are inviolate.
 */
public final class IndustrialTemplateParcelStage implements GenStage {
    @Override public void run(GenContext c) {
        Bsp.Partition partition=c.get(BspKeys.PARTITION);
        TrunkPlan.Plan plan=c.get(BspKeys.TRUNK_PLAN);
        if (partition==null || plan==null) return;
        List<BlockLeaf> planned=new ArrayList<>();
        for (BlockKind kind : new BlockKind[]{BlockKind.INDUSTRIAL_YARD,BlockKind.BUILDING_INDUSTRIAL}) {
            BlockLeaf best=null; List<BlockLeaf> owned=List.of();
            List<BlockLeaf> eligible=partition.leaves.stream().filter(IndustrialTemplateParcelStage::eligible)
                    .filter(leaf -> !planned.contains(leaf)).toList();
            for (int i=0;i<eligible.size();i++) for (int j=i+1;j<eligible.size();j++) {
                BlockLeaf a=eligible.get(i),b=eligible.get(j);
                if (a.kind!=kind && b.kind!=kind) continue;
                BlockLeaf box=new BlockLeaf(Math.min(a.left,b.left),Math.min(a.top,b.top),
                        Math.max(a.right,b.right),Math.max(a.bottom,b.bottom),false);
                if (best!=null && box.area()>=best.area()) continue;
                boolean fits=kind==BlockKind.INDUSTRIAL_YARD ? ServiceYardTemplate.fitsParcel(box.width(),box.height())
                        : BuildingStamp.fitsFreightParcel(box.width(),box.height());
                if (!fits || !roadRing(box,partition) || crossesTrunk(box,plan)) continue;
                List<BlockLeaf> members=new ArrayList<>(); boolean valid=true;
                for (BlockLeaf leaf : partition.leaves) if (overlaps(box,leaf)) {
                    if (!eligible.contains(leaf) || !box.contains(leaf.left,leaf.top) || !box.contains(leaf.right,leaf.bottom)) { valid=false; break; }
                    members.add(leaf);
                }
                if (!valid) continue;
                // Unclaimed holes are not ours merely because they lie inside a bounding rectangle.
                for (int y=box.top;y<=box.bottom && valid;y++) for (int x=box.left;x<=box.right;x++) {
                    if (partition.roadCells[x][y]) continue;
                    boolean member=false;
                    for (BlockLeaf leaf : members) if (leaf.contains(x,y)) { member=true; break; }
                    if (!member) { valid=false; break; }
                }
                if (valid) { best=box; owned=members; }
            }
            if (best==null) continue;
            best.kind=kind;
            int index=partition.leaves.indexOf(owned.get(0));
            partition.leaves.removeAll(owned); partition.leaves.add(index,best);
            planned.add(best);
            for (int y=best.top;y<=best.bottom;y++) for (int x=best.left;x<=best.right;x++) {
                partition.roadCells[x][y]=false; plan.roadCells[x][y]=false;
            }
        }
    }
    private static boolean eligible(BlockLeaf leaf) {
        return leaf.kind==BlockKind.BUILDING_INDUSTRIAL || leaf.kind==BlockKind.INDUSTRIAL_YARD;
    }
    static boolean overlaps(BlockLeaf a,BlockLeaf b) {
        return a.left<=b.right && a.right>=b.left && a.top<=b.bottom && a.bottom>=b.top;
    }
    static boolean roadRing(BlockLeaf b,Bsp.Partition p) {
        if (b.left<1 || b.top<1 || b.right>=p.width-1 || b.bottom>=p.height-1) return false;
        for (int x=b.left-1;x<=b.right+1;x++) if (!p.roadCells[x][b.top-1] || !p.roadCells[x][b.bottom+1]) return false;
        for (int y=b.top-1;y<=b.bottom+1;y++) if (!p.roadCells[b.left-1][y] || !p.roadCells[b.right+1][y]) return false;
        return true;
    }
    static boolean crossesTrunk(BlockLeaf b,TrunkPlan.Plan p) {
        for (TrunkPlan.TrunkSegment t : p.trunks)
            if (b.left<=t.right && b.right>=t.left && b.top<=t.bottom && b.bottom>=t.top) return true;
        return false;
    }
}

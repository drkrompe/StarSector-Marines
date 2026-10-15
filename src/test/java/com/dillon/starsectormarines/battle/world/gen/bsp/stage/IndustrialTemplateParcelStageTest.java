package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class IndustrialTemplateParcelStageTest {
    private GenContext fixture(boolean trunk, boolean otherOwner) {
        int w=34,h=24; boolean[][] roads=new boolean[w][h];
        for (int y=0;y<h;y++) for (int x=0;x<w;x++) roads[x][y]=true;
        BlockLeaf a=new BlockLeaf(2,2,14,19,false),b=new BlockLeaf(18,2,30,19,false);
        a.kind=BlockKind.INDUSTRIAL_YARD;
        b.kind=otherOwner ? BlockKind.BUILDING_CIVIC : BlockKind.BUILDING_INDUSTRIAL;
        for (BlockLeaf leaf : List.of(a,b)) for (int y=leaf.top;y<=leaf.bottom;y++)
            for (int x=leaf.left;x<=leaf.right;x++) roads[x][y]=false;
        GenContext c=new GenContext(new NavigationGrid(w,h),new CellTopology(w,h),new Random(42),w,h,42);
        c.put(BspKeys.PARTITION,new Bsp.Partition(new ArrayList<>(List.of(a,b)),roads,w,h));
        c.put(BspKeys.TRUNK_PLAN,new TrunkPlan.Plan(roads,List.of(),trunk
                ? List.of(new TrunkPlan.TrunkSegment(15,0,17,h-1,TrunkPlan.TrunkKind.TERTIARY,false)) : List.of(),null,w,h));
        return c;
    }
    @Test void coalescingOwnsWholeLeavesAndMinorRoadBeforeGraphPublication() {
        GenContext c=fixture(false,false); new IndustrialTemplateParcelStage().run(c);
        Bsp.Partition p=c.get(BspKeys.PARTITION);
        assertEquals(1,p.leaves.size()); assertEquals(BlockKind.INDUSTRIAL_YARD,p.leaves.get(0).kind);
        assertEquals(29,p.leaves.get(0).width());
        assertFalse(p.roadCells[16][10]); assertTrue(p.roadCells[16][1]); assertTrue(p.roadCells[31][10]);
        assertEquals(new Random(42).nextLong(),c.rng.nextLong());
    }
    @Test void trunksOtherOwnersAndMissingOuterAccessRefuseWithoutChanges() {
        for (int mode=0;mode<3;mode++) {
            GenContext c=fixture(mode==0,mode==1); Bsp.Partition p=c.get(BspKeys.PARTITION);
            if (mode==2) p.roadCells[1][10]=false;
            new IndustrialTemplateParcelStage().run(c);
            assertEquals(2,p.leaves.size()); assertTrue(p.roadCells[16][10]);
        }
    }
}

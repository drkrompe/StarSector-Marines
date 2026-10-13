package com.dillon.starsectormarines.battle.world.gen.terrain;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.dillon.starsectormarines.battle.world.gen.terrain.TerrainPlan.*;

class TerrainBrushTest {
    @Test void excavationBlocksCrossingButPassesSightAndFireWithoutRimCover() {
        NavigationGrid g=new NavigationGrid(24,24);CellTopology t=new CellTopology(24,24);
        for(int y=0;y<24;y++)for(int x=0;x<24;x++) {
            g.setWalkableFloor(x,y);t.setGroundKind(x,y,CellTopology.GroundKind.GRASS);
        }
        g.setCoverAtFacing(1,1,NavigationGrid.FACING_E,3);
        TerrainBrush.stamp(plan(List.of(stroke(Mode.EXCAVATE,3,new Point(12,12)))),g,t);
        assertFalse(g.isWalkable(12,12));assertTrue(g.isSeeThrough(12,12));
        assertFalse(g.blocksStructuralLineOfSightAt(g.index(12,12)));
        assertTrue(g.hasLineOfSight(5,12,19,12));
        assertFalse(g.damageCell(12,12,Integer.MAX_VALUE));
        assertTrue(g.isEdgeCoverSuppressed(12,12));
        assertTrue(g.isWalkable(15,12));assertFalse(g.isWalkable(14,12));
        g.recomputeCoverAt(15,12);
        assertEquals(0,g.getCoverAtFacing(15,12,NavigationGrid.FACING_W));
        assertEquals(3,g.getCoverAtFacing(1,1,NavigationGrid.FACING_E));
        assertTrue(t.terrainSurface().isExcavated(12,12));
        assertFalse(t.terrainSurface().isExcavated(0,0));
        assertFalse(t.terrainSurface().isFullSummit(12,12));
        assertNotNull(t.terrainSurface().patch(12,12,14));
    }
    @Test void raisedAndExcavatedChannelsCoexistAndEraseBothWithoutChangingLegacyRelief() {
        var raised=stroke(Mode.RAISE,3,new Point(5,12));
        var excavation=stroke(Mode.EXCAVATE,3,new Point(19,12));
        TerrainField legacy=TerrainField.build(plan(List.of(raised)));
        TerrainField mixed=TerrainField.build(plan(List.of(raised,excavation)));
        for(int y=0;y<=24;y++)for(int x=0;x<=24;x++)assertEquals(legacy.corner(x,y),mixed.corner(x,y));
        assertTrue(mixed.raised(5,12));assertFalse(mixed.excavated(5,12));
        assertTrue(mixed.excavated(19,12));assertFalse(mixed.raised(19,12));
        var erased=TerrainField.build(plan(List.of(raised,excavation,
                stroke(Mode.ERASE,4,new Point(5,12),new Point(19,12)))));
        assertFalse(erased.blocked(5,12));assertFalse(erased.blocked(19,12));
    }
    @Test void conflictingReliefRefusesBeforeMutatingGrid() {
        NavigationGrid g=new NavigationGrid(24,24);CellTopology t=new CellTopology(24,24);
        for(int y=0;y<24;y++)for(int x=0;x<24;x++) {
            g.setWalkableFloor(x,y);t.setGroundKind(x,y,CellTopology.GroundKind.GRASS);
        }
        assertThrows(IllegalArgumentException.class,()->TerrainBrush.stamp(plan(List.of(
                stroke(Mode.RAISE,3,new Point(12,12)),stroke(Mode.EXCAVATE,3,new Point(12,12)))),g,t));
        assertTrue(g.isWalkable(12,12));assertNull(t.terrainSurface());
    }
    @Test void fullSummitSupportIsIndependentOfMaterialAndExcludesCliffFaces() {
        for (Finish finish : Finish.values()) {
            var plan = new TerrainPlan(24, 24, finish, 2, 0, 91,
                    List.of(stroke(Mode.RAISE, 8, new Point(12, 12))));
            var surface = TerrainSurface.build(plan, TerrainField.build(plan), new CellTopology(24, 24));
            assertTrue(surface.isFullSummit(12, 12));
            assertFalse(surface.isFullSummit(19, 12), "the cliff face cannot support a summit prop");
            assertFalse(surface.isFullSummit(0, 0));
        }
    }
    private static TerrainPlan plan(List<Stroke> strokes) { return new TerrainPlan(24,24,Finish.ROCK,2,0,91,strokes); }
    private static Stroke stroke(Mode mode,double radius,Point... points) { return new Stroke(mode,radius,List.of(points)); }
    @Test void sparseDragIsAContinuousCapsuleNotIsolatedBrushDabs() {
        TerrainField f=TerrainField.build(plan(List.of(stroke(Mode.RAISE,3,new Point(3,12),new Point(20,12)))));
        for(int x=3;x<=20;x++)assertTrue(f.blocked(x,12));
        assertFalse(f.blocked(12,16));assertTrue(f.blocked(12,14));
    }
    @Test void eraserCutsAPassAndLaterPaintingCanCloseIt() {
        TerrainPlan p=plan(List.of(stroke(Mode.RAISE,6,new Point(3,12),new Point(20,12)),
                stroke(Mode.ERASE,2,new Point(12,0),new Point(12,23))));
        TerrainField cut=TerrainField.build(p);
        for(int y=0;y<24;y++)assertFalse(cut.blocked(12,y));
        assertTrue(cut.blocked(6,12));
        assertTrue(TerrainField.build(p.append(stroke(Mode.RAISE,2,new Point(12,12)))).blocked(12,12));
    }
    @Test void jsonPreservesStrokeOrderAndBrushSettings() throws Exception {
        TerrainPlan p=plan(List.of(stroke(Mode.RAISE,3.5,new Point(5.2,6.1)),stroke(Mode.ERASE,1,new Point(7,6)),
                stroke(Mode.EXCAVATE,2,new Point(19,16))));
        for (Finish finish : Finish.values()) {
            var variant = new TerrainPlan(p.width(), p.height(), finish, p.faceDepth(), p.ruggedness(), p.seed(), p.strokes());
            assertEquals(variant, TerrainPlan.read(variant.json()));
        }
        assertThrows(IllegalArgumentException.class,()->TerrainPlan.read(p.json().put("version",42)));
        assertThrows(IllegalArgumentException.class,()->stroke(Mode.RAISE,Double.NaN,new Point(1,1)));
    }
    @Test void changingFinishAndFaceDepthDoesNotChangeTheBlockedFootprint() {
        TerrainPlan p=plan(List.of(stroke(Mode.RAISE,6,new Point(8,9),new Point(17,15))));
        TerrainField a=TerrainField.build(p);
        for (Finish finish : Finish.values()) {
            TerrainField b=TerrainField.build(new TerrainPlan(24,24,finish,6,0,91,p.strokes()));
            for(int y=0;y<24;y++)for(int x=0;x<24;x++)assertEquals(a.blocked(x,y),b.blocked(x,y));
        }
    }
    @Test void smoothSurfaceHasFractionalEdgesAndNoVerticesOutsideTheirCell() {
        TerrainPlan p=plan(List.of(stroke(Mode.RAISE,5.3,new Point(12,12))));
        TerrainField f=TerrainField.build(p);TerrainSurface s=TerrainSurface.build(p,f,new CellTopology(24,24));
        int fractional=0;
        for(int y=0;y<24;y++)for(int x=0;x<24;x++)for(int layer=0;layer<TerrainSurface.LAYERS;layer++) {
            var patch=s.patch(x,y,layer);if(patch==null)continue;
            for(float v:patch.vertices()){assertTrue(v>=0&&v<=1);if(v>.001&&v<.999)fractional++;}
        }
        assertTrue(fractional>40);
    }
    @Test void overlapRefusalIsAtomicAndIncludesDecorativeEdge() {
        NavigationGrid g=new NavigationGrid(24,24);CellTopology t=new CellTopology(24,24);
        for(int y=0;y<24;y++)for(int x=0;x<24;x++){g.setWalkableFloor(x,y);t.setGroundKind(x,y,CellTopology.GroundKind.GRASS);}
        t.setGroundKind(12,12,CellTopology.GroundKind.STREET);
        assertThrows(IllegalArgumentException.class,()->TerrainBrush.stamp(plan(List.of(stroke(Mode.RAISE,6,new Point(12,12)))),g,t));
        for(int y=0;y<24;y++)for(int x=0;x<24;x++)assertTrue(g.isWalkable(x,y));
        assertNull(t.terrainSurface());assertEquals(0,t.surfaceCount());
    }
    @Test void placementPreservesOtherCoverAndPublishesNaturalCollision() {
        NavigationGrid g=new NavigationGrid(24,24);CellTopology t=new CellTopology(24,24);
        for(int y=0;y<24;y++)for(int x=0;x<24;x++){g.setWalkableFloor(x,y);t.setGroundKind(x,y,CellTopology.GroundKind.GRASS);}
        g.setCoverAtFacing(1,1,NavigationGrid.FACING_E,3);
        TerrainBrush.stamp(plan(List.of(stroke(Mode.RAISE,3,new Point(12,12)))),g,t);
        assertEquals(3,g.getCoverAtFacing(1,1,NavigationGrid.FACING_E));
        assertFalse(g.isWalkable(12,12));assertTrue(t.isFixture(12,12));assertFalse(t.isWall(12,12));
        assertFalse(g.damageCell(12,12,10000));assertNotNull(t.terrainSurface());
        assertThrows(IllegalArgumentException.class,()->TerrainBrush.stamp(plan(List.of()),g,t));
    }
    @Test void reservedLandingGroundRefusesTheWholePlacement() {
        NavigationGrid g=new NavigationGrid(24,24);CellTopology t=new CellTopology(24,24);
        for(int y=0;y<24;y++)for(int x=0;x<24;x++){g.setWalkableFloor(x,y);t.setGroundKind(x,y,CellTopology.GroundKind.GRASS);}
        boolean[] reserved=new boolean[24*24];reserved[12*24+12]=true;
        assertThrows(IllegalArgumentException.class,()->TerrainBrush.stamp(plan(List.of(stroke(Mode.RAISE,3,new Point(12,12)))),g,t,reserved));
        assertTrue(g.isWalkable(12,12));assertNull(t.terrainSurface());
    }
    @Test void naturalMassCannotBeBreachedEvenByDirectDamageAndCopiesKeepIt() {
        NavigationGrid g=new NavigationGrid(3,3);g.setTag(1,1,NavigationGrid.CellTag.NATURAL_SOLID,true);
        assertFalse(g.damageCell(1,1,Integer.MAX_VALUE));assertFalse(g.isWalkable(1,1));
        assertTrue(g.blocksLineOfSightAt(g.index(1,1)));
        assertTrue(g.copyRoutingTopology().hasTag(1,1,NavigationGrid.CellTag.NATURAL_SOLID));
        // Ordinary structural damage still works.
        g.setWallHp(0,0,10);assertTrue(g.damageCell(0,0,11));assertTrue(g.isWalkable(0,0));
    }
}

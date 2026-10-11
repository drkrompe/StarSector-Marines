package com.dillon.starsectormarines.tools.forestauthoring;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.OutdoorLandscapeStage;
import com.dillon.starsectormarines.battle.world.gen.nature.FormationBrush;
import com.dillon.starsectormarines.battle.world.gen.nature.MineralFormation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import com.dillon.starsectormarines.ops.battleview.PlayerViewFrameRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Production terrain and fog evidence, with paired generation controls and actual combat destruction. */
public final class PlanetLandscapeSnapshotSuite implements SnapshotSuite {
    private static final String CONTROL="battle.mapgen.outdoorLandscape";
    public String id() { return "planet-landscapes"; }
    public String label() { return "Barren ridges and mineral fields; glacial ridges and pinnacles"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        var frames=new ArrayList<SnapshotArtifact>();var renderer=new HeadlessBattleMapRenderer(context.modRoot());
        String previous=System.getProperty(CONTROL);
        try {
            for(var surface:List.of(SurfacePalette.ROCK,SurfacePalette.FROZEN)) {
                System.setProperty(CONTROL,"true");
                var c=openCountry(surface);new OutdoorLandscapeStage(8,48,119,48).run(c);
                if(c.topology.terrainSurface()==null || c.doodads.size()<5)throw new IllegalStateException("Planet has no landscape: "+surface+", props="+c.doodads.size()+", ridge="+(c.topology.terrainSurface()!=null));
                for(int x=8;x<=119;x++)for(int y=46;y<=50;y++)if(!c.grid.isWalkable(x,y))
                    throw new IllegalStateException("Protected cross-country route narrowed");
                var map=new MapResult(c.grid,c.topology,8,48,119,48,List.of(),c.doodads);
                frames.add(new SnapshotArtifact(surface.name().toLowerCase()+"-landscape.png",caption(renderer.render(map,42,16),
                        surface==SurfacePalette.ROCK?"BARREN WORLD: crater rims, boulders and rock spires; central access route preserved"
                                :"ICE WORLD: glacial pressure ridges and ice pinnacles; central access route preserved")));
                // Real mission recipes: same seed, same finished facilities, with/without the optional pass.
                for(String family:List.of("relief","counterattack","junction","last-lift","relay-nine","thunder-run","city","conquest-west","conquest-south","precinct")) {
                    System.setProperty(CONTROL,"false");var before=OutdoorForestSnapshotSuite.generate(family,9301,profile(surface));
                    System.setProperty(CONTROL,"true");var after=OutdoorForestSnapshotSuite.generate(family,9301,profile(surface));
                    OutdoorForestSnapshotSuite.verify(before,after,surface);
                    long count=after.doodads.stream().filter(d->d.definitionId()!=null && d.definitionId().startsWith("formation.")).count();
                    // Dense facilities can legitimately leave no eligible wild ground; access wins.
                    System.out.println("[planet-landscapes] "+family+" / "+surface+": "+count+" formations; routes, components and landing approaches retained");
                    if(family.equals("junction"))frames.add(new SnapshotArtifact("junction-"+surface.name().toLowerCase()+".png",renderer.render(after,42,8)));
                }
            }
            frames.add(new SnapshotArtifact("formation-combat.png",combat(context)));
        } finally {
            if(previous==null)System.clearProperty(CONTROL);else System.setProperty(CONTROL,previous);
        }
        return frames;
    }
    private static TargetProfile profile(SurfacePalette surface) {
        return new TargetProfile(4,5,2,1,"independent",Set.of(),surface,SettlementLink.ROAD);
    }
    private static GenContext openCountry(SurfacePalette surface) {
        var grid=new NavigationGrid(128,96);var topology=new CellTopology(128,96);
        ground(grid,topology,surface==SurfacePalette.FROZEN);
        var c=new GenContext(grid,topology,new Random(42),128,96,42);c.put(BspKeys.MARKET_PROFILE,profile(surface));
        return c;
    }
    private static BufferedImage combat(SnapshotContext context) throws Exception {
        var comparison=new BufferedImage(1800,840,BufferedImage.TYPE_INT_ARGB);var g=comparison.createGraphics();
        var renderer=new PlayerViewFrameRenderer(context.modRoot(),600,420);
        int row=0;
        for(var shape:List.of(MineralFormation.SPIRES,MineralFormation.ICE_PINNACLES)) {
            var grid=new NavigationGrid(20,14);var topology=new CellTopology(20,14);ground(grid,topology,shape.ice());
            List<Doodad> props=new ArrayList<>();
            FormationBrush.stamp(List.of(new FormationBrush.Placement(9,5,shape,0)),grid,topology,props,null);
            try(var sim=new BattleSimulation(grid,topology)) {
                props.forEach(sim::addDoodad);
                sim.spawn(new EntitySpec("observer",Faction.MARINE,UnitType.MARINE,5,7).moveSpeed(0));
                sim.spawn(new EntitySpec("defender",Faction.DEFENDER,UnitType.MARINE,12,7).moveSpeed(0));
                for(int state=0;state<3;state++) {
                    if(state>0)sim.damageCell(10,7,shape.hitPoints()/2);
                    for(int tick=0;tick<9;tick++)sim.advance(BattleSimulation.TICK_DT);
                    boolean revealed=sim.getFogOfWar().getUnitVisibility(1)==FogOfWarService.VIS_VISIBLE;
                    if(revealed!=(state==2))throw new IllegalStateException("Formation fog/destruction disagrees with state "+state);
                    String material=shape.ice()?"ICE":"ROCK";
                    String label=state==0?"intact, defender hidden":state==1?"cracked, still blocks sight":"destroyed, passage and sight open";
                    g.drawImage(renderer.render(sim,material+": "+label),state*600,row*420,null);
                }
            }
            row++;
        }
        g.dispose();return comparison;
    }
    private static void ground(NavigationGrid grid,CellTopology topology,boolean ice) {
        for(int y=0;y<grid.getHeight();y++)for(int x=0;x<grid.getWidth();x++) {
            grid.setWalkableFloor(x,y);topology.setGroundKind(x,y,ice?CellTopology.GroundKind.SNOW:CellTopology.GroundKind.STONE);
        }
    }
    private static BufferedImage caption(BufferedImage image,String text) {
        var g=image.createGraphics();g.setColor(new Color(0,0,0,220));g.fillRect(0,0,image.getWidth(),34);
        g.setColor(Color.WHITE);g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,18));g.drawString(text,14,24);g.dispose();return image;
    }
}

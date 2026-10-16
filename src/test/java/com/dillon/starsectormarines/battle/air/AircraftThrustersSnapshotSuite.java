package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.InstalledHullEngines;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.CompressedFrameList;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Controlled production loiter flight; only cameras and presentation are authored here. */
public final class AircraftThrustersSnapshotSuite implements SnapshotSuite {
    private static final int FPS=15, FRAMES=150;
    public String id(){return "aircraft-thrusters";}
    public String label(){return "Aircraft thrusters / controlled production flight";}
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        InstalledHullSpecs.install();InstalledHullEngines.install();InstalledVanillaData.install();
        FighterProfile[] profiles={FighterProfile.TALON,FighterProfile.WASP,FighterProfile.THUNDER,
                FighterProfile.BROADSWORD,FighterProfile.LONGBOW,FighterProfile.DAGGER};
        String[] names={"Talon","Wasp","Thunder","Broadsword","Longbow","Dagger"};
        List<BattleSimulation> sims=new ArrayList<>();
        List<BattleReviewFrameRenderer> renderers=new ArrayList<>();
        long[] craft=new long[6];
        List<SnapshotArtifact> artifacts=new ArrayList<>();
        try {
            for(int i=0;i<6;i++) {
                var sim=SceneBuilder.openGround(96,96).seed(20261015+i).missionCompletion(false).build().sim();
                sims.add(sim);
                for(int y=0;y<96;y++)for(int x=0;x<96;x++)
                    sim.getTopology().setGroundKind(x,y,((x/8+y/8)%5==0)?GroundKind.DIRT:GroundKind.STONE);
                double angle=i*Math.PI/3;
                float x=48+16*(float)Math.cos(angle),y=48+16*(float)Math.sin(angle);
                craft[i]=sim.spawnSortie(profiles[i],Faction.MARINE,48,48,x,y,90,90,0);
                var body=sim.world().kinematics(craft[i]);
                body.facingDegrees=(float)Math.toDegrees(angle);
                float heading=(float)Math.toRadians(body.facingDegrees);
                body.vx=-(float)Math.sin(heading)*GunshipOrbit.ORBIT_SPEED;
                body.vy=(float)Math.cos(heading)*GunshipOrbit.ORBIT_SPEED;
                var mission=sim.world().mission(craft[i]);
                GunshipOrbit.begin(mission,x,y,body.facingDegrees);
                mission.state=ShuttleState.ORBIT;
                sim.world().setAltitudeT(craft[i],1);
                renderers.add(new BattleReviewFrameRenderer(context.modRoot(),600,414,x,y+3,10));
            }
            for(int frame=0;frame<FRAMES;frame++) {
                // Two production 30Hz ticks per displayed frame: exact real-time 15fps.
                for(var sim:sims)for(int tick=0;tick<2;tick++)sim.advance(BattleSimulation.TICK_DT);
                BufferedImage image=new BufferedImage(1920,1080,BufferedImage.TYPE_3BYTE_BGR);
                Graphics2D g=image.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(new Color(20,28,33));g.fillRect(0,0,1920,1080);
                g.setColor(new Color(234,240,242));g.setFont(new Font("Segoe UI",Font.PLAIN,30));
                g.drawString("Aircraft thrusters / controlled flight preview",40,49);
                g.setFont(new Font("Segoe UI",Font.PLAIN,19));g.setColor(new Color(170,187,197));
                g.drawString("Production steering and engine effects / 60 px per cell in every view / real-time playback",42,82);
                for(int i=0;i<6;i++) {
                    int px=30+(i%3)*630,py=110+(i/3)*464;
                    var sim=sims.get(i);var body=sim.world().kinematics(craft[i]);
                    float altitude=AirAppearance.visualAltitudeOffsetCells(sim.world().altitudeT(craft[i]));
                    var view=renderers.get(i).refocus(body.x,body.y+altitude,10);
                    g.setColor(new Color(234,240,242));g.setFont(new Font("Segoe UI",Font.PLAIN,23));
                    g.drawString(names[i],px+10,py+27);
                    g.drawImage(view.render(sim,"Controlled loiter / turns and throttle"),px,py+40,null);
                }
                g.setColor(new Color(170,187,197));g.setFont(new Font("Segoe UI",Font.PLAIN,18));
                g.drawString(String.format(Locale.ROOT,"%.2f s / 10.00 s",(frame+1)/(double)FPS),42,1061);
                g.drawString("Tracking cameras / existing six airframes / no combat",1170,1061);
                g.dispose();
                // Each artifact keeps a compressed frame; the runner still owns every output write.
                CompressedFrameList packed=new CompressedFrameList();packed.add(image);
                artifacts.add(new SnapshotArtifact(Path.of(String.format(Locale.ROOT,"frames/frame-%04d.png",frame)),packed,67));
                if(frame==45)artifacts.add(new SnapshotArtifact("representative.png",image));
            }
            return artifacts;
        } finally {
            for(var sim:sims)sim.close();
            InstalledHullEngines.uninstall();TurretSlotResolver.useHullSpecs(null);
        }
    }
}

package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.InstalledHullEngines;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

/** Static production-rendered lineup: no combat, scaling overrides, or simulation stepping. */
public final class MissileAaScaleSnapshotSuite implements SnapshotSuite {
    public String id() { return "missile-aa-scale"; }
    public String label() { return "Missile AA / staged common-scale reference"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        InstalledHullSpecs.install(); InstalledHullEngines.install(); InstalledVanillaData.install();
        try {
            return List.of(new SnapshotArtifact("scale-reference.png", renderLineup(context, false)),
                    new SnapshotArtifact("parked-reference.png", renderLineup(context, true)));
        } finally { InstalledHullEngines.uninstall();TurretSlotResolver.useHullSpecs(null); }
    }
    private BufferedImage renderLineup(SnapshotContext context, boolean parked) throws Exception {
        try (var sim = SceneBuilder.openGround(40,30).missionCompletion(false).build().sim()) {
            for (int y=0;y<30;y++) for (int x=0;x<40;x++)
                sim.getTopology().setGroundKind(x,y,GroundKind.STONE);
            String[] turrets={"locust","swarmer","proximity"};
            for (int i=0;i<3;i++) sim.spawn(MapTurret.create("scale-"+i,Faction.MARINE,
                    "structure.aa-"+turrets[i],8+i*8,13));
            FighterProfile[] fighters={FighterProfile.TALON,FighterProfile.WASP,FighterProfile.THUNDER,FighterProfile.BROADSWORD,FighterProfile.LONGBOW,FighterProfile.DAGGER};
            float[] xs={3,8.2f,13.4f,18.6f,23.8f,29};
            for (int i=0;i<fighters.length;i++) {
                float y=parked ? 5.5f : 4;
                long id=sim.spawnSortie(fighters[i],Faction.MARINE,xs[i],y,xs[i],y,xs[i],28,0);
                sim.world().mission(id).state=parked ? ShuttleState.LOADING : ShuttleState.INCOMING;
                sim.world().setAltitudeT(id,parked ? 0 : 1);
                sim.world().kinematics(id).facingDegrees=90;
            }
            var renderer=new BattleReviewFrameRenderer(context.modRoot(),1920,1080,16,9,32);
            BufferedImage image=renderer.render(sim,parked ? "STAGED SCALE REFERENCE / normal ground and parked appearance / no engagement" : "STAGED SCALE REFERENCE / normal cruise appearance / no engagement");
            Graphics2D g=image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(230,239,236,24));
            // Camera is 60 px/cell; ground viewport is 1050 pixels high and centered on world (16,9).
            for(int x=0;x<=1920;x+=60)g.drawLine(x,130,x,1010);
            for(int y=15;y<1080;y+=60)if(y>=130&&y<=1010)g.drawLine(0,y,1920,y);
            g.setColor(new Color(19,27,30,240)); g.fillRect(0,30,1920,100);g.fillRect(0,1010,1920,70);
            g.setColor(new Color(235,240,236)); g.setFont(new Font("Segoe UI",Font.PLAIN,32));
            g.drawString(parked ? "Turrets and parked fighters / one shared scale" : "Turrets and fighters / one shared scale",60,76);
            g.setFont(new Font("Segoe UI",Font.PLAIN,22));
            g.drawString("60 px per cell  •  every object at the same scale",60,112);
            g.setFont(new Font("Segoe UI",Font.PLAIN,25));
            String[] names={"Locust","Swarmer","Proximity Charge"};
            for(int i=0;i<3;i++) label(g,names[i],(8+i*8)*60,430);
            String[] hulls={"Talon","Wasp","Thunder","Broadsword","Longbow","Dagger"};
            for(int i=0;i<hulls.length;i++) label(g,hulls[i],Math.round(xs[i]*60),850);
            g.setFont(new Font("Segoe UI",Font.PLAIN,20));
            g.drawString(parked ? "Static lineup / production sprites and normal ground appearance" : "Static lineup / existing production sprites, mount dimensions and aircraft cruise appearance",60,1054);
            g.setStroke(new BasicStroke(3));g.drawLine(1660,1040,1720,1040);
            g.drawLine(1660,1033,1660,1047);g.drawLine(1720,1033,1720,1047);
            g.drawString("1 cell",1740,1047);g.dispose();
            return image;
        }
    }
    private static void label(Graphics2D g,String text,int x,int y) {
        int width=g.getFontMetrics().stringWidth(text);
        g.setColor(new Color(15,23,25,205));g.fillRoundRect(x-width/2-14,y-28,width+28,40,10,10);
        g.setColor(new Color(235,240,236));g.drawString(text,x-width/2,y);
    }
}

package com.dillon.starsectormarines.tools.terrainauthoring;

import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainPlan;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import org.json.JSONObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.Container;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainField;

/** Opt-in production rendering and actual authoring gestures, all at 1920x1080 / 100%. */
public final class TerrainSnapshotSuite implements SnapshotSuite {
    public String id(){return "terrain";}
    public String label(){return "Natural terrain brush";}
    public List<SnapshotArtifact> render(SnapshotContext c) throws Exception {
        TileRegistryTestInstaller.install(c.projectRoot());
        TerrainPlan input=TerrainPlan.read(new JSONObject(Files.readString(c.projectRoot().resolve("art-source/terrain/freight-pass.terrain.json"))));
        RoadPlan roads=RoadPlan.read(new JSONObject(Files.readString(c.projectRoot().resolve("art-source/terrain/freight-pass.road.json"))));
        TerrainPreview preview=new TerrainPreview(c.projectRoot());List<SnapshotArtifact> frames=new ArrayList<>();
        frames.add(new SnapshotArtifact("grass-escarpment.png",preview.render(input,roads).image()));
        frames.add(new SnapshotArtifact("rock-escarpment.png",preview.render(new TerrainPlan(input.width(),input.height(),TerrainPlan.Finish.ROCK,4.5,.85,411,input.strokes()),roads).image()));
        var prepared=TerrainPreview.prepare(input,roads);NavigationGrid grid=prepared.map().grid;
        int blocked=0;
        for(int y=0;y<input.height();y++)for(int x=0;x<input.width();x++)if(prepared.field().blocked(x,y)) {
            blocked++;if(grid.isWalkable(x,y)||grid.isSeeThrough(x,y)||grid.damageCell(x,y,100000)||prepared.map().topology.isWall(x,y))
                throw new IllegalStateException("Natural obstruction did not retain its tactical identity");
        }
        if(blocked<2000)throw new IllegalStateException("Fixture did not exercise a substantial mass");
        // The road and its neighboring cells remain a drivable band all the way across.
        for(int x=0;x<128;x++)for(int y=33;y<=42;y++)if(!grid.isWalkable(x,y))throw new IllegalStateException("Freight corridor blocked");
        TerrainAuthoringPage[] page=new TerrainAuthoringPage[1];
        SwingUtilities.invokeAndWait(()->{try{page[0]=new TerrainAuthoringPage(new AuthoringPageContext(c.projectRoot(),c.starsectorCore(),s->{},()->{}));page[0].component().setSize(1920,1080);layout(page[0].component());}catch(Exception ex){throw new IllegalStateException(ex);}});
        await(page[0]);frames.add(new SnapshotArtifact("editor.png",capture(page[0])));
        TerrainPlan[] edited=new TerrainPlan[1];
        SwingUtilities.invokeAndWait(()->{
            Component canvas=find(page[0].component(),"terrain-canvas");
            JSpinner radius=(JSpinner)find(page[0].component(),"terrain-radius");radius.setValue(6d);
            gesture(canvas,110,20,117,20,MouseEvent.BUTTON1);
            if(!TerrainField.build(page[0].document()).blocked(114,25) || TerrainField.build(input).blocked(114,25))
                throw new IllegalStateException("Raise gesture did not add solid terrain");
            page[0].undo();if(!page[0].document().equals(input))throw new IllegalStateException("Raise undo failed");
            radius.setValue(8d);
            gesture(canvas,62,0,62,28,MouseEvent.BUTTON3);
            edited[0]=page[0].document();if(edited[0].strokes().size()!=input.strokes().size()+1)throw new IllegalStateException("Drag was not one stroke");
            page[0].undo();if(!page[0].document().equals(input))throw new IllegalStateException("Undo did not restore terrain");
            page[0].redo();if(!page[0].document().equals(edited[0]))throw new IllegalStateException("Redo did not restore stroke");
            ((JCheckBox)find(page[0].component(),"terrain-navigation")).doClick();
        });
        Path folder=Files.createTempDirectory("terrain-brush-flow-");Path saved=folder.resolve("edited.terrain.json");
        try {SwingUtilities.invokeAndWait(()->{try{page[0].saveDocument(saved);page[0].openDocument(saved);if(!page[0].document().equals(edited[0])||page[0].hasUnsavedChanges())throw new IllegalStateException("Terrain save/open failed");}catch(Exception ex){throw new IllegalStateException(ex);}});
            await(page[0]);frames.add(new SnapshotArtifact("editor-erased-pass.png",capture(page[0])));
            frames.add(new SnapshotArtifact("erased-pass.png",preview.render(edited[0],roads).image()));
        } finally {SwingUtilities.invokeAndWait(()->page[0].close());Files.deleteIfExists(saved);Files.deleteIfExists(folder);}
        System.out.println("Terrain: "+blocked+" solid cells; corridor clear; damage refused; raise/erase/undo/redo/save/open verified.");
        return frames;
    }
    private static void gesture(Component canvas,double x0,double y0,double x1,double y1,int button) {
        double scale=Math.min(canvas.getWidth()/128d,canvas.getHeight()/72d),ox=(canvas.getWidth()-128*scale)/2,oy=(canvas.getHeight()-72*scale)/2;
        int a=(int)Math.round(ox+(x0+.5)*scale),b=(int)Math.round(canvas.getHeight()-oy-(y0+.5)*scale);
        int x=(int)Math.round(ox+(x1+.5)*scale),y=(int)Math.round(canvas.getHeight()-oy-(y1+.5)*scale);
        canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,1,0,a,b,1,false,button));
        canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_DRAGGED,2,0,x,y,1,false,MouseEvent.NOBUTTON));
        canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_RELEASED,3,0,x,y,1,false,button));
    }
    private static void await(TerrainAuthoringPage page) throws Exception {
        long until=System.nanoTime()+60_000_000_000L;boolean[] ready={false};
        while(!ready[0]&&System.nanoTime()<until){Thread.sleep(100);SwingUtilities.invokeAndWait(()->ready[0]=page.previewReady());}
        if(!ready[0])throw new IllegalStateException("Terrain preview did not finish");
    }
    private static BufferedImage capture(TerrainAuthoringPage page) throws Exception {
        BufferedImage ui=new BufferedImage(1920,1080,BufferedImage.TYPE_INT_ARGB);
        SwingUtilities.invokeAndWait(()->{Graphics2D g=ui.createGraphics();page.component().printAll(g);g.dispose();});return ui;
    }
    private static Component find(Component c,String name){if(name.equals(c.getName()))return c;if(c instanceof Container p)for(Component child:p.getComponents()){Component found=find(child,name);if(found!=null)return found;}return null;}
    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container nested)layout(nested);}
}

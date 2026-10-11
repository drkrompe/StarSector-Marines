package com.dillon.starsectormarines.tools.roadauthoring;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import org.json.JSONObject;
import java.nio.file.Files;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.Container;
import java.awt.Component;
import java.awt.event.MouseEvent;
import javax.swing.JSlider;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.EndCap;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

/** Actual road surfaces through the production renderer, plus the editor at the requested display size. */
public final class RoadSnapshotSuite implements SnapshotSuite {
    public String id(){return "roads";}
    public String label(){return "Spline road authoring";}
    public List<SnapshotArtifact> render(SnapshotContext c) throws Exception {
        TileRegistryTestInstaller.install(c.projectRoot());
        RoadPlan input=RoadPlan.read(new JSONObject(Files.readString(c.projectRoot().resolve("art-source/roads/curved-junction.road.json"))));
        RoadPreview preview=new RoadPreview(c.projectRoot());List<SnapshotArtifact> frames=new ArrayList<>();
        for(int age=0;age<=4;age++) {
            double wear=age/4d;RoadPlan plan=new RoadPlan(input.width(),input.height(),input.routes().stream().map(r->r.withWear(wear)).toList());
            frames.add(new SnapshotArtifact("wear-"+age+".png",preview.render(plan)));
        }
        RoadAuthoringPage[] page=new RoadAuthoringPage[1];
        SwingUtilities.invokeAndWait(()->{try{page[0]=new RoadAuthoringPage(new AuthoringPageContext(c.projectRoot(),c.starsectorCore(),s->{},()->{}));
            page[0].component().setSize(1920,1080);layout(page[0].component());}catch(Exception ex){throw new IllegalStateException(ex);}});
        // Exercise the actual event listeners and the same I/O seams as Open/Save.
        Path folder=Files.createTempDirectory("road-authoring-flow-");
        Path saved=folder.resolve("edited-road.road.json");
        SwingUtilities.invokeAndWait(()->{try{
            JSlider dial=(JSlider)find(page[0].component(),"road-wear");dial.setValue(75);
            ((JComboBox<?>)find(page[0].component(),"road-end-cap")).setSelectedItem(EndCap.BUTT);
            ((JCheckBox)find(page[0].component(),"road-auto-edge")).doClick();
            Component canvas=find(page[0].component(),"road-canvas");
            double scale=Math.min(canvas.getWidth()/96d,canvas.getHeight()/54d),ox=(canvas.getWidth()-96*scale)/2,oy=(canvas.getHeight()-54*scale)/2;
            int x=(int)Math.round(ox+24.5*scale),y=(int)Math.round(canvas.getHeight()-oy-16.5*scale);
            canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,1,0,x,y,1,false));
            canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_DRAGGED,2,0,x+40,y-35,1,false));
            canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_RELEASED,3,0,x+40,y-35,1,false));
            RoadPlan edited=page[0].document();
            if(edited.routes().get(0).autoEdge() || edited.routes().get(0).endCap()!=EndCap.BUTT || edited.routes().get(0).wear()!=.75 || edited.routes().get(0).points().get(1).equals(input.routes().get(0).points().get(1)))
                throw new IllegalStateException("Road interaction did not change the document");
            if(!page[0].hasUnsavedChanges())throw new IllegalStateException("Road edits not tracked");
            page[0].saveDocument(saved);page[0].openDocument(saved);
            if(!edited.equals(page[0].document()) || page[0].hasUnsavedChanges())throw new IllegalStateException("Road save/reopen changed the document");
        }catch(Exception ex){throw new IllegalStateException(ex);}});
        Files.delete(saved);Files.delete(folder);
        long deadline=System.nanoTime()+30_000_000_000L;
        boolean[] ready={false};
        while(!ready[0]&&System.nanoTime()<deadline){Thread.sleep(100);SwingUtilities.invokeAndWait(()->ready[0]=page[0].previewReady());}
        if(!ready[0])throw new IllegalStateException("Road editor did not render");
        BufferedImage ui=new BufferedImage(1920,1080,BufferedImage.TYPE_INT_ARGB);
        SwingUtilities.invokeAndWait(()->{Graphics2D g=ui.createGraphics();page[0].component().printAll(g);g.dispose();page[0].close();});
        frames.add(new SnapshotArtifact("editor.png",ui));return frames;
    }
    private static Component find(Component component,String name) {
        if(name.equals(component.getName()))return component;
        if(component instanceof Container c)for(Component child:c.getComponents()){Component found=find(child,name);if(found!=null)return found;}
        return null;
    }
    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container nested)layout(nested);}
}

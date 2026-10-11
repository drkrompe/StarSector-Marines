package com.dillon.starsectormarines.tools.roadauthoring;

import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
import com.dillon.starsectormarines.battle.world.gen.road.RoadCatalog;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Route;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.EndCap;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.tools.authoring.AuthoringPage;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import org.json.JSONObject;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.ArrayDeque;

/** Direct spline manipulation with a production-rendered preview and versioned JSON persistence. */
public final class RoadAuthoringPage implements AuthoringPage {
    private final AuthoringPageContext context;
    private final JPanel root=new JPanel(new BorderLayout(8,8));
    private final Canvas canvas=new Canvas();
    private final JComboBox<String> roads=new JComboBox<>();
    private final JComboBox<Material> material=new JComboBox<>(Material.values());
    private final JComboBox<EndCap> endCap=new JComboBox<>(EndCap.values());
    private final JSpinner width=new JSpinner(new SpinnerNumberModel(9d,3d,32d,.5));
    private final JSpinner shoulder=new JSpinner(new SpinnerNumberModel(2d,0d,8d,.5));
    private final JSpinner seed=new JSpinner(new SpinnerNumberModel(51L,Long.MIN_VALUE,Long.MAX_VALUE,1L));
    private final JCheckBox autoEdge=new JCheckBox("Blend into neighboring terrain",true);
    private final JSlider wear=new JSlider(0,100,35);
    private final JLabel state=new JLabel(" ");
    private final RoadPreview preview;
    private final Timer debounce;
    private final ArrayDeque<RoadPlan> undo=new ArrayDeque<>();
    private RoadPlan plan, saved;
    private Path file;
    private int selectedPoint=-1, revision;
    private boolean binding,busy,closed;
    private BufferedImage image;

    public RoadAuthoringPage(AuthoringPageContext context) throws Exception {
        this.context=context; TileRegistryTestInstaller.install(context.projectRoot());
        preview=new RoadPreview(context.projectRoot());
        plan=RoadPlan.read(new JSONObject(Files.readString(context.projectRoot().resolve("art-source/roads/curved-junction.road.json"))));saved=plan;
        debounce=new Timer(180,e->render());debounce.setRepeats(false);
        JPanel toolbar=new JPanel(new FlowLayout(FlowLayout.LEFT));
        button(toolbar,"New…",this::newPlan);button(toolbar,"Open…",this::open);button(toolbar,"Save",()->save(false));button(toolbar,"Save as…",()->save(true));
        button(toolbar,"Undo",this::undo);button(toolbar,"Add road",this::addRoad);button(toolbar,"Remove road",this::removeRoad);
        button(toolbar,"Remove anchor",this::removePoint);
        root.add(toolbar,BorderLayout.NORTH);
        JPanel controls=new JPanel();controls.setLayout(new BoxLayout(controls,BoxLayout.Y_AXIS));controls.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        field(controls,"Selected road",roads);field(controls,"Pavement",material);field(controls,"Ends · round / flush",endCap);field(controls,"Width · cells",width);
        field(controls,"Auto edging",autoEdge);field(controls,"Shoulder · cells",shoulder);field(controls,"Wear pattern seed",seed);field(controls,"Wear · new → cracked",wear);
        JLabel help=new JLabel("<html>Drag an anchor to bend the road.<br>Double-click to insert an anchor.<br>Roads merge at intersections.<br><br>Wear changes art, not clearance.<br>Save JSON to reuse in a map recipe.<br><br>Preview: production tiles, 100% scale.</html>");
        help.setAlignmentX(Component.LEFT_ALIGNMENT);help.setMaximumSize(new Dimension(245,230));controls.add(help);controls.add(Box.createVerticalGlue());controls.setPreferredSize(new Dimension(265,600));
        root.add(controls,BorderLayout.WEST);root.add(canvas,BorderLayout.CENTER);root.add(state,BorderLayout.SOUTH);
        roads.addActionListener(e->{if(!binding){selectedPoint=-1;bind();canvas.repaint();}});
        autoEdge.addActionListener(e->controlsChanged());material.addActionListener(e->controlsChanged());endCap.addActionListener(e->controlsChanged());width.addChangeListener(e->controlsChanged());shoulder.addChangeListener(e->controlsChanged());seed.addChangeListener(e->controlsChanged());
        wear.setMajorTickSpacing(50);wear.setPaintLabels(true);wear.addChangeListener(e->{if(!wear.getValueIsAdjusting())controlsChanged();});
        autoEdge.setName("road-auto-edge");canvas.setName("road-canvas");wear.setName("road-wear");endCap.setName("road-end-cap");
        canvas.setToolTipText("Drag a gold anchor; double-click to insert one. The line is the editable spline, not a vehicle route.");
        bind();requestRender();
    }
    private static void button(JPanel bar,String label,Runnable action) { JButton b=new JButton(label);b.addActionListener(e->action.run());bar.add(b); }
    private static void field(JPanel p,String label,JComponent control) {
        JLabel title=new JLabel(label);title.setAlignmentX(Component.LEFT_ALIGNMENT);p.add(title);control.setAlignmentX(Component.LEFT_ALIGNMENT);control.setMaximumSize(new Dimension(245,36));p.add(control);p.add(Box.createVerticalStrut(12));
    }
    RoadPlan document() { return plan; }
    void openDocument(Path path) throws Exception {
        RoadPlan loaded=RoadPlan.read(new JSONObject(Files.readString(path)));
        plan=loaded;saved=plan;file=path;undo.clear();selectedPoint=-1;bind();requestRender();
    }
    void saveDocument(Path target) throws Exception {
        for (String path : RoadCatalog.BUILTINS)
            if(target.toAbsolutePath().normalize().equals(context.projectRoot().resolve("mod").resolve(path).toAbsolutePath().normalize())) RoadCatalog.validate(path,plan);
        Files.writeString(target,plan.json().toString(2)+"\n");file=target;saved=plan;requestRender();context.reportStatus("Saved road plan: "+file);
    }
    boolean previewReady() { return image!=null && !busy && !debounce.isRunning(); }
    public JComponent component() { return root; }
    public boolean hasUnsavedChanges() { return !plan.equals(saved); }
    public void close() { closed=true;debounce.stop(); }
    private int selected() { return Math.max(0,roads.getSelectedIndex()); }
    private Route route() { return plan.routes().get(selected()); }
    private void bind() {
        binding=true;int index=selected();roads.removeAllItems();for(Route r:plan.routes())roads.addItem(r.id());roads.setSelectedIndex(Math.min(index,plan.routes().size()-1));
        Route r=route();autoEdge.setSelected(r.autoEdge());material.setSelectedItem(r.material());endCap.setSelectedItem(r.endCap());width.setValue(r.width());shoulder.setValue(r.shoulder());seed.setValue(r.seed());wear.setValue((int)Math.round(r.wear()*100));binding=false;
    }
    private void controlsChanged() {
        if(binding)return;
        Route r=route();replace(new Route(r.id(),r.points(),((Number)width.getValue()).doubleValue(),((Number)shoulder.getValue()).doubleValue(),wear.getValue()/100d,((Number)seed.getValue()).longValue(),(Material)material.getSelectedItem(),(EndCap)endCap.getSelectedItem(),autoEdge.isSelected()),true);
    }
    private void remember() {undo.push(plan);while(undo.size()>50)undo.removeLast();}
    private void replace(Route road,boolean history) {
        List<Route> rs=new ArrayList<>(plan.routes());rs.set(selected(),road);RoadPlan next=new RoadPlan(plan.width(),plan.height(),rs);
        if(next.equals(plan))return;if(history)remember();plan=next;requestRender();
    }
    private void requestRender() {revision++;canvas.repaint();context.stateChanged();debounce.restart();}
    private void render() {
        if(closed)return;if(busy){debounce.restart();return;}busy=true;int stamp=revision;RoadPlan snapshot=plan;state.setText("Rendering pavement…");
        new SwingWorker<BufferedImage,Void>() {
            protected BufferedImage doInBackground() {return preview.render(snapshot);}
            protected void done() {
                busy=false;if(closed)return;
                try {BufferedImage rendered=get();if(stamp==revision){image=rendered;state.setText((file==null?"Untitled":file.getFileName())+(hasUnsavedChanges()?" · unsaved":" · saved")+"  |  "+plan.width()+" × "+plan.height()+" cells");canvas.repaint();}}
                catch(Exception ex){state.setText("Preview failed: "+ex.getMessage());}
                if(stamp!=revision)debounce.restart();
            }
        }.execute();
    }
    private void undo() {if(undo.isEmpty())return;plan=undo.pop();selectedPoint=-1;bind();requestRender();}
    private void addRoad() {
        if(plan.routes().size()>=32)return;remember();List<Route> rs=new ArrayList<>(plan.routes());int n=1;
        while(roadsContain(rs,"road-"+n))n++;
        rs.add(new Route("road-"+n,List.of(new Point(plan.width()*.25,plan.height()*.5),new Point(plan.width()*.75,plan.height()*.5)),7,2,.35,51,Material.ASPHALT));
        plan=new RoadPlan(plan.width(),plan.height(),rs);bind();roads.setSelectedIndex(rs.size()-1);requestRender();
    }
    private static boolean roadsContain(List<Route> rs,String id) {return rs.stream().anyMatch(r->r.id().equals(id));}
    private void removeRoad() {if(plan.routes().size()<2)return;remember();List<Route> rs=new ArrayList<>(plan.routes());rs.remove(selected());plan=new RoadPlan(plan.width(),plan.height(),rs);selectedPoint=-1;bind();requestRender();}
    private void removePoint() {if(selectedPoint<0 || route().points().size()<=2)return;List<Point> points=new ArrayList<>(route().points());points.remove(selectedPoint);selectedPoint=-1;points(points,true);}
    private void points(List<Point> points,boolean history) {
        Route r=route();try{replace(new Route(r.id(),points,r.width(),r.shoulder(),r.wear(),r.seed(),r.material(),r.endCap(),r.autoEdge()),history);}catch(IllegalArgumentException ex){state.setText(ex.getMessage());}
    }
    private boolean discard() {return !hasUnsavedChanges() || JOptionPane.showConfirmDialog(root,"Discard unsaved road edits?","Open roads",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION;}
    private JFileChooser chooser() {var c=new JFileChooser(context.projectRoot().resolve("art-source/roads").toFile());c.setFileFilter(new FileNameExtensionFilter("Road document (*.json)","json"));return c;}
    private void newPlan() {
        if(!discard())return;
        JSpinner w=new JSpinner(new SpinnerNumberModel(96,8,1024,1)),h=new JSpinner(new SpinnerNumberModel(54,8,1024,1));
        JPanel form=new JPanel(new GridLayout(2,2,8,8));form.add(new JLabel("Map width · cells"));form.add(w);form.add(new JLabel("Map height · cells"));form.add(h);
        if(JOptionPane.showConfirmDialog(root,form,"New road plan",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        int columns=(Integer)w.getValue(),rows=(Integer)h.getValue();
        plan=new RoadPlan(columns,rows,List.of(new Route("road-1",List.of(new Point(0,rows/2d),new Point(columns-1,rows/2d)),7,1,.35,51,Material.ASPHALT)));
        saved=null;file=null;undo.clear();selectedPoint=-1;bind();requestRender();
    }
    private void open() {
        if(!discard())return;var c=chooser();if(c.showOpenDialog(root)!=JFileChooser.APPROVE_OPTION)return;
        try {openDocument(c.getSelectedFile().toPath());}
        catch(Exception ex){JOptionPane.showMessageDialog(root,ex.getMessage(),"Cannot open roads",JOptionPane.ERROR_MESSAGE);}
    }
    private void save(boolean as) {
        Path target=file;
        if(as || target==null){var c=chooser();if(c.showSaveDialog(root)!=JFileChooser.APPROVE_OPTION)return;target=c.getSelectedFile().toPath();if(!target.toString().endsWith(".json"))target=Path.of(target+".road.json");
            if(Files.exists(target)&&JOptionPane.showConfirmDialog(root,"Replace "+target.getFileName()+"?","Save roads",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;}
        try {saveDocument(target);}
        catch(Exception ex){JOptionPane.showMessageDialog(root,ex.getMessage(),"Cannot save roads",JOptionPane.ERROR_MESSAGE);}
    }
    private final class Canvas extends JPanel {
        Canvas() {
            setBackground(new Color(22,26,24));
            MouseAdapter mouse=new MouseAdapter() {
                public void mousePressed(MouseEvent e) {selectedPoint=nearest(e.getX(),e.getY());if(selectedPoint>=0)remember();repaint();}
                public void mouseDragged(MouseEvent e) {if(selectedPoint<0)return;List<Point> ps=new ArrayList<>(route().points());ps.set(selectedPoint,world(e.getX(),e.getY()));points(ps,false);}
                public void mouseClicked(MouseEvent e) {
                    if(e.getClickCount()!=2 || selectedPoint>=0 || route().points().size()>=64)return;
                    Point point=world(e.getX(),e.getY());List<Point> ps=new ArrayList<>(route().points());int insert=1;double closest=Double.MAX_VALUE;
                    for(int i=1;i<ps.size();i++){Point a=ps.get(i-1),b=ps.get(i);double score=Math.hypot(point.x()-(a.x()+b.x())/2,point.y()-(a.y()+b.y())/2);if(score<closest){closest=score;insert=i;}}
                    ps.add(insert,point);points(ps,true);selectedPoint=insert;
                }
            };addMouseListener(mouse);addMouseMotionListener(mouse);
        }
        double scale() {return Math.min(getWidth()/(double)plan.width(),getHeight()/(double)plan.height());}
        double ox(){return (getWidth()-plan.width()*scale())/2;}double oy(){return (getHeight()-plan.height()*scale())/2;}
        int sx(Point p){return (int)Math.round(ox()+(p.x()+.5)*scale());}int sy(Point p){return (int)Math.round(getHeight()-oy()-(p.y()+.5)*scale());}
        Point world(int x,int y){return new Point(Math.max(0,Math.min(plan.width()-1,(x-ox())/scale()-.5)),Math.max(0,Math.min(plan.height()-1,(getHeight()-y-oy())/scale()-.5)));}
        int nearest(int x,int y){for(int i=0;i<route().points().size();i++){Point p=route().points().get(i);if(Math.hypot(sx(p)-x,sy(p)-y)<12)return i;}return -1;}
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();
            // Rendered frame is 16:9; fit its world region to this panel's world transform.
            if(image!=null){double rs=Math.min(1920d/plan.width(),1080d/plan.height()),ratio=scale()/rs;
                int iw=(int)Math.round(1920*ratio),ih=(int)Math.round(1080*ratio);g.drawImage(image,(getWidth()-iw)/2,(getHeight()-ih)/2,iw,ih,null);}
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(new Color(242,196,102,180));g.setStroke(new BasicStroke(1.5f));
            Path2D path=new Path2D.Double();boolean first=true;for(Point p:RoadRasterizer.sample(route())){if(first){path.moveTo(sx(p),sy(p));first=false;}else path.lineTo(sx(p),sy(p));}g.draw(path);
            for(int i=0;i<route().points().size();i++){Point p=route().points().get(i);g.setColor(i==selectedPoint?Color.WHITE:new Color(245,190,75));g.fillOval(sx(p)-5,sy(p)-5,10,10);g.drawString(Integer.toString(i+1),sx(p)+8,sy(p)-8);}g.dispose();
        }
    }
}

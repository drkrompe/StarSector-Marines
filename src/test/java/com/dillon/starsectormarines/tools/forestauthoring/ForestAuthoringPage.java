package com.dillon.starsectormarines.tools.forestauthoring;

import com.dillon.starsectormarines.battle.world.gen.forest.ForestPlan;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestPlan.Stroke;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestPlan.Mode;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestPlan.Woodland;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
import com.dillon.starsectormarines.tools.authoring.AuthoringPage;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import org.json.JSONObject;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.List;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Objects;

/** Paint on the production scene; one drag is one undoable stroke. No live-battle mutation. */
public final class ForestAuthoringPage implements AuthoringPage {
    private final AuthoringPageContext context;
    private final JPanel root=new JPanel(new BorderLayout(8,8));
    private final Canvas canvas=new Canvas();
    private final ForestPreview preview;
    private final JLabel state=new JLabel(" ");
    private final JComboBox<Mode> mode=new JComboBox<>(Mode.values());
    private final JComboBox<Woodland> woodland=new JComboBox<>(Woodland.values());
    private final JSpinner radius=new JSpinner(new SpinnerNumberModel(8d,1d,64d,.5));
    private final JSpinner seed=new JSpinner(new SpinnerNumberModel(411L,Long.MIN_VALUE,Long.MAX_VALUE,1L));
    private final JCheckBox navigation=new JCheckBox("Show blocked cells");
    private final ArrayDeque<ForestPlan> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private final Timer debounce;
    private ForestPlan plan,saved;
    private RoadPlan roads;
    private Path roadFile,savedRoadFile,file;
    private ForestPreview.Frame frame;
    private int revision,displayed=-1;
    private boolean busy,closed,binding;
    private String error;

    public ForestAuthoringPage(AuthoringPageContext context) throws Exception {
        this.context=context;TileRegistryTestInstaller.install(context.projectRoot());preview=new ForestPreview(context.projectRoot());
        plan=ForestPlan.read(new JSONObject(Files.readString(context.projectRoot().resolve("art-source/forests/woodland.forest.json"))));saved=plan;
        debounce=new Timer(100,e->render());debounce.setRepeats(false);
        JPanel bar=new JPanel(new FlowLayout(FlowLayout.LEFT));
        button(bar,"New",this::newPlan);button(bar,"Open",this::open);button(bar,"Save",()->save(false));button(bar,"Save as",()->save(true));
        button(bar,"Undo",this::undo);button(bar,"Redo",this::redo);button(bar,"Road context",this::roadContext);
        button(bar,"Clear road context",()->{roads=null;roadFile=null;requestRender();});root.add(bar,BorderLayout.NORTH);
        JPanel controls=new JPanel();controls.setLayout(new BoxLayout(controls,BoxLayout.Y_AXIS));controls.setPreferredSize(new Dimension(252,600));
        controls.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        field(controls,"BRUSH",mode);field(controls,"Radius / cells",radius);field(controls,"TREES",woodland);
        field(controls,"Tree pattern seed",seed);
        navigation.setAlignmentX(Component.LEFT_ALIGNMENT);controls.add(navigation);controls.add(Box.createVerticalStrut(20));
        JLabel help=new JLabel("<html>Drag to paint a forest.<br>Right-drag to clear a path.<br>Mouse wheel adjusts radius.<br><br>One stroke = one undo.<br><br>Trees span 3-6 cells.<br>The full canopy blocks travel,<br>sight and direct fire.<br><br>Erasing clears whole crowns.<br>Use a radius of 3 or more<br>to fit a tree.<br><br>Road context protects roads.<br>Save the plan for map generation.</html>");
        help.setAlignmentX(Component.LEFT_ALIGNMENT);controls.add(help);controls.add(Box.createVerticalGlue());
        root.add(controls,BorderLayout.WEST);root.add(canvas,BorderLayout.CENTER);root.add(state,BorderLayout.SOUTH);
        mode.setName("forest-mode");radius.setName("forest-radius");navigation.setName("forest-navigation");canvas.setName("forest-canvas");
        woodland.setName("forest-woodland");
        woodland.addActionListener(e->settings());seed.addChangeListener(e->settings());
        navigation.addActionListener(e->canvas.repaint());radius.addChangeListener(e->canvas.repaint());
        theme(root);bind();requestRender();
    }
    private static void theme(Component c) {
        c.setBackground(new Color(31,34,34));c.setForeground(new Color(222,215,193));
        if(c instanceof JComponent j)j.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,13));
        if(c instanceof Container p)for(Component child:p.getComponents())theme(child);
    }
    private static void button(JPanel p,String label,Runnable action) {JButton b=new JButton(label);b.setName("forest-"+label.toLowerCase().replace(' ','-'));b.addActionListener(e->action.run());p.add(b);}
    private static void field(JPanel p,String title,JComponent value) {
        JLabel label=new JLabel(title);label.setAlignmentX(Component.LEFT_ALIGNMENT);p.add(label);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);value.setMaximumSize(new Dimension(225,32));p.add(value);p.add(Box.createVerticalStrut(15));
    }
    public JComponent component(){return root;}
    public boolean hasUnsavedChanges(){return !plan.equals(saved)||!Objects.equals(roadFile,savedRoadFile);}
    public void close(){closed=true;debounce.stop();}
    ForestPlan document(){return plan;}
    boolean previewReady(){return displayed==revision&&!busy&&!debounce.isRunning();}
    private void bind(){binding=true;woodland.setSelectedItem(plan.woodland());seed.setValue(plan.seed());binding=false;}
    private void settings(){if(binding)return;commit(new ForestPlan(plan.width(),plan.height(),(Woodland)woodland.getSelectedItem(),((Number)seed.getValue()).longValue(),plan.strokes()));}
    private void commit(ForestPlan next){if(next.equals(plan))return;undo.push(plan);while(undo.size()>80)undo.removeLast();redo.clear();plan=next;requestRender();}
    void undo(){if(undo.isEmpty())return;redo.push(plan);plan=undo.pop();bind();requestRender();}
    void redo(){if(redo.isEmpty())return;undo.push(plan);plan=redo.pop();bind();requestRender();}
    private void requestRender(){revision++;error=null;canvas.repaint();context.stateChanged();debounce.restart();}
    private void render(){
        if(closed)return;if(busy){debounce.restart();return;}busy=true;int version=revision;ForestPlan document=plan;RoadPlan guide=roads;
        state.setText("Placing tree canopies and checking clearance...");
        new SwingWorker<ForestPreview.Frame,Void>(){
            protected ForestPreview.Frame doInBackground(){return preview.render(document,guide);}
            protected void done(){busy=false;if(closed)return;try{
                var result=get();if(version==revision){frame=result;displayed=version;error=null;state.setText((file==null?"Untitled":file.getFileName())+" | "+plan.width()+" x "+plan.height()+" cells | "+plan.strokes().size()+" strokes | "+result.field().trees().size()+" trees"+(hasUnsavedChanges()?" | unsaved":" | saved")+" | 1920 x 1080 / 100%");}
            }catch(Exception ex){if(version==revision){error=ex.getCause()==null?ex.getMessage():ex.getCause().getMessage();state.setText(error);}}
                canvas.repaint();if(version!=revision)debounce.restart();
            }
        }.execute();
    }
    void openDocument(Path source) throws Exception {
        JSONObject json=new JSONObject(Files.readString(source));ForestPlan loaded=ForestPlan.read(json);
        Path guide=json.has("previewRoad")?context.projectRoot().resolve(json.getString("previewRoad")).normalize():null;
        RoadPlan loadedRoad=guide==null?null:RoadPlan.read(new JSONObject(Files.readString(guide)));
        ForestPreview.prepare(loaded,loadedRoad);plan=loaded;saved=loaded;file=source;roads=loadedRoad;roadFile=guide;savedRoadFile=guide;
        undo.clear();redo.clear();bind();requestRender();
    }
    void saveDocument(Path target) throws Exception {
        ForestPreview.prepare(plan,roads);
        JSONObject json=plan.json();if(roadFile!=null)json.put("previewRoad",context.projectRoot().toAbsolutePath().normalize().relativize(roadFile.toAbsolutePath().normalize()).toString().replace('\\','/'));
        Path absolute=target.toAbsolutePath().normalize();Path temp=Files.createTempFile(absolute.getParent(),"forest-",".tmp");
        try {Files.writeString(temp,json.toString(2)+"\n");
            try{Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ex){Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
        file=absolute;saved=plan;savedRoadFile=roadFile;requestRender();context.reportStatus("Saved forest: "+file);
    }
    private JFileChooser chooser(){var c=new JFileChooser(context.projectRoot().resolve("art-source/forests").toFile());c.setFileFilter(new FileNameExtensionFilter("Forest / road JSON","json"));return c;}
    private boolean discard(){return !hasUnsavedChanges()||JOptionPane.showConfirmDialog(root,"Discard unsaved forest edits?","Forest",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION;}
    private void problem(Exception ex){JOptionPane.showMessageDialog(root,ex.getMessage(),"Forest",JOptionPane.ERROR_MESSAGE);}
    private void open(){if(!discard())return;var c=chooser();if(c.showOpenDialog(root)==JFileChooser.APPROVE_OPTION)try{openDocument(c.getSelectedFile().toPath());}catch(Exception ex){problem(ex);}}
    private void save(boolean as){Path target=file;if(as||target==null){var c=chooser();if(c.showSaveDialog(root)!=JFileChooser.APPROVE_OPTION)return;target=c.getSelectedFile().toPath();if(!target.toString().endsWith(".json"))target=Path.of(target+".forest.json");if(Files.exists(target)&&JOptionPane.showConfirmDialog(root,"Replace "+target.getFileName()+"?","Save forest",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;}try{saveDocument(target);}catch(Exception ex){problem(ex);}}
    private void newPlan(){if(!discard())return;JSpinner w=new JSpinner(new SpinnerNumberModel(128,8,1024,1)),h=new JSpinner(new SpinnerNumberModel(72,8,1024,1));JPanel form=new JPanel(new GridLayout(2,2));form.add(new JLabel("Width / cells"));form.add(w);form.add(new JLabel("Height / cells"));form.add(h);if(JOptionPane.showConfirmDialog(root,form,"New forest",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;plan=new ForestPlan((Integer)w.getValue(),(Integer)h.getValue(),Woodland.MIXED,411,List.of());saved=null;file=null;roads=null;roadFile=null;undo.clear();redo.clear();bind();requestRender();}
    private void roadContext(){var c=chooser();if(c.showOpenDialog(root)!=JFileChooser.APPROVE_OPTION)return;try{Path path=c.getSelectedFile().toPath();RoadPlan next=RoadPlan.read(new JSONObject(Files.readString(path)));if(next.width()!=plan.width()||next.height()!=plan.height())throw new IllegalArgumentException("Road context must match "+plan.width()+" x "+plan.height()+" cells. Create a new forest document at that extent first.");ForestPreview.prepare(plan,next);roads=next;roadFile=path;requestRender();}catch(Exception ex){problem(ex);}}

    private final class Canvas extends JPanel {
        private List<Point> stroke;
        private Mode strokeMode;
        private double strokeRadius;
        private int mx=-1,my=-1;
        Canvas(){
            MouseAdapter mouse=new MouseAdapter(){
                public void mousePressed(MouseEvent e){if(!insideMap(e.getX(),e.getY()))return;stroke=new ArrayList<>();strokeMode=SwingUtilities.isRightMouseButton(e)?Mode.ERASE:(Mode)mode.getSelectedItem();strokeRadius=((Number)radius.getValue()).doubleValue();add(e);}
                public void mouseDragged(MouseEvent e){add(e);}
                public void mouseMoved(MouseEvent e){mx=e.getX();my=e.getY();repaint();}
                public void mouseExited(MouseEvent e){mx=-1;repaint();}
                public void mouseReleased(MouseEvent e){if(stroke==null)return;add(e);try{commit(plan.append(new Stroke(strokeMode,strokeRadius,stroke)));}catch(Exception ex){state.setText(ex.getMessage());}stroke=null;repaint();}
                public void mouseWheelMoved(MouseWheelEvent e){radius.setValue(Math.max(1,Math.min(64,((Number)radius.getValue()).doubleValue()-e.getPreciseWheelRotation())));}
            };addMouseListener(mouse);addMouseMotionListener(mouse);addMouseWheelListener(mouse);
            setToolTipText("Paint overhead trees; right-drag clears full canopies. Wheel changes radius. Gold hatching is the actual blocked grid.");
        }
        private void add(MouseEvent e){mx=e.getX();my=e.getY();if(stroke==null||!insideMap(mx,my))return;Point p=world(mx,my);if(stroke.isEmpty()||Math.hypot(p.x()-stroke.get(stroke.size()-1).x(),p.y()-stroke.get(stroke.size()-1).y())>.3){if(stroke.size()<4096)stroke.add(p);}repaint();}
        double scale(){return Math.min(getWidth()/(double)plan.width(),getHeight()/(double)plan.height());}
        double ox(){return (getWidth()-plan.width()*scale())/2;}double oy(){return (getHeight()-plan.height()*scale())/2;}
        boolean insideMap(int x,int y){return x>=ox()&&x<getWidth()-ox()&&y>=oy()&&y<getHeight()-oy();}
        Point world(int x,int y){return new Point(Math.max(0,Math.min(plan.width()-1,(x-ox())/scale()-.5)),Math.max(0,Math.min(plan.height()-1,(getHeight()-y-oy())/scale()-.5)));}
        int sx(double x){return (int)Math.round(ox()+(x+.5)*scale());}int sy(double y){return (int)Math.round(getHeight()-oy()-(y+.5)*scale());}
        protected void paintComponent(Graphics raw){
            super.paintComponent(raw);Graphics2D g=(Graphics2D)raw.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            if(frame!=null){double rs=Math.min(1920d/plan.width(),1080d/plan.height()),ratio=scale()/rs;int iw=(int)Math.round(1920*ratio),ih=(int)Math.round(1080*ratio);g.drawImage(frame.image(),(getWidth()-iw)/2,(getHeight()-ih)/2,iw,ih,null);
                if(navigation.isSelected()&&displayed==revision){g.setColor(new Color(244,182,77,110));for(int y=0;y<plan.height();y++)for(int x=0;x<plan.width();x++)if(frame.field().blocked(x,y)){int x0=sx(x-.5),y0=sy(y+.5),sz=(int)Math.ceil(scale());g.drawRect(x0,y0,sz,sz);g.drawLine(x0,y0,x0+sz,y0+sz);}}
            }
            if(stroke!=null){g.setColor(strokeMode==Mode.ERASE?new Color(210,80,60,95):new Color(235,184,88,75));g.setStroke(new BasicStroke((float)(strokeRadius*2*scale()),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));for(int i=0;i<stroke.size();i++){Point a=stroke.get(Math.max(0,i-1)),b=stroke.get(i);g.drawLine(sx(a.x()),sy(a.y()),sx(b.x()),sy(b.y()));}}
            if(mx>=0&&insideMap(mx,my)){int size=(int)Math.round(((Number)radius.getValue()).doubleValue()*scale());g.setStroke(new BasicStroke(1.5f));g.setColor(new Color(248,203,114));g.drawOval(mx-size,my-size,size*2,size*2);g.drawLine(mx-5,my,mx+5,my);g.drawLine(mx,my-5,mx,my+5);}
            if(error!=null||displayed!=revision){g.setColor(new Color(20,23,23,235));g.fillRect(12,12,getWidth()-24,48);g.setColor(error==null?new Color(237,196,116):new Color(245,151,129));g.drawString(error==null?"Updating forest...":error,24,41);}
            g.dispose();
        }
    }
}

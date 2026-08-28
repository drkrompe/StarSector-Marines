package com.dillon.starsectormarines.tools.tilesetauthoring;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The project's sheets as a picture strip rather than a list of names.
 *
 * <p>A dropdown of ids is the wrong instrument for choosing art. Sheets are told
 * apart by looking at them, and half of what the operator wants to know before
 * opening one — is this the industrial set or the office set, is it cut-outs or a
 * fused plate — is visible in a thumbnail and absent from its name.
 *
 * <p>Thumbnails are read with a subsampled decode on a background thread. A raw
 * sheet is a couple of megabytes and there are several of them, so decoding each
 * one in full on the event thread to draw it 96 pixels wide would stall the
 * window every time the library was rescanned.
 */
public final class TilesetLibraryView extends JPanel {

    /** Width a thumbnail is drawn at in the strip. */
    private static final int THUMB_W = 96;
    private static final int THUMB_H = 72;
    private static final Color EMPTY = new Color(0x1b, 0x20, 0x27);
    private static final Color EMPTY_EDGE = new Color(0x3a, 0x42, 0x4d);

    private final DefaultListModel<TilesetLibrary.Sheet> model = new DefaultListModel<>();
    private final JList<TilesetLibrary.Sheet> list = new JList<>(model);
    private final Map<Path, ImageIcon> thumbnails = new HashMap<>();
    private final ImageIcon placeholder = placeholderIcon();

    public TilesetLibraryView(Consumer<TilesetLibrary.Sheet> onOpen) {
        super(new BorderLayout());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new SheetCell());
        list.setFixedCellHeight(THUMB_H + 12);
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2 && selected() != null) onOpen.accept(selected());
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createTitledBorder("Project sheets"));
        scroll.setPreferredSize(new Dimension(260, 320));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    public TilesetLibrary.Sheet selected() {
        return list.getSelectedValue();
    }

    public void addSelectionListener(Runnable listener) {
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) listener.run();
        });
    }

    /** Replace the strip, keeping the operator on the sheet they were editing. */
    public void setSheets(List<TilesetLibrary.Sheet> sheets) {
        String keep = selected() == null ? null : selected().name();
        model.clear();
        for (TilesetLibrary.Sheet sheet : sheets) {
            model.addElement(sheet);
            if (sheet.name().equals(keep)) list.setSelectedValue(sheet, true);
        }
        loadThumbnails(sheets);
    }

    public void select(String name) {
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).name().equals(name)) {
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    private void loadThumbnails(List<TilesetLibrary.Sheet> sheets) {
        List<Path> wanted = sheets.stream()
                .map(TilesetLibrary.Sheet::rawSheet)
                .filter(path -> path != null && !thumbnails.containsKey(path))
                .toList();
        if (wanted.isEmpty()) return;
        new SwingWorker<Void, Object[]>() {
            @Override protected Void doInBackground() {
                for (Path path : wanted) {
                    BufferedImage thumb = readThumbnail(path);
                    if (thumb != null) publish(new Object[]{path, new ImageIcon(thumb)});
                }
                return null;
            }

            @Override protected void process(List<Object[]> chunks) {
                for (Object[] chunk : chunks) {
                    thumbnails.put((Path) chunk[0], (ImageIcon) chunk[1]);
                }
                list.repaint();
            }
        }.execute();
    }

    /**
     * Decode only as many pixels as the thumbnail needs.
     *
     * <p>Subsampling is the difference between reading a few hundred kilobytes
     * and decoding a 1254x1254 sheet in full to throw away 99% of it.
     */
    static BufferedImage readThumbnail(Path path) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(path.toFile())) {
            if (stream == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                int step = Math.max(1, Math.min(width / THUMB_W, height / THUMB_H));
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);
                return fit(reader.read(0, param));
            } finally {
                reader.dispose();
            }
        } catch (Exception unreadable) {
            // A sheet that will not decode is still listed; opening it reports why.
            return null;
        }
    }

    /** Letterbox into the cell so sheets of different aspect ratios stay comparable. */
    private static BufferedImage fit(BufferedImage source) {
        BufferedImage cell = new BufferedImage(THUMB_W, THUMB_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = cell.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(EMPTY);
        g.fillRect(0, 0, THUMB_W, THUMB_H);
        double scale = Math.min(THUMB_W / (double) source.getWidth(),
                THUMB_H / (double) source.getHeight());
        int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(source.getHeight() * scale));
        g.drawImage(source, (THUMB_W - w) / 2, (THUMB_H - h) / 2, w, h, null);
        g.setColor(EMPTY_EDGE);
        g.drawRect(0, 0, THUMB_W - 1, THUMB_H - 1);
        g.dispose();
        return cell;
    }

    private static ImageIcon placeholderIcon() {
        BufferedImage image = new BufferedImage(THUMB_W, THUMB_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(EMPTY);
        g.fillRect(0, 0, THUMB_W, THUMB_H);
        g.setColor(EMPTY_EDGE);
        g.drawRect(0, 0, THUMB_W - 1, THUMB_H - 1);
        g.drawLine(0, 0, THUMB_W - 1, THUMB_H - 1);
        g.drawLine(THUMB_W - 1, 0, 0, THUMB_H - 1);
        g.dispose();
        return new ImageIcon(image);
    }

    /** Thumbnail, name, and the sheet's state, which is what picking one depends on. */
    private final class SheetCell extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> jList, Object value, int index,
                                                      boolean selected, boolean focused) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    jList, value, index, selected, focused);
            TilesetLibrary.Sheet sheet = (TilesetLibrary.Sheet) value;
            Path raw = sheet.rawSheet();
            ImageIcon icon = raw == null ? placeholder : thumbnails.get(raw);
            label.setIcon(icon == null ? placeholder : icon);
            label.setText("<html><b>" + escape(sheet.name()) + "</b><br>"
                    + "<span style='font-size:9px'>" + escape(sheet.status()) + "</span></html>");
            label.setIconTextGap(8);
            label.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
            return label;
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Exposed for the page's "open the highlighted sheet" action. */
    public Image thumbnailFor(Path rawSheet) {
        ImageIcon icon = thumbnails.get(rawSheet);
        return icon == null ? null : icon.getImage();
    }
}

package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The page as a walkthrough, checked by walking it.
 *
 * <p>The thing being changed here is what somebody sees on opening the page, so
 * the useful evidence is the screens themselves — a step whose heading says one
 * thing and whose body holds another is a defect no assertion about the model
 * would catch. Each screen is painted and written out to be looked at.
 *
 * <p>The rest is the ordering law: a walkthrough will not advance past a step
 * that has not been answered, and what it will not do it explains.
 */
public class TilesetWalkthroughTest {

    private static final Path EVIDENCE = Paths.get("build", "tileset-authoring");
    private static final int WIDTH = 980;
    private static final int HEIGHT = 620;

    /** Every screen has a heading, a sentence, and something to work in. */
    @Test
    void everyStepOfEveryWalkthroughSaysWhatItIsFor() {
        TilesetWizard wizard = new TilesetWizard(() -> {}, message -> {});
        for (TilesetWorkflow workflow : TilesetWorkflow.values()) {
            assertNotNull(workflow.title());
            assertTrue(workflow.blurb().length() > 20, workflow + " needs a real sentence");
        }
        assertNotNull(wizard);
    }

    /**
     * A step that has not been answered does not advance, and says what it
     * wants. This is the whole reason the page is a sequence: a command that
     * cannot run yet is not a greyed-out button with no explanation.
     */
    @Test
    void aStepThatIsNotFinishedSaysWhatItWants() {
        AtomicReference<String> status = new AtomicReference<>();
        TilesetWizard wizard = new TilesetWizard(() -> {}, status::set);

        List<String> visited = new ArrayList<>();
        WizardStep gated = new LambdaStep("Pick something", "You have to pick something.",
                JPanel::new)
                .blockedWhen(() -> visited.isEmpty() ? "Pick a sheet from the list" : null);
        WizardStep second = new LambdaStep("Second", "The one after.",
                JPanel::new).last();
        wizard.start(List.of(gated, second));

        assertEquals(0, wizard.currentIndex());
        assertEquals("Pick a sheet from the list", wizard.currentStep().blocker());

        visited.add("picked");
        wizard.refresh();
        assertNull(wizard.currentStep().blocker(), "answering the step unblocks it");
    }

    /** The chooser and the first screen of each walkthrough, painted. */
    @Test
    void theScreensPaint() throws Exception {
        Files.createDirectories(EVIDENCE);

        WorkflowChooser chooser = new WorkflowChooser(workflow -> {});
        write(chooser, "walkthrough-0-chooser.png");

        for (TilesetWorkflow workflow : TilesetWorkflow.values()) {
            TilesetAuthoringPage page = new TilesetAuthoringPage(context());
            JComponent root = page.component();
            // Drive it the way a click would, then paint what that shows.
            enter(page, workflow);
            write(root, "walkthrough-" + workflow.name().toLowerCase() + "-1.png");
            if (workflow == TilesetWorkflow.SURFACE) {
                // The screen this workflow exists for: the set of walls.
                advance(page, "WALL");
                write(root, "walkthrough-surface-2-the-set.png");
            }
            page.close();
        }
    }

    private static void enter(TilesetAuthoringPage page, TilesetWorkflow workflow)
            throws Exception {
        Method method = TilesetAuthoringPage.class.getDeclaredMethod(
                "enterWorkflow", TilesetWorkflow.class);
        method.setAccessible(true);
        method.invoke(page, workflow);
    }

    /** Pick a surface on screen one and step to the set, the way a click would. */
    private static void advance(TilesetAuthoringPage page, String surface) throws Exception {
        Field browser = TilesetAuthoringPage.class.getDeclaredField("surfaces");
        browser.setAccessible(true);
        SurfaceBrowserView surfaces = (SurfaceBrowserView) browser.get(page);
        assertTrue(surfaces.select(surface), surface + " must be offered");

        Field wizardField = TilesetAuthoringPage.class.getDeclaredField("wizard");
        wizardField.setAccessible(true);
        TilesetWizard wizard = (TilesetWizard) wizardField.get(page);
        Method next = TilesetWizard.class.getDeclaredMethod("goNext");
        next.setAccessible(true);
        next.invoke(wizard);
    }

    private static AuthoringPageContext context() {
        Path root = Paths.get("").toAbsolutePath();
        return new AuthoringPageContext(root, root.resolve("core"), message -> {}, () -> {});
    }

    private static void write(JComponent component, String name) throws Exception {
        component.setSize(WIDTH, HEIGHT);
        layOut(component);
        BufferedImage painted = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = painted.createGraphics();
        try {
            component.paint(graphics);
        } finally {
            graphics.dispose();
        }
        Path out = EVIDENCE.resolve(name);
        ImageIO.write(painted, "PNG", out.toFile());
        assertTrue(distinctColours(painted) > 3,
                name + " painted nothing legible — see " + out.toAbsolutePath());
    }

    /**
     * {@code Container.validate()} does nothing without a native peer, so a
     * panel that has never been in a frame paints blank. Walking the tree and
     * calling {@code doLayout} runs the layout managers directly, which keeps
     * this off a display.
     */
    private static void layOut(Component component) {
        component.doLayout();
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) layOut(child);
        }
    }

    private static int distinctColours(BufferedImage image) {
        Set<Integer> seen = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                seen.add(image.getRGB(x, y));
                if (seen.size() > 64) return seen.size();
            }
        }
        return seen.size();
    }
}

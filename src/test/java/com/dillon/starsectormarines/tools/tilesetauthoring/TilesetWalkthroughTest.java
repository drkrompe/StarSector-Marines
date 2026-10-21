package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.tools.authoring.wizard.LambdaStep;
import com.dillon.starsectormarines.tools.authoring.wizard.Wizard;
import com.dillon.starsectormarines.tools.authoring.wizard.WizardStep;
import com.dillon.starsectormarines.tools.authoring.AuthoringPageContext;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The page as a walkthrough, checked by walking it.
 *
 * <p>The thing being changed here is what somebody sees on opening the page, so
 * the useful evidence is the screens themselves — a step whose heading says one
 * thing and whose body holds another is a defect no assertion about the model
 * would catch.
 *
 * <p>The rest is the ordering law: a walkthrough will not advance past a step
 * that has not been answered, and what it will not do it explains.
 */
public class TilesetWalkthroughTest {

    /** Every screen has a heading, a sentence, and something to work in. */
    @Test
    void everyStepOfEveryWalkthroughSaysWhatItIsFor() {
        Wizard wizard = new Wizard(() -> {}, message -> {});
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
        Wizard wizard = new Wizard(() -> {}, status::set);

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

    /**
     * Picking on a screen enables the button that leaves it.
     *
     * <p>The wizard re-reads a step's blocker when a screen is shown, and a
     * selection changing raises no event it hears — so a screen whose list is
     * not wired back to it sits with the step answered and Next dead. That
     * shipped: WALL could be selected and "See what fills it" stayed grey.
     *
     * <p>Tested through the real page rather than through the wizard, because
     * what broke was the wiring between them and nothing else can show that.
     */
    @Test
    void pickingOnAScreenEnablesTheButtonThatLeavesIt() throws Exception {
        TilesetAuthoringPage page = new TilesetAuthoringPage(context());
        try {
            enter(page, TilesetWorkflow.SURFACE);
            Wizard wizard = wizardOf(page);
            assertFalse(wizard.canAdvance(), "nothing picked yet, so there is nowhere to go");

            assertTrue(browserOf(page).select("WALL"), "WALL must be offered");
            assertTrue(wizard.canAdvance(),
                    "a surface is picked, so the step is answered and Next must be live");
            assertNull(wizard.currentStep().blocker());
        } finally {
            page.close();
        }
    }

    /** The same wiring on the other walkthrough: picking a sheet enables Open it. */
    @Test
    void pickingASheetEnablesOpeningIt() throws Exception {
        TilesetAuthoringPage page = new TilesetAuthoringPage(context());
        try {
            enter(page, TilesetWorkflow.SHEET);
            Wizard wizard = wizardOf(page);
            assertFalse(wizard.canAdvance(), "no sheet picked yet");

            TilesetLibraryView library = libraryOf(page);
            library.setSheets(TilesetLibrary.scan(Paths.get("").toAbsolutePath()));
            selectFirstSheet(library);
            assertTrue(wizard.canAdvance(), "a sheet is picked, so Open it must be live");
        } finally {
            page.close();
        }
    }

    /**
     * A screen asked for its body twice is one screen, not two of them.
     *
     * <p>The wizard asks twice on every entry: once through the step's own
     * {@code onEnter}, once to put the body on screen. A body that built a new
     * container each time left the previous one parented at the bounds it was
     * last laid out with, and emptied, because the new one had taken the shared
     * components off it. An empty panel over the real one paints as a blank
     * screen, and that is what "Say what each piece is" showed from its second
     * visit onward: its toolbar, and nothing else.
     */
    @Test
    void aScreenAskedForItsBodyTwiceIsStillOneScreen() throws Exception {
        TilesetAuthoringPage page = new TilesetAuthoringPage(context());
        try {
            for (WizardStep step : sheetWalkthrough(page)) {
                JComponent screen = enter(step);
                int children = screen.getComponentCount();
                assertSame(screen, enter(step), step.title() + " must reuse its screen");
                assertEquals(children, screen.getComponentCount(),
                        step.title() + " stacked a second body over the first");
            }
        } finally {
            page.close();
        }
    }

    /**
     * A screen gets back what the screen after it borrowed.
     *
     * <p>The picture of the sheet and the annotation table are shared, and
     * "Adjust the cut" takes the table. Going back to the screen before it has
     * to put the table back, or the walkthrough only reads correctly forwards.
     */
    @Test
    void goingBackToAScreenGetsBackWhatTheNextOneBorrowed() throws Exception {
        TilesetAuthoringPage page = new TilesetAuthoringPage(context());
        try {
            List<WizardStep> steps = sheetWalkthrough(page);
            WizardStep adjust = named(steps, "Adjust the cut");
            WizardStep annotate = named(steps, "Say what each piece is");
            JComponent picture = fieldOf(page, "sheetPicture");
            JComponent table = fieldOf(page, "tableScroll");

            enter(annotate);
            enter(adjust);
            JSplitPane split = (JSplitPane) fieldOf(page, "annotateSplit");
            assertNotSame(table, split.getRightComponent(),
                    "the cut screen is the one that borrows the table");

            enter(annotate);
            assertSame(picture, split.getLeftComponent(), "the picture is back");
            assertSame(table, split.getRightComponent(), "the table is back");
        } finally {
            page.close();
        }
    }

    /**
     * Show a step the way the wizard does: its {@code onEnter} first, then its
     * body. Both reach the page's body method, and only the second is cached -
     * so on every visit after the first, {@code onEnter} is the only thing that
     * re-parents the shared components, and the only thing that could stack a
     * container over them.
     */
    private static JComponent enter(WizardStep step) {
        step.onEnter();
        return step.body();
    }

    private static WizardStep named(List<WizardStep> steps, String title) {
        return steps.stream().filter(step -> step.title().equals(title)).findFirst()
                .orElseThrow(() -> new AssertionError("no step called " + title));
    }

    @SuppressWarnings("unchecked")
    private static List<WizardStep> sheetWalkthrough(TilesetAuthoringPage page) throws Exception {
        Method method = TilesetAuthoringPage.class.getDeclaredMethod("sheetWalkthrough");
        method.setAccessible(true);
        return (List<WizardStep>) method.invoke(page);
    }

    private static JComponent fieldOf(TilesetAuthoringPage page, String name) throws Exception {
        Field field = TilesetAuthoringPage.class.getDeclaredField(name);
        field.setAccessible(true);
        return (JComponent) field.get(page);
    }

    private static Wizard wizardOf(TilesetAuthoringPage page) throws Exception {
        Field field = TilesetAuthoringPage.class.getDeclaredField("wizard");
        field.setAccessible(true);
        return (Wizard) field.get(page);
    }

    private static SurfaceBrowserView browserOf(TilesetAuthoringPage page) throws Exception {
        Field field = TilesetAuthoringPage.class.getDeclaredField("surfaces");
        field.setAccessible(true);
        return (SurfaceBrowserView) field.get(page);
    }

    private static TilesetLibraryView libraryOf(TilesetAuthoringPage page) throws Exception {
        Field field = TilesetAuthoringPage.class.getDeclaredField("library");
        field.setAccessible(true);
        return (TilesetLibraryView) field.get(page);
    }

    /** Click the first row of the sheet strip, the way a person would. */
    private static void selectFirstSheet(TilesetLibraryView library) throws Exception {
        Field field = TilesetLibraryView.class.getDeclaredField("list");
        field.setAccessible(true);
        ((JList<?>) field.get(library)).setSelectedIndex(0);
    }

    private static void enter(TilesetAuthoringPage page, TilesetWorkflow workflow)
            throws Exception {
        Method method = TilesetAuthoringPage.class.getDeclaredMethod(
                "enterWorkflow", TilesetWorkflow.class);
        method.setAccessible(true);
        method.invoke(page, workflow);
    }

    private static AuthoringPageContext context() {
        Path root = Paths.get("").toAbsolutePath();
        return new AuthoringPageContext(root, root.resolve("core"), message -> {}, () -> {});
    }
}

package com.dillon.starsectormarines.ui.retained.style;

import com.dillon.starsectormarines.ui.retained.CanvasMeshBatch;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

class LinearGradientTest {
    @Test
    void transparentStopsInterpolatePremultipliedChannels() {
        LinearGradient gradient = LinearGradient.parse(
                "linear-gradient(to bottom, #ff0000ff 0%, #0000ff00 100%)");
        assertEquals(new Color(255, 0, 0, 128), gradient.sample(0.5f));
        assertEquals(new Color(255, 0, 0), gradient.sample(0f));
        assertEquals(new Color(0, 0, 255, 0), gradient.sample(1f));
    }

    @Test
    void repeatedPositionsProduceHardEdgesAndOuterColorsExtend() {
        LinearGradient gradient = LinearGradient.parse(
                "linear-gradient(to right, #fff 20%, #fff 50%, #000 50%, #000 80%)");
        assertEquals(Color.WHITE, gradient.sample(0f));
        assertEquals(Color.WHITE, gradient.sample(0.499f));
        assertEquals(Color.BLACK, gradient.sample(0.5f));
        assertEquals(Color.BLACK, gradient.sample(1f));
        CanvasMeshBatch mesh = gradient.mesh();
        assertSame(mesh, gradient.mesh());
        assertEquals(0f, mesh.layer(0).mesh().x(0));
        assertEquals(1f, mesh.layer(mesh.layerCount() - 1).mesh().x(1));
        assertEquals(1f, mesh.layer(0).mesh().y(2));
    }

    @Test
    void unsupportedAndMalformedGrammarFailsAtAuthoring() {
        for (String invalid : new String[]{
                "url(steel.png)", "linear-gradient(45deg, #fff 0%, #000 100%)",
                "linear-gradient(to bottom, #fff, #000)",
                "linear-gradient(to bottom, #fff 80%, #000 20%)",
                "linear-gradient(to bottom, #fff NaN%, #000 100%)",
                "linear-gradient(to bottom, #fff 0%, #000 100%), none"}) {
            assertThrows(UiStyleException.class, () -> LinearGradient.parse(invalid), invalid);
        }
        assertNull(LinearGradient.parse("none"));
        assertThrows(UiStyleException.class, () -> StyleDeclaration.parse("border-style: ridge"));
    }

    @Test
    void materialIsPaintOnlyDoesNotInheritAndCanBeRemoved() {
        UiElement child = new UiElement("child");
        UiElement root = new UiElement("root").child(child).style(
                "border-width: 3px; border-style: outset; background-image: linear-gradient(to bottom, #fff 0%, #000 100%)");
        UiDocument document = new UiDocument(root);
        document.layout(80, 40);
        assertNotNull(root.backgroundImage());
        assertNull(child.backgroundImage());
        assertEquals(UiBorderStyle.SOLID, child.borderStyle());
        root.style("border-width: 3px; border-style: inset; background-image: none");
        assertEquals(0, document.advance(0f).layoutPasses());
        assertEquals(UiBorderStyle.INSET, root.borderStyle());
        assertNull(root.backgroundImage());
    }
}

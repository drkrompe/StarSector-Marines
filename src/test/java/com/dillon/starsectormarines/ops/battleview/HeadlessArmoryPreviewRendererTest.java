package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadlessArmoryPreviewRendererTest {

    @Test
    void controlledRasterContextIsDeterministicAndVisuallyPopulated() {
        HeadlessArmoryPreviewRenderer renderer =
                new HeadlessArmoryPreviewRenderer(Path.of("mod"));
        Set<Integer> caseHashes = new HashSet<>();

        for (HeadlessArmoryPreviewRenderer.PreviewCase preview
                : HeadlessArmoryPreviewRenderer.previewCases()) {
            BufferedImage first = renderer.render(preview.billet());
            BufferedImage second = renderer.render(preview.billet());
            int[] firstPixels = ((DataBufferInt) first.getRaster().getDataBuffer()).getData();
            int[] secondPixels = ((DataBufferInt) second.getRaster().getDataBuffer()).getData();

            assertEquals(ArmoryLoadoutPreviewComposer.SURFACE_WIDTH, first.getWidth());
            assertEquals(ArmoryLoadoutPreviewComposer.SURFACE_HEIGHT, first.getHeight());
            assertTrue(Arrays.equals(firstPixels, secondPixels), preview.slug());
            assertTrue(Arrays.stream(firstPixels).distinct().count() > 300,
                    preview.slug() + " should contain composed source art");
            caseHashes.add(Arrays.hashCode(firstPixels));
        }

        assertEquals(4, caseHashes.size(), "each controlled loadout should render distinctly");
    }
}

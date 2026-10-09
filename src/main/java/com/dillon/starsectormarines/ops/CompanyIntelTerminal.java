package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;

import java.awt.Color;

/** Shared live/preview wiring; its receiver clock survives ordinary document refresh. */
final class CompanyIntelTerminal {
    private final CompanyIntelCanvas network;
    private UiDocument document;
    private UiElement networkElement;
    private UiElement scanElement;

    CompanyIntelTerminal(CompanyHqNewsDesk desk) {
        network = new CompanyIntelCanvas(desk);
    }

    void bind(UiDocument document, MarkupInstance instance) {
        this.document = document;
        networkElement = instance.requireElement("company-hq-network-canvas");
        scanElement = instance.requireElement("company-hq-dossier-scan");
        document.canvases().set(networkElement, network);
        document.canvases().set(scanElement, this::dossierScan);
        advance(0f);
    }

    void advance(float dt) {
        network.advance(dt);
        if (document != null) {
            document.canvases().invalidate(networkElement);
            document.canvases().invalidate(scanElement);
        }
    }

    private void dossierScan(CanvasContext context) {
        float reveal = network.motion().selectionReveal();
        if (reveal >= 1f) return;
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        float edge = width * reveal;
        // A brief archive read sweep; report text never changes to fake decoded prose.
        context.fillRect(edge, 0f, width - edge, height, new Color(10, 21, 23, 205));
        for (int trail = 5; trail >= 0; trail--) {
            float x = Math.max(0f, edge - trail * 5f);
            context.fillRect(x, 0f, 3f, height, new Color(98, 210, 194, 12 + (5 - trail) * 8));
        }
    }
}

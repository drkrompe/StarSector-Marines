package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.audio.OperationRadio;
import com.dillon.starsectormarines.battle.audio.BattleRadioChatter.Cue;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.json.JSONObject;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Actual radio overlay, at 100% scale, with every cast portrait and the text-only fallback. */
public final class RadioPortraitSnapshotSuite implements SnapshotSuite {
    public String id() { return "radio-portraits"; }
    public String label() { return "Marine radio faces and subtitle readability"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        var catalog = OperationRadio.parse(new JSONObject(
                Files.readString(context.modRoot().resolve(OperationRadio.PATH))));
        var turns = new ArrayList<OperationRadio.Transmission>();
        for (var actor : catalog.actors()) {
            var line = actor.reports().values().stream().flatMap(List::stream)
                    .map(catalog.lines()::get).max(Comparator.comparingInt(l -> l.text().length())).orElseThrow();
            turns.add(new OperationRadio.Transmission(line, actor.label(), actor.portrait()));
        }
        var command = catalog.lines().get(catalog.actors().get(0).call());
        turns.add(new OperationRadio.Transmission(command, "COMPANY COMMAND", "graphics/portraits/castle.png"));
        var actor = catalog.actors().get(0);
        turns.add(new OperationRadio.Transmission(catalog.lines().get(actor.reports().get(Cue.CONTACT).get(0)),
                actor.label(), ""));
        SettingsAPI previous = Global.getSettings();
        Global.setSettings((SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(),
                new Class<?>[]{SettingsAPI.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getScreenScaleMult")) return 1f;
                    if (method.getName().equals("loadText"))
                        return Files.readString(context.modRoot().resolve((String) args[0]));
                    return previous == null ? null : method.invoke(previous, args);
                }));
        var overlay = new StorySubtitleOverlay();
        try {
            PositionAPI position = (PositionAPI) Proxy.newProxyInstance(PositionAPI.class.getClassLoader(),
                    new Class<?>[]{PositionAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getX", "getY" -> 0f;
                        case "getWidth" -> 1920f;
                        case "getHeight" -> 1080f;
                        default -> throw new AssertionError(method.getName());
                    });
            overlay.attach(position);
            var renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
            var sheet = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
            var graphics = sheet.createGraphics();
            try {
                graphics.setColor(new Color(21, 28, 26));
                graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
                for (int i = 0; i < turns.size(); i++) {
                    overlay.update(null, null, turns.get(i), 0);
                    var surface = overlay.surface();
                    graphics.drawImage(renderer.render(surface.document(),
                            (int) surface.viewport().documentWidth(), (int) surface.viewport().documentHeight()),
                            280 + (i % 2) * 720, 180 + (i / 2) * 180, null);
                }
            } finally { graphics.dispose(); }
            return List.of(new SnapshotArtifact("radio-panels.png", sheet));
        } finally {
            overlay.detach();
            Global.setSettings(previous);
        }
    }
}

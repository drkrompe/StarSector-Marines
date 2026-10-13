package com.dillon.starsectormarines.testsupport;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import org.json.JSONObject;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.function.UnaryOperator;

/** Loads the shipped localization catalog at the same SettingsAPI seam used in game. */
public final class HeadlessStrings implements AutoCloseable {
    private final SettingsAPI previous = Global.getSettings();
    public HeadlessStrings(Path modRoot) throws Exception {
        this(modRoot, UnaryOperator.identity());
    }
    /** Translation overlays exercise the same lookup without rewriting production assets. */
    public HeadlessStrings(Path modRoot, UnaryOperator<String> translation) throws Exception {
        String source = Files.readString(modRoot.resolve("data/strings/strings.json"));
        String uncommented = source.lines().filter(line -> !line.stripLeading().startsWith("#"))
                .collect(Collectors.joining("\n"));
        JSONObject catalog = new JSONObject(uncommented);
        Global.setSettings((SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(),
                new Class<?>[]{SettingsAPI.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getString") && args.length == 2)
                        return translation.apply(catalog.getJSONObject((String) args[0]).getString((String) args[1]));
                    if (method.getName().equals("loadText")) return Files.readString(modRoot.resolve((String) args[0]));
                    if (previous != null) return method.invoke(previous, args);
                    if (method.getReturnType() == float.class) return 1f;
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                }));
    }
    @Override public void close() { Global.setSettings(previous); }
}

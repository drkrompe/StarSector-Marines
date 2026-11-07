package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.ExpressionAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

/**
 * The game's own rule lines, run in {@code playBattle} with no game booted: every condition and script line of a
 * {@code rules.csv} row is parsed, scored, tested and run by the game's expression class, so matching, {@code score:},
 * memory reads and writes, and command lookup behave exactly as they do in the game.
 *
 * <p>Reached by reflection only: the game's jars are on {@code playBattle}'s runtime classpath and on no compile
 * classpath. Booting does what the game's loader would: it gives the expression class the {@code ruleCommandPackages}
 * list from settings, and the script store a class loader to look commands up in. The packages put
 * {@code playBattle}'s stand-ins ({@code playrules}) first, so a stand-in shadows the game command of the same name,
 * then the game's own packages, where the mod's rule commands also live.
 */
final class PlayRuleLines {

    /** The game's own command packages, from {@code settings.json}. */
    private static final List<String> GAME_PACKAGES = List.of(
            "com.fs.starfarer.api.impl.campaign.rulecmd",
            "com.fs.starfarer.api.impl.campaign.rulecmd.salvage",
            "com.fs.starfarer.api.impl.campaign.rulecmd.newgame",
            "com.fs.starfarer.api.impl.campaign.rulecmd.missions",
            "com.fs.starfarer.api.impl.campaign.rulecmd.academy");

    private static Constructor<?> lineType;
    private static Method isTrueFor;
    private static Method getScore;
    private static Constructor<?> memoryType;

    private PlayRuleLines() {}

    /** One condition or script line, parsed by the game. */
    record Line(String source, ExpressionAPI expression) {
        boolean test(String ruleId, InteractionDialogAPI dialog, Map<String, MemoryAPI> memory) {
            return (Boolean) call(isTrueFor, expression, ruleId, dialog, memory);
        }

        int score() {
            return (Integer) call(getScore, expression);
        }

        void run(String ruleId, InteractionDialogAPI dialog, Map<String, MemoryAPI> memory) {
            expression.execute(ruleId, dialog, memory);
        }
    }

    /** Boots the game's rule lines once; {@code standIns} is the package whose commands shadow the game's. */
    static synchronized void boot(String standIns) {
        if (lineType != null) return;
        try {
            JSONArray packages = new JSONArray().put(standIns);
            for (String name : GAME_PACKAGES) packages.put(name);
            seedSettings(new JSONObject().put("ruleCommandPackages", packages));
            seedScriptStore();
            Class<?> line = Class.forName("com.fs.starfarer.campaign.rules.oOOO");
            lineType = line.getConstructor(String.class);
            isTrueFor = line.getMethod("isTrueFor", String.class, InteractionDialogAPI.class, Map.class);
            getScore = line.getMethod("getScore");
            memoryType = Class.forName("com.fs.starfarer.campaign.rules.Memory").getConstructor();
        } catch (ReflectiveOperationException | JSONException failure) {
            throw new IllegalStateException("playBattle could not boot the game's rule lines: run it through"
                    + " gradlew playBattle, which puts the game's jars on its classpath", failure);
        }
    }

    static Line line(String source) {
        try {
            return new Line(source, (ExpressionAPI) lineType.newInstance(source));
        } catch (InvocationTargetException failure) {
            throw new IllegalArgumentException("rule line [" + source + "]: " + failure.getCause().getMessage(),
                    failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** The game's own memory, as every rule-based dialog's memory is. */
    static MemoryAPI memory() {
        try {
            return (MemoryAPI) memoryType.newInstance();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /**
     * The game keeps its settings JSON in a private static field that one public no-argument getter returns. The
     * field is found by setting each candidate and asking the getter, so no obfuscated name is written down here.
     */
    private static void seedSettings(JSONObject settings) throws ReflectiveOperationException {
        Class<?> type = Class.forName("com.fs.starfarer.settings.StarfarerSettings");
        Method getter = null;
        for (Method method : type.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers) && method.getParameterCount() == 0
                    && method.getReturnType() == JSONObject.class) {
                getter = method;
            }
        }
        if (getter == null) throw new NoSuchMethodException("the game's settings getter");
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != JSONObject.class) continue;
            field.setAccessible(true);
            Object before = field.get(null);
            field.set(null, settings);
            if (getter.invoke(null) == settings) return;
            field.set(null, before);
        }
        throw new NoSuchFieldException("the game's settings field");
    }

    /** Command lookup asks the script store's loader for its parent; this test tree's loader stands in. */
    private static void seedScriptStore() throws ReflectiveOperationException {
        Class<?> store = Class.forName("com.fs.starfarer.loading.scripts.ScriptStore");
        Class<?> loaderType = Class.forName("org.codehaus.janino.JavaSourceClassLoader");
        for (Field field : store.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == loaderType) {
                field.setAccessible(true);
                field.set(null, loaderType.getConstructor(ClassLoader.class)
                        .newInstance(PlayRuleLines.class.getClassLoader()));
                return;
            }
        }
        throw new NoSuchFieldException("the game's script store loader");
    }

    private static Object call(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(failure);
        }
    }
}

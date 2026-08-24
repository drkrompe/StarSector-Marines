package com.dillon.starsectormarines.battle.fixture;

import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BattleFixtureTestSupport {

    private static final String DEFAULT_RESOURCE =
            "/battle-fixtures/civilian-rescue-v1.json";

    private BattleFixtureTestSupport() {}

    public static BattleFixture loadDefaultFixture() throws Exception {
        try (InputStream stream = BattleFixtureTestSupport.class
                .getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Missing fixture resource: " + DEFAULT_RESOURCE);
            }
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return BattleFixtureJson.fromJson(new JSONObject(json));
        }
    }

    public static BattleFixture loadSelectedFixture() throws Exception {
        String fixturePath = System.getProperty("battle.fixture.path", "").trim();
        if (!fixturePath.isEmpty()) {
            return BattleFixtureJson.fromJson(new JSONObject(
                    Files.readString(Path.of(fixturePath))));
        }
        return loadDefaultFixture();
    }
}

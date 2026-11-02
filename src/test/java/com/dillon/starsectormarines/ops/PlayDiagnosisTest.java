package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayDiagnosisTest {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private static CompletableFuture<HttpResponse<String>> call(PlayDiagnosis server, String tool, String arguments) {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"" + tool
                + "\",\"arguments\":" + arguments + "}}";
        return CLIENT.sendAsync(HttpRequest.newBuilder(URI.create(server.url()))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static PlayDiagnosis server() throws Exception {
        return PlayDiagnosis.open("0", Path.of(""), Path.of(""));
    }

    @Test
    void aReadWaitsForTheFrameAndRunsOnItsThread() throws Exception {
        AtomicReference<Thread> asked = new AtomicReference<>();
        PlayDiagnosis.View view = new PlayDiagnosis.View() {
            @Override public BattleSimulation battle() {
                asked.set(Thread.currentThread());
                return null;
            }
            @Override public String screen() { return "BATTLE"; }
            @Override public String world() { return "Jangala"; }
            @Override public String controls() { return ""; }
        };
        try (PlayDiagnosis server = server()) {
            var response = call(server, "play_status", "{}");
            while (!response.isDone()) server.frame(view);
            assertSame(Thread.currentThread(), asked.get());
            assertTrue(response.get().body().contains("BATTLE"), response.get().body());
        }
    }

    @Test
    void aSteppingCommandIsRefusedWithNoFrameToAnswerIt() throws Exception {
        try (PlayDiagnosis server = server()) {
            JSONObject result = new JSONObject(call(server, "battle_query", "{\"commands\":\"tick; advance 30\"}").get().body())
                    .getJSONObject("result");
            assertTrue(result.getBoolean("isError"));
            assertTrue(result.toString().contains("advance 30"), result.toString());
        }
    }

    @Test
    void onlyACallerNamingThisMachineIsAnswered() {
        assertTrue(PlayDiagnosis.allowedCaller("127.0.0.1:47811", null));
        assertTrue(PlayDiagnosis.allowedCaller("localhost:47811", "http://localhost:3000"));
        assertFalse(PlayDiagnosis.allowedCaller("evil.example:47811", null));
        assertFalse(PlayDiagnosis.allowedCaller("127.0.0.1:47811", "https://evil.example"));
        assertFalse(PlayDiagnosis.allowedCaller(null, null));
    }
}

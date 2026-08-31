package com.dillon.starsectormarines.battle.weapon.fx;

import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponFxDefTest {

    @Test
    void parsesEverySlotAndScalarOrRangeShorthands() throws Exception {
        WeaponFxDef fx = WeaponFxDef.parse("fx.test", new JSONObject("""
                {
                  "launch": [{"kind":"smoke", "radius":0.35, "lifetime":0.8,
                              "offsetForward":-0.2, "velocityForward":-1.5}],
                  "muzzle": [{"kind":"glow", "radius":0.3, "lifetime":0.06, "color":"FFF0C0"}],
                  "tracer": [{"kind":"glow", "radius":[0.1,0.2], "lifetime":0.04}],
                  "trail": [{"kind":"smoke", "radius":[0.2,0.4], "lifetime":[0.4,0.8],
                             "count":[2,3], "jitter":0.25}],
                  "impact": [{"kind":"explosion", "radius":0.7, "lifetime":0.42},
                             {"kind":"ring", "radius":0.48, "lifetime":0.34}],
                  "aftermath": [{"kind":"smoke", "radius":[0.45,1.0], "lifetime":[0.9,1.7],
                                  "delay":[0.0,0.05], "emissionDuration":1.0,
                                  "emissionInterval":[0.2,0.3]}]
                }
                """));

        assertEquals("fx.test", fx.id);
        for (FxSlot slot : FxSlot.values()) assertFalse(fx.layers(slot).isEmpty(), slot.key);
        assertEquals(new FxFloatRange(0.3f, 0.3f), fx.layers(FxSlot.MUZZLE).get(0).radius());
        assertEquals(new FxFloatRange(0.1f, 0.2f), fx.layers(FxSlot.TRACER).get(0).radius());
        assertEquals(new FxIntRange(2, 3), fx.layers(FxSlot.TRAIL).get(0).count());
        assertThrows(UnsupportedOperationException.class,
                () -> fx.layers(FxSlot.IMPACT).add(fx.layers(FxSlot.IMPACT).get(0)));
        assertThrows(UnsupportedOperationException.class,
                () -> fx.slots().put(FxSlot.MUZZLE, List.of()));
    }

    @Test
    void localOffsetsAndVelocitiesRotateWithTheAuthoredBearing() throws Exception {
        WeaponFxDef fx = WeaponFxDef.parse("fx.backblast", new JSONObject("""
                {"launch":[{"kind":"smoke", "radius":0.3, "lifetime":1,
                  "offsetForward":-1, "offsetLateral":0.25,
                  "velocityForward":-2, "velocityLateral":0.5}]}
                """));

        FxParticleCommand command = WeaponFxComposer.compose(fx, FxSlot.LAUNCH,
                new FxCompositionContext(10f, 20f, -90f, false, 3f)).get(0);

        assertEquals(9f, command.x(), 0.0001f);
        assertEquals(19.75f, command.y(), 0.0001f);
        assertEquals(-2f, command.velocityX(), 0.0001f);
        assertEquals(-0.5f, command.velocityY(), 0.0001f);
    }

    @Test
    void seededCompositionIsStableAndSchedulesAftermath() throws Exception {
        WeaponFxDef fx = WeaponFxDef.parse("fx.cannon", new JSONObject("""
                {"aftermath":[
                  {"kind":"smoke", "radius":[0.45,1.0], "lifetime":[0.9,1.7],
                   "count":[1,2], "jitter":0.35, "delay":[0.0,0.05],
                   "emissionDuration":1.0, "emissionInterval":[0.2,0.3]}
                ]}
                """));
        FxCompositionContext context = new FxCompositionContext(4f, 7f, 90f, false, 12.5f);

        List<FxParticleCommand> first = WeaponFxComposer.compose(fx, FxSlot.AFTERMATH, context);
        List<FxParticleCommand> second = WeaponFxComposer.compose(fx, FxSlot.AFTERMATH, context);

        assertEquals(first, second);
        assertTrue(first.size() >= 4, "one-second plume should schedule several emissions");
        assertTrue(first.stream().allMatch(command -> command.delaySeconds() >= 0f));
        assertTrue(first.stream().allMatch(command -> command.kind() == FxLayerKind.SMOKE));
        assertNotEquals(first, WeaponFxComposer.compose(fx, FxSlot.AFTERMATH,
                new FxCompositionContext(4f, 7f, 90f, false, 12.75f)),
                "the stable event timestamp participates in the seed");
        assertTrue(WeaponFxComposer.compose(fx, FxSlot.MUZZLE, context).isEmpty());
    }

    @Test
    void installedCatalogsAuthorEveryImpactAndPreserveCanonicalLayerOrder() throws Exception {
        WeaponRegistry registry = new WeaponRegistry();
        for (String path : WeaponRegistry.BUILTIN_CATALOGS) {
            registry.ingest(new JSONObject(Files.readString(Path.of("mod", path))));
        }

        assertFalse(registry.all().isEmpty(), "the built-in weapon catalogs must load");
        FxCompositionContext context = new FxCompositionContext(3f, 5f, 0f, false, 7f);
        for (WeaponDef weapon : registry.all()) {
            assertFalse(weapon.fx.layers(FxSlot.IMPACT).isEmpty(), weapon.id);
            assertEquals(WeaponFxComposer.compose(weapon.fx, FxSlot.IMPACT, context),
                    WeaponFxComposer.compose(weapon.fx, FxSlot.IMPACT, context), weapon.id);
        }

        assertKinds(registry.get("weapon.field-rifle").fx,
                FxLayerKind.GLOW, FxLayerKind.DUST);
        assertKinds(registry.get("weapon.dmr").fx,
                FxLayerKind.GLOW, FxLayerKind.DUST, FxLayerKind.SMOKE);
        assertKinds(registry.get("weapon.frag-grenade").fx,
                FxLayerKind.GLOW, FxLayerKind.FIRE, FxLayerKind.SMOKE, FxLayerKind.DUST);
        assertKinds(registry.get("weapon.mech-heavy-cannon").fx,
                FxLayerKind.GLOW, FxLayerKind.EXPLOSION, FxLayerKind.RING,
                FxLayerKind.FIRE, FxLayerKind.SMOKE, FxLayerKind.DUST);
        assertKinds(registry.get("weapon.mech-shoulder-laser").fx,
                FxLayerKind.GLOW, FxLayerKind.EXPLOSION, FxLayerKind.RING,
                FxLayerKind.SMOKE, FxLayerKind.DUST);

        FxLayerDef rifleGlow = registry.get("weapon.field-rifle").fx
                .layers(FxSlot.IMPACT).get(0);
        assertEquals(new FxFloatRange(.28f, .28f), rifleGlow.radius());
        assertEquals(new FxFloatRange(.10f, .10f), rifleGlow.lifetime());
        FxLayerDef kineticSmoke = registry.get("weapon.dmr").fx
                .layers(FxSlot.IMPACT).get(2);
        assertEquals(new FxFloatRange(.35f, .35f), kineticSmoke.radius());
        assertEquals(new FxFloatRange(.70f, .70f), kineticSmoke.lifetime());
        FxLayerDef heSmoke = registry.get("weapon.frag-grenade").fx
                .layers(FxSlot.IMPACT).get(2);
        assertEquals(new FxFloatRange(.55f, .80f), heSmoke.radius());
        assertEquals(new FxFloatRange(1.10f, 1.50f), heSmoke.lifetime());
        assertEquals(new FxIntRange(2, 3), heSmoke.count());
        assertEquals(.45f, heSmoke.jitter(), .0001f);
    }

    @Test
    void dustResolvesSurfaceTintBeforeReachingABackend() throws Exception {
        WeaponFxDef fx = WeaponFxDef.parse("fx.dust", new JSONObject("""
                {"impact":[{"kind":"dust", "radius":0.4, "lifetime":0.2}]}
                """));

        FxParticleCommand floor = WeaponFxComposer.compose(fx, FxSlot.IMPACT,
                new FxCompositionContext(0f, 0f, 0f, false, 0f)).get(0);
        FxParticleCommand wall = WeaponFxComposer.compose(fx, FxSlot.IMPACT,
                new FxCompositionContext(0f, 0f, 0f, true, 0f)).get(0);

        assertNotEquals(floor.color(), wall.color());
        assertEquals(FxBlend.NORMAL, floor.blend());
        assertEquals(FxBlend.NORMAL, wall.blend());
    }

    @Test
    void malformedAuthoringFailsLoudlyAtParseTime() {
        assertInvalid("{\"impact\":[{\"kind\":\"embers\",\"radius\":1,\"lifetime\":1}]}",
                "unknown layer kind");
        assertInvalid("{\"impact\":[{\"kind\":\"glow\",\"radius\":[2,1],\"lifetime\":1}]}",
                "minimum exceeds maximum");
        assertInvalid("{\"impact\":[{\"kind\":\"glow\",\"radius\":1,\"lifetime\":1,\"lifetiem\":2}]}",
                "unknown fields");
        assertInvalid("{\"impact\":[{\"kind\":\"smoke\",\"radius\":1,\"lifetime\":1,\"emissionDuration\":2}]}",
                "emissionInterval is required");
        assertInvalid("{\"impact\":[{\"kind\":\"smoke\",\"radius\":1,\"lifetime\":1,\"emissionDuration\":100,\"emissionInterval\":0.001,\"count\":100}]}",
                "maximum is");
        assertInvalid("{\"impact\":[]}", "may not be empty");
        assertInvalid("{\"after-math\":[{\"kind\":\"smoke\",\"radius\":1,\"lifetime\":1}]}",
                "unknown slot");
    }

    private static void assertInvalid(String json, String messageFragment) {
        JSONException error = assertThrows(JSONException.class,
                () -> WeaponFxDef.parse("fx.bad", new JSONObject(json)));
        assertTrue(error.getMessage().contains(messageFragment), error.getMessage());
    }

    private static void assertKinds(WeaponFxDef fx, FxLayerKind... expected) {
        assertEquals(List.of(expected), fx.layers(FxSlot.IMPACT).stream()
                .map(FxLayerDef::kind)
                .toList());
    }
}

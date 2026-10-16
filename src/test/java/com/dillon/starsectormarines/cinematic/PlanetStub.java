package com.dillon.starsectormarines.cinematic;

import com.fs.starfarer.api.campaign.PlanetAPI;

import java.lang.reflect.Proxy;

/** A planet that answers only {@code getId}. */
record PlanetStub(String id) {
    PlanetAPI api() {
        return (PlanetAPI) Proxy.newProxyInstance(PlanetAPI.class.getClassLoader(), new Class<?>[] {PlanetAPI.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getId")) return id;
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}

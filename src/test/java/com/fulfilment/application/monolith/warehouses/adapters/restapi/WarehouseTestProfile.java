package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class WarehouseTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.profile", "test",
                "quarkus.http.test-port", "8081"
        );
    }

    @Override
    public String getConfigProfile() {
        return "test";
    }
}
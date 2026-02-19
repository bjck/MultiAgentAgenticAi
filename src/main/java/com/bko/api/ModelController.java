package com.bko.api;

import com.bko.config.MultiAgentProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.List;

@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final MultiAgentProperties properties;
    private final RestClient restClient;

    public ModelController(MultiAgentProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.getOpenai().getBaseUrl())
                .build();
    }

    @GetMapping
    public ModelListResponse getModels() {
        var spec = restClient.get().uri("/models");
        String apiKey = properties.getOpenai().getApiKey();
        String headerValue;
        if (apiKey == null || apiKey.isBlank() || apiKey.equalsIgnoreCase("none")) {
            // Send empty bearer token when key is missing or explicitly set to 'none'
            headerValue = "Bearer ";
        } else {
            headerValue = "Bearer " + apiKey;
        }
        spec = spec.header("Authorization", headerValue);
        return spec.retrieve().body(ModelListResponse.class);
    }

    public record ModelListResponse(String object, List<ModelData> data) {}
    public record ModelData(String id, String object, long created, String owned_by) {}
}

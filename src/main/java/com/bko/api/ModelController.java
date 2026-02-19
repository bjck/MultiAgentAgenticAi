package com.bko.api;

import com.bko.config.MultiAgentProperties;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final MultiAgentProperties properties;
    private final RestClient openAiClient;
    private final RestClient googleClient;

    public ModelController(MultiAgentProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.openAiClient = restClientBuilder
                .baseUrl(properties.getOpenai().getBaseUrl())
                .build();
        this.googleClient = restClientBuilder
                .baseUrl(properties.getGoogle().getBaseUrl())
                .build();
    }

    @GetMapping
    public ModelListResponse getModels(@RequestParam(value = "provider", required = false) String providerParam) {
        String provider = StringUtils.hasText(providerParam) ? providerParam.toUpperCase() : properties.getAiProvider().name();
        if ("OPENAI".equals(provider)) {
            return fetchOpenAiModels();
        }
        // Default to Google
        return fetchGoogleModels();
    }

    private ModelListResponse fetchOpenAiModels() {
        var spec = openAiClient.get().uri("/models");
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

    private ModelListResponse fetchGoogleModels() {
        // Google Generative Language API: GET {baseUrl}/models with x-goog-api-key header
        var spec = googleClient.get().uri("/models");
        String apiKey = properties.getGoogle().getApiKey();
        if (apiKey == null) apiKey = "";
        spec = spec.header("x-goog-api-key", apiKey);
        GoogleModelListResponse googleResponse = spec.retrieve().body(GoogleModelListResponse.class);
        List<ModelData> data = new ArrayList<>();
        if (googleResponse != null && googleResponse.models != null) {
            for (GoogleModel m : googleResponse.models) {
                String fullName = m.name(); // e.g., "models/gemini-2.5-flash"
                String id = fullName != null && fullName.contains("/") ? fullName.substring(fullName.lastIndexOf('/') + 1) : fullName;
                if (!StringUtils.hasText(id)) {
                    id = fullName; // fallback
                }
                data.add(new ModelData(id, "model", 0L, "google"));
            }
        }
        return new ModelListResponse("list", data);
    }

    public record ModelListResponse(String object, List<ModelData> data) {}
    public record ModelData(String id, String object, long created, String owned_by) {}

    // Minimal mapping records for Google API response
    public record GoogleModelListResponse(List<GoogleModel> models) {}
    public record GoogleModel(String name, String displayName, String description) {}
}

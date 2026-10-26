package com.careersphere.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

/**
 * Thin client for the Gemini generateContent REST API.
 * The API key stays on the server - React never sees it.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private static final int RETRIES = 3;

    private final String apiKey;
    private final String model;
    private final String fallbackModel;
    private final RestClient http;
    private final ObjectMapper mapper;

    public GeminiClient(@Value("${app.ai.gemini.api-key:}") String apiKey,
                        @Value("${app.ai.gemini.model:gemini-flash-latest}") String model,
                        @Value("${app.ai.gemini.fallback-model:gemini-flash-lite-latest}") String fallbackModel,
                        ObjectMapper mapper) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.fallbackModel = fallbackModel == null ? "" : fallbackModel.trim();
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.http = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .requestFactory(factory)
                .build();
        log.info(isConfigured() ? "Copilot: Gemini model {}" : "Copilot: no GEMINI_API_KEY set, using offline mode", model);
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    public String model() {
        return model;
    }

    /**
     * Sends one generateContent request.
     * @param system   system instruction text
     * @param contents conversation turns (user / model / function responses), passed through unchanged
     * @param tools    the "tools" array with functionDeclarations
     * @return the first candidate's "content" object (role + parts), exactly as Gemini returned it
     */
    public JsonNode generate(String system, ArrayNode contents, ArrayNode tools) {
        ObjectNode body = mapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", system);
        body.set("contents", contents);
        body.set("tools", tools);
        body.putObject("generationConfig").put("temperature", 0.3);
        return post(body);
    }

    /** One-shot request that asks Gemini to answer with JSON only. Returns the JSON text. */
    public String generateJson(String system, String userText) {
        ObjectNode body = mapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", system);
        ObjectNode turn = body.putArray("contents").addObject();
        turn.put("role", "user");
        turn.putArray("parts").addObject().put("text", userText);
        body.putObject("generationConfig").put("temperature", 0.1).put("responseMimeType", "application/json");
        StringBuilder text = new StringBuilder();
        for (JsonNode part : post(body).path("parts")) {
            if (part.has("text") && !part.path("thought").asBoolean(false)) text.append(part.get("text").asText());
        }
        return text.toString();
    }

    /**
     * Sends the request to the main model. If Google says the model is busy (500/503/504), waits and
     * retries, then tries the lighter backup model once before giving up.
     */
    private JsonNode post(ObjectNode body) {
        for (int attempt = 1; attempt <= RETRIES; attempt++) {
            try {
                return postOnce(model, body);
            } catch (ModelBusyException e) {
                log.info("Gemini model {} busy (attempt {} of {})", model, attempt, RETRIES);
                if (attempt < RETRIES) pause(attempt * 1500L);
            }
        }
        if (!fallbackModel.isEmpty() && !fallbackModel.equals(model)) {
            try {
                log.info("Trying backup model {}", fallbackModel);
                return postOnce(fallbackModel, body);
            } catch (ModelBusyException e) {
                // backup model busy too
            }
        }
        throw new AiUnavailableException("Gemini is busy right now (high demand at Google). Try again in a minute.");
    }

    private JsonNode postOnce(String useModel, ObjectNode body) {
        try {
            JsonNode res = http.post()
                    .uri("/models/{model}:generateContent", useModel)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode content = res == null ? null : res.path("candidates").path(0).path("content");
            if (content == null || content.isMissingNode() || !content.has("parts")) {
                throw new AiUnavailableException("The AI returned an empty answer.");
            }
            return content;
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            log.warn("Gemini error {} from {}: {}", code, useModel, e.getResponseBodyAsString());
            if (code == 500 || code == 503 || code == 504) throw new ModelBusyException();
            if (code == 429) throw new AiUnavailableException("The AI's free quota is used up for now. Try again in a minute.");
            if (code == 400 || code == 401 || code == 403) throw new AiUnavailableException("The AI rejected the request. Check GEMINI_API_KEY and GEMINI_MODEL.");
            if (code == 404) throw new AiUnavailableException("Gemini model " + useModel + " was not found. Check GEMINI_MODEL.");
            throw new AiUnavailableException("The AI service is not responding right now.");
        } catch (AiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Gemini call failed", e);
            throw new AiUnavailableException("Could not reach the AI service.");
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Google reported the model as overloaded; worth retrying. */
    private static class ModelBusyException extends AiUnavailableException {
        ModelBusyException() { super("Gemini is busy right now."); }
    }

    /** Raised when the AI cannot answer; the Copilot then falls back to offline mode. */
    public static class AiUnavailableException extends RuntimeException {
        public AiUnavailableException(String message) { super(message); }
    }
}

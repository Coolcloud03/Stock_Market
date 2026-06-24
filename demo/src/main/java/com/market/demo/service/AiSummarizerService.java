package com.market.demo.service;

import com.market.demo.model.AiSummaryResponse;
import com.market.demo.model.FinnhubNewsDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AiSummarizerService {

    private static final Logger log = LoggerFactory.getLogger(AiSummarizerService.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final String openAiApiKey;
    private final String openAiModel;

    public AiSummarizerService(
            @Value("${openai.api-key:}") String openAiApiKey,
            @Value("${openai.model:gpt-4o-mini}") String openAiModel
    ) {
        this.openAiApiKey = openAiApiKey;
        this.openAiModel = openAiModel;
    }

    public AiSummaryResponse summarizeMarket(String symbol, List<FinnhubNewsDto> news, double latestPrice) {
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            String fallback = buildFallbackSummary(symbol, news, latestPrice);
            return new AiSummaryResponse(fallback, "fallback");
        }

        String prompt = buildPrompt(symbol, news, latestPrice);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);

        Map<String, Object> request = Map.of(
                "model", openAiModel,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a financial summarization assistant."),
                        Map.of("role", "user", "content", prompt)
                )
        );

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity("https://api.openai.com/v1/chat/completions", entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<?, ?> body = response.getBody();
                List<?> choices = (List<?>) body.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Object firstChoice = choices.get(0);
                    if (firstChoice instanceof Map<?, ?> choiceMap) {
                        Map<?, ?> message = (Map<?, ?>) choiceMap.get("message");
                        if (message != null) {
                            String summary = String.valueOf(message.get("content"));
                            return new AiSummaryResponse(summary.trim(), openAiModel);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to call OpenAI API for market summary", e);
        }

        String fallback = buildFallbackSummary(symbol, news, latestPrice);
        return new AiSummaryResponse(fallback, "fallback");
    }

    private String buildPrompt(String symbol, List<FinnhubNewsDto> news, double latestPrice) {
        String headlines = news.stream()
                .map(n -> "- " + n.headline())
                .collect(Collectors.joining("\n"));

        String source = news.stream()
                .map(n -> "Headline: " + n.headline() + "\nSummary: " + (n.summary() == null ? "" : n.summary()))
                .collect(Collectors.joining("\n\n"));

        return "Summarize the market situation for " + symbol + ". " +
                "Current latest price is $" + String.format("%.2f", latestPrice) + ". " +
                "Use the headlines and news summaries below to produce a short market summary. " +
                "Mention whether the news appears bullish, bearish, or neutral and any notable price context.\n\n" +
                "Headlines:\n" + headlines + "\n\n" +
                "Details:\n" + source + "\n\n" +
                "Provide a concise summary in 3-4 sentences.";
    }

    private String buildFallbackSummary(String symbol, List<FinnhubNewsDto> news, double latestPrice) {
        if (news == null || news.isEmpty()) {
            return "No news available for " + symbol + ". Latest price is $" + String.format("%.2f", latestPrice) + ".";
        }

        String topHeadlines = news.stream()
                .limit(3)
                .map(n -> n.headline())
                .collect(Collectors.joining("; "));
        return "Latest price for " + symbol + " is $" + String.format("%.2f", latestPrice) + ". Top headlines: " + topHeadlines + ".";
    }
}

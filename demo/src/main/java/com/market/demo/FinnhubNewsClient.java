package com.market.demo;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class FinnhubNewsClient {

    private static final Logger log = LoggerFactory.getLogger(FinnhubNewsClient.class);
    private static final String FINNHUB_NEWS_URL = "https://finnhub.io/api/v1/company-news";
    private static final String DEFAULT_SYMBOL = "AAPL";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final RestTemplate restClient = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private List<FinnhubNews> latestNews = new ArrayList<>();

    public FinnhubNewsClient(@Value("${finnhub.api-key}") String apiKey) {
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
    }

    @PostConstruct
    public void validateApiKey() {
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("dummy") || apiKey.contains("000")) {
            log.warn("Finnhub API key is not configured or is a placeholder. Please set finnhub.api-key in application.properties.");
        } else {
            log.info("Finnhub API key loaded successfully.");
        }
    }

    @Scheduled(fixedDelay = 300000) // 5 minutes in milliseconds
    public void fetchLatestNews() {
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("dummy") || apiKey.contains("000")) {
            log.warn("Skipping Finnhub news fetch because API key is not configured or invalid.");
            return;
        }

        log.info("Fetching latest news for {} from Finnhub API...", DEFAULT_SYMBOL);

        LocalDate todayDate = LocalDate.now();
        LocalDate yesterdayDate = todayDate.minusDays(1);

        try {
            List<FinnhubNews> news = fetchCompanyNews(DEFAULT_SYMBOL, yesterdayDate, todayDate);
            if (news != null && !news.isEmpty()) {
                latestNews = news;
                log.info("Successfully fetched {} news articles for {}", latestNews.size(), DEFAULT_SYMBOL);
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("Received 429 Too Many Requests error from Finnhub API. Retrying in next cycle.");
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.warn("Finnhub API returned 401 Unauthorized. Please verify the API key in application.properties.");
            } else {
                log.error("HTTP error fetching news from Finnhub API", e);
            }
        } catch (Exception e) {
            log.error("Unexpected error fetching news from Finnhub API", e);
        }
    }

    /**
     * Fetch company news for a given symbol and date range (inclusive).
     * Dates are provided as LocalDate and formatted as YYYY-MM-DD.
     */
    public List<FinnhubNews> fetchCompanyNews(String symbol, LocalDate from, LocalDate to) {
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("dummy") || apiKey.contains("000")) {
            log.warn("Skipping Finnhub news fetch because API key is not configured or invalid.");
            return List.of();
        }

        String fromStr = from.format(DATE_FORMATTER);
        String toStr = to.format(DATE_FORMATTER);

        String url = UriComponentsBuilder.fromUriString(FINNHUB_NEWS_URL)
                .queryParam("symbol", symbol)
                .queryParam("from", fromStr)
                .queryParam("to", toStr)
                .queryParam("token", apiKey)
                .toUriString();

        try {
            String response = restClient.getForObject(url, String.class);
            if (response != null && !response.isBlank()) {
                return parseNewsResponse(response);
            }
        } catch (HttpClientErrorException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching company news from Finnhub", e);
        }

        return List.of();
    }

    private List<FinnhubNews> parseNewsResponse(String jsonResponse) {
        List<FinnhubNews> newsList = new ArrayList<>();

        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            if (rootNode.isArray()) {
                for (JsonNode article : rootNode) {
                    String headline = article.has("headline") && !article.get("headline").isNull()
                            ? article.get("headline").asText()
                            : "N/A";
                    String summary = article.has("summary") && !article.get("summary").isNull()
                            ? article.get("summary").asText()
                            : "N/A";
                    String url = article.has("url") && !article.get("url").isNull()
                            ? article.get("url").asText()
                            : "N/A";
                    long datetime = article.has("datetime") && !article.get("datetime").isNull()
                            ? article.get("datetime").asLong()
                            : 0L;

                    newsList.add(new FinnhubNews(headline, summary, url, datetime));
                }
            }
        } catch (Exception e) {
            log.error("Error parsing news JSON response", e);
        }

        return newsList;
    }

    public List<FinnhubNews> getLatestNews() {
        return new ArrayList<>(latestNews);
    }

    /**
     * Java Record representing a news article from Finnhub API
     */
    public record FinnhubNews(String headline, String summary, String url, long datetime) {
    }
}

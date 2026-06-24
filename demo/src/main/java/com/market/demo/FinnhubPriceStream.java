package com.market.demo;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class FinnhubPriceStream extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(FinnhubPriceStream.class);
    private final String apiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();
    public long tradeCount = 0;
    private final List<FinnhubTrade> recentTrades = new CopyOnWriteArrayList<>();
    private static final int MAX_TRADES_STORED = 100;
    private volatile boolean isConnected = false;
    private volatile long lastMessageTime = 0;

    public FinnhubPriceStream(@Value("${finnhub.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connectOnStartup() {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("Finnhub API key is not configured. Please set finnhub.api-key in application.properties.");
            return;
        }

        String finnhubUri = "wss://ws.finnhub.io?token=" + apiKey;
        StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
        try {
            webSocketClient.execute(this, finnhubUri).whenComplete((session, ex) -> {
                if (ex != null) {
                    log.error("Failed to establish Finnhub websocket connection.", ex);
                    isConnected = false;
                } else {
                    log.info("Finnhub websocket connection established.");
                    isConnected = true;
                }
            });
        } catch (Exception ex) {
            log.error("Unexpected error while connecting to Finnhub websocket.", ex);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            String subscribePayload = "{\"type\":\"subscribe\",\"symbol\":\"AAPL\"}";
            session.sendMessage(new TextMessage(subscribePayload));
            log.info("Subscribed to AAPL trades on Finnhub websocket.");
        } catch (Exception ex) {
            log.error("Failed to send subscribe message to Finnhub websocket.", ex);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        lastMessageTime = System.currentTimeMillis();
        log.debug("Raw Finnhub websocket payload: {}", payload);
        
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.has("data") && root.get("data").isArray() && root.get("data").size() > 0) {
                for (JsonNode tradeNode : root.get("data")) {
                    double price = tradeNode.path("p").asDouble(0.0);
                    String symbol = tradeNode.path("s").asText("UNKNOWN");
                    long volume = tradeNode.path("v").asLong(0L);
                    long timestamp = tradeNode.path("t").asLong(0L);
                    double bid = tradeNode.path("bp").asDouble(0.0);
                    double ask = tradeNode.path("ap").asDouble(0.0);
                    
                    FinnhubTrade trade = new FinnhubTrade(price, symbol, volume, timestamp, bid, ask);
                    tradeCount++;
                    
                    // Store recent trade in memory
                    recentTrades.add(0, trade);
                    if (recentTrades.size() > MAX_TRADES_STORED) {
                        recentTrades.remove(recentTrades.size() - 1);
                    }
                    
                    log.info("Trade #{}: {} | Price: ${} | Volume: {} | Bid: ${} | Ask: ${} | Timestamp: {}",
                            tradeCount, symbol, String.format("%.2f", price), volume,
                            String.format("%.2f", bid), String.format("%.2f", ask), timestamp);
                }
            } else {
                log.debug("Received non-trade websocket message (possibly pong/status update): {}", payload);
            }
        } catch (Exception ex) {
            log.error("Failed to parse incoming Finnhub websocket message: {}", payload, ex);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Transport error on Finnhub websocket.", exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        isConnected = false;
        log.info("Finnhub websocket connection closed: {}", status);
    }

    public List<FinnhubTrade> getRecentTrades() {
        return new ArrayList<>(recentTrades);
    }

    public Map<String, Object> getStatus() {
        return Map.of(
            "isConnected", isConnected,
            "totalTradesReceived", tradeCount,
            "lastMessageTimeMs", lastMessageTime,
            "lastMessageAgeMs", System.currentTimeMillis() - lastMessageTime,
            "recentTradeCount", recentTrades.size()
        );
    }

    public double getLatestPrice(String symbol) {
        return recentTrades.stream()
                .filter(trade -> trade.symbol().equalsIgnoreCase(symbol))
                .findFirst()
                .map(FinnhubTrade::price)
                .orElse(0.0);
    }
}

@RestController
class TradeController {
    private final FinnhubPriceStream finnhubPriceStream;
    private final FinnhubNewsClient finnhubNewsClient;

    public TradeController(FinnhubPriceStream finnhubPriceStream, FinnhubNewsClient finnhubNewsClient) {
        this.finnhubPriceStream = finnhubPriceStream;
        this.finnhubNewsClient = finnhubNewsClient;
    }

    @GetMapping("/api/trades")
    public Map<String, Object> getTrades() {
        List<FinnhubTrade> trades = finnhubPriceStream.getRecentTrades();
        return Map.of(
            "totalTradesReceived", finnhubPriceStream.tradeCount,
            "recentTrades", trades
        );
    }

    @GetMapping("/api/status")
    public Map<String, Object> getStatus() {
        return finnhubPriceStream.getStatus();
    }

    @GetMapping("/api/news")
    public Map<String, Object> getLatestNews() {
        return Map.of(
            "symbol", "AAPL",
            "articles", finnhubNewsClient.getLatestNews()
        );
    }
}

record FinnhubTrade(double price, String symbol, long volume, long timestamp, double bid, double ask) {
}

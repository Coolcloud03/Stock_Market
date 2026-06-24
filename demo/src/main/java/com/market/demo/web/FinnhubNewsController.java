package com.market.demo.web;

import com.market.demo.FinnhubPriceStream;
import com.market.demo.model.AiSummaryResponse;
import com.market.demo.model.FinnhubNewsDto;
import com.market.demo.service.AiSummarizerService;
import com.market.demo.service.FinnhubNewsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/company-news")
public class FinnhubNewsController {

    private final FinnhubNewsService newsService;
    private final AiSummarizerService aiSummarizerService;
    private final FinnhubPriceStream priceStream;

    public FinnhubNewsController(FinnhubNewsService newsService,
                                 AiSummarizerService aiSummarizerService,
                                 FinnhubPriceStream priceStream) {
        this.newsService = newsService;
        this.aiSummarizerService = aiSummarizerService;
        this.priceStream = priceStream;
    }

    @GetMapping
    public ResponseEntity<List<FinnhubNewsDto>> getCompanyNews(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<FinnhubNewsDto> news = newsService.getCompanyNews(symbol, from, to);
        return ResponseEntity.ok(news);
    }

    @GetMapping("/openai-summary")
    public ResponseEntity<AiSummaryResponse> getMarketSummary(
            @RequestParam String symbol
    ) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        List<FinnhubNewsDto> news = newsService.getCompanyNews(symbol, yesterday, today);
        double latestPrice = priceStream.getLatestPrice(symbol);
        AiSummaryResponse summary = aiSummarizerService.summarizeMarket(symbol, news, latestPrice);
        return ResponseEntity.ok(summary);
    }
}

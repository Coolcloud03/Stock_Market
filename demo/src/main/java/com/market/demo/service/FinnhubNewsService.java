package com.market.demo.service;

import com.market.demo.FinnhubNewsClient;
import com.market.demo.model.FinnhubNewsDto;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FinnhubNewsService {

    private final FinnhubNewsClient newsClient;

    public FinnhubNewsService(FinnhubNewsClient newsClient) {
        this.newsClient = newsClient;
    }

    public List<FinnhubNewsDto> getCompanyNews(String symbol, LocalDate from, LocalDate to) {
        return newsClient.fetchCompanyNews(symbol, from, to).stream()
                .map(n -> new FinnhubNewsDto(null, n.datetime(), n.headline(), n.summary(), n.url()))
                .collect(Collectors.toList());
    }
}

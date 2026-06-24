package com.market.demo.model;

public record FinnhubNewsDto(
        String category,
        long datetime,
        String headline,
        String summary,
        String url
) {
}

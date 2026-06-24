CITS1242t
two pojects combined 
AI/ML Integration in Java
Real-time Stock Monitor

# Finnhub Market News Demo

A Spring Boot Java application that retrieves stock market news and live trade prices from Finnhub, then summarizes today’s news using OpenAI.

## What it does

- Fetches company news from the Finnhub REST API
- Streams live stock trades through Finnhub WebSocket
- Summarizes only today’s news via OpenAI to keep token usage minimal
- Exposes REST endpoints for news and AI-generated summaries

## Technology stack

- Java 17
- Spring Boot 4.1.0
- Spring Web MVC
- Spring WebSocket
- Jackson JSON support
- Maven build

## Key features

- `GET /company-news?symbol={symbol}&from={date}&to={date}`
  - Returns company news articles for the given date range
- `GET /company-news/openai-summary?symbol={symbol}`
  - Summarizes only today’s news headlines and recent stock price
  - Uses OpenAI model configured by `openai.model`
- Live trade streaming via `FinnhubPriceStream`
- Scheduled news refresh from Finnhub in `FinnhubNewsClient`

## Configuration

Update `demo/src/main/resources/application.properties` with your keys:

```properties
spring.application.name=demo
finnhub.api-key=YOUR_FINNHUB_API_KEY
openai.api-key=YOUR_OPENAI_API_KEY
openai.model=gpt-5.4mini
```

## Build and run

From the `demo` folder:

```bash
cd demo
mvn -DskipTests package
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

## Example usage

Fetch today's news for AAPL:

```bash
curl "http://localhost:8080/company-news?symbol=AAPL&from=2026-06-23&to=2026-06-23"
```

Get today-only AI summary for AAPL:

```bash
curl "http://localhost:8080/company-news/openai-summary?symbol=AAPL"
```

## Notes

- The AI summary endpoint is intentionally restricted to today’s news to reduce OpenAI usage.
- The current implementation uses `LocalDate.now()` for the summary query period.
- Keep API keys secure and do not commit them to source control.

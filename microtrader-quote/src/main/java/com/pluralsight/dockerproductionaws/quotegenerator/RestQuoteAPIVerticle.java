package com.pluralsight.dockerproductionaws.quotegenerator;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by jmenga on 13/09/16.
 */
public class RestQuoteAPIVerticle extends AbstractVerticle {
    private Config config = ConfigFactory.load();
    private Map<String, JsonObject> quotes = new HashMap<>();

    @Override
    public void start() throws Exception {
        // Update quotes with market data
        vertx.eventBus().<JsonObject>consumer(config.getString("market.address"))
                .handler(message -> {
                    JsonObject quote = message.body();
                    quotes.put(quote.getString("name"), quote);
                });

        vertx.createHttpServer()
                .requestHandler(request -> {
                    HttpServerResponse response = request.response().putHeader("Content-Type", "application/json");
                    String company = request.getParam("name");
                    if (company == null) {
                        String content = Json.encodePrettily(quotes);
                        response.end(content);
                    } else {
                        JsonObject quote = quotes.get(company);
                        if (quote == null) {
                            response.setStatusCode(404).end();
                        } else {
                            response.end(quote.encodePrettily());
                        }
                    }
                })
                .listen(config.getInt("http.port"), ar -> {
                    if (ar.succeeded()) {
                        System.out.println("Server started");
                    } else {
                        System.out.println("Cannot start the server: " + ar.cause());
                    }
                });
    }
}

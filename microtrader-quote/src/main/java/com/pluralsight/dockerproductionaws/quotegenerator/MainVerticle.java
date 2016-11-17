package com.pluralsight.dockerproductionaws.quotegenerator;

import com.pluralsight.dockerproductionaws.common.MicroserviceVerticle;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Created by jmenga on 13/09/16.
 */
public class MainVerticle extends MicroserviceVerticle {

    private Config config = ConfigFactory.load();

    @Override
    public void start() {
        super.start();

        // Deploy Market Data Verticles
        JsonArray quotes = new JsonArray(config.getAnyRefList("companies"));
        for (Object q : quotes) {
            JsonObject company = (JsonObject) q;
            company.put("period", config.getInt("market.period"));
            vertx.deployVerticle(MarketDataVerticle.class.getName(), new DeploymentOptions().setConfig(company));
        }

        // Deploy REST Quote API Verticle
        vertx.deployVerticle(RestQuoteAPIVerticle.class.getName(), new DeploymentOptions().setConfig(config()));

        // Publish the services in the discovery infrastructure.
        publishMessageSource("market-data", config.getString("market.address"), rec -> {
            if (!rec.succeeded()) {
                rec.cause().printStackTrace();
            }
            System.out.println("Market data service published : " + rec.succeeded());
        });

        publishHttpEndpoint("quotes", config.getString("http.host"), config.getInt("http.public.port"), config.getString("http.root"), ar -> {
            if (ar.failed()) {
                ar.cause().printStackTrace();
            } else {
                System.out.println("Quotes (Rest endpoint) service published : " + ar.succeeded());
            }
        });
    }
}

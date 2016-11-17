package com.pluralsight.dockerproductionaws.trader;

import com.pluralsight.dockerproductionaws.common.MicroserviceVerticle;
import com.pluralsight.dockerproductionaws.portfolio.PortfolioService;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.servicediscovery.types.EventBusService;

/**
 * A compulsive trader...
 */
public class CompulsiveTraderVerticle extends MicroserviceVerticle {
    private Config config;

    @Override
    public void start(Future<Void> future) {
        super.start();

        // Get configuration
        config = ConfigFactory.load();

        String company = TraderUtils.pickACompany();
        int numberOfShares = TraderUtils.pickANumber();

        EventBus eventBus = vertx.eventBus();
        EventBusService.getProxy(discovery, PortfolioService.class, ar -> {
            if (ar.failed()) {
                System.out.println("Portfolio service could not be retrieved: " + ar.cause());
            } else {
                // Our services:
                PortfolioService portfolio = ar.result();
                MessageConsumer<JsonObject> marketConsumer = eventBus.consumer(config.getString("market.address"));

                // Listen to the market...
                marketConsumer.handler(message -> {
                    JsonObject quote = message.body();
                    TraderUtils.dumbTradingLogic(company, numberOfShares, portfolio, quote);
                });
            }
        });
    }
}

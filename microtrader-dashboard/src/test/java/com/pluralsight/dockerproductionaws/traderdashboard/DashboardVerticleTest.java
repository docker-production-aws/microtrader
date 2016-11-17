package com.pluralsight.dockerproductionaws.traderdashboard;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Created by jmenga on 13/10/16.
 */
@RunWith(VertxUnitRunner.class)
public class DashboardVerticleTest {
    private Config config;
    private Vertx vertx;
    private JsonObject stockQuote() {
        return new JsonObject()
                .put("exchange", "vert.x stock exchange")
                .put("symbol", "MCH")
                .put("name", "MacroHard")
                .put("bid", 3389.0)
                .put("ask", 3391.0)
                .put("volume", 90000)
                .put("open", 1000)
                .put("shares", 88000);
    }

    private JsonObject stockTrade(String action, int amount, int newAmount) {
        return new JsonObject()
                .put("action", action)
                .put("quote",stockQuote())
                .put("date", System.currentTimeMillis())
                .put("amount", amount)
                .put("owned", newAmount);
    }

    @Before
    public void testSetup(TestContext context) {
        Async async = context.async();
        config = ConfigFactory.load();
        vertx = Vertx.vertx();

        vertx.deployVerticle(DashboardVerticle.class.getName(), ar -> {
            context.assertTrue(ar.succeeded());
            async.complete();
        });
    }

    @Test
    public void testQuoteEvent(TestContext context) {
//        Async async = context.async();
//        String portfolioAddr = config.getString("portfolio.address");
//        String marketAddr = config.getString("market.address");
//        HttpClientOptions options = new HttpClientOptions().setDefaultHost(config.getString("http.host"));
//        options.setDefaultPort(config.getInt("http.port"));
//        Future<Void> future = Future.future();
//        HttpClient client = vertx.createHttpClient(options);
//        client.websocket("/eventbus", websocket -> {
//            websocket.frameHandler(frame -> {
//                System.out.println("Received a frame");
//                System.out.println(frame.textData());
//            });
//            future.complete();
//        });
//
//        future.setHandler(f -> {
//            vertx.eventBus().send(marketAddr,stockQuote());
//        });
//        vertx.eventBus().send(portfolioAddr, stockTrade("BUY", 3, 3));
//        vertx.eventBus().send(portfolioAddr, stockTrade("SELL", 2, 1));
//        vertx.eventBus().send(portfolioAddr, stockTrade("SELL", 1, 0));
//        vertx.setTimer(2000, done -> async.complete());
    }
}

package com.pluralsight.dockerproductionaws.portfolio;

import com.pluralsight.dockerproductionaws.portfolio.PortfolioService;
import com.pluralsight.dockerproductionaws.portfolio.impl.PortfolioServiceImpl;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by jmenga on 9/10/16.
 */
@RunWith(VertxUnitRunner.class)
public class PortfolioServiceSellTest {
    private Config config = ConfigFactory.load();
    private Portfolio portfolio;
    private Vertx vertx;
    private ServiceDiscovery discovery;
    private PortfolioService svc;
    private JsonObject getQuote() {
        return new JsonObject()
                .put("exchange", "vert.x stock exchange")
                .put("symbol", "MCH")
                .put("name", "MacroHard")
                .put("bid", 3328.0)
                .put("ask", 3329.0)
                .put("volume", 3)
                .put("open", 600.0)
                .put("shares", 3);

    }

    @Before
    public void testSetup(TestContext context) {
        Async async = context.async();
        vertx = Vertx.vertx();
        discovery = ServiceDiscovery
                .create(vertx, new ServiceDiscoveryOptions()
                        .setBackendConfiguration(vertx.getOrCreateContext().config()));
        svc = new PortfolioServiceImpl(vertx, discovery, 10000);
        svc.getPortfolio(ar -> {
            portfolio = ar.result();
            svc.buy(3, getQuote(), result -> {
                async.complete();
            });
        });
    }

    @Test
    public void testSell(TestContext context) {
        Async async = context.async();
        svc.sell(2, getQuote(), result -> {
            assertThat(result.succeeded()).isTrue();
            assertThat(portfolio.getCash()).isEqualTo(6669);
            assertThat(portfolio.getShares().get("MacroHard")).isEqualTo(1);
            async.complete();
        });
    }

    @Test
    public void testSellNotEnoughStocks(TestContext context) {
        Async async = context.async();
        svc.sell(4, getQuote(), result -> {
            assertThat(result.succeeded()).isFalse();
            assertThat(portfolio.getCash()).isEqualTo(13);
            async.complete();
        });
    }

    @Test
    public void testSellNegativeStocks(TestContext context) {
        Async async = context.async();
        svc.sell(-1, getQuote(), result -> {
            assertThat(result.succeeded()).isFalse();
            assertThat(portfolio.getCash()).isEqualTo(13);
            async.complete();
        });
    }
}

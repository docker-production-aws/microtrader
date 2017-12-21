package com.pluralsight.dockerproductionaws.audit;

import com.pluralsight.dockerproductionaws.admin.Migrate;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by jmenga on 11/10/16.
 */
@RunWith(VertxUnitRunner.class)
public class AuditVerticleTest {
    private Vertx vertx;
    private Config config;
    private String portfolioAddr;
    private static final String SELECT_STATEMENT = "SELECT * FROM AUDIT ORDER BY ID DESC LIMIT 10";

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
        portfolioAddr = config.getString("portfolio.address");
        vertx = Vertx.vertx();
        Migrate.main(null);
        vertx.deployVerticle(AuditVerticle.class.getName(), ar -> {
            vertx.eventBus().send(portfolioAddr, stockTrade("BUY", 3, 3));
            vertx.eventBus().send(portfolioAddr, stockTrade("SELL", 2, 1));
            vertx.eventBus().send(portfolioAddr, stockTrade("SELL", 1, 0));
            vertx.setTimer(2000, done -> async.complete());
        });
    }

    @Test
    public void testStockTradesAudited(TestContext context) {
        Async async = context.async();
        HttpClientOptions options = new HttpClientOptions().setDefaultHost(config.getString("http.host"));
        options.setDefaultPort(config.getInt("http.port"));
        HttpClient client = vertx.createHttpClient(options);
        client.get("/", response -> {
            context.assertEquals(response.statusCode(), 200);
            response.bodyHandler(buffer -> {
                JsonArray body = buffer.toJsonArray();
                context.assertTrue(body.size() >= 3);
                async.complete();
            });
        }).end();
    }

    @Test
    public void testStockTradesPersisted(TestContext context) throws ClassNotFoundException {
        Async async = context.async();
        JsonObject jdbcConfig = new JsonObject(config.getObject("jdbc").render(ConfigRenderOptions.concise()));
        JDBCClient jdbc = JDBCClient.createNonShared(vertx, jdbcConfig);
        Class.forName(jdbcConfig.getString("driverclass"));

        jdbc.getConnection(ar -> {
            SQLConnection connection = ar.result();
            if (ar.failed()) {
                context.fail(ar.cause());
            } else {
                connection.query(SELECT_STATEMENT, result -> {
                    ResultSet set = result.result();
                    List<JsonObject> operations = set.getRows().stream()
                            .map(json -> new JsonObject(json.getString("OPERATION")))
                            .collect(Collectors.toList());
                    context.assertTrue(operations.size() >= 3);
                    connection.close();
                    async.complete();
                });
            }
        });
    }
}

package com.pluralsight.dockerproductionaws.audit;

import com.pluralsight.dockerproductionaws.common.MicroserviceVerticle;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.servicediscovery.types.MessageSource;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A verticle storing operations in a database (hsql) and providing access to the operations.
 */
public class AuditVerticle extends MicroserviceVerticle {

    private static final String INSERT_STATEMENT = "INSERT INTO AUDIT (OPERATION) VALUES (?)";
    private static final String SELECT_STATEMENT = "SELECT * FROM AUDIT ORDER BY ID DESC LIMIT 10";
    private Config config;
    private JDBCClient jdbc;

    /**
     * Starts the verticle asynchronously. The the initialization is completed, it calls
     * `complete()` on the given {@link Future} object. If something wrong happens,
     * `fail` is called.
     *
     * @param future the future to indicate the completion
     */
    @Override
    public void start(Future<Void> future) throws ClassNotFoundException {
        super.start();

        // Get configuration
        config = ConfigFactory.load();

        // creates the jdbc client.
        JsonObject jdbcConfig = new JsonObject(config.getObject("jdbc").render(ConfigRenderOptions.concise()));
        jdbc = JDBCClient.createNonShared(vertx, jdbcConfig);
        Class.forName(jdbcConfig.getString("driverclass"));

        // Start HTTP server and listen for portfolio events
        EventBus eventBus = vertx.eventBus();
        Future<HttpServer> httpEndpointReady = configureTheHTTPServer();
        httpEndpointReady.setHandler(ar -> {
           if (ar.succeeded()) {
               MessageConsumer<JsonObject> portfolioConsumer = eventBus.consumer(config.getString("portfolio.address"));
               portfolioConsumer.handler(message -> {
                   storeInDatabase(message.body());
               });
               future.complete();
           } else {
               future.fail(ar.cause());
           }
        });

        publishHttpEndpoint("audit", config.getString("http.host"), config.getInt("http.public.port"), config.getString("http.root"), ar -> {
            if (ar.failed()) {
                ar.cause().printStackTrace();
            } else {
                System.out.println("Audit (Rest endpoint) service published : " + ar.succeeded());
            }
        });
    }

    @Override
    public void stop(Future<Void> future) throws Exception {
        jdbc.close();
        super.stop(future);
    }

    private Future<List<JsonObject>> retrieveOperations() {
        Future<List<JsonObject>> future = Future.future();

        // 1 - we retrieve the connection
        jdbc.getConnection(ar -> {
            SQLConnection connection = ar.result();
            if (ar.failed()) {
                future.fail(ar.cause());
            } else {
                // 2. we execute the query
                connection.query(SELECT_STATEMENT, result -> {
                    ResultSet set = result.result();

                    // 3. Build the list of operations
                    List<JsonObject> operations = set.getRows().stream()
                            .map(json -> new JsonObject(json.getString("OPERATION")))
                            .collect(Collectors.toList());

                    // 4. Send the list to the response
                    future.complete(operations);

                    // 5. Close the connection
                    connection.close();
                });
            }
        });
        return future;
    }

    private Future<HttpServer> configureTheHTTPServer() {
        Future<HttpServer> future = Future.future();

        // Use a Vert.x Web router for this REST API.
        Router router = Router.router(vertx);
        router.get(config.getString("http.root")).handler(context -> {
            Future<List<JsonObject>> jdbcFuture = retrieveOperations();
            jdbcFuture.setHandler(jdbc -> {
                if (jdbc.succeeded()) {
                    context.response()
                            .putHeader("Content-Type", "application/json")
                            .setStatusCode(200)
                            .end(Json.encodePrettily(jdbcFuture.result()));
                } else {
                    context.response().setStatusCode(500).end(jdbc.cause().toString());
                }
            });
        });

        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(config.getInt("http.port"), future.completer());

        return future;
    }

    private Future<MessageConsumer<JsonObject>> retrieveThePortfolioMessageSource() {
        Future<MessageConsumer<JsonObject>> future = Future.future();
        MessageSource.getConsumer(discovery,
                new JsonObject().put("name", "portfolio-events"),
                future.completer()
        );
        return future;
    }


    private void storeInDatabase(JsonObject operation) {
        // Storing in the database is also a multi step process,
        // 1. need to retrieve a connection
        // 2. execute the insertion statement
        // 3. close the connection
        Future<SQLConnection> connectionRetrieved = Future.future();
        Future<UpdateResult> insertionDone = Future.future();

        // Step 1 get the connection
        jdbc.getConnection(connectionRetrieved.completer());

        // Step 2, when the connection is retrieved (this may have failed), do the insertion (upon success)
        connectionRetrieved.setHandler(
                ar -> {
                    if (ar.failed()) {
                        System.err.println("Failed to connect to database: " + ar.cause());
                    } else {
                        SQLConnection connection = ar.result();
                        connection.updateWithParams(INSERT_STATEMENT,
                                new JsonArray().add(operation.encode()),
                                insertionDone.completer());
                    }
                }
        );

        // Step 3, when the insertion is done, close the connection.
        insertionDone.setHandler(
                ar -> {
                    if (ar.failed()) {
                        System.err.println("Failed to insert operation in database: " + ar.cause());
                    } else {
                        connectionRetrieved.result().close();
                    }
                }
        );
    }

    /**
     * A utility method returning a `Handler<SQLConnection>`
     *
     * @param future     the future.
     * @param connection the connection
     * @return the handler.
     */
    private static Handler<AsyncResult<Void>> completer(Future<SQLConnection> future, SQLConnection connection) {
        return ar -> {
            if (ar.failed()) {
                future.fail(ar.cause());
            } else {
                future.complete(connection);
            }
        };
    }
}


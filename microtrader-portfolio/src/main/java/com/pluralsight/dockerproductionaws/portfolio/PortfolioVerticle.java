package com.pluralsight.dockerproductionaws.portfolio;

import com.pluralsight.dockerproductionaws.common.MicroserviceVerticle;
import com.pluralsight.dockerproductionaws.portfolio.PortfolioService;
import com.pluralsight.dockerproductionaws.portfolio.impl.PortfolioServiceImpl;
import com.pluralsight.dockerproductionaws.trader.CompulsiveTraderVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.serviceproxy.ProxyHelper;

import static com.pluralsight.dockerproductionaws.portfolio.PortfolioService.ADDRESS;
import static com.pluralsight.dockerproductionaws.portfolio.PortfolioService.EVENT_ADDRESS;

/**
 * A verticle publishing the portfolio service.
 */
public class PortfolioVerticle extends MicroserviceVerticle {

    @Override
    public void start() {
        super.start();

        // Create the service object
        PortfolioServiceImpl service = new PortfolioServiceImpl(vertx, discovery, config().getDouble("money", 10000.00));

        // Register the service proxy on the event bus
        ProxyHelper.registerService(PortfolioService.class, vertx, service, ADDRESS);

        // Publish it in the discovery infrastructure
        publishEventBusService("portfolio", ADDRESS, PortfolioService.class, ar -> {
            if (ar.failed()) {
                ar.cause().printStackTrace();
            } else {
                System.out.println("Portfolio service published : " + ar.succeeded());
            }
        });

        publishMessageSource("portfolio-events", EVENT_ADDRESS, ar -> {
            if (ar.failed()) {
                ar.cause().printStackTrace();
            } else {
                System.out.println("Portfolio Events service published : " + ar.succeeded());
            }
        });

        // Java traders
        vertx.deployVerticle(CompulsiveTraderVerticle.class.getName(), new DeploymentOptions().setInstances(3));
    }
}

# Docker in Production using AWS - Microtrader Application

This is the sample application for the Pluralsight course Docker in Production using AWS.

The application is based upon the excellent [Vert.x Microservices Workshop](https://github.com/cescoffier/vertx-microservices-workshop), although a number of modifications have been made as outlined below:

- Full continuous delivery workflow using Docker (based upon my course [Continuous Delivery using Docker and Ansible](https://www.pluralsight.com/courses/docker-ansible-continuous-delivery))
- Use of Gradle as the multi-project build tool.
- Use of the [Typesafe config library](https://github.com/typesafehub/config) for friendlier 12-factor environment variable based configuration support.
- Use of [Flyway](https://flywaydb.org) for lightweight database migrations
- Some of the individual microservices have been refactored to be more resilient to failure
- Addition of unit/integration tests and acceptance tests

## Contents

- [Application Architecture](#application-architecture)
- [Quick Start](#quick-start)
- [Docker Workflow](#docker-workflow)
- [Environment Configuration Settings](#environment-configuration-settings)
- [Branches](#branches)
- [Repository Timeline](#repository-timeline)
- [Errata](#errata)

## Application Architecture

The application consists of four Microservices that collectively comprise a simple fictitious stock trading application:

- [Quote Generator](./microtrader-quote) - periodically generates stock market quotes for three fictitious companies
- [Portfolio Service](./microtrader-portfolio) - trades stocks starting from an initial portfolio of $10000 cash on hand.  The trading logic is completely random and non-sensical.
- [Audit Service](./microtrader-audit) - audits all stock trading activity, persisting each stock trade to a database
- [Trader Dashboard](./microtrader-dashboard) - provides a web dashboard displaying stock market quote activity, recent stock trades and the current state of the portfolio.  The dashboard also provides an operational view of the status and service discovery inforamtion for each service.

The application demonstrates several key features of a Microservices architecture and the underlying [Vert.x](http://vertx.io) toolkit that powers the application:

- [Distributed event bus](http://vertx.io/docs/vertx-core/java/#event_bus) - Vert.x supports an asynchronous message driven architecture, with support for common patterns including publish/subscribe, point-to-point, request-response and service proxy RPC messaging.
- [SockJS event bus bridge](http://vertx.io/docs/vertx-web/java/#_sockjs_event_bus_bridge) - this feature allows the event bus to be extended in a secure and controlled fashion to web clients via websockets using the SockJS JavaScript client library.  This provides the ability to create real-time web applications and message client endpoints directly if appropriate.
- [Service discovery](http://vertx.io/docs/vertx-service-discovery/java/) - allows Microservices to discover, locate and interact with other services.  Vert.x includes a simple distributed map structure to store service discovery, however this can be replaced with several popular backends such as Consul and Kubernetes.
- [Circuit breaker pattern](http://vertx.io/docs/vertx-circuit-breaker/java/) - this is a core pattern for larger Microservices architectures that helps prevent cascading failures and handle failures gracefully.
- [Multi reactor pattern](http://vertx.io/docs/vertx-core/java/#_reactor_and_multi_reactor) - Vert.x provides a lightweight and highly performant event loop based scheduling algorithm allowing applications written in an asynchronous, non-blocking manner to scale considerably greater than traditional application architectures.  The Vert.x programming model provides the simplicity of single threaded development for each deployable unit of code, but schedules each deployable unit of code across all available processor cores for maximum concurrency.

## Quick Start

### Building the Application Locally

Before building and running the application locally, your system must have the following prerequisites installed:

- Java JDK 8
- NodeJS 4.x or higher (to install the `npm` package manager)
- Bower (`npm install -g bower`)

You first need to build "fat" jars for each Microservice, using the Gradle shadowJar plugin as shown below:

```
$ ./gradlew clean test shadowJar
...
...
:clean
:microtrader-audit:clean
:microtrader-common:clean
:microtrader-dashboard:clean
:microtrader-portfolio:clean
:microtrader-quote:clean
...
...
com.pluralsight.dockerproductionaws.audit.AuditVerticleTest > testStockTradesPersisted PASSED
com.pluralsight.dockerproductionaws.audit.AuditVerticleTest > testStockTradesAudited PASSED
com.pluralsight.dockerproductionaws.portfolio.PortfolioServiceBuyTest > testBuy PASSED
com.pluralsight.dockerproductionaws.portfolio.PortfolioServiceBuyTest > testBuyNotEnoughCash PASSED
com.pluralsight.dockerproductionaws.portfolio.PortfolioServiceBuyTest > testBuyNegativeStocks PASSED
com.pluralsight.dockerproductionaws.portfolio.PortfolioServiceBuyTest > testBuyNotEnoughStocks PASSED
com.pluralsight.dockerproductionaws.portfolio.PortfolioServiceSellTest > testSellNotEnoughStocks PASSED
com.pluralsight.dockerproductionaws.portfolio.PortfolioServiceSellTest > testSell PASSED
com.pluralsight.dockerproductionaws.portfolio.PortfolioServiceSellTest > testSellNegativeStocks PASSED
com.pluralsight.dockerproductionaws.traderdashboard.DashboardVerticleTest > testQuoteEvent PASSED
com.pluralsight.dockerproductionaws.quotegenerator.MarketDataVerticleTest > testComputation PASSED
com.pluralsight.dockerproductionaws.quotegenerator.MarketDataVerticleTest > testMarketData PASSED
...
...
:shadowJar
:microtrader-audit:shadowJar
:microtrader-common:shadowJar
:microtrader-dashboard:shadowJar
:microtrader-portfolio:shadowJar
:microtrader-quote:shadowJar

BUILD SUCCESSFUL
```

This will output "fat" jars to the `build/jars/` folder as demonstrated below:

```
$ ls build/jars/

18 Oct 19:59 microtrader-audit-20161018004318.d4ce05e-fat.jar
18 Oct 19:59 microtrader-dashboard-20161018004318.d4ce05e-fat.jar
18 Oct 19:59 microtrader-portfolio-20161018004318.d4ce05e-fat.jar
18 Oct 20:00 microtrader-quote-20161018004318.d4ce05e-fat.jar
```

### Application Versioning

The application uses a simple versioning scheme for all components:

  `<git-commit-timestamp>.<git-commit-short-hash>`

You can use the `make version` command to view the current version.  The versioning scheme also appends a build identifier if the `BUILD_ID` environment variable is set:

```
$ make version
20161018004318.d4ce05e

$ export BUILD_ID=1234
$ make version
20161018004318.d4ce05e.1234
```

### Running the Application Locally

To run the application locally, first execute audit database migrations as demonstrated below to create the initial DB schema.  

Locally this will use HSQL and write to audit-db.* files in the project root:

```
$ java -cp build/jars/microtrader-audit-20161018004318.d4ce05e-fat.jar com.pluralsight.dockerproductionaws.admin.Migrate
Oct 18, 2016 8:18:54 PM org.flywaydb.core.internal.util.VersionPrinter printVersion
INFO: Flyway 4.0.3 by Boxfuse
$ ls -l
total 176
-rw-r--r--  1 jmenga  staff   7981  8 Nov 23:20 Makefile
-rw-r--r--  1 jmenga  staff   3956  8 Nov 23:09 Makefile.settings
-rw-r--r--  1 jmenga  staff  30452  8 Nov 23:18 README.md
-rw-r--r--  1 jmenga  staff   9772  9 Nov 06:02 audit-db.log
-rw-r--r--  1 jmenga  staff    101  9 Nov 06:01 audit-db.properties
-rw-r--r--  1 jmenga  staff   4849  9 Nov 06:01 audit-db.script
drwxr-xr-x  2 jmenga  staff     68  9 Nov 06:00 audit-db.tmp
...
...
```

Next start the audit service, ensuring the `-cluster` flag is set:

```
$ java -jar build/jars/microtrader-audit-20161018004318.d4ce05e-fat.jar -cluster
Oct 18, 2016 8:19:41 PM io.vertx.core.impl.launcher.commands.RunCommand
INFO: Starting clustering...
Oct 18, 2016 8:19:41 PM io.vertx.core.impl.launcher.commands.RunCommand
...
...
INFO: [127.0.0.1]:5701 [dev] [3.6.5]


Members [1] {
  Member [127.0.0.1]:5701 this
}
...
...
Audit (Rest endpoint) service published : true
Oct 18, 2016 8:19:46 PM io.vertx.core.impl.launcher.commands.VertxIsolatedDeployer
INFO: Succeeded in deploying verticle
```

In a new console window, start the portfolio service, ensuring the `-cluster` flag is set.  You should see the cluster members increase to 2 in both console windows.

```
$ java -jar build/jars/microtrader-portfolio-20161018004318.d4ce05e-fat.jar -cluster
Oct 18, 2016 8:19:41 PM io.vertx.core.impl.launcher.commands.RunCommand
INFO: Starting clustering...
Oct 18, 2016 8:19:41 PM io.vertx.core.impl.launcher.commands.RunCommand
...
...
INFO: [127.0.0.1]:5701 [dev] [3.6.5]

Members [2] {
  Member [127.0.0.1]:5701
  Member [127.0.0.1]:5702 this
}

...
...
Portfolio service published : true
Oct 18, 2016 8:21:41 PM io.vertx.core.impl.launcher.commands.VertxIsolatedDeployer
INFO: Succeeded in deploying verticle
Portfolio Events service published : true
```

In another console window, start the quote generator service, once again ensuring the `-cluster` flag is set.  You should see the cluster members increase to 3 in all console windows.

```
$ java -jar build/jars/microtrader-quote-20161018004318.d4ce05e-fat.jar -cluster
Oct 18, 2016 8:19:41 PM io.vertx.core.impl.launcher.commands.RunCommand
INFO: Starting clustering...
Oct 18, 2016 8:19:41 PM io.vertx.core.impl.launcher.commands.RunCommand
...
...
INFO: [127.0.0.1]:5701 [dev] [3.6.5]

Members [3] {
  Member [127.0.0.1]:5701
  Member [127.0.0.1]:5702
  Member [127.0.0.1]:5703 this
}

Oct 18, 2016 8:24:23 PM com.hazelcast.core.LifecycleService
INFO: [127.0.0.1]:5703 [dev] [3.6.5] Address[127.0.0.1]:5703 is STARTED
Market data service published : true
Oct 18, 2016 8:24:26 PM io.vertx.core.impl.launcher.commands.VertxIsolatedDeployer
INFO: Succeeded in deploying verticle
Quotes (Rest endpoint) service published : true
Server started
```

After the quote generator service has started, you should start to see trading activity in the portfolio service console output:

```
...
...
Bought 1 of Black Coat !
Bought 6 of Divinator !
Bought 2 of Divinator !
Bought 1 of Black Coat !
Bought 2 of Divinator !
...
...
```

Finally, in a new console window, start the trader dashboard service, ensuring the `-cluster` flag is set.  You should see the custer members increase to 4 in all console windows.

```
$ java -jar build/jars/microtrader-dashboard-20161018004318.d4ce05e-fat.jar -cluster
Oct 18, 2016 8:19:41 PM io.vertx.core.impl.launcher.commands.RunCommand
INFO: Starting clustering...
Oct 18, 2016 8:19:41 PM io.vertx.core.impl.launcher.commands.RunCommand
...
...
INFO: [127.0.0.1]:5701 [dev] [3.6.5]

Members [4] {
  Member [127.0.0.1]:5701
  Member [127.0.0.1]:5702
  Member [127.0.0.1]:5703
  Member [127.0.0.1]:5704 this
}

Oct 18, 2016 8:28:03 PM com.hazelcast.core.LifecycleService
INFO: [127.0.0.1]:5704 [dev] [3.6.5] Address[127.0.0.1]:5704 is STARTED
Oct 18, 2016 8:28:05 PM io.vertx.core.impl.launcher.commands.VertxIsolatedDeployer
INFO: Succeeded in deploying verticle
```

At this point, you should be able to browse to [http://localhost:8000](http://localhost:8000).  

The following screenshot demonstrates a fully functional Microtrader application:

![alt Microtrader Dashboard](https://cloud.githubusercontent.com/assets/3351083/19468489/16fc6140-9573-11e6-917f-5717e7564d57.png)

### Testing the Application Locally

You can run unit tests using standard gradle tasks:

```
$ ./gradlew clean test
...
...
$ ./gradlew :microtrader-audit:clean :microtrader-audit:test
...
...
```

If you have the application running locally, you can run acceptance tests, which are defined in the [`microtrader-specs`](./microtrader-specs) folder.

These tests are written in JavaScript using [Mocha](https://mochajs.org) and test browser-like interactions with the dashboard service and other REST endpoints, as well as the SockJS Event Bus bridge provided by the dashboard service:

> You only need to run the `npm install` commands once

```
microtrader$ cd microtrader-specs
microtrader-specs$ npm install -g mocha
...
...
microtrader-specs$ npm install 
...
...
microtrader-specs$ mocha --exit

  Trader Dashboard Frontend
    ✓ should return a 200 OK response

  Quote Service REST Endpoint
    ✓ should return a 200 OK response
    ✓ should return a MacroHard quote
    ✓ should return a Black Coat quote
    ✓ should return a Divinator quote

  Audit Service REST Endpoint
    ✓ should return a 200 OK response (41ms)
    ✓ should return an array of stock trades

  Operations Endpoint
    ✓ should return a 200 OK response (43ms)
    ✓ should return an array of stock trades

  Discovery Endpoint
    ✓ should return a 200 OK response (44ms)
    ✓ should return an array of service location records
    ✓ should list all services having a status of UP

  Market Events
    ✓ should receive market data (4018ms)

  Portfolio Events
    ✓ should receive portfolio trading events (3010ms)
    ✓ should retrieve portfolio service data

  15 passing (7s)
```

## Docker Workflow

The repository includes a Docker-based continuous delivery workflow that creates two environments:

- Test Environment
- Release Environment

### Test Environment

The test environment is expressed via a [`docker-compose.yml`](./docker/test/docker-compose.yml) file in the [docker/test](./docker/test) folder.  

The environment defines a single service `test` which creates a Docker development image (defined in [docker/test/Dockerfile](./docker/test/Dockerfile)), which tests and builds the application "fat" jar artefacts.  

All artefacts and test reports are copied to the local `build` folder, so that running tests and builds locally is no different from running the Docker workflow.  

The various actions required to run the tests and build are defined in the `test` task of the local [`Makefile`](./Makefile).

### Release Environment

The release environment is expressed via a [`docker-compose.yml`](./docker/release/docker-compose.yml) file in the [docker/release](./docker/release) folder.  

The release environment defines multiple services:

- Application services - the various application services are built into "release" images as defined in `Dockerfile.<service>` files in the [docker/release](./docker/release) folder.  These images represent the final artifact that will run in production, assuming all future tests and quality checks pass.  Each application service release image is based from the [`microtrader-base`](https://github.com/docker-production-aws/microtrader-base) image.
- Database service - this creates a MySQL database container, which is the database engine used in production
- Helper services - these help orchestrate the correct initialisation and startup of the environment
- Specs service - this runs the acceptance tests defined in the [`microtrader-specs`](./microtrader-specs) folder.

### Running the Workflow

To run the workflow your system must meet the following requirements:

- Docker 1.12 or higher client installed and pointed to a local or remote Docker Engine
- Docker Compose 1.7 or higher
- GNU Make

The workflow consists of the following tasks:

- Test stage - Run unit tests and build artefacts
- Release stage - Build release images and run acceptance tests
- Publish stage - Tag and publish release images to Docker registry

To run the test stage, use the `make test` command:

```
$ make test
=> Checking networking...
=> Building images...
Building test
Step 1 : FROM openjdk:8-jdk-alpine
...
...
Step 22 : COPY microtrader-dashboard /app/microtrader-dashboard
 ---> fb00a7fb8df4
Removing intermediate container 6f231cf4bc93
Successfully built fb00a7fb8df4
=> Running tests...
Creating microtradertest_test_1
Attaching to microtradertest_test_1
test_1  | Starting a Gradle Daemon, 1 incompatible and 1 stopped Daemons could not be reused, use --status for details
test_1  | :clean UP-TO-DATE
test_1  | :microtrader-audit:clean UP-TO-DATE
...
...
test_1  | com.pluralsight.dockerproductionaws.quotegenerator.MarketDataVerticleTest > testMarketData PASSED
test_1  | :testReport
test_1  | :test UP-TO-DATE
test_1  | :shadowJar
test_1  | :microtrader-audit:shadowJar
test_1  | :microtrader-common:shadowJar
test_1  | :microtrader-dashboard:shadowJar
test_1  | :microtrader-portfolio:shadowJar
test_1  | :microtrader-quote:shadowJar
test_1  |
test_1  | BUILD SUCCESSFUL
test_1  |
test_1  | Total time: 39.164 secs
microtradertest_test_1 exited with code 0
=> Removing existing artefacts...
=> Copying build artefacts...
=> Test complete
```

After the test stage, application artefacts and test reports will be available in the local `build/jars` and `build/test-reports` folders respectively.

To run the release stage, use the `make release` command:

```
$ make release
=> Checking networking...
=> Pulling latest images...
Pulling db (mysql:5.7)...
...
...
=> Building images...
Building microtrader-portfolio
Step 1 : FROM dockerproductionaws/microtrader-base
...
...
=> Starting audit database...
Creating microtrader_db_1
...
...
=> Running audit migrations...
...
...
=> Starting audit service...
...
...
=> Starting portfolio service...
Creating microtrader_microtrader-portfolio_1
=> Starting quote generator...
...
...
=> Starting trader dashboard...
...
...
=> Release environment created
=> Running acceptance tests...
Creating microtrader_specs_1
Attaching to microtrader_specs_1
specs_1                  |
specs_1                  |
specs_1                  |
specs_1                  |   Suite duration: 0.006 s, Tests: 0
specs_1                  |
specs_1                  |   Trader Dashboard Frontend
specs_1                  |       ․ should return a 200 OK response: 107ms
...
...
specs_1                  |   Portfolio Events
specs_1                  |       ․ should receive portfolio trading events: 6010ms
specs_1                  |       ․ should retrieve portfolio service data: 20ms
specs_1                  |
specs_1                  |   Suite duration: 6.045 s, Tests: 2
specs_1                  |
specs_1                  |   15 passing (13s)
specs_1                  |
microtrader_specs_1 exited with code 0
=> Acceptance testing complete
=> Quote REST endpoint is running at http://172.16.154.128:33165/quote/
=> Audit REST endpoint is running at http://172.16.154.128:33163/audit/
=> Trader dashboard is running at http://172.16.154.128:33166
```

At this point, the release environment is operational and you can actually interact with the application using the URLs shown.

### Publishing Release Images

The final stage is to publish the release images.

To be able to publish your release images, you will need to reconfigure this project to point to a parent repository and Docker registry that you have write access to.

This can be achieved by editing the [`Makefile`](./Makefile) and configuring the `ORG_NAME` and `DOCKER_REGISTRY` settings:

```
...
...
# Project variables
PROJECT_NAME ?= microtrader
ORG_NAME ?= <your-organization>
REPO_NAME ?= microtrader
TEST_REPO_NAME ?= microtrader-dev
DOCKER_REGISTRY ?= <your-registry-url>
...
...
```

After specifying the correct Docker registry and organization name, you next need to ensure you are logged into the Docker registry:

```
$ docker login myregistry.org
Username: justin
Password: **
Login Succeeded
```

Once logged in, you can tag and publish the release images by running `make tag` and `make publish`:

> The workflow includes a `make tag:default` task which tags the image with the current version (i.e. `make version`), short commit hash, any annotated tags that are present on the current commit, and the 'latest' tag

```
$ make tag $(make version) latest
=> Tagging development image with tags 20161018004318.d4ce05e latest...
=> Tagging release images with tags 20161018004318.d4ce05e latest...
=> Tagging complete

$ make publish
=> Publishing development image sha256:fb00a7fb8df435b8189604f761c3e30efceece6f58e67acc5779ee117dd1c623 to docker.io/dockerproductionaws/microtrader-dev...
The push refers to a repository [docker.io/dockerproductionaws/microtrader-dev]
37fc39364752: Pushing 3.429 MB
321ffde7a0f3: Pushing 170.5 kB
e75559b931ad: Layer already exists
75ddd18ec36a: Layer already exists
af70c2411550: Layer already exists
6a6695567041: Layer already exists
1ef8bb56757c: Layer already exists
...
...
=> Publishing release images to docker.io/dockerproductionaws...
The push refers to a repository [docker.io/dockerproductionaws/microtrader-quote]
...
...
```

### Cleaning Up

To clean up the Docker environments, run the `make clean` task:

```
$ make clean
=> Destroying test environment...
Removing microtradertest_test_1 ... done
Network docker_workflow is external, skipping
=> Destroying release environment...
Stopping microtrader_microtrader-dashboard_1 ... done
Stopping microtrader_microtrader-quote_1 ... done
Stopping microtrader_microtrader-portfolio_1 ... done
Stopping microtrader_microtrader-audit_1 ... done
Stopping microtrader_db_1 ... done
Removing microtrader_specs_1 ... done
Removing microtrader_trader-agent_run_1 ... done
Removing microtrader_microtrader-dashboard_1 ... done
Removing microtrader_quote-agent_run_1 ... done
Removing microtrader_microtrader-quote_1 ... done
Removing microtrader_microtrader-portfolio_1 ... done
Removing microtrader_audit-agent_run_1 ... done
Removing microtrader_microtrader-audit_1 ... done
Removing microtrader_microtrader-audit_run_1 ... done
Removing microtrader_audit-db-agent_run_1 ... done
Removing microtrader_db_1 ... done
Network docker_workflow is external, skipping
=> Removing dangling images...
=> Clean complete
```

### Running an End-to-end Workflow

The workflow includes a convenient `make all` task, which automatically runs the following tasks:

- `make clean`
- `make test`
- `make release`
- `make tag:default` - tags the current version (i.e. `make version`), short commit hash, any annotated tags that are present on the current commit and the 'latest' tag
- `make publish`
- `make clean` 

## Environment Configuration Settings

Each application service Docker release image supports a variety of configuration parameters expressed in the form of environment variables.

These settings are based upon two categories:

- Application settings - used to configure the application services.
- Clustering settings - used to control the Hazelcast autodiscovery mechanism used to form clusters.

The following table lists environment variables that configure application settings:

| Scope           | Environment Variable   | Default Value            | Notes                                                                                                                                                                                                                                                  |
|-----------------|------------------------|--------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| All             | HTTP_HOST              | localhost                | Defines the hostname of the application service endpoint.  This should be set to the public DNS service name you want other components to reach the service at (e.g. a load balancer endpoint).  This value is published in service discovery records. |
| All             | HTTP_PORT              |                          | Defines the local HTTP port the HTTP service runs on, if an HTTP service is defined for the application service.                                                                                                                                       |
| All             | HTTP_PUBLIC_PORT       | ${HTTP_PORT}             | Defines the public HTTP port the HTTP service is published to.  For example, this should be set to the public listener configured on a front-end load balancer endpoint.  This value is published in service discovery records.                        |
| All             | HTTP_ROOT              |                          | Defines the root path the HTTP service is served from.  This setting is useful if you perform content-based routing based upon URL path on a front-end load balancer.  This value is published in service discovery records.                           |
| All             | MARKET_DATA_ADDRESS    | market                   | The Event Bus address to publish/receive stock market quotes to/from                                                                                                                                                                                   |
| All             | PORTFOLIO_ADDRESS      | portfolio                | The Event Bus address to publish/receive stock trade events to/from                                                                                                                                                                                    |
| Quote Generator | MARKET_PERIOD          | 3000                     | The frequency in milliseconds that each stock quote is generated                                                                                                                                                                                       |
| Audit Service   | JDBC_URL               | jdbc:hqldb:file:audit-db | The JDBC URL of the audit database                                                                                                                                                                                                                     |
| Audit Service   | JDBC_USER              | audit                    | The JDBC user of the audit database                                                                                                                                                                                                                    |
| Audit Service   | JDBC_PASSWORD          | password                 | The JDBC password of the audit database                                                                                                                                                                                                                |
| Audit Service   | JDBC_DRIVERCLASS       | org.hsqldb.jdbcDriver    | The JDBC driver class for the audit database engine                                                                                                                                                                                                    |
| Audit Service   | DB_MIGRATIONS_LOCATION | db/hsqldb                | The resources root relative path to the appropriate database migrations for the configured database engine.                                                                                                                                            |

The following table lists environment variables that configure clustering settings:

| Environment Variable   | Default Value   | Notes                                                                                                                                                                                                                                                                                      |
|------------------------|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| CLUSTER_GROUP_NAME     | microtrader     | Sets the Hazelcast cluster group name.  Use to separate multiple Hazelcast clusters running on the same subnet.                                                                                                                                                                            |
| CLUSTER_GROUP_PASSWORD |                 | Sets the Hazelcast cluster group password.  Use to prevent unauthorised hosts attempting to join the cluster.                                                                                                                                                                              |
| CLUSTER_AWS_ENABLED    | False           | When set to "true", enables discovery of other cluster members in an AWS environment.                                                                                                                                                                                                      |
| CLUSTER_AWS_REGION     |                 | Specifies the AWS region when using AWS discovery mechanism                                                                                                                                                                                                                                |
| CLUSTER_AWS_IAM_ROLE   | DEFAULT         | Specifies an IAM role with appropriate privileges to query the EC2 API and find other cluster instances based upon the CLUSTER_AWS_TAG_KEY and CLUSTER_AWS_TAG_VALUE configuration.  The default value of "DEFAULT" specifies to use the EC2 IAM Instance Profile and associated IAM role. |
| CLUSTER_AWS_TAG_KEY    | hazelcast:group | The value of the tag key to search for when querying the EC2 API for other cluster members                                                                                                                                                                                                 |
| CLUSTER_AWS_TAG_VALUE  | microtrader     | The value of the tag value to search for when querying the EC2 API for other cluster members                                                                                                                                                                                               |

## Branches

This repository contains two branches:

- [`master`](https://github.com/docker-production-aws/microtrader/tree/master) - represents the initial starting state of the repository as viewed in the course.

- [`final`](https://github.com/docker-production-aws/microtrader/tree/final) - represents the final state of the repository after completing all configuration tasks as described in the course material.

> The `final` branch is provided as a convenience in the case you get stuck, or want to avoid manually typing out large configuration files.  In most cases however, you should attempt to configure this repository by following the course material.

To clone this repository and checkout a branch you can simply use the following commands:

```
$ git clone https://github.com/docker-production-aws/packer-ecs.git
...
...
$ git checkout final
Switched to branch 'final'
$ git checkout master
Switched to branch 'master'
```

## Repository Timeline

The following provides links to commits in this repository that represent important milestones in the course material:

- [Running Docker Applications Using the EC2 Container Service](https://github.com/docker-production-aws/microtrader/tree/running-docker-applications-using-ecs) - this commit represents the state of the repository once you have completed the **Running Docker Applications Using the EC2 Container Service** module.

- [Continuous Delivery Using CodePipeline](https://github.com/docker-production-aws/microtrader/tree/continuous-delivery-codepipeline) - this commit represents the state of the repository once you have completed the **Continuous Delivery Using CodePipeline** module.

## Errata

See [issues](https://github.com/docker-production-aws/microtrader/issues?utf8=✓&q=is%3Aissue)

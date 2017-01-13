# Import local environment overrides
$(shell touch .env)
include .env

# Project variables
PROJECT_NAME ?= microtrader
ORG_NAME ?= dockerproductionaws
REPO_NAME ?= microtrader
TEST_REPO_NAME ?= microtrader-dev
TEST_DIR ?= build/test-results/junit/

# Release settings
export HTTP_PORT ?= 8000
export AUDIT_HTTP_ROOT ?= /audit/
export QUOTE_HTTP_ROOT ?= /quote/
export MARKET_DATA_ADDRESS ?= market
export MARKET_PERIOD ?= 3000
export DB_NAME ?= audit
export DB_USER ?= audit
export DB_PASSWORD ?= password
export BUILD_ID ?=

# Common settings
include Makefile.settings

.PHONY: version test build release clean tag tag%default login logout publish compose dcompose database save load demo all

# Prints version
version:
	@ echo $(APP_VERSION)

# Runs unit and integration tests
# Pulls images and base images by default
# Use 'make test :nopull' to disable default pull behaviour
test:
	${INFO} "Building images..."
	@ docker-compose $(TEST_ARGS) build $(NOPULL_FLAG) test
	${INFO} "Running tests..."
	@ docker-compose $(TEST_ARGS) up test
	@ mkdir -p $(TEST_DIR)
	@ docker cp $$(docker-compose $(TEST_ARGS) ps -q test):/app/build/test-results/junit/. $(TEST_DIR)
	@ $(call check_exit_code,$(TEST_ARGS),test)
	${INFO} "Removing existing artefacts..."
	@ rm -rf build
	${INFO} "Copying build artefacts..."
	@ docker cp $$(docker-compose $(TEST_ARGS) ps -q test):/app/build/. build
	${INFO} "Test complete"

# Builds release image and runs acceptance tests
# Use 'make release :nopull' to disable default pull behaviour
release:
	${INFO} "Building images..."
	@ docker-compose $(RELEASE_ARGS) build $(NOPULL_FLAG) microtrader-dashboard microtrader-quote microtrader-audit microtrader-portfolio db specs
	${INFO} "Starting audit database..."
	@ docker-compose $(RELEASE_ARGS) up -d db
	@ $(call check_service_health,$(RELEASE_ARGS),db)
	${INFO} "Running audit migrations..."
	@ docker-compose $(RELEASE_ARGS) run microtrader-audit java -cp /app/app.jar com.pluralsight.dockerproductionaws.admin.Migrate
	${INFO} "Starting audit service..."
	@ docker-compose $(RELEASE_ARGS) up -d microtrader-audit
	@ $(call check_service_health,$(RELEASE_ARGS),microtrader-audit)
	${INFO} "Starting portfolio service..."
	@ docker-compose $(RELEASE_ARGS) up -d microtrader-portfolio
	${INFO} "Starting quote generator..."
	@ docker-compose $(RELEASE_ARGS) up -d microtrader-quote
	@ $(call check_service_health,$(RELEASE_ARGS),microtrader-quote)
	${INFO} "Starting trader dashboard..."
	@ docker-compose $(RELEASE_ARGS) up -d microtrader-dashboard
	@ $(call check_service_health,$(RELEASE_ARGS),microtrader-dashboard)
	${INFO} "Release environment created"
	${INFO} "Running acceptance tests..."
	@ docker-compose $(RELEASE_ARGS) up specs
	@ docker cp $$(docker-compose $(RELEASE_ARGS) ps -q specs):/reports/. $(TEST_DIR)
	@ $(call check_exit_code,$(RELEASE_ARGS),specs)
	${INFO} "Acceptance testing complete"
	${INFO} "Quote REST endpoint is running at http://$(DOCKER_HOST_IP):$(call get_port_mapping,$(RELEASE_ARGS),microtrader-quote,$(HTTP_PORT))$(QUOTE_HTTP_ROOT)"
	${INFO} "Audit REST endpoint is running at http://$(DOCKER_HOST_IP):$(call get_port_mapping,$(RELEASE_ARGS),microtrader-audit,$(HTTP_PORT))$(AUDIT_HTTP_ROOT)"
	${INFO} "Trader dashboard is running at http://$(DOCKER_HOST_IP):$(call get_port_mapping,$(RELEASE_ARGS),microtrader-dashboard,$(HTTP_PORT))"


# Executes a full workflow
all: clean test release tag-default publish clean

# Cleans environment
clean: clean-test clean-release
	${INFO} "Removing dangling images..."
	@ $(call clean_dangling_images,$(TEST_REPO_NAME))
	@ $(call clean_dangling_images,$(REPO_NAME))
	${INFO} "Clean complete"

clean%test:
	${INFO} "Destroying test environment..."
	@ docker-compose $(TEST_ARGS) down -v || true

clean%release:
	${INFO} "Destroying release environment..."
	@ docker-compose $(RELEASE_ARGS) down -v || true

# 'make tag <tag> [<tag>...]' tags development and/or release image with specified tag(s)
tag:
	${INFO} "Tagging development image with tags $(TAG_ARGS)..."
	@ $(foreach tag,$(TAG_ARGS),$(call tag_image,$(TEST_ARGS),test,$(DOCKER_REGISTRY)/$(ORG_NAME)/$(TEST_REPO_NAME):$(tag));)
	${INFO} "Tagging release images with tags $(TAG_ARGS)..."
	@ $(foreach tag,$(TAG_ARGS),$(call tag_image,$(RELEASE_ARGS),microtrader-quote,$(DOCKER_REGISTRY)/$(ORG_NAME)/microtrader-quote:$(tag));)
	@ $(foreach tag,$(TAG_ARGS),$(call tag_image,$(RELEASE_ARGS),microtrader-audit,$(DOCKER_REGISTRY)/$(ORG_NAME)/microtrader-audit:$(tag));)
	@ $(foreach tag,$(TAG_ARGS),$(call tag_image,$(RELEASE_ARGS),microtrader-portfolio,$(DOCKER_REGISTRY)/$(ORG_NAME)/microtrader-portfolio:$(tag));)
	@ $(foreach tag,$(TAG_ARGS),$(call tag_image,$(RELEASE_ARGS),microtrader-dashboard,$(DOCKER_REGISTRY)/$(ORG_NAME)/microtrader-dashboard:$(tag));)
	${INFO} "Tagging complete"

# Tags with default set of tags
tag%default:
	@ make tag latest $(APP_VERSION) $(COMMIT_ID) $(COMMIT_TAG)

# Login to Docker registry
login:
	${INFO} "Logging in to Docker registry $$DOCKER_REGISTRY..."
	@ $(DOCKER_LOGIN_EXPRESSION)
	${INFO} "Logged in to Docker registry $$DOCKER_REGISTRY"

# Logout of Docker registry
logout:
	${INFO} "Logging out of Docker registry $$DOCKER_REGISTRY..."
	@ docker logout
	${INFO} "Logged out of Docker registry $$DOCKER_REGISTRY"

# Publishes image(s) tagged using make tag commands
publish:
	${INFO} "Publishing release images to $(DOCKER_REGISTRY)/$(ORG_NAME)..."
	@ $(call publish_image,$(RELEASE_ARGS),microtrader-quote,$(DOCKER_REGISTRY)/$(ORG_NAME)/microtrader-quote)
	@ $(call publish_image,$(RELEASE_ARGS),microtrader-audit,$(DOCKER_REGISTRY)/$(ORG_NAME)/microtrader-audit)
	@ $(call publish_image,$(RELEASE_ARGS),microtrader-portfolio,$(DOCKER_REGISTRY)/$(ORG_NAME)/microtrader-portfolio)
	@ $(call publish_image,$(RELEASE_ARGS),microtrader-dashboard,$(DOCKER_REGISTRY)/$(ORG_NAME)/microtrader-dashboard)
	${INFO} "Publish complete"

# Executes docker-compose commands in release environment
#   e.g. 'make compose ps' is the equivalent of docker-compose -f path/to/dockerfile -p <project-name> ps
#   e.g. 'make compose run nginx' is the equivalent of docker-compose -f path/to/dockerfile -p <project-name> run nginx
#
# Use '--'' after make to pass flags/arguments
#   e.g. 'make -- compose run --rm nginx' ensures the '--rm' flag is passed to docker-compose and not interpreted by make
compose:
	${INFO} "Running docker-compose command in release environment..."
	@ docker-compose -p $(REL_PROJECT) -f $(REL_COMPOSE_FILE) $(ARGS)

# Executes docker-compose commands in test environment
#   e.g. 'make dcompose ps' is the equivalent of docker-compose -f path/to/dockerfile -p <project-name> ps
#   e.g. 'make dcompose run test' is the equivalent of docker-compose -f path/to/dockerfile -p <project-name> run test
#
# Use '--'' after make to pass flags/arguments
#   e.g. 'make -- compose run --rm test' ensures the '--rm' flag is passed to docker-compose and not interpreted by make
dcompose:
	${INFO} "Running docker-compose command in test environment..."
	@ docker-compose -p $(TEST_PROJECT) -f $(TEST_COMPOSE_FILE) $(ARGS)

# IMPORTANT - ensures arguments are not interpreted as make targets
%:
	@:

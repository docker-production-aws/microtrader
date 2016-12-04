FROM dockerproductionaws/microtrader-base
MAINTAINER Justin Menga <justin.menga@gmail.com>
LABEL application.component=microtrader-dashboard
HEALTHCHECK --interval=3s --retries=20 CMD curl -fs http://localhost:${HTTP_PORT:-8000}/

# Copy application artefacts
ARG app_version
LABEL application.version=${app_version}
COPY build/jars/microtrader-dashboard-${app_version}-fat.jar /app/app.jar


FROM dockerproductionaws/microtrader-base
MAINTAINER Justin Menga <justin.menga@gmail.com>
LABEL application.component=microtrader-audit
HEALTHCHECK --interval=3s --retries=20 CMD curl -fs http://localhost:${HTTP_PORT:-33000}/${HTTP_ROOT}

# Copy application artefacts
ARG app_version
LABEL application.version=${app_version}
COPY build/jars/microtrader-audit-${app_version}-fat.jar /app/app.jar
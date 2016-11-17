FROM dockerproductionaws/microtrader-base
MAINTAINER Justin Menga <justin.menga@gmail.com>
LABEL application.component=microtrader-portfolio

# Copy application artefacts
ARG app_version
LABEL application.version=${app_version}
COPY build/jars/microtrader-portfolio-${app_version}-fat.jar /app/app.jar
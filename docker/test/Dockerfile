FROM openjdk:8-jdk-alpine
MAINTAINER Justin Menga <justin.menga@gmail.com>
LABEL application=microtrader-dev

# Install system dependencies
RUN echo "http://nl.alpinelinux.org/alpine/edge/testing/" >> /etc/apk/repositories && \
    apk update && \
    apk add --no-cache bash libstdc++ nodejs nodejs-npm git && \
    npm install bower -g

# Install common dependencies
COPY gradle /app/gradle
COPY build.gradle settings.gradle gradlew /app/
WORKDIR /app/
RUN chmod +x gradlew && \
    ./gradlew copyDeps

# Install module dependencies
COPY microtrader-audit/build.gradle /app/microtrader-audit/
COPY microtrader-quote/build.gradle /app/microtrader-quote/
COPY microtrader-portfolio/build.gradle /app/microtrader-portfolio/
COPY microtrader-dashboard/build.gradle /app/microtrader-dashboard/
COPY microtrader-dashboard/src/main/resources/webroot/bower.json /app/microtrader-dashboard/src/main/resources/webroot/bower.json
RUN ./gradlew copyDeps

# Set the app version and copy the application source
ARG app_version
ENV APP_VERSION=${app_version}
COPY microtrader-common /app/microtrader-common
COPY microtrader-audit /app/microtrader-audit
COPY microtrader-quote /app/microtrader-quote
COPY microtrader-portfolio /app/microtrader-portfolio
COPY microtrader-dashboard /app/microtrader-dashboard



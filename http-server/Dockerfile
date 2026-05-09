FROM eclipse-temurin:11-jdk-jammy AS build

WORKDIR /workspace

COPY . .

RUN chmod +x ./gradlew \
    && ./gradlew :http-server:installDist --no-daemon

FROM eclipse-temurin:11-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl fontconfig libfreetype6 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /workspace/http-server/build/install/http-server /app/http-server

ENV TAVOLO_HTTP_HOST=0.0.0.0
ENV TAVOLO_HTTP_PORT=8080
ENV TAVOLO_ASSETS_DIR=/app/assets

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -fsS "http://127.0.0.1:${TAVOLO_HTTP_PORT}/health" || exit 1

ENTRYPOINT ["/app/http-server/bin/http-server"]

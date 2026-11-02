# Calendar: one image, one process. Ktor serves the API and the built React frontend.
#
#   docker build -t calendar .
#   docker run --rm -p 8080:8080 -e PORT=8080 calendar      # http://localhost:8080
#
# Stage 1 builds the frontend, stage 2 the backend, stage 3 is the small runtime image.
# Data (meeting types, bookings) is kept in memory and is lost on every restart.

# ---- 1. Frontend: Node -------------------------------------------------------------------------
FROM node:24.22.2-alpine AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --ignore-scripts --no-audit --no-fund
# The SDK is generated from the committed OpenAPI contract (it is not stored in git).
COPY api/openapi/openapi.yaml /build/api/openapi/openapi.yaml
COPY frontend/ ./
RUN npm run generate:sdk && npm run build

# ---- 2. Backend: Gradle on a JDK ----------------------------------------------------------------
FROM eclipse-temurin:21.0.13_9-jdk AS backend
# Optional, empty by default (Render, CI): lets a build behind an HTTP proxy reach Gradle/Maven, e.g.
#   --build-arg JAVA_TOOL_OPTIONS="-Dhttps.proxyHost=<host> -Dhttps.proxyPort=<port>"
# The JVM ignores the HTTPS_PROXY variables. It only exists in this build stage, never in the final image.
ARG JAVA_TOOL_OPTIONS
WORKDIR /build/backend
# The Gradle wrapper jar is used directly (gradlew has no executable bit in git).
COPY backend/gradle ./gradle
COPY backend/settings.gradle.kts backend/build.gradle.kts ./
COPY api/openapi/openapi.yaml /build/api/openapi/openapi.yaml
COPY backend/src ./src
RUN java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-daemon installDist

# ---- 3. Runtime: JRE 21, no root ---------------------------------------------------------------
FROM eclipse-temurin:21.0.13_9-jre AS runtime
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=backend /build/backend/build/install/calendar-backend/ ./
COPY --from=frontend /build/frontend/dist ./static

# PORT is read by the app (Render sets it itself; Hexlet runs the image with PORT=8080).
ENV PORT=8080 \
    STATIC_DIR=/app/static
# JVM sized for a 512 MB container (Render Free): heap 192m + metaspace <= 96m + code cache <= 48m
# + direct buffers <= 48m + thread stacks leaves headroom for the rest of the JVM. Fixed -Xmx (not a
# percentage) keeps the size predictable even if the container limit is not visible to the JVM.
# SerialGC and C1-only compilation keep memory and start-up CPU low. Override with -e JAVA_OPTS=...
ENV JAVA_OPTS="-Xms32m -Xmx192m -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=48m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/app/bin/calendar-backend"]

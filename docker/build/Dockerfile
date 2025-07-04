# set Java
ARG JAVA_VERSION=21.0.7_6
# extracts spring layers from the app jar (to optimize boot)
FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine as build

ARG HAWKBIT_APP=hawkbit-update-server
ARG HAWKBIT_VERSION=pass_as_buils_arg

ENV BUILD_DIR=/opt/hawkbit_build
ENV APP=${HAWKBIT_APP}
ENV VERSION=${HAWKBIT_VERSION}

COPY KEY .

RUN set -x &&\
 apk add --no-cache --virtual build-dependencies gnupg unzip libressl wget &&\
 gpg --import KEY &&\
 wget -O ${APP}.jar --no-verbose https://repo1.maven.org/maven2/org/eclipse/hawkbit/${APP}/${VERSION}/${APP}-${VERSION}.jar &&\
 wget -O ${APP}.jar.asc --no-verbose https://repo1.maven.org/maven2/org/eclipse/hawkbit/${APP}/${VERSION}/${APP}-${VERSION}.jar.asc &&\
 gpg --batch --verify ${APP}.jar.asc ${APP}.jar &&\
 rm ${APP}.jar.asc KEY &&\
 apk del build-dependencies &&\
 mkdir -p ${BUILD_DIR} &&\
 cd ${BUILD_DIR} &&\
 java -Djarmode=tools -jar /${APP}.jar extract --layers --launcher --destination . &&\
 rm /${APP}.jar

FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine

RUN addgroup -S hawkbit_group && adduser -D hawkbit -G hawkbit_group
RUN mkdir -p /artifactrepo && chown -R hawkbit /artifactrepo
USER hawkbit

ENV BUILD_DIR=/opt/hawkbit_build
COPY --from=build ${BUILD_DIR}/dependencies/ ./
COPY --from=build ${BUILD_DIR}/spring-boot-loader/ ./
COPY --from=build ${BUILD_DIR}/snapshot-dependencies/ ./
COPY --from=build ${BUILD_DIR}/application/ ./

VOLUME "/artifactrepo"

ARG CONTAINER_PORT=8080
EXPOSE ${CONTAINER_PORT}

ARG PROFILES=h2
ENV PROFILES=${PROFILES}
ARG X_MS=768m
ENV X_MS=${X_MS}
ARG X_MX=768m
ENV X_MX=${X_MX}
ARG XX_MAX_METASPACE_SIZE=250m
ENV XX_MAX_METASPACE_SIZE=${XX_MAX_METASPACE_SIZE}
ARG XX_METASPACE_SIZE=250m
ENV XX_METASPACE_SIZE=${XX_METASPACE_SIZE}
ARG XSS=300K
ENV XSS=${XSS}
ARG GC=G1
ENV GC=${GC}

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Dspring.profiles.active=${PROFILES} -Xms${X_MS} -Xmx${X_MX} -XX:MaxMetaspaceSize=${XX_MAX_METASPACE_SIZE} -XX:MetaspaceSize=${XX_METASPACE_SIZE} -Xss${XSS} -XX:+Use${GC}GC -XX:+UseStringDeduplication -XX:+UseCompressedOops -XX:+HeapDumpOnOutOfMemoryError org.springframework.boot.loader.launch.JarLauncher ${@}"]

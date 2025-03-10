
ARG ARCH
FROM bellsoft/liberica-openjre-alpine:21.0.4-9 AS final

WORKDIR /users/carrotdata/membench

ARG JAR_FILENAME
ENV app_bundle=${JAR_FILENAME}
ENV PATH_AND_NAME=/users/carrotdata/membench/lib/${app_bundle}

COPY bin/* /users/carrotdata/membench/bin/
COPY target/${app_bundle} /users/carrotdata/membench/target/

RUN chmod +x /users/carrotdata/membench/bin/membench.sh

ENTRYPOINT ["/users/carrotdata/membench/bin/membench.sh"]
CMD [""]

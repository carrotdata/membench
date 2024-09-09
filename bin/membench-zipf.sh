#!/usr/bin/env bash

# Determine the directory of the script
SCRIPT_DIR=$(dirname "$(realpath "$0")")

# Set the application directory to the parent directory of the script
APP_DIR=$(dirname "$SCRIPT_DIR")

echo "Membench home directory is ${APP_DIR}"

MAX_HEAP_SIZE=10g
MEMBENCH_RELEASE=membench-0.11-SNAPSHOT-jar-with-dependencies.jar
cd "${APP_DIR}" || exit

CPATH="${APP_DIR}/conf:${APP_DIR}/target/${MEMBENCH_RELEASE}"

export JVM_OPTS="-Xmx${MAX_HEAP_SIZE} -cp ${CPATH}"

exec_cmd="${JAVA_HOME}/bin/java ${JVM_OPTS} com.carrotdata.membench.MembenchZipf"

# Pass all command line arguments to the Java application
${exec_cmd} "$@"



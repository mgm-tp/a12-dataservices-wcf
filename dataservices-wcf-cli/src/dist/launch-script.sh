#!/bin/sh
###############################################################################
# Custom Spring Boot launch script (simplified)
# Goal: preserve the original working directory when executing the fat JAR.
# This script intentionally does NOT 'cd' to the script's directory.
#
# You can pass standard JVM options via JAVA_OPTS environment variable.
###############################################################################

# Resolve Java
if [ -n "$JAVA_HOME" ]; then
	JAVA_CMD="$JAVA_HOME/bin/java"
else
	JAVA_CMD="java"
fi

if ! command -v "$JAVA_CMD" >/dev/null 2>&1; then
	echo "ERROR: Java not found (JAVA_HOME not set and 'java' not on PATH)" >&2
	exit 1
fi

# Optional: allow extra CLI opts via WCF_CLI_JAVA_OPTS
ALL_JAVA_OPTS="${JAVA_OPTS} ${WCF_CLI_JAVA_OPTS}"

# Execute the embedded Spring Boot fat jar (this file)
# "$0" still points to the combined script+jar; the JVM ignores the script preamble.
exec "$JAVA_CMD" $ALL_JAVA_OPTS -jar "$0" "$@"

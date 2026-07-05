#!/bin/sh
#
# Gradle start up script for UN*X
#
APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

# Resolve links
PRG="$0"
while [ -h "$PRG" ]; do
  ls=$(ls -ld "$PRG")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' > /dev/null; then PRG="$link"; else PRG=$(dirname "$PRG")"/$link"; fi
done
SAVED=$(pwd)
cd "$(dirname "$PRG")/" >/dev/null
APP_HOME=$(pwd -P)
cd "$SAVED" >/dev/null

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@" 2>/dev/null || true

# Full POSIX-compatible launcher
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
JAVA_HOME="${JAVA_HOME:-}"
JAVACMD="${JAVA_HOME:+$JAVA_HOME/bin/}java"
set -- "$@"
exec "$JAVACMD" $DEFAULT_JVM_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"

#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
mkdir -p lib target/classes
JSON_JAR="$ROOT/lib/json-20240303.jar"
if [[ ! -f "$JSON_JAR" ]]; then
  curl -fsSL -o "$JSON_JAR" \
    "https://repo1.maven.org/maven2/org/json/json/20240303/json-20240303.jar"
fi
javac --release 17 -encoding UTF-8 -d target/classes -cp "$JSON_JAR" \
  src/main/java/quotes/AuxiliaryServer.java \
  src/main/java/quotes/MainServer.java
echo "OK: classes u target/classes (classpath: target/classes:$JSON_JAR)"

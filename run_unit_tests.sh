#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

CORRETTO_JDK="/Library/Java/JavaVirtualMachines/amazon-corretto-11.jdk/Contents/Home"
if [ -d "$CORRETTO_JDK" ]; then
    export JAVA_HOME="$CORRETTO_JDK"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

mkdir -p bin

CP="bin:lib/sqlite-jdbc-3.36.0.3.jar"

echo "Compilando código y suite de pruebas unitarias..."
javac -cp "$CP" -d bin $(find src/main/java src/test/java -name "*.java")

echo "Ejecutando pruebas unitarias..."
java -cp "$CP" com.solidaria.UnitTests


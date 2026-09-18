#!/usr/bin/env bash
set -e

echo "Compilando código Java..."
mkdir -p bin

CP="bin:lib/*"
javac -cp "$CP" -d bin $(find src/main/java -name "*.java")

echo "✔ Compilación completada con éxito en ./bin"

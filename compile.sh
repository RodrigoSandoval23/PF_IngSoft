#!/usr/bin/env bash
set -e

echo "Compilando código Java..."
mkdir -p bin

# Buscar todos los archivos .java y compilar con javac
javac -d bin $(find src/main/java -name "*.java")

echo "✔ Compilación completada con éxito en ./bin"


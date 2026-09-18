#!/usr/bin/env bash
set -e

echo "Compilando código y suite de pruebas de seguridad..."
mkdir -p bin
CP="bin:lib/*"
javac -cp "$CP" -d bin $(find src/main/java src/test/java -name "*.java")

echo "Ejecutando pruebas de seguridad OWASP Top 10..."
java -cp "$CP" com.solidaria.OwaspSecurityTest

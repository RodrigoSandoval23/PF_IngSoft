#!/usr/bin/env bash
set -e

echo "Compilando código y suite de pruebas de seguridad..."
./compile.sh
javac -d bin $(find src/main/java src/test/java -name "*.java")

echo "Ejecutando pruebas de seguridad OWASP Top 10..."
java -cp bin com.solidaria.OwaspSecurityTest

#!/usr/bin/env bash
set -e

# Compilar si la carpeta bin no existe
if [ ! -d "bin" ] || [ ! -f "bin/com/solidaria/Main.class" ]; then
    ./compile.sh
fi

PORT="${1:-8080}"
echo "Ejecutando servidor Java en el puerto $PORT..."
CP="bin:lib/*"
java -cp "$CP" com.solidaria.Main "$PORT"

#!/usr/bin/env bash
set -e

if [ -z "$JAVA_HOME" ]; then
    if [ -d "/Library/Java/JavaVirtualMachines/amazon-corretto-11.jdk/Contents/Home" ]; then
        export JAVA_HOME="/Library/Java/JavaVirtualMachines/amazon-corretto-11.jdk/Contents/Home"
    elif [ -x "/usr/libexec/java_home" ]; then
        export JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null || true)"
    fi
fi
if [ -n "$JAVA_HOME" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# Compilar si la carpeta bin no existe
if [ ! -d "bin" ] || [ ! -f "bin/com/solidaria/Main.class" ]; then
    ./compile.sh
fi

PORT="${1:-8080}"
echo "Ejecutando servidor Java en el puerto $PORT..."
CP="bin:lib/*"
java -cp "$CP" com.solidaria.Main "$PORT"

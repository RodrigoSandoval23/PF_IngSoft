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

echo "Compilando código Java..."
mkdir -p bin

CP="bin:lib/*"
javac -cp "$CP" -d bin $(find src/main/java -name "*.java")

echo "✔ Compilación completada con éxito en ./bin"

#!/bin/bash
# ==============================================================================
# RationGuard - Terminal Startup Script
# ==============================================================================

# Ensure Java and Maven paths are set (for macOS Homebrew OpenJDK)
export PATH="/opt/homebrew/opt/openjdk/bin:$PATH"

# Move to the project root directory
cd "$(dirname "$0")"

echo "======================================================="
echo "        STARTING RATIONGUARD WEB SERVER               "
echo "======================================================="

# Compile Java sources and copy static web assets
mvn compile -q

# Launch WebServer
java -cp ".:target/classes:lib/*:$(mvn dependency:build-classpath | grep -A 1 'Dependencies classpath:' | tail -n 1)" WebServer


#!/usr/bin/env bash
export JAVA_HOME="$HOME/opt/jdk-21"
export PATH="$HOME/opt/apache-maven-3.9.16/bin:$JAVA_HOME/bin:$PATH"
cd "$(dirname "$0")/../backend"
exec mvn "$@"
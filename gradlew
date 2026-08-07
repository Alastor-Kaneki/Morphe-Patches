#!/bin/sh
# CI Gradle entrypoint. GitHub Actions installs Gradle 9.6.1 before this is invoked.
exec gradle "$@"

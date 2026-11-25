#!/usr/bin/env bash
# Moves the Gradle dependencies of the image build in and out of the BuildKit cache mount that
# the Dockerfile uses, so that CI can keep them between runs with actions/cache.
#
#   image-gradle-cache.sh seed DIR     copies DIR into the cache mount
#   image-gradle-cache.sh extract DIR  copies the downloaded dependencies out of it into DIR
set -euo pipefail

if [[ $# -ne 2 || ( $1 != seed && $1 != extract ) ]]; then
  echo "usage: $0 seed|extract DIR" >&2
  exit 2
fi
mode=$1
dir=$2

# The JDK image of the build stage: already pulled for the image build, and pinned in one place.
base=$(awk '/^FROM .* AS build$/ { print $2; exit }' Dockerfile)
if [[ -z $base ]]; then
  echo "No build stage found in the Dockerfile" >&2
  exit 1
fi

# The target is the one the Dockerfile uses, which makes it the same cache mount.
if [[ $mode == seed ]]; then
  docker build --quiet --build-arg BASE="$base" --file - "$dir" > /dev/null <<'EOF'
ARG BASE
FROM ${BASE}
COPY . /seed
RUN --mount=type=cache,target=/root/.gradle,sharing=locked cp -a /seed/. /root/.gradle/
EOF
else
  # Only what was downloaded: the wrapper distribution and the module artifacts. Lock files
  # belong to the Gradle run that wrote them.
  context=$(mktemp -d)
  mkdir -p "$dir"
  docker build --quiet --build-arg BASE="$base" --output "type=local,dest=$dir" \
    --file - "$context" <<'EOF'
ARG BASE
FROM ${BASE} AS copy
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    mkdir -p /out/caches \
    && cp -a /root/.gradle/wrapper /out/ \
    && cp -a /root/.gradle/caches/modules-2 /out/caches/ \
    && find /out -name '*.lock' -delete
FROM scratch
COPY --from=copy /out /
EOF
  rmdir "$context"
fi

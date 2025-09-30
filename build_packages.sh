#!/bin/bash

OS=${1:-"ubuntu-jammy"}
cp boot/target/carbonio-user-management-*-jar-with-dependencies.jar package/carbonio-user-management.jar

echo "Building for OS: $OS"

docker run -it \
  --entrypoint=yap \
  -v $(pwd)/artifacts:/artifacts \
  -v $(pwd):/project \
  -w /project \
  "docker.io/m0rf30/yap-${OS}:1.44" \
  build "${OS}" .
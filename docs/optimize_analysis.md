# Build with debug symbols
./gradlew :server:linkDebugExecutableArch

# Run under valgrind (suppress GDAL's own intentional one-time allocs)
CHART_SERVER_OPTS='{ "webStaticContent": "./web/build/dist/js/productionExecutable" }' \
valgrind --leak-check=full \
--show-leak-kinds=definite,indirect \
--track-origins=yes \
./server/build/bin/arch/debugExecutable/server.kexe \
./server/src/nativeMain/resources 

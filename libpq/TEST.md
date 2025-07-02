# Stepping through tests

```shell
./gradlew :libpq:cleanNativeTest :libpq:nativeTestBinaries
lldb ./libpq/build/bin/native/debugTest/test.kexe -- --ktest_filter="PgPreparedStatementTest.testPreparedStatement" 
```

set break points
``` 
b -l 39 -f ./libpq/src/nativeMain/kotlin/PgPreparedStatement.kt 
```

run / rerun
``` 
r
```
# ktpq is [libpq](https://www.postgresql.org/docs/current/libpq.html) for kotlin native
status: experimental

publish to maven local
```shell
 ./gradlew publishToMavenLocal
```

publish to github packages 
```shell
 ./gradlew publishAllPublicationsToGitHubPackagesRepository
```

```kotlin
repositories {
    mavenLocal()
}

sourceSets {
    nativeMain.dependencies {
	implementation("io.madrona:ktpq:1.0-SNAPSHOT")
    }
}
```

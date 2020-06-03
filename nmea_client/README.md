# Njord NMEA Client

Run:
```bash
./gradlew clean :nmea_client:installDist
./nmea_client/build/install/njord-client/bin/njord-client 192.168.86.31 10110
```

or

```bash
./gradlew :nmea_client:run
```


Publish:
```bash
./gradlew publishToMavenLocal
```

```
repositories {
    mavenLocal()
}

dependencies {
    implementation 'io.madrona.njord:client:1.0-SNAPSHOT'
} 
```
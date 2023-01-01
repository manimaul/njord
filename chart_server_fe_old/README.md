# Njord Front End - WebUI

This module includes source code which builds the web front end to Njord. Like the `chart_server` backend the front
end is written in Kotlin. However, the front end is transpiled to JS so that it can be run in the browser. 


### Start the FE
```shell
./gradlew :chart_server_fe:browserDevelopmentWebpack
```
Note: `./gradlew :chart_server_fe:browserDevelopmentRun --continuous` is currently NOT supported


### Start the back end
```shell
./gradlew :chart_server:run
```


### Open Browser
http://localhost:9000/v1/app/about
http://localhost:9000/v1/app

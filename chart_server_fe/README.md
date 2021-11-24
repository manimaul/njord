# Njord Front End - WebUI

This modules includes source code which builds the web front end to Njord. Like the `chart_server` backend the front
end is written in Kotlin. However, the front end is transpiled to JS so that it can be run in the browser. 

### Start the FE
```shell
./gradlew :chart_server_fe:browserDevelopmentRun --continuous
```

todo: (WK) how do we point the FE at a specific BE?
      should the BE serve FE content or should the FE be a separate deployment?

### Start the back end
```shell
./gradlew :chart_server:run
```

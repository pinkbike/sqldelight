# SQLDelight-Fork

This is a hard-fork of SQLDelight to add some cursor features.

Build locally with:

```shell
./gradlew :drivers:android-driver:assemble :drivers:native-driver:assemble :runtime:assemble
```

### Deploy only required artifacts
./gradlew :drivers:android-driver:publishToMavenLocal :drivers:native-driver:publishToMavenLocal :runtime:publishToMavenLocal :extensions:coroutines-extensions:publishToMavenLocal

### Deploy everything
```shell
./gradlew publishToMavenLocal
```

### Copy from mavenLocal to the project
This is to prevent the team to rebuild everything each time they need it!

```shell
cp -R ~/.m2/repository/com <current-checkout-of-trailforks-map-app>/platforms/android/tf-shared/sqldelight-repository
```



# Original project

See the [project website](https://cashapp.github.io/sqldelight/) for documentation and APIs
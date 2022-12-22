# Plugin receives build result

This repository contains some samples to demonstrate the use of the [new API](./docs/spec.md) to be introduced in Gradle 8.1 as the configuration cache compatible replacement for the [`buildFinished` event hook](https://docs.gradle.org/current/userguide/upgrading_version_7.html#build_finished_events).

## Repository structure

This repository contains 3 projects, the _plugins_ project holds the implementation of the plugins that are exercised by the other 2 projects, _project-with-soundfeedback_ and _project-with-lavalamp_.

### Plugins

The [SoundfeedbackPlugin](./plugins/src/main/java/plugins/soundfeedback/SoundFeedbackPlugin.java) is a `Project` plugin that plays an appropriate sound at the end of a build. It demonstrates the use of service injection with the new `FlowAction` API.

The [LavaLampPlugin](./plugins/src/main/java/plugins/lavalamp/LavaLampPlugin.java) makes a lava lamp, controlled by a shared build service, shine in an appropriate color at the end of the build. It demonstrates the use of a `@ServiceReference` parameter for getting access to a shared build service from a `FlowAction` implementation. 

### project-with-soundfeedback

This project applies the `soundfeedback` plugin and can be exercised in the following manner:

    $ cd ./project-with-soundfeedback

    # exercise a successful build
    $ ./gradlew ok
    ...
    BUILD SUCCESSFUL in 3s
   
    # exercise a task failure
    $ ./gradlew fail
    ...
    * What went wrong:
      Execution failed for task ':fail'.
    ...
    BUILD FAILED in 5s

    # exercise a configuration failure
    $ ./gradlew -PfailConfig
    ...
    * What went wrong:
      Simulated configuration failure.
    ...
    BUILD FAILED in 5s

For the sound feedback to actually be heard, [`ffplay`](https://ffmpeg.org/ffplay.html) must be installed and available in the `PATH` visible to Gradle.

### project-with-lavalamp

This project applies the `lavalamp` plugin in its [settings file](./project-with-lavalamp/settings.gradle.kts), a plugin that uses a _shared build service_ from a _flow action_. It can be exercised in following manner:

    $ cd ./project-with-lavalamp

    # exercise a successful build
    $ ./gradlew ok
    Lava lamp is shining green.
    ...
    BUILD SUCCESSFUL in 600ms
   
    # exercise a task failure
    $ ./gradlew fail
    > Task :fail FAILED
    Lava lamp is shining red.
    ...
    BUILD FAILED in 607ms

    # exercise a configuration failure
    $ ./gradlew -PfailConfig
    Lava lamp is shining red.
    ...
    BUILD FAILED in 608ms

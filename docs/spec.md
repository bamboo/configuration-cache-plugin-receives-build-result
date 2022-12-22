# Specification: Plugin receives build result

## Abstract

The `gradle.buildFinished` event hook has been missing a configuration cache compatible replacement.

This specification proposes filling that gap with a new API centered around the concept of _dataflow actions_ (not very
unlike [work actions](https://docs.gradle.org/current/javadoc/org/gradle/workers/WorkAction.html)) intended to integrate with, highlight and promote the flow-based programming model that sits at the
core of Gradle.

A prototype is available in the following pull-request: [gradle/gradle#23222](https://github.com/gradle/gradle/pull/23222).

## Background

The [`buildFinished` event hook](https://docs.gradle.org/current/userguide/upgrading_version_7.html#build_finished_events) has been deprecated in Gradle 7.4 for it was deemed incompatible with the configuration
cache model.

The lack of a suitable replacement has been a source of frustration for the community:

* [Build finished event now deprecated](https://github.com/gradle/gradle/issues/20151)
  > Starting in Gradle 7.4, both the Gradle.buildFinished and BuildListener.buildFinished are deprecated. Apparently, it was not possible to get these methods to work with the configuration caching feature. The deprecation message does not list an alternative mechanism for being called when a build completes. In addition to this deprecation breaking one of my plugins, it seems like a serious regression to remove the ability to receive such a fundamental event as the completion of the build.
* [Introduce a mechanism to obtain the build result when using configuration caching](https://github.com/gradle/gradle/issues/17659)
  > I'd like to have the ability to
  acquire the build result from within my build when configuration caching is enabled. In particular, I'd like for build
  services to be able to acquire the build result at the time the build finishes.
* [Play an mp3 when the build is done](https://github.com/gradle/gradle/issues/20151#issuecomment-1282816901)
  > My gradle.buildFinished { result -> ... } is a single line of code playing an mp3 when the build is done. In the new version, will I need to write 20 lines (of code I don't understand) in order to achieve the same thing?
* [Inspect build failure exception](https://github.com/gradle/gradle/issues/20151#issuecomment-1263642351)
  > we have code that gets the access to build failure's exception stack trace. With build service, it's not possible. Also if a code in project.getGradle().projectsEvaluated() callback throws an exception, it will not be caught by the OperationCompletionListener (tested with Gradle 7.5).
* [Is there a way to determine whether a build failed or succeeded?](https://github.com/gradle/gradle/issues/14860#issuecomment-999210736)

It is time to finally add new APIs that can be used as a replacement for the deprecated and configuration caching
incompatible hook. In particular, we want to allow a plugin to define some work that runs after all requested tasks have
completed, taking their result as input.

## Why

It's an important milestone for declaring the Configuration Cache feature stable.

## Questions/problems

### What's wrong with buildFinished?

#### Event handlers are not isolated

`buildFinished` event handlers are closures, free to capture their environment and share state. Shared mutable state goes
against "isolated execution" which, as we know, "is essential to safely provide features like output caching,
incremental build, and parallel execution."

#### It makes it hard to separate configuration state from execution state

The `buildFinished` hook doesn't exist in isolation, it is part of the larger [`BuildListener` interface](https://github.com/gradle/gradle/blob/86eca1dc7f5830550822a691eee3151e995c9e8d/subprojects/core-api/src/main/java/org/gradle/BuildListener.java#L70-L69), an interface mostly
concerned with configuration phase hooks. It seems counterproductive to serialize `BuildListener` instances to the
configuration cache when only one method and likely a fraction of their implementation state have any use at all during
the execution phase.

#### Programming model

It promotes an imperative, control-flow based programming model instead of a declarative, flow-based programming model.

#### Lack of Observability

It is currently hard to identify particularly expensive and/or long-running event handlers.
In build scans, for example, all the work that happens at the end of the build (i.e. after the last task has finished)
is lumped together as the single entry End of build in the Performance tab.

## Functional design
After considering different implementation options in light of our recent discussions about the future of the Gradle
configuration model, with Project Isolation and finer granularity invalidation of caching structures, we propose the
introduction of a new, and for now small, API to allow the definition of anonymous, parameterized and isolated pieces of
work that augment the cached work graph and that are triggered solely based on the availability of their input
parameters:

```java
/**
 * A dataflow action.
 *
 * <p>
 * A parameterized and isolated piece of work that becomes eligible for execution
 * within a {@link FlowScope dataflow scope} as soon as all of its
 * input {@link FlowParameters parameters} become available.
 * </p>
 * <p>
 * Implementations can benefit from constructor injection of services
 * using the {@link javax.inject.Inject @Inject} annotation.
 * </p>
 */
public interface FlowAction<P extends FlowParameters> {
   void execute(P parameters) throws Exception;
} 
```

Given the above API, one could define an action that plays a media file once its `mediaFile` parameter becomes available
in the following manner:

```java
  /**
   * Plays a given {@link Parameters#getMediaFile() media file} using {@code ffplay}.
   */
  class FFPlay implements FlowAction<FFPlay.Parameters> {
 
      interface Parameters extends FlowParameters {
          RegularFileProperty getMediaFile();
      }
 
      private final ExecOperations execOperations;
 
      @Inject
      FFPlay(ExecOperations execOperations) {
          this.execOperations = execOperations;
      }
 
      @Override
      public void execute(Parameters parameters) throws Exception {
          execOperations.exec(spec -> {
              spec.commandLine(
                  "ffplay", "-nodisp", "-autoexit", "-hide_banner", "-loglevel", "quiet",
                  parameters.getMediaFile().get().getAsFile().getAbsolutePath()
              );
              spec.setIgnoreExitValue(true);
          });
      }
  }
```

The proposed API also exposes a way for reading a summary of the result of executing the requested tasks (once it
becomes available):

```java
/**
 * Exposes build lifecycle events as {@link Provider providers} so they
 * can be used as inputs to {@link FlowAction dataflow actions}.
 */
public interface FlowProviders {

    /**
     * Returns a {@link Provider provider} for the summary of the
     * result of executing the requested tasks.
     * <p>
     * <b>IMPORTANT:</b> trying to access the provider's value before
     * the requested tasks have finished will result in an error.
     * </p>
     */
    Provider<RequestedTasksResult> getRequestedTasksResult();
}

/**
 * Summary of the result of executing the requested tasks.
 */
public interface RequestedTasksResult {
    /**
     * A summary of the failure(s) that occurred as Gradle tried
     * to execute the requested tasks.
     *
     * @return {@link Optional#empty() empty} when no failures occur.
     */
    Optional<Throwable> getFailure();
}
```

And a scoped mechanism for augmenting the work graph:

```java
/**
 * Augments the cached work graph with {@link FlowAction dataflow actions},
 * anonymous, parameterized and isolated pieces of work that are triggered
 * solely based on the availability of their input parameters.
 */
public interface FlowScope {

    /**
     * Registers a {@link FlowAction dataflow action} that's always part of the
     * dataflow graph.
     *
     * @param action the {@link FlowAction dataflow action} type.
     * @param configure configuration for the given action parameters.
     * @param <P> the parameters defined by the given action type.
     * @return a {@link Registration} object representing the registered action.
     */
    <P extends FlowParameters> Registration<P> always(
        Class<? extends FlowAction<P>> action,
        Action<? super FlowActionSpec<P>> configure
    );
}
```

The newly introduced `FlowScope` and `FlowProvider` services are available for injection on `Settings` and `Project` plugins (in
fact they would be visible everywhere that can see build scoped services).

Altogether, the new API allows implementing the _play an mp3 when the build is done_ use-case in a few lines of code:

```java
/**
 * A settings plugin that plays an appropriate sound at the end of a build.
 */
class SoundFeedbackPlugin implements Plugin<Settings> {

    private final FlowScope flowScope;
    private final FlowProviders flowProviders;

    @Inject
    SoundFeedbackPlugin(FlowScope flowScope, FlowProviders flowProviders) {
        this.flowScope = flowScope;
        this.flowProviders = flowProviders;
    }

    @Override
    public void apply(Settings target) {
        final File soundsDir = new File(target.getSettingsDir(), "sounds");
        flowScope.always(FFPlay.class, spec ->
            spec.getParameters().getMediaFile().fileProvider(
                flowProviders.getRequestedTasksResult().map(result ->
                    new File(
                        soundsDir,
                        result.getFailure().isPresent()
                            ? "sad-trombone.mp3"
                            : "tada.mp3"
                    )
                )
            )
        );
    }
}
```

## Considered Alternatives

### Add a "requested tasks finished" event to the build events stream

A plugin could then handle the event via a shared build service registered as a listener.

Pros:
* Shared build services are already used as listeners for related use-cases.

Cons:
* It promotes an imperative, control-flow based programming model.
* Shared build services are meant to represent shared resources, not units of work, as such, their callback style event handlers lack many of the features usually available to other work abstractions in Gradle such as composability, observability, incremental execution and caching.
* For simpler use-cases, shared build services as named singletons that also need to be registered as listeners seem to bring an unjustifiable amount of conceptual weight.

### Introduce a "goal" lifecycle task exposing the result of the build as an output

Plugins could then register finalizers for the task in order to react to the result of the build. Gradle would always
execute finalizers for such a "goal" task and the "goal" task would always be scheduled, even after a configuration
phase failure.

```kotlin
tasks {
  goal {
    finalizedBy("playMp3")
  }

  register<PlayMp3>("playMp3") {
    mp3.set(
      goal
        .flatMap { it.result }
        .map { if (it.failure.present) "sad-trombone.mp3" else "tada.mp3" }
    )
  }
}
```

As a bonus, arbitrary tasks could be injected into the task graph via "goal" dependencies:

    goal { dependsOn("myTask") }

Pros:

* It reuses a proper work abstraction (a Task) to represent work that reacts to the result of the build.

Cons:

* It requires a change to the phased execution semantics of Gradle to allow the execution of certain tasks (finalizers of the goal task and their dependencies) after a failure during the configuration phase.
* For simpler use-cases, the registration of a named Task seems to impose an unnecessary burden on users.
* It is unclear how such a "goal" task, a task that would always be present in the work graph, should be represented in the various Gradle frontends (command line, build scans, tooling API models and IDEs).

### Introduce a provider for the summary of the result of executing the requested tasks

Let it be used as an input to a regular task and provide a mechanism to make a task always present in the work graph.

```kotlin
tasks {
    val isFailed: Provider<Boolean> = buildLifecycle
        .requestedTasksResult
        .map { it.failure.isPresent }
    always<PlayMp3> {
        mp3.set(isFailed.map { if (it) "sad-trombone.mp3" else "tada.mp3" })
    }
}
```

This is very similar to the proposed solution except it relies on tasks instead of a new, lighter-weight abstraction.

## Won’t Do Yet

* Dataflow actions with output parameters.
* Dataflow actions with dependencies on tasks or, in other words, dataflow actions with input parameters sourced from
  task output properties.
* A DSL for more ergonomic use from scripts.
* Introduce project level flow scopes that can be invalidated independently or any kind of integration with Project
  Isolation.
* Address observability in build scans. However, the implementation of this spec should reuse the Worker API
  infrastructure as much as possible in order to provide observability in build scans once that's available.
* Submitting flow actions from other work items.
* Submitting work items from flow actions.


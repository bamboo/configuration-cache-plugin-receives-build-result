package plugins.lavalamp;

import org.gradle.api.Plugin;
import org.gradle.api.flow.FlowProviders;
import org.gradle.api.flow.FlowScope;
import org.gradle.api.initialization.Settings;

import javax.inject.Inject;

/**
 * A {@link Settings} plugin that makes a lava lamp shine in an appropriate
 * color at the end of the build.
 */
class LavaLampPlugin implements Plugin<Settings> {

    private final FlowScope flowScope;
    private final FlowProviders flowProviders;

    @Inject
    LavaLampPlugin(FlowScope flowScope, FlowProviders flowProviders) {
        this.flowScope = flowScope;
        this.flowProviders = flowProviders;
    }

    @Override
    public void apply(Settings target) {
        registerLavaLampService(target);
        scheduleSetColorAction();
    }

    private static void registerLavaLampService(Settings target) {
        target.getGradle().getSharedServices().registerIfAbsent("lamp", LavaLamp.class, spec -> {
        });
    }

    private void scheduleSetColorAction() {
        flowScope.always(SetLavaLampColor.class, spec ->
            spec.getParameters().getColor().set(
                flowProviders.getRequestedTasksResult().map(result ->
                    result.getFailure().isPresent()
                        ? "red"
                        : "green"
                )
            )
        );
    }
}


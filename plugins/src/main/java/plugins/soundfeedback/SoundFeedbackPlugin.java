package plugins.soundfeedback;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.flow.FlowProviders;
import org.gradle.api.flow.FlowScope;

import javax.inject.Inject;
import java.io.File;

/**
 * A {@link Project} plugin that plays an appropriate sound at the end of a build.
 */
public class SoundFeedbackPlugin implements Plugin<Project> {

    private final FlowScope flowScope;
    private final FlowProviders flowProviders;

    @Inject
    SoundFeedbackPlugin(FlowScope flowScope, FlowProviders flowProviders) {
        this.flowScope = flowScope;
        this.flowProviders = flowProviders;
    }

    @Override
    public void apply(Project target) {
        File failure = target.file("sounds/sad-trombone.mp3");
        File success = target.file("sounds/tada.mp3");
        flowScope.always(PlayMediaFile.class, spec ->
            spec.getParameters().getMediaFile().fileProvider(
                flowProviders.getRequestedTasksResult().map(result ->
                    result.getFailure().isPresent()
                        ? failure
                        : success
                )
            )
        );
    }
}

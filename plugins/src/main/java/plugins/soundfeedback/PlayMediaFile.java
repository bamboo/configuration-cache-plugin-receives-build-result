package plugins.soundfeedback;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.flow.FlowAction;
import org.gradle.api.flow.FlowParameters;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;

/**
 * Plays a given {@link Parameters#getMediaFile() media file} using {@code ffplay}.
 */
public class PlayMediaFile implements FlowAction<PlayMediaFile.Parameters> {

    public interface Parameters extends FlowParameters {
        RegularFileProperty getMediaFile();
    }

    private final ExecOperations execOperations;

    @Inject
    public PlayMediaFile(ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    @Override
    public void execute(Parameters parameters) {
        execOperations.exec(spec -> {
            String mediaFilePath = parameters.getMediaFile().get().getAsFile().getAbsolutePath();
            spec.commandLine("ffplay", "-nodisp", "-autoexit", "-hide_banner", "-loglevel", "quiet", mediaFilePath);
            spec.setIgnoreExitValue(true);
        });
    }
}

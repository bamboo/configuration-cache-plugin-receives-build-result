package plugins.lavalamp;

import org.gradle.api.flow.FlowAction;
import org.gradle.api.flow.FlowParameters;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;

/**
 * Makes a given {@link Parameters#getLamp() lava lamp}
 * shine in the chosen {@link Parameters#getColor() color}.
 */
public class SetLavaLampColor implements FlowAction<SetLavaLampColor.Parameters> {

    interface Parameters extends FlowParameters {

        @ServiceReference("lamp")
        Property<LavaLamp> getLamp();

        Property<String> getColor();
    }

    @Override
    public void execute(Parameters parameters) throws Exception {
        parameters.getLamp().get().setColor(
            parameters.getColor().get()
        );
    }
}

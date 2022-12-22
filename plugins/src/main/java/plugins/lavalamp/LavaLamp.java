package plugins.lavalamp;

import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

/**
 * Controls an (imaginary) lava lamp connected to the system.
 */
public abstract class LavaLamp implements BuildService<BuildServiceParameters.None> {

    public void setColor(String color) {
        System.out.printf("Lava lamp is shining %s.%n", color);
    }
}

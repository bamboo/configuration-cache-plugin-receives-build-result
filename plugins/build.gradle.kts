plugins {
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("soundfeedback") {
            id = "soundfeedback"
            implementationClass = "plugins.soundfeedback.SoundFeedbackPlugin"
        }
        create("lavalamp") {
            id = "lavalamp"
            implementationClass = "plugins.lavalamp.LavaLampPlugin"
        }
    }
}

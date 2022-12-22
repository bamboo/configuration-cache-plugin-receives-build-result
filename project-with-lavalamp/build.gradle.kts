
require(!hasProperty("failConfig")) {
    "Simulated configuration failure."
}

tasks {

    register("ok") {
    }

    register("fail") {
        doLast {
            require(false) {
                "Simulated task failure."
            }
        }
    }
}

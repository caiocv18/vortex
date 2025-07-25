package br.com.vortex.authorization;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class VortexAuthorizationApplication implements QuarkusApplication {

    public static void main(String... args) {
        Quarkus.run(VortexAuthorizationApplication.class, args);
    }

    @Override
    public int run(String... args) throws Exception {
        Quarkus.waitForExit();
        return 0;
    }
}
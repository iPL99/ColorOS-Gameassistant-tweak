package dev.gyrokr.vectorhook;

import android.util.Log;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class GyroKrModule extends XposedModule {

    private static final String TAG = "OplusGyroKR";

    private static final String GAMESPACE_PACKAGE =
            "com.oplus.games";

    private static final String TARGET_GAME =
            "com.pubg.krmobile";

    private static final String FEATURE_CLASS =
            "com.coloros.gamespaceui.gamefunction.gyroscope.GyroscopeConfigFeature";

    private static final String GATE_METHOD =
            "s";

    /*
     * Constructor test.
     *
     * This is intentionally the only diagnostic change.
     * It creates a marker file if Vector actually instantiates
     * GyroKrModule.
     */
    public GyroKrModule() {
        super();

        try {
            java.io.File file =
                    new java.io.File(
                            "/data/local/tmp/gyrokr_constructor"
                    );

            java.io.FileWriter writer =
                    new java.io.FileWriter(file);

            writer.write(
                    "MODULE CONSTRUCTOR CALLED\n"
            );

            writer.close();

        } catch (Throwable ignored) {
        }
    }

    private final class GyroGateHooker
            implements XposedInterface.Hooker {

        @Override
        public Object intercept(
                XposedInterface.Chain chain)
                throws Throwable {

            Object packageName =
                    chain.getArg(0);

            log(
                    Log.INFO,
                    TAG,
                    "GyroscopeConfigFeature.s() called: "
                            + packageName
            );

            if (TARGET_GAME.equals(packageName)) {

                log(
                        Log.INFO,
                        TAG,
                        "Returning TRUE for "
                                + TARGET_GAME
                );

                return Boolean.TRUE;
            }

            return chain.proceed();
        }
    }

    @Override
    public void onPackageReady(
            XposedModuleInterface.PackageReadyParam param) {

        String packageName =
                param.getPackageName();

        log(
                Log.INFO,
                TAG,
                "onPackageReady: "
                        + packageName
        );

        if (!GAMESPACE_PACKAGE.equals(packageName)) {

            log(
                    Log.INFO,
                    TAG,
                    "Ignoring package: "
                            + packageName
            );

            return;
        }

        log(
                Log.INFO,
                TAG,
                "================================"
        );

        log(
                Log.INFO,
                TAG,
                "GameSpace detected"
        );

        log(
                Log.INFO,
                TAG,
                "Package = "
                        + packageName
        );

        log(
                Log.INFO,
                TAG,
                "================================"
        );

        try {

            ClassLoader loader =
                    param.getClassLoader();

            if (loader == null) {

                log(
                        Log.ERROR,
                        TAG,
                        "GameSpace ClassLoader is NULL"
                );

                return;
            }

            log(
                    Log.INFO,
                    TAG,
                    "ClassLoader = "
                            + loader
            );

            Class<?> feature =
                    Class.forName(
                            FEATURE_CLASS,
                            false,
                            loader
                    );

            log(
                    Log.INFO,
                    TAG,
                    "Found class: "
                            + feature.getName()
            );

            Method gate =
                    feature.getDeclaredMethod(
                            GATE_METHOD,
                            String.class
                    );

            log(
                    Log.INFO,
                    TAG,
                    "Found method: "
                            + gate
            );

            if (!Modifier.isStatic(
                    gate.getModifiers())) {

                throw new NoSuchMethodException(
                        "s(String) is not static"
                );
            }

            if (gate.getReturnType()
                    != boolean.class) {

                throw new NoSuchMethodException(
                        "s(String) does not return boolean"
                );
            }

            log(
                    Log.INFO,
                    TAG,
                    "Method signature verified"
            );

            log(
                    Log.INFO,
                    TAG,
                    "Installing hook..."
            );

            hook(gate)
                    .intercept(
                            new GyroGateHooker()
                    );

            log(
                    Log.INFO,
                    TAG,
                    "================================"
            );

            log(
                    Log.INFO,
                    TAG,
                    "OplusGyroKR HOOK INSTALLED"
            );

            log(
                    Log.INFO,
                    TAG,
                    "Target = "
                            + TARGET_GAME
            );

            log(
                    Log.INFO,
                    TAG,
                    "Method = "
                            + FEATURE_CLASS
                            + "."
                            + GATE_METHOD
                            + "(String)"
            );

            log(
                    Log.INFO,
                    TAG,
                    "================================"
            );

        } catch (Throwable t) {

            log(
                    Log.ERROR,
                    TAG,
                    "Failed to install GameSpace gyro hook",
                    t
            );
        }
    }
}

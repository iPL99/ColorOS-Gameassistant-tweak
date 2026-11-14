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

    /**
     * Hooker for libxposed API 102.
     */
    private static final class GyroGateHooker
            implements XposedInterface.Hooker {

        @Override
        public Object intercept(XposedInterface.Chain chain)
                throws Throwable {

            Object packageName = chain.getArg(0);

            Log.i(
                    TAG,
                    "GyroscopeConfigFeature.s() called: "
                            + packageName
            );

            if (TARGET_GAME.equals(packageName)) {

                Log.i(
                        TAG,
                        "Returning TRUE for "
                                + TARGET_GAME
                );

                return Boolean.TRUE;
            }

            return chain.proceed();
        }
    }

    /**
     * libxposed API 102 callback.
     */
    @Override
    public void onPackageReady(
            XposedModuleInterface.PackageReadyParam param) {

        String packageName = param.getPackageName();

        Log.i(
                TAG,
                "onPackageReady: "
                        + packageName
        );

        if (!GAMESPACE_PACKAGE.equals(packageName)) {
            return;
        }

        Log.i(
                TAG,
                "GameSpace detected"
        );

        try {

            ClassLoader loader =
                    param.getClassLoader();

            if (loader == null) {

                Log.e(
                        TAG,
                        "GameSpace ClassLoader is null"
                );

                return;
            }

            Log.i(
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

            Log.i(
                    TAG,
                    "Found class: "
                            + feature.getName()
            );

            Method gate =
                    feature.getDeclaredMethod(
                            GATE_METHOD,
                            String.class
                    );

            Log.i(
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

            Log.i(
                    TAG,
                    "Installing hook..."
            );

            hook(gate)
                    .intercept(
                            new GyroGateHooker()
                    );

            Log.i(
                    TAG,
                    "================================"
            );

            Log.i(
                    TAG,
                    "OplusGyroKR HOOK INSTALLED"
            );

            Log.i(
                    TAG,
                    "Target = "
                            + TARGET_GAME
            );

            Log.i(
                    TAG,
                    "================================"
            );

        } catch (Throwable t) {

            Log.e(
                    TAG,
                    "Failed to install gyro hook",
                    t
            );
        }
    }
}

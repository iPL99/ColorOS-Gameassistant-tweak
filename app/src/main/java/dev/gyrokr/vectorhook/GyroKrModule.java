package dev.gyrokr.vectorhook;

import android.util.Log;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Minimal libxposed API 102 module for the ColorOS/Oplus GameSpace allow-list gate.
 * It changes only GyroscopeConfigFeature.s("com.pubg.krmobile") to true.
 * The hardware capability check in GyroscopeConfigFeature.q() is not modified.
 */
public final class GyroKrModule extends XposedModule {
    private static final String TAG = "OplusGyroKR";
    private static final String GAMESPACE_PACKAGE = "com.oplus.games";
    private static final String TARGET_GAME = "com.pubg.krmobile";
    private static final String FEATURE_CLASS =
            "com.coloros.gamespaceui.gamefunction.gyroscope.GyroscopeConfigFeature";
    private static final String GATE_METHOD = "s";

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!GAMESPACE_PACKAGE.equals(param.getPackageName())) {
            return;
        }

        try {
            ClassLoader loader = param.getDefaultClassLoader();
            Class<?> feature = Class.forName(FEATURE_CLASS, false, loader);
            Method gate = feature.getDeclaredMethod(GATE_METHOD, String.class);

            // Fail closed if an OTA changes the expected method shape.
            if (!Modifier.isStatic(gate.getModifiers()) || gate.getReturnType() != boolean.class) {
                throw new NoSuchMethodException("Unexpected signature for " + FEATURE_CLASS + "." + GATE_METHOD);
            }

            hook(gate)
                    .setId("oplus-gyro-kr-allowlist")
                    .intercept(chain -> {
                        Object packageName = chain.getArg(0);
                        if (TARGET_GAME.equals(packageName)) {
                            log(Log.INFO, TAG, "Allow-list gate bypassed for " + TARGET_GAME);
                            return Boolean.TRUE;
                        }
                        return chain.proceed();
                    });

            log(Log.INFO, TAG, "Installed allow-list hook in " + GAMESPACE_PACKAGE);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to install GameSpace gyro hook", t);
        }
    }
}

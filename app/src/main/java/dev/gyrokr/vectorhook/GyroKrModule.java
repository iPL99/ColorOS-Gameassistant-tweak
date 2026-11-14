package dev.gyrokr.vectorhook;

import android.util.Log;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * OplusGyroKR
 *
 * ColorOS / Oplus GameSpace의 GyroscopeConfigFeature.s(String)
 * allow-list gate만 우회한다.
 *
 * 기존 q()의 hardware capability check는 건드리지 않는다.
 */
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
     * libxposed API 102 Hooker.
     *
     * 실제 notdeveloper-1.7.0에서 확인한 구조:
     *
     *   intercept(Chain)
     *   chain.getArg(0)
     *   chain.proceed()
     */
    private static final class GyroGateHooker
            implements XposedInterface.Hooker {

        @Override
        public Object intercept(XposedInterface.Chain chain)
                throws Throwable {

            Object arg = chain.getArg(0);

            if (TARGET_GAME.equals(arg)) {
                Log.i(TAG,
                        "Gyroscope allow-list bypass: "
                                + TARGET_GAME);

                return Boolean.TRUE;
            }

            return chain.proceed();
        }
    }


    /**
     * Vector / libxposed API 102의 실제 package callback.
     */
    @Override
    public void onPackageReady(
            XposedModuleInterface.PackageReadyParam param) {

        String packageName = param.getPackageName();

        if (!GAMESPACE_PACKAGE.equals(packageName)) {
            return;
        }

        try {
            ClassLoader loader = param.getClassLoader();

            if (loader == null) {
                log(
                        Log.ERROR,
                        TAG,
                        "GameSpace ClassLoader is null",
                        null
                );
                return;
            }

            Class<?> feature = Class.forName(
                    FEATURE_CLASS,
                    false,
                    loader
            );

            Method gate = feature.getDeclaredMethod(
                    GATE_METHOD,
                    String.class
            );

            /*
             * OTA로 메서드 signature가 바뀌면
             * 안전하게 hook하지 않는다.
             */
            if (!Modifier.isStatic(gate.getModifiers())) {
                throw new NoSuchMethodException(
                        "GyroscopeConfigFeature.s is not static"
                );
            }

            if (gate.getReturnType() != boolean.class) {
                throw new NoSuchMethodException(
                        "GyroscopeConfigFeature.s does not return boolean"
                );
            }

            /*
             * 실제 API 102에서 확인한 방식:
             *
             * module.hook(method)
             *        .intercept(Hooker)
             */
            hook(gate)
                    .intercept(new GyroGateHooker());

            log(
                    Log.INFO,
                    TAG,
                    "Installed gyro allow-list hook in "
                            + GAMESPACE_PACKAGE,
                    null
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

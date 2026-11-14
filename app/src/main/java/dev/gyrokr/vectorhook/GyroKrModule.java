package dev.gyrokr.vectorhook;

import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

public class GyroKrModule extends XposedModule {

    private static final String TAG = "OplusGyroKR";

    private static final String GAME_SPACE_PACKAGE =
            "com.oplus.games";

    private static final String TARGET_GAME_PACKAGE =
            "com.pubg.krmobile";

    private static final String GYRO_CLASS =
            "business.module.gyroscope.GyroscopeConfigFeature";

    private static final String COSA_CLASS =
            "com.oplus.cosa.COSASDKManager";

    private static final String TOUCH_RESPONSE_COMMAND =
            "command_multi_touch_rate";

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {

        log(
                Log.INFO,
                TAG,
                "onModuleLoaded"
        );

        try {
            log(
                    Log.INFO,
                    TAG,
                    "Process = " + param.getProcessName()
            );
        } catch (Throwable t) {
            log(
                    Log.ERROR,
                    TAG,
                    "getProcessName failed: " + t
            );
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {

        String packageName = param.getPackageName();

        log(
                Log.INFO,
                TAG,
                "onPackageReady: " + packageName
        );

        if (!GAME_SPACE_PACKAGE.equals(packageName)) {
            return;
        }

        log(
                Log.INFO,
                TAG,
                "GameSpace detected"
        );

        try {

            ClassLoader classLoader =
                    param.getClassLoader();

            log(
                    Log.INFO,
                    TAG,
                    "Package = " + packageName
            );

            log(
                    Log.INFO,
                    TAG,
                    "ClassLoader = " + classLoader
            );

            /*
             * =========================================================
             * GyroscopeConfigFeature
             * =========================================================
             */

            Class<?> gyroClass =
                    Class.forName(
                            GYRO_CLASS,
                            false,
                            classLoader
                    );

            log(
                    Log.INFO,
                    TAG,
                    "GyroscopeConfigFeature FOUND"
            );

            log(
                    Log.INFO,
                    TAG,
                    "Class = " + gyroClass.getName()
            );

            Method[] methods =
                    gyroClass.getDeclaredMethods();

            log(
                    Log.INFO,
                    TAG,
                    "Method count = " + methods.length
            );

            for (Method method : methods) {

                log(
                        Log.INFO,
                        TAG,
                        "Method: " + method.toString()
                );
            }

            /*
             * r(String)
             * isSupportGyroscopeChiralityAntishake
             */

            Method chiralityAntishakeGate =
                    gyroClass.getDeclaredMethod(
                            "r",
                            String.class
                    );

            chiralityAntishakeGate.setAccessible(true);

            log(
                    Log.INFO,
                    TAG,
                    "Found gate method: "
                            + chiralityAntishakeGate.toString()
            );

            hook(chiralityAntishakeGate)
                    .intercept(
                            new GyroGateHooker("r")
                    );

            log(
                    Log.INFO,
                    TAG,
                    "Gyroscope chirality/antishake hook INSTALLED"
            );

            /*
             * s(String)
             * isSupportGyroscopeSensitivity
             */

            Method sensitivityGate =
                    gyroClass.getDeclaredMethod(
                            "s",
                            String.class
                    );

            sensitivityGate.setAccessible(true);

            log(
                    Log.INFO,
                    TAG,
                    "Found gate method: "
                            + sensitivityGate.toString()
            );

            log(
                    Log.INFO,
                    TAG,
                    "Method s() is NOT static"
            );

            hook(sensitivityGate)
                    .intercept(
                            new GyroGateHooker("s")
                    );

            log(
                    Log.INFO,
                    TAG,
                    "Gyroscope sensitivity hook INSTALLED"
            );

            log(
                    Log.INFO,
                    TAG,
                    "GameSpace gyro hooks INSTALLED"
            );

            /*
             * =========================================================
             * COSASDKManager
             * =========================================================
             */

            installCosaTouchResponseHooks(
                    classLoader
            );

        } catch (Throwable t) {

            log(
                    Log.ERROR,
                    TAG,
                    "Failed to install hooks: " + t
            );

            Log.e(
                    TAG,
                    "Hook error",
                    t
            );
        }
    }

    private void installCosaTouchResponseHooks(
            ClassLoader classLoader
    ) {

        try {

            log(
                    Log.INFO,
                    TAG,
                    "Searching for COSASDKManager..."
            );

            Class<?> cosaClass =
                    Class.forName(
                            COSA_CLASS,
                            false,
                            classLoader
                    );

            log(
                    Log.INFO,
                    TAG,
                    "COSASDKManager FOUND"
            );

            log(
                    Log.INFO,
                    TAG,
                    "COSA Class = "
                            + cosaClass.getName()
            );

            Method[] cosaMethods =
                    cosaClass.getDeclaredMethods();

            log(
                    Log.INFO,
                    TAG,
                    "COSA Method count = "
                            + cosaMethods.length
            );

            Method supportTouchResponseMethod = null;

            Method supportFeatureListMethod = null;

            for (Method method : cosaMethods) {

                String name =
                        method.getName();

                if ("isSupportTouchResponse"
                        .equals(name)) {

                    supportTouchResponseMethod =
                            method;

                    log(
                            Log.INFO,
                            TAG,
                            "FOUND isSupportTouchResponse: "
                                    + method.toString()
                    );
                }

                if ("getSupportFeatureList"
                        .equals(name)) {

                    supportFeatureListMethod =
                            method;

                    log(
                            Log.INFO,
                            TAG,
                            "FOUND getSupportFeatureList: "
                                    + method.toString()
                    );
                }
            }

            /*
             * ---------------------------------------------------------
             * getSupportFeatureList()
             * ---------------------------------------------------------
             */

            if (supportFeatureListMethod != null) {

                try {

                    supportFeatureListMethod
                            .setAccessible(true);

                    log(
                            Log.INFO,
                            TAG,
                            "Attempting to inspect "
                                    + "getSupportFeatureList()"
                    );

                    /*
                     * COSASDKManager is normally a singleton.
                     * We do not invoke it here because we first
                     * want to avoid guessing its singleton accessor.
                     *
                     * The actual method will be hooked below if possible.
                     */

                } catch (Throwable t) {

                    log(
                            Log.ERROR,
                            TAG,
                            "getSupportFeatureList setup failed: "
                                    + t
                    );
                }
            }

            /*
             * ---------------------------------------------------------
             * isSupportTouchResponse()
             * ---------------------------------------------------------
             */

            if (supportTouchResponseMethod != null) {

                supportTouchResponseMethod
                        .setAccessible(true);

                hook(
                        supportTouchResponseMethod
                ).intercept(
                        new TouchResponseHooker()
                );

                log(
                        Log.INFO,
                        TAG,
                        "isSupportTouchResponse hook INSTALLED"
                );

            } else {

                log(
                        Log.WARN,
                        TAG,
                        "isSupportTouchResponse() NOT FOUND"
                );
            }

            /*
             * Print all COSA methods containing
             * Touch / Feature / Support / MultiTouch.
             */

            for (Method method : cosaMethods) {

                String text =
                        method.toString();

                if (text.contains("Touch")
                        || text.contains("touch")
                        || text.contains("Feature")
                        || text.contains("feature")
                        || text.contains("Support")
                        || text.contains("support")
                        || text.contains("MultiTouch")
                        || text.contains("multiTouch")) {

                    log(
                            Log.INFO,
                            TAG,
                            "COSA relevant method: "
                                    + text
                    );
                }
            }

        } catch (Throwable t) {

            log(
                    Log.ERROR,
                    TAG,
                    "Failed to inspect COSASDKManager: "
                            + t
            );

            Log.e(
                    TAG,
                    "COSA inspection error",
                    t
            );
        }
    }

    private final class TouchResponseHooker
            implements XposedInterface.Hooker {

        @Override
        public Object intercept(
                XposedInterface.Chain chain
        ) throws Throwable {

            Object originalResult =
                    chain.proceed();

            log(
                    Log.INFO,
                    TAG,
                    "isSupportTouchResponse() "
                            + "ORIGINAL = "
                            + originalResult
            );

            return originalResult;
        }
    }

    private final class GyroGateHooker
            implements XposedInterface.Hooker {

        private final String methodName;

        GyroGateHooker(String methodName) {

            this.methodName =
                    methodName;
        }

        @Override
        public Object intercept(
                XposedInterface.Chain chain
        ) throws Throwable {

            Object arg0 =
                    chain.getArg(0);

            if (arg0 instanceof String) {

                String packageName =
                        (String) arg0;

                if (TARGET_GAME_PACKAGE
                        .equals(packageName)) {

                    Object originalResult =
                            chain.proceed();

                    log(
                            Log.INFO,
                            TAG,
                            methodName
                                    + "("
                                    + packageName
                                    + ") ORIGINAL = "
                                    + originalResult
                    );

                    log(
                            Log.INFO,
                            TAG,
                            methodName
                                    + "("
                                    + packageName
                                    + ") FORCED = true"
                    );

                    return Boolean.TRUE;
                }
            }

            return chain.proceed();
        }
    }
}

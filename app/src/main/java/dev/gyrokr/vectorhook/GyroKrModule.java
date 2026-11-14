package dev.gyrokr.vectorhook;

import android.util.Log;

import java.lang.reflect.Method;

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

        String packageName =
                param.getPackageName();

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

            installCosaTouchHooks(
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

    private void installCosaTouchHooks(
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

            Method[] methods =
                    cosaClass.getDeclaredMethods();

            log(
                    Log.INFO,
                    TAG,
                    "COSA Method count = "
                            + methods.length
            );

            for (Method method : methods) {

                String name =
                        method.getName();

                /*
                 * -----------------------------------------------------
                 * isSupportTouchResponse()
                 * -----------------------------------------------------
                 */

                if ("isSupportTouchResponse"
                        .equals(name)
                        && method.getParameterTypes().length == 0) {

                    method.setAccessible(true);

                    hook(method)
                            .intercept(
                                    new TouchResponseHooker()
                            );

                    log(
                            Log.INFO,
                            TAG,
                            "isSupportTouchResponse hook INSTALLED: "
                                    + method.toString()
                    );
                }

                /*
                 * -----------------------------------------------------
                 * isSupportExtremeTouch(String)
                 * -----------------------------------------------------
                 */

                if ("isSupportExtremeTouch"
                        .equals(name)
                        && method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0]
                        == String.class) {

                    method.setAccessible(true);

                    hook(method)
                            .intercept(
                                    new GamePackageBooleanHooker(
                                            "isSupportExtremeTouch"
                                    )
                            );

                    log(
                            Log.INFO,
                            TAG,
                            "isSupportExtremeTouch hook INSTALLED: "
                                    + method.toString()
                    );
                }

                /*
                 * -----------------------------------------------------
                 * getTouchCustomizedCapability(String)
                 * -----------------------------------------------------
                 */

                if ("getTouchCustomizedCapability"
                        .equals(name)
                        && method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0]
                        == String.class) {

                    method.setAccessible(true);

                    hook(method)
                            .intercept(
                                    new GamePackageObjectHooker(
                                            "getTouchCustomizedCapability"
                                    )
                            );

                    log(
                            Log.INFO,
                            TAG,
                            "getTouchCustomizedCapability hook INSTALLED: "
                                    + method.toString()
                    );
                }

                /*
                 * -----------------------------------------------------
                 * setTouchSensibility(int)
                 * -----------------------------------------------------
                 */

                if ("setTouchSensibility"
                        .equals(name)
                        && method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0]
                        == int.class) {

                    method.setAccessible(true);

                    hook(method)
                            .intercept(
                                    new TouchIntSetterHooker(
                                            "setTouchSensibility"
                                    )
                            );

                    log(
                            Log.INFO,
                            TAG,
                            "setTouchSensibility hook INSTALLED: "
                                    + method.toString()
                    );
                }

                /*
                 * -----------------------------------------------------
                 * setTouchSmoothly(int)
                 * -----------------------------------------------------
                 */

                if ("setTouchSmoothly"
                        .equals(name)
                        && method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0]
                        == int.class) {

                    method.setAccessible(true);

                    hook(method)
                            .intercept(
                                    new TouchIntSetterHooker(
                                            "setTouchSmoothly"
                                    )
                            );

                    log(
                            Log.INFO,
                            TAG,
                            "setTouchSmoothly hook INSTALLED: "
                                    + method.toString()
                    );
                }
            }

            /*
             * Print useful COSA touch methods.
             */

            for (Method method : methods) {

                String text =
                        method.toString();

                if (text.contains("Touch")
                        || text.contains("touch")
                        || text.contains("Extreme")
                        || text.contains("extreme")
                        || text.contains("Support")
                        || text.contains("support")) {

                    log(
                            Log.INFO,
                            TAG,
                            "COSA touch method: "
                                    + text
                    );
                }
            }

            log(
                    Log.INFO,
                    TAG,
                    "COSA touch hooks INSTALLED"
            );

        } catch (Throwable t) {

            log(
                    Log.ERROR,
                    TAG,
                    "Failed to install COSA touch hooks: "
                            + t
            );

            Log.e(
                    TAG,
                    "COSA touch hook error",
                    t
            );
        }
    }

    /*
     * ================================================================
     * isSupportTouchResponse()
     * ================================================================
     */

    private final class TouchResponseHooker
            implements XposedInterface.Hooker {

        @Override
        public Object intercept(
                XposedInterface.Chain chain
        ) throws Throwable {

            Object result =
                    chain.proceed();

            log(
                    Log.INFO,
                    TAG,
                    "isSupportTouchResponse() "
                            + "ORIGINAL = "
                            + result
            );

            return result;
        }
    }

    /*
     * ================================================================
     * Game-specific boolean methods
     *
     * isSupportExtremeTouch(String)
     * ================================================================
     */

    private final class GamePackageBooleanHooker
            implements XposedInterface.Hooker {

        private final String methodName;

        GamePackageBooleanHooker(
                String methodName
        ) {

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

                Object result =
                        chain.proceed();

                log(
                        Log.INFO,
                        TAG,
                        methodName
                                + "("
                                + packageName
                                + ") ORIGINAL = "
                                + result
                );

                if (TARGET_GAME_PACKAGE
                        .equals(packageName)) {

                    log(
                            Log.INFO,
                            TAG,
                            methodName
                                    + " TARGET = "
                                    + TARGET_GAME_PACKAGE
                    );
                }

                return result;
            }

            return chain.proceed();
        }
    }

    /*
     * ================================================================
     * getTouchCustomizedCapability(String)
     * ================================================================
     */

    private final class GamePackageObjectHooker
            implements XposedInterface.Hooker {

        private final String methodName;

        GamePackageObjectHooker(
                String methodName
        ) {

            this.methodName =
                    methodName;
        }

        @Override
        public Object intercept(
                XposedInterface.Chain chain
        ) throws Throwable {

            Object arg0 =
                    chain.getArg(0);

            Object result =
                    chain.proceed();

            if (arg0 instanceof String) {

                String packageName =
                        (String) arg0;

                log(
                        Log.INFO,
                        TAG,
                        methodName
                                + "("
                                + packageName
                                + ") = "
                                + result
                );

                if (TARGET_GAME_PACKAGE
                        .equals(packageName)) {

                    log(
                            Log.INFO,
                            TAG,
                            methodName
                                    + " TARGET PACKAGE HIT"
                    );
                }
            }

            return result;
        }
    }

    /*
     * ================================================================
     * setTouchSensibility(int)
     * setTouchSmoothly(int)
     * ================================================================
     */

    private final class TouchIntSetterHooker
            implements XposedInterface.Hooker {

        private final String methodName;

        TouchIntSetterHooker(
                String methodName
        ) {

            this.methodName =
                    methodName;
        }

        @Override
        public Object intercept(
                XposedInterface.Chain chain
        ) throws Throwable {

            Object arg0 =
                    chain.getArg(0);

            log(
                    Log.INFO,
                    TAG,
                    methodName
                            + "("
                            + String.valueOf(arg0)
                            + ") CALLED"
            );

            Object result =
                    chain.proceed();

            log(
                    Log.INFO,
                    TAG,
                    methodName
                            + " RESULT = "
                            + result
            );

            return result;
        }
    }

    /*
     * ================================================================
     * Existing gyro hooks
     * ================================================================
     */

    private final class GyroGateHooker
            implements XposedInterface.Hooker {

        private final String methodName;

        GyroGateHooker(
                String methodName
        ) {

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

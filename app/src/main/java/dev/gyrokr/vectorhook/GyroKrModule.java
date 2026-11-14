package dev.gyrokr.vectorhook;

import android.util.Log;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

public class GyroKrModule extends XposedModule {

    private static final String TAG = "OplusGyroKR";
    private static final String GAME_SPACE_PACKAGE = "com.oplus.games";
    private static final String TARGET_GAME_PACKAGE = "com.pubg.krmobile";
    private static final String GYRO_CLASS =
            "business.module.gyroscope.GyroscopeConfigFeature";

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
            ClassLoader classLoader = param.getClassLoader();

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

            Class<?> gyroClass =
                    Class.forName(GYRO_CLASS, false, classLoader);

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

            Method[] methods = gyroClass.getDeclaredMethods();

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
                    .intercept(new GyroGateHooker("r"));

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
                    .intercept(new GyroGateHooker("s"));

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

        } catch (Throwable t) {

            log(
                    Log.ERROR,
                    TAG,
                    "Failed to install gyro hooks: " + t
            );

            Log.e(TAG, "Gyro hook error", t);
        }
    }

    private final class GyroGateHooker
            implements XposedInterface.Hooker {

        private final String methodName;

        GyroGateHooker(String methodName) {
            this.methodName = methodName;
        }

        @Override
        public Object intercept(
                XposedInterface.Chain chain
        ) throws Throwable {

            Object arg0 = chain.getArg(0);

            if (arg0 instanceof String) {

                String packageName = (String) arg0;

                if (TARGET_GAME_PACKAGE.equals(packageName)) {

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

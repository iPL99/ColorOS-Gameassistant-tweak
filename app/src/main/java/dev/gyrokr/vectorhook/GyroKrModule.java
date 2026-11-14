package dev.gyrokr.vectorhook;

import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class GyroKrModule extends XposedModule {

    private static final String TAG = "OplusGyroKR";

    private static final String GAME_SPACE_PACKAGE =
            "com.oplus.games";

    private static final String TARGET_GAME_PACKAGE =
            "com.pubg.krmobile";

    private static final String GYRO_CLASS =
            "business.module.gyroscope.GyroscopeConfigFeature";

    public GyroKrModule() {
        // 절대로 여기서 log() 호출하지 않음.
        // Vector framework가 아직 attach되지 않은 상태일 수 있음.
    }

    @Override
    public void onModuleLoaded(
            XposedModuleInterface.ModuleLoadedParam param) {

        try {
            log(TAG + " onModuleLoaded");
            log(TAG + " Process = " + param.getProcessName());
        } catch (Throwable e) {
            Log.e(TAG, "onModuleLoaded failed", e);
        }
    }

    @Override
    public void onPackageReady(
            XposedModuleInterface.PackageReadyParam param) {

        String packageName = null;

        try {
            packageName = param.getPackageName();

            log(TAG + " onPackageReady: " + packageName);

            if (!GAME_SPACE_PACKAGE.equals(packageName)) {
                return;
            }

            log(TAG + " GameSpace detected");

            ClassLoader classLoader = param.getClassLoader();

            log(TAG + " Package = " + packageName);
            log(TAG + " ClassLoader = " + classLoader);

            installGameSpaceGyroHook(classLoader);

        } catch (Throwable e) {
            log(TAG + " onPackageReady exception: " + e);
            Log.e(TAG, "onPackageReady failed", e);
        }
    }

    private void installGameSpaceGyroHook(
            ClassLoader classLoader) {

        try {
            /*
             * Current ColorOS 17 GameSpace:
             *
             * business.module.gyroscope.GyroscopeConfigFeature
             */

            Class<?> featureClass = Class.forName(
                    GYRO_CLASS,
                    false,
                    classLoader
            );

            log(TAG + " GyroscopeConfigFeature FOUND");
            log(TAG + " Class = " + featureClass.getName());

            Method[] methods = featureClass.getDeclaredMethods();

            log(TAG + " Method count = " + methods.length);

            Method targetMethod = null;

            for (Method method : methods) {

                log(
                        TAG
                                + " Method: "
                                + method.getName()
                                + " "
                                + method.toGenericString()
                );

                if ("s".equals(method.getName())
                        && method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0] == String.class
                        && method.getReturnType() == boolean.class) {

                    targetMethod = method;
                    break;
                }
            }

            if (targetMethod == null) {

                log(TAG + " Method s(String):boolean NOT FOUND");

                return;
            }

            log(TAG + " Found gate method:");
            log(TAG + " " + targetMethod.toGenericString());

            if (!Modifier.isStatic(targetMethod.getModifiers())) {
                log(TAG + " Method s() is NOT static");
            } else {
                log(TAG + " Method s() is static");
            }

            targetMethod.setAccessible(true);

            hook(
                    targetMethod
            ).intercept(
                    new GyroGateHooker()
            );

            log(TAG + " GameSpace gyro hook INSTALLED");

        } catch (ClassNotFoundException e) {

            log(
                    TAG
                            + " GyroscopeConfigFeature NOT FOUND: "
                            + GYRO_CLASS
            );

            Log.e(TAG, "GyroscopeConfigFeature not found", e);

        } catch (Throwable e) {

            log(TAG + " Failed to install GameSpace gyro hook");

            Log.e(
                    TAG,
                    "Failed to install GameSpace gyro hook",
                    e
            );
        }
    }

    private final class GyroGateHooker
            implements XposedInterface.Hooker {

        @Override
        public Object intercept(
                XposedInterface.Chain chain)
                throws Throwable {

            try {

                Object arg0 = chain.getArg(0);

                if (arg0 instanceof String) {

                    String packageName = (String) arg0;

                    if (TARGET_GAME_PACKAGE.equals(packageName)) {

                        log(
                                TAG
                                        + " Gyro gate intercepted: "
                                        + packageName
                        );

                        log(
                                TAG
                                        + " Returning TRUE for "
                                        + TARGET_GAME_PACKAGE
                        );

                        return Boolean.TRUE;
                    }
                }

            } catch (Throwable e) {

                log(
                        TAG
                                + " GyroGateHooker exception: "
                                + e
                );

                Log.e(
                        TAG,
                        "GyroGateHooker failed",
                        e
                );
            }

            return chain.proceed();
        }
    }
}

package dev.gyrokr.vectorhook;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class GyroKrModule extends XposedModule {

    private static final String TAG = "OplusGyroKR";

    private static final int LOG_INFO = 4;
    private static final int LOG_ERROR = 6;

    private static final String GAME_SPACE_PACKAGE =
            "com.oplus.games";

    private static final String TARGET_GAME_PACKAGE =
            "com.pubg.krmobile";

    private static final String GYRO_CLASS =
            "business.module.gyroscope.GyroscopeConfigFeature";

    public GyroKrModule() {
        // Constructor에서는 log() 호출 금지.
    }

    @Override
    public void onModuleLoaded(
            XposedModuleInterface.ModuleLoadedParam param) {

        try {
            log(
                    LOG_INFO,
                    TAG,
                    "onModuleLoaded"
            );

            log(
                    LOG_INFO,
                    TAG,
                    "Process = " + param.getProcessName()
            );

        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onPackageReady(
            XposedModuleInterface.PackageReadyParam param) {

        try {
            String packageName = param.getPackageName();

            log(
                    LOG_INFO,
                    TAG,
                    "onPackageReady: " + packageName
            );

            if (!GAME_SPACE_PACKAGE.equals(packageName)) {
                return;
            }

            log(
                    LOG_INFO,
                    TAG,
                    "GameSpace detected"
            );

            ClassLoader classLoader = param.getClassLoader();

            log(
                    LOG_INFO,
                    TAG,
                    "Package = " + packageName
            );

            log(
                    LOG_INFO,
                    TAG,
                    "ClassLoader = " + classLoader
            );

            installGameSpaceGyroHook(classLoader);

        } catch (Throwable e) {

            log(
                    LOG_ERROR,
                    TAG,
                    "onPackageReady exception",
                    e
            );
        }
    }

    private void installGameSpaceGyroHook(
            ClassLoader classLoader) {

        try {

            Class<?> featureClass = Class.forName(
                    GYRO_CLASS,
                    false,
                    classLoader
            );

            log(
                    LOG_INFO,
                    TAG,
                    "GyroscopeConfigFeature FOUND"
            );

            log(
                    LOG_INFO,
                    TAG,
                    "Class = " + featureClass.getName()
            );

            Method[] methods =
                    featureClass.getDeclaredMethods();

            log(
                    LOG_INFO,
                    TAG,
                    "Method count = " + methods.length
            );

            Method targetMethod = null;

            for (Method method : methods) {

                log(
                        LOG_INFO,
                        TAG,
                        "Method: "
                                + method.getName()
                                + " "
                                + method.toGenericString()
                );

                if ("s".equals(method.getName())
                        && method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0]
                                == String.class
                        && method.getReturnType()
                                == boolean.class) {

                    targetMethod = method;
                    break;
                }
            }

            if (targetMethod == null) {

                log(
                        LOG_ERROR,
                        TAG,
                        "Method s(String):boolean NOT FOUND"
                );

                return;
            }

            log(
                    LOG_INFO,
                    TAG,
                    "Found gate method: "
                            + targetMethod.toGenericString()
            );

            if (Modifier.isStatic(
                    targetMethod.getModifiers())) {

                log(
                        LOG_INFO,
                        TAG,
                        "Method s() is static"
                );

            } else {

                log(
                        LOG_INFO,
                        TAG,
                        "Method s() is NOT static"
                );
            }

            targetMethod.setAccessible(true);

            hook(targetMethod)
                    .intercept(new GyroGateHooker());

            log(
                    LOG_INFO,
                    TAG,
                    "GameSpace gyro hook INSTALLED"
            );

        } catch (ClassNotFoundException e) {

            log(
                    LOG_ERROR,
                    TAG,
                    "GyroscopeConfigFeature NOT FOUND: "
                            + GYRO_CLASS,
                    e
            );

        } catch (Throwable e) {

            log(
                    LOG_ERROR,
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

                    String packageName =
                            (String) arg0;

                    log(
                            LOG_INFO,
                            TAG,
                            "Gyro gate called: "
                                    + packageName
                    );

                    if (TARGET_GAME_PACKAGE
                            .equals(packageName)) {

                        log(
                                LOG_INFO,
                                TAG,
                                "Returning TRUE for "
                                        + TARGET_GAME_PACKAGE
                        );

                        return Boolean.TRUE;
                    }
                }

            } catch (Throwable e) {

                log(
                        LOG_ERROR,
                        TAG,
                        "GyroGateHooker exception",
                        e
                );
            }

            return chain.proceed();
        }
    }
}

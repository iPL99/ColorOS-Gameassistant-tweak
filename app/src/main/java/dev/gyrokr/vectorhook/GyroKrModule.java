package dev.gyrokr.vectorhook;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class GyroKrModule extends XposedModule {

    private static final String TAG = "OplusGyroKR";
    private static final String GAME_SPACE = "com.oplus.games";
    private static final String TARGET_GAME = "com.pubg.krmobile";

    private static final int LOG_INFO = 4;
    private static final int LOG_ERROR = 6;

    public GyroKrModule() {
        super();
    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(LOG_INFO, TAG, "onModuleLoaded");
        log(LOG_INFO, TAG, "Process = " + param.getProcessName());
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        String packageName = param.getPackageName();
        ClassLoader loader = param.getClassLoader();

        if (!GAME_SPACE.equals(packageName)) {
            return;
        }

        log(LOG_INFO, TAG, "GameSpace detected");
        installGyroscopeHooks(loader);
    }

    // ================================================================
    // Gyroscope support hooks only.
    // Everything unrelated to the gyroscope has been removed.
    // ================================================================

    private void installGyroscopeHooks(ClassLoader loader) {
        try {
            Class<?> clazz = loader.loadClass(
                    "business.module.gyroscope.GyroscopeConfigFeature"
            );

            log(LOG_INFO, TAG, "GyroscopeConfigFeature FOUND");

            int installed = 0;

            for (Method method : clazz.getDeclaredMethods()) {
                String name = method.getName();
                Class<?>[] params = method.getParameterTypes();

                // r(String): boolean
                if ("r".equals(name)
                        && method.getReturnType() == boolean.class
                        && params.length == 1
                        && params[0] == String.class) {

                    method.setAccessible(true);
                    hook(method).intercept(new GyroGateHooker("r"));

                    log(LOG_INFO, TAG,
                            "Gyroscope hook INSTALLED: " + method);
                    installed++;
                }

                // s(String): boolean
                else if ("s".equals(name)
                        && method.getReturnType() == boolean.class
                        && params.length == 1
                        && params[0] == String.class) {

                    method.setAccessible(true);
                    hook(method).intercept(new GyroGateHooker("s"));

                    log(LOG_INFO, TAG,
                            "Gyroscope hook INSTALLED: " + method);
                    installed++;
                }
            }

            log(LOG_INFO, TAG,
                    "Gyroscope hooks installed = " + installed);

        } catch (Throwable e) {
            log(LOG_ERROR, TAG,
                    "Gyroscope hook ERROR: " + e);
        }
    }

    private class GyroGateHooker implements XposedInterface.Hooker {

        private final String methodName;

        GyroGateHooker(String methodName) {
            this.methodName = methodName;
        }

        @Override
        public Object intercept(
                XposedInterface.Chain chain
        ) throws Throwable {

            Object arg = null;

            if (chain.getExecutable().getParameterCount() > 0) {
                arg = chain.getArg(0);
            }

            String packageName = String.valueOf(arg);

            if (TARGET_GAME.equals(packageName)) {
                log(LOG_INFO, TAG,
                        "GYRO TARGET HIT: " + methodName
                                + "(" + packageName + ") -> true");

                return Boolean.TRUE;
            }

            return chain.proceed();
        }
    }
}

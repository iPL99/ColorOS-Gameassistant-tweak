package dev.gyrokr.vectorhook;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class GyroKrModule extends XposedModule {

    private static final String TAG = "OplusGyroKR";

    private static final String GAME_SPACE = "com.oplus.games";
    private static final String TARGET_GAME = "com.pubg.krmobile";

    private static final int LOG_INFO = 4;
    private static final int LOG_ERROR = 6;

    public GyroKrModule(
            XposedInterface base,
            XposedModuleInterface.ModuleLoadedParam param
    ) {
        super(base, param);
    }

    @Override
    public void onModuleLoaded(
            XposedModuleInterface.ModuleLoadedParam param
    ) {
        log(LOG_INFO, TAG, "onModuleLoaded");
        log(LOG_INFO, TAG, "Process = " + param.getProcessName());
    }

    @Override
    public void onPackageReady(
            XposedModuleInterface.PackageReadyParam param
    ) {
        String packageName = param.getPackageName();

        log(LOG_INFO, TAG, "onPackageReady: " + packageName);

        if (!GAME_SPACE.equals(packageName)) {
            return;
        }

        log(LOG_INFO, TAG, "GameSpace detected");

        ClassLoader loader = param.getClassLoader();

        installGyroscopeHooks(loader);
        installTouchHooks(loader);
        installFeatureListHook(loader);
    }

    private void installGyroscopeHooks(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "business.module.gyroscope.GyroscopeConfigFeature",
                    false,
                    loader
            );

            log(LOG_INFO, TAG, "GyroscopeConfigFeature FOUND");

            int installed = 0;

            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.getName().equals("r")
                        && !method.getName().equals("s")) {
                    continue;
                }

                if (method.getParameterCount() != 1
                        || method.getParameterTypes()[0] != String.class
                        || method.getReturnType() != boolean.class) {
                    continue;
                }

                hook(method).intercept(new GyroGateHooker());
                installed++;

                log(
                        LOG_INFO,
                        TAG,
                        "Gyroscope hook INSTALLED: " + method
                );
            }

            log(
                    LOG_INFO,
                    TAG,
                    "Gyroscope hooks installed = " + installed
            );

        } catch (Throwable t) {
            log(LOG_ERROR, TAG, "Gyroscope hook error", t);
        }
    }

    private void installTouchHooks(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.oplus.cosa.COSASDKManager",
                    false,
                    loader
            );

            log(LOG_INFO, TAG, "COSASDKManager FOUND");

            int installed = 0;

            for (Method method : clazz.getDeclaredMethods()) {
                String name = method.getName();

                if (!name.toLowerCase().contains("touch")) {
                    continue;
                }

                int modifiers = method.getModifiers();

                if (method.isSynthetic()
                        || method.isBridge()
                        || Modifier.isAbstract(modifiers)
                        || Modifier.isNative(modifiers)) {
                    continue;
                }

                try {
                    hook(method).intercept(new TouchTraceHooker());

                    installed++;

                    log(
                            LOG_INFO,
                            TAG,
                            "TOUCH HOOK INSTALLED: " + method
                    );

                } catch (Throwable t) {
                    log(
                            LOG_ERROR,
                            TAG,
                            "Touch hook failed: " + method,
                            t
                    );
                }
            }

            log(
                    LOG_INFO,
                    TAG,
                    "ALL TOUCH HOOKS INSTALLED = " + installed
            );

        } catch (Throwable t) {
            log(LOG_ERROR, TAG, "Touch hook error", t);
        }
    }

    private void installFeatureListHook(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.oplus.cosa.COSASDKManager",
                    false,
                    loader
            );

            Method target = null;

            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.getName().equals("getSupportFeatureList")) {
                    continue;
                }

                if (method.getParameterCount() != 0) {
                    continue;
                }

                if (!java.util.List.class.isAssignableFrom(
                        method.getReturnType())) {
                    continue;
                }

                target = method;
                break;
            }

            if (target == null) {
                log(
                        LOG_ERROR,
                        TAG,
                        "getSupportFeatureList() NOT FOUND"
                );
                return;
            }

            hook(target).intercept(new FeatureListHooker());

            log(
                    LOG_INFO,
                    TAG,
                    "FEATURE LIST HOOK INSTALLED: " + target
            );

        } catch (Throwable t) {
            log(LOG_ERROR, TAG, "Feature list hook error", t);
        }
    }

    private class GyroGateHooker implements XposedInterface.Hooker {

        @Override
        public Object intercept(XposedInterface.Chain chain)
                throws Throwable {

            Object arg = chain.getArg(0);

            if (TARGET_GAME.equals(String.valueOf(arg))) {
                log(
                        LOG_INFO,
                        TAG,
                        "Gyroscope support forced TRUE for " + TARGET_GAME
                );

                return Boolean.TRUE;
            }

            return chain.proceed();
        }
    }

    private class TouchTraceHooker implements XposedInterface.Hooker {

        @Override
        public Object intercept(XposedInterface.Chain chain)
                throws Throwable {

            Method method = chain.getExecutable() instanceof Method
                    ? (Method) chain.getExecutable()
                    : null;

            String name = method != null
                    ? method.getName()
                    : "unknown";

            StringBuilder args = new StringBuilder();

            for (int i = 0; i < method.getParameterCount(); i++) {
                if (i > 0) {
                    args.append(", ");
                }

                args.append("arg")
                        .append(i)
                        .append("=")
                        .append(String.valueOf(chain.getArg(i)));
            }

            log(
                    LOG_INFO,
                    TAG,
                    "TOUCH CALL: " + name + "(" + args + ")"
            );

            Object result = chain.proceed();

            log(
                    LOG_INFO,
                    TAG,
                    "TOUCH RESULT: " + name + " = " + result
            );

            return result;
        }
    }

    private class FeatureListHooker implements XposedInterface.Hooker {

        @Override
        public Object intercept(XposedInterface.Chain chain)
                throws Throwable {

            log(
                    LOG_INFO,
                    TAG,
                    "FEATURE LIST CALL: getSupportFeatureList()"
            );

            Object result = chain.proceed();

            log(
                    LOG_INFO,
                    TAG,
                    "FEATURE LIST RESULT: " + String.valueOf(result)
            );

            if (result instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) result;

                log(
                        LOG_INFO,
                        TAG,
                        "MULTI TOUCH RATE SUPPORTED = "
                                + list.contains("command_multi_touch_rate")
                );
            }

            return result;
        }
    }
}

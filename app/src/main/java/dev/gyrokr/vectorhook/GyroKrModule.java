
package dev.gyrokr.vectorhook;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class GyroKrModule extends XposedModule {

    private static final String TAG = "OplusGyroKR";
    private static final String GAME_SPACE = "com.oplus.games";
    private static final String TARGET_GAME = "com.pubg.krmobile";

    private static final String PEER_GAME = "com.pubg.imobile";

    private static final String ADFR_VIEW_MODEL =
            "com.coloros.gamespaceui.module.adfr.GameAdfrViewModel";
    private static final String ADFR_ENTITY =
            "com.coloros.gamespaceui.module.adfr.db.GameAdfrEntity";

    private static final String FRAME_INSERT_FEATURE =
            "business.module.frameinsert.FrameInsertFeature";

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
        installHyperResponseHooks(loader);
        installHyperFrameRateHooks(loader);
    }

    // ================================================================
    // Gyroscope
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

                if (("r".equals(name) || "s".equals(name))
                        && method.getReturnType() == boolean.class
                        && params.length == 1
                        && params[0] == String.class) {

                    method.setAccessible(true);
                    hook(method).intercept(
                            new GyroGateHooker(name)
                    );

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
        public Object intercept(XposedInterface.Chain chain)
                throws Throwable {

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

    // ================================================================
    // Hyper Response Engine (ADFR)
    // ================================================================

    private void installHyperResponseHooks(ClassLoader loader) {
        try {
            Class<?> viewModel = loader.loadClass(ADFR_VIEW_MODEL);
            Class<?> entity = loader.loadClass(ADFR_ENTITY);

            Method getPkgName = entity.getDeclaredMethod("getPkgName");
            Method getState = entity.getDeclaredMethod("getState");
            Method getBrightness =
                    entity.getDeclaredMethod("getBrightnessThreshold");

            Constructor<?> ctor = entity.getDeclaredConstructor(
                    String.class, int.class, int.class);

            getPkgName.setAccessible(true);
            getState.setAccessible(true);
            getBrightness.setAccessible(true);
            ctor.setAccessible(true);

            log(LOG_INFO, TAG,
                    "GameAdfrViewModel / GameAdfrEntity FOUND");

            int installed = 0;

            for (Method method : viewModel.getDeclaredMethods()) {
                if ("p".equals(method.getName())
                        && method.getParameterCount() == 0
                        && List.class.isAssignableFrom(
                                method.getReturnType())) {

                    method.setAccessible(true);

                    hook(method).intercept(
                            new AdfrListHooker(
                                    getPkgName,
                                    getState,
                                    getBrightness,
                                    ctor
                            )
                    );

                    log(LOG_INFO, TAG,
                            "ADFR list hook INSTALLED: " + method);
                    installed++;
                }
            }

            if (installed == 0) {
                log(LOG_ERROR, TAG,
                        "ADFR list hook NOT installed: p() not found");
            }

        } catch (Throwable e) {
            log(LOG_ERROR, TAG,
                    "Hyper response hook ERROR: " + e);
        }
    }

    private class AdfrListHooker implements XposedInterface.Hooker {

        private final Method getPkgName;
        private final Method getState;
        private final Method getBrightness;
        private final Constructor<?> ctor;

        private boolean logged = false;

        AdfrListHooker(
                Method getPkgName,
                Method getState,
                Method getBrightness,
                Constructor<?> ctor) {

            this.getPkgName = getPkgName;
            this.getState = getState;
            this.getBrightness = getBrightness;
            this.ctor = ctor;
        }

        @Override
        public Object intercept(XposedInterface.Chain chain)
                throws Throwable {

            Object result = chain.proceed();

            try {
                if (!(result instanceof List)) {
                    return result;
                }

                List<?> source = (List<?>) result;
                Object peer = null;

                for (Object item : source) {
                    if (item == null) {
                        continue;
                    }

                    String pkg = (String) getPkgName.invoke(item);

                    if (TARGET_GAME.equals(pkg)) {
                        return result;
                    }

                    if (PEER_GAME.equals(pkg)) {
                        peer = item;
                    }
                }

                int state = 0;
                int brightness = -1;

                if (peer != null) {
                    state = (Integer) getState.invoke(peer);
                    brightness = (Integer) getBrightness.invoke(peer);
                }

                Object injected = ctor.newInstance(
                        TARGET_GAME, state, brightness);

                List<Object> merged =
                        new ArrayList<>(source.size() + 1);

                merged.addAll(source);
                merged.add(injected);

                if (!logged) {
                    logged = true;

                    log(LOG_INFO, TAG,
                            "ADFR list: injected " + TARGET_GAME
                                    + " (peer=" + (peer != null)
                                    + ", state=" + state
                                    + ", brightness=" + brightness
                                    + ", size=" + merged.size() + ")");
                }

                return merged;

            } catch (Throwable e) {
                log(LOG_ERROR, TAG,
                        "ADFR list inject ERROR: " + e);
                return result;
            }
        }
    }

    // ================================================================
    // Hyper Frame Rate
    // ================================================================

    private void installHyperFrameRateHooks(ClassLoader loader) {
        try {
            Class<?> clazz = loader.loadClass(FRAME_INSERT_FEATURE);

            log(LOG_INFO, TAG, "FrameInsertFeature FOUND");

            int installed = 0;

            for (Method method : clazz.getDeclaredMethods()) {

                if ("isFeatureEnabled".equals(method.getName())
                        && method.getReturnType() == boolean.class
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == String.class) {

                    method.setAccessible(true);

                    hook(method).intercept(
                            new HyperFrameRateHooker()
                    );

                    log(LOG_INFO, TAG,
                            "HYPER FRAME RATE hook INSTALLED: " + method);

                    installed++;
                }
            }

            if (installed == 0) {
                log(LOG_ERROR, TAG,
                        "HYPER FRAME RATE hook NOT INSTALLED: method not found");
            }

        } catch (Throwable e) {
            log(LOG_ERROR, TAG,
                    "HYPER FRAME RATE hook ERROR: " + e);
        }
    }

    private class HyperFrameRateHooker
            implements XposedInterface.Hooker {

        private boolean logged = false;

        @Override
        public Object intercept(XposedInterface.Chain chain)
                throws Throwable {

            Object arg = null;

            if (chain.getExecutable().getParameterCount() > 0) {
                arg = chain.getArg(0);
            }

            if (!logged) {
                logged = true;

                log(LOG_INFO, TAG,
                        "HYPER FRAME RATE HIT: isFeatureEnabled("
                                + arg + ") -> true");
            }

            return Boolean.TRUE;
        }
    }
}

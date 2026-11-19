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

    // Hyper Response Engine (ADFR) support list
    private static final String PEER_GAME = "com.pubg.imobile"; // BGMI: 엔티티 값 복제용
    private static final String ADFR_VIEW_MODEL =
            "com.coloros.gamespaceui.module.adfr.GameAdfrViewModel";
    private static final String ADFR_ENTITY =
            "com.coloros.gamespaceui.module.adfr.db.GameAdfrEntity";

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
    }

    // ================================================================
    // Gyroscope support hooks
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

    // ================================================================
    // Hyper Response Engine (ADFR support list)
    //
    // 하이퍼 리스폰스 섹션은 PerfTouchSettingFragment 에서
    //   touch.d.e() = GameAdfrFeature 지원 && GameAdfrViewModel.i(현재 게임) != null
    // 일 때만 만들어진다. i(pkg) 는 GameAdfrViewModel.p() 목록에서
    // pkgName 이 같은 GameAdfrEntity 를 찾으므로, p() 결과에 한국 PUBG
    // 엔티티를 추가한다.
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

            log(LOG_INFO, TAG, "GameAdfrViewModel / GameAdfrEntity FOUND");

            int installed = 0;

            for (Method method : viewModel.getDeclaredMethods()) {
                // p(): List<GameAdfrEntity>  (지원 ADFR 게임 목록)
                if ("p".equals(method.getName())
                        && method.getParameterCount() == 0
                        && List.class.isAssignableFrom(method.getReturnType())) {

                    method.setAccessible(true);
                    hook(method).intercept(new AdfrListHooker(
                            getPkgName, getState, getBrightness, ctor));

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

        AdfrListHooker(Method getPkgName, Method getState,
                       Method getBrightness, Constructor<?> ctor) {
            this.getPkgName = getPkgName;
            this.getState = getState;
            this.getBrightness = getBrightness;
            this.ctor = ctor;
        }

        @Override
        public Object intercept(
                XposedInterface.Chain chain
        ) throws Throwable {

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
                        // 이미 목록에 있으면 그대로 사용
                        return result;
                    }
                    if (PEER_GAME.equals(pkg)) {
                        peer = item;
                    }
                }

                // BGMI 엔티티가 있으면 값을 복제, 없으면 기본 JSON 값(0, -1) 사용
                int state = 0;
                int brightness = -1;
                if (peer != null) {
                    state = (Integer) getState.invoke(peer);
                    brightness = (Integer) getBrightness.invoke(peer);
                }

                Object injected = ctor.newInstance(
                        TARGET_GAME, state, brightness);

                List<Object> merged = new ArrayList<>(source.size() + 1);
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
                log(LOG_ERROR, TAG, "ADFR list inject ERROR: " + e);
                return result;
            }
        }
    }
}

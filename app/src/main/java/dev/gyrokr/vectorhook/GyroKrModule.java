package dev.gyrokr.vectorhook;

import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class GyroKrModule extends XposedModule {

    private static final String TAG = "OplusGyroKR";
    private static final String GAME_SPACE = "com.oplus.games";
    private static final String COSA = "com.oplus.cosa";
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

        log(LOG_INFO, TAG, "onPackageReady: " + packageName);

        if (GAME_SPACE.equals(packageName)) {
            log(LOG_INFO, TAG, "GameSpace detected");

            // 기존 자이로 지원 판정 우회
            installGyroscopeHooks(loader);

            // Hyper Response 엔진 관찰 전용
            installTouchHooks(loader);
            installFeatureListHook(loader);
            installGameSpaceUiTraceHooks(loader);

            // APK 내부 Hyper Response 실제 UI 판정 경로 추적
            installHyperResponseApkTraceHooks(loader);
            installPerfTouchKDumpHook(loader);
            installPerfTouchFragmentCallTrace(loader);
            installWihTraceHooks(loader);
            installPerfTouchModelTraceHook(loader);
            return;
        }

        if (COSA.equals(packageName)) {
            log(LOG_INFO, TAG, "COSA detected");

            // COSA setPerformanceValue(mode, resolution, touch) 호출 관찰
            installCosaPerformanceValueHook(loader);

            // Hyper Response 설정 흐름 관찰 전용
            installPerformanceSettingHooks(loader);
            installCosaNetworkParserHook(loader);
            installDBARouterServiceHook(loader);
            installGameConfigLookupHook(loader);

            // ExtremeTouch / Hyper Response 실제 진입점 관찰
            installExtremeTouchHooks(loader);

            // PUBG KR에서 ExtremeTouch 지원 판정만 테스트
            installExtremeTouchForceHook(loader);
        }
    }

    // ================================================================
    // Gyroscope hooks: 기존 동작 유지
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
    // GameSpace touch tracing only
    // ================================================================

    private void installTouchHooks(ClassLoader loader) {
        try {
            Class<?> clazz = loader.loadClass(
                    "com.oplus.cosa.COSASDKManager"
            );

            log(LOG_INFO, TAG, "COSASDKManager FOUND");

            int installed = 0;

            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.getName().toLowerCase().contains("touch")) {
                    continue;
                }

                if (Modifier.isStatic(method.getModifiers())) {
                    continue;
                }

                method.setAccessible(true);
                hook(method).intercept(new TouchTraceHooker());

                log(LOG_INFO, TAG,
                        "TOUCH TRACE HOOK INSTALLED: " + method);
                installed++;
            }

            log(LOG_INFO, TAG,
                    "TOUCH TRACE HOOKS INSTALLED = " + installed);

        } catch (Throwable e) {
            log(LOG_ERROR, TAG,
                    "Touch trace hook ERROR: " + e);
        }
    }

    private class TouchTraceHooker implements XposedInterface.Hooker {

        @Override
        public Object intercept(
                XposedInterface.Chain chain
        ) throws Throwable {

            Method method = (Method) chain.getExecutable();

            StringBuilder args = new StringBuilder();

            for (int i = 0;
                 i < method.getParameterCount();
                 i++) {

                if (i > 0) {
                    args.append(", ");
                }

                args.append("arg")
                        .append(i)
                        .append("=")
                        .append(String.valueOf(chain.getArg(i)));
            }

            log(LOG_INFO, TAG,
                    "TOUCH CALL: " + method.getName()
                            + "(" + args + ")");

            Object result = chain.proceed();

            log(LOG_INFO, TAG,
                    "TOUCH RESULT: " + method.getName()
                            + " = " + String.valueOf(result));

            return result;
        }
    }

    // ================================================================
    // GameSpace UI decision tracing
    // Read-only: traces likely UI support/availability methods without
    // forcing their return values.
    // ================================================================

    private void installGameSpaceUiTraceHooks(ClassLoader loader) {
        try {
            Class<?> clazz = loader.loadClass(
                    "com.oplus.cosa.COSASDKManager"
            );

            int installed = 0;
            for (Method method : clazz.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())) continue;
                if (method.getReturnType() != boolean.class
                        && method.getReturnType() != String.class) continue;

                String n = method.getName().toLowerCase();
                boolean relevant = n.contains("touch")
                        || n.contains("extreme")
                        || n.contains("response")
                        || n.contains("support")
                        || n.contains("feature")
                        || n.contains("enable")
                        || n.contains("available")
                        || n.contains("allow")
                        || n.contains("multi");
                if (!relevant) continue;

                boolean hasString = false;
                for (Class<?> p : method.getParameterTypes()) {
                    if (p == String.class) { hasString = true; break; }
                }
                if (!hasString && method.getParameterCount() > 0) continue;

                method.setAccessible(true);
                hook(method).intercept(new GameSpaceUiTraceHooker());
                log(LOG_INFO, TAG, "UI TRACE HOOK INSTALLED: " + method);
                installed++;
            }
            log(LOG_INFO, TAG, "UI TRACE HOOKS INSTALLED = " + installed);
        } catch (Throwable e) {
            log(LOG_ERROR, TAG, "GameSpace UI trace ERROR: " + e);
        }
    }

    private class GameSpaceUiTraceHooker implements XposedInterface.Hooker {
        @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
            Method m = (Method) chain.getExecutable();
            boolean target = false;
            StringBuilder a = new StringBuilder();
            for (int i=0;i<m.getParameterCount();i++) {
                Object v=chain.getArg(i);
                if (i>0) a.append(", ");
                a.append("arg").append(i).append("=").append(String.valueOf(v));
                if (TARGET_GAME.equals(v)) target=true;
            }
            Object result=chain.proceed();
            if (target || m.getParameterCount()==0) {
                log(LOG_INFO,TAG,"UI DECISION: " + m.getName() + "(" + a + ") -> " + String.valueOf(result));
            }
            return result;
        }
    }

    // ================================================================
    // GameSpace feature list tracing only
    // ================================================================

    private void installFeatureListHook(ClassLoader loader) {
        try {
            Class<?> clazz = loader.loadClass(
                    "com.oplus.cosa.COSASDKManager"
            );

            Method target = null;

            for (Method method : clazz.getDeclaredMethods()) {
                if ("getSupportFeatureList".equals(method.getName())
                        && method.getParameterCount() == 0) {

                    target = method;
                    break;
                }
            }

            if (target == null) {
                log(LOG_ERROR, TAG,
                        "getSupportFeatureList NOT FOUND");
                return;
            }

            target.setAccessible(true);

            hook(target).intercept(
                    new FeatureListHooker()
            );

            log(LOG_INFO, TAG,
                    "FEATURE LIST HOOK INSTALLED: " + target);

        } catch (Throwable e) {
            log(LOG_ERROR, TAG,
                    "Feature list hook ERROR: " + e);
        }
    }

    private class FeatureListHooker implements XposedInterface.Hooker {

        @Override
        public Object intercept(
                XposedInterface.Chain chain
        ) throws Throwable {

            log(LOG_INFO, TAG,
                    "FEATURE LIST CALL: getSupportFeatureList()");

            Object result = chain.proceed();

            log(LOG_INFO, TAG,
                    "FEATURE LIST RESULT: " + String.valueOf(result));

            if (result instanceof List) {
                List<?> list = (List<?>) result;

                log(LOG_INFO, TAG,
                        "MULTI TOUCH RATE SUPPORTED = "
                                + list.contains(
                                "command_multi_touch_rate"
                        ));
            }

            return result;
        }
    }

    // ================================================================
    // COSAExportedImpl.setPerformanceValue(III)Z tracing
    // ================================================================

    private void installCosaPerformanceValueHook(
            ClassLoader loader
    ) {
        try {
            Class<?> clazz = loader.loadClass(
                    "com.oplus.cosa.exported.COSAExportedImpl"
            );

            log(LOG_INFO, TAG,
                    "COSAExportedImpl FOUND");

            Method target = clazz.getDeclaredMethod(
                    "setPerformanceValue",
                    int.class,
                    int.class,
                    int.class
            );

            target.setAccessible(true);

            hook(target).intercept(
                    new CosaPerformanceValueHooker()
            );

            log(LOG_INFO, TAG,
                    "COSA setPerformanceValue HOOK INSTALLED: "
                            + target);

        } catch (Throwable e) {
            log(LOG_ERROR, TAG,
                    "COSA setPerformanceValue hook ERROR: " + e);
        }
    }

    private class CosaPerformanceValueHooker
            implements XposedInterface.Hooker {

        @Override
        public Object intercept(
                XposedInterface.Chain chain
        ) throws Throwable {

            Object mode = chain.getArg(0);
            Object resolution = chain.getArg(1);
            Object touch = chain.getArg(2);

            log(LOG_INFO, TAG,
                    "COSA setPerformanceValue CALL"
                            + " mode=" + mode
                            + " resolution=" + resolution
                            + " touch=" + touch);

            Object result = chain.proceed();

            log(LOG_INFO, TAG,
                    "COSA setPerformanceValue RESULT="
                            + result);

            return result;
        }
    }

    // ================================================================
    // PerformanceSetting tracing
    // ================================================================

    private void installPerformanceSettingHooks(
            ClassLoader loader
    ) {
        try {
            Class<?> clazz = loader.loadClass(
                    "com.oplus.feature.gameopt.actionfeatures.PerformanceSetting$a"
            );

            log(LOG_INFO, TAG,
                    "PerformanceSetting$a FOUND");

            int installed = 0;

            for (Method method : clazz.getDeclaredMethods()) {

                // a(PerformanceSetting$b, String): PerformanceSetting$b
                if ("a".equals(method.getName())
                        && method.getParameterCount() == 2
                        && method.getParameterTypes()[1] == String.class
                        && method.getReturnType().getName().equals(
                        "com.oplus.feature.gameopt.actionfeatures.PerformanceSetting$b"
                )) {

                    method.setAccessible(true);

                    hook(method).intercept(
                            new PerformanceSettingAHooker()
                    );

                    log(LOG_INFO, TAG,
                            "PERFORMANCE a() TRACE HOOK INSTALLED: "
                                    + method);

                    installed++;
                }

                // d(String): PerformanceSetting$b
                else if ("d".equals(method.getName())
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == String.class
                        && method.getReturnType().getName().equals(
                        "com.oplus.feature.gameopt.actionfeatures.PerformanceSetting$b"
                )) {

                    method.setAccessible(true);

                    hook(method).intercept(
                            new PerformanceSettingDHooker()
                    );

                    log(LOG_INFO, TAG,
                            "PERFORMANCE d() TRACE HOOK INSTALLED: "
                                    + method);

                    installed++;
                }
            }

            log(LOG_INFO, TAG,
                    "PerformanceSetting trace hooks installed = "
                            + installed);

        } catch (Throwable e) {
            log(LOG_ERROR, TAG,
                    "PerformanceSetting hook ERROR: " + e);
        }
    }

    private class PerformanceSettingAHooker
            implements XposedInterface.Hooker {

        @Override
        public Object intercept(
                XposedInterface.Chain chain
        ) throws Throwable {

            String input = null;

            if (chain.getExecutable().getParameterCount() >= 2) {
                input = String.valueOf(chain.getArg(1));
            }

            log(LOG_INFO, TAG,
                    "PERFORMANCE a() INPUT = " + input);

            Object result = chain.proceed();

            log(LOG_INFO, TAG,
                    "PERFORMANCE a() RESULT = "
                            + describePerformanceSetting(result));

            forceTouchRateEnabled(result);

            log(LOG_INFO, TAG,
                    "PERFORMANCE a() RESULT AFTER TOUCH FORCE = "
                            + describePerformanceSetting(result));

            return result;
        }
    }

    private class PerformanceSettingDHooker
            implements XposedInterface.Hooker {

        @Override
        public Object intercept(
                XposedInterface.Chain chain
        ) throws Throwable {

            String packageName =
                    String.valueOf(chain.getArg(0));

            Object result = chain.proceed();

            if (TARGET_GAME.equals(packageName)) {
                log(LOG_INFO, TAG,
                        "PERFORMANCE d() PACKAGE = "
                                + packageName);

                log(LOG_INFO, TAG,
                        "PERFORMANCE d() RESULT = "
                                + describePerformanceSetting(result));

                forceTouchRateEnabled(result);

                log(LOG_INFO, TAG,
                        "PERFORMANCE d() RESULT AFTER TOUCH FORCE = "
                                + describePerformanceSetting(result));
            }

            return result;
        }
    }

    // ================================================================
    // COSA network response and package config parser tracing
    // ================================================================

    private void installCosaNetworkParserHook(
            ClassLoader loader
    ) {
        try {
            Class<?> clazz = loader.loadClass("v8.e");

            log(LOG_INFO, TAG, "v8.e FOUND");

            Method responseTarget = null;

            for (Method method : clazz.getDeclaredMethods()) {
                if ("d".equals(method.getName())
                        && method.getParameterCount() == 2
                        && method.getParameterTypes()[0] == String.class
                        && method.getParameterTypes()[1] == String.class
                        && java.util.HashMap.class.isAssignableFrom(
                        method.getReturnType()
                )) {

                    responseTarget = method;
                    break;
                }
            }

            if (responseTarget == null) {
                log(LOG_ERROR, TAG,
                        "v8.e.d(String,String):HashMap NOT FOUND");
            } else {
                responseTarget.setAccessible(true);

                hook(responseTarget).intercept(
                        new CosaNetworkParserHooker()
                );

                log(LOG_INFO, TAG,
                        "COSA NETWORK PARSER TRACE HOOK INSTALLED: "
                                + responseTarget);
            }

            Method packageConfigTarget = null;

            for (Method method : clazz.getDeclaredMethods()) {
                if ("b".equals(method.getName())
                        && method.getParameterCount() == 2
                        && method.getParameterTypes()[0] == String.class
                        && method.getParameterTypes()[1] == boolean.class) {

                    packageConfigTarget = method;
                    break;
                }
            }

            if (packageConfigTarget == null) {
                log(LOG_ERROR, TAG,
                        "v8.e.b(String,boolean) NOT FOUND");
            } else {
                packageConfigTarget.setAccessible(true);

                hook(packageConfigTarget).intercept(
                        new CosaNetworkPackageHooker()
                );

                log(LOG_INFO, TAG,
                        "COSA PACKAGE CONFIG TRACE HOOK INSTALLED: "
                                + packageConfigTarget);
            }

        } catch (Throwable e) {
            log(LOG_ERROR, TAG,
                    "COSA network parser hook ERROR: " + e);
        }
    }

    private class CosaNetworkParserHooker
            implements XposedInterface.Hooker {

        @Override
        public Object intercept(
                XposedInterface.Chain chain
        ) throws Throwable {

            String response =
                    String.valueOf(chain.getArg(0));

            String source =
                    String.valueOf(chain.getArg(1));

            // 민감하거나 큰 응답 원문은 기록하지 않고
            // 대상 포함 여부만 기록
            boolean targetHit =
                    response.contains(TARGET_GAME);

            if (targetHit) {
                log(LOG_INFO, TAG,
                        "COSA RESPONSE CONTAINS PUBG CONFIG");

                log(LOG_INFO, TAG,
                        "COSA RESPONSE SOURCE = " + source);
            }

            Object result = chain.proceed();

            if (targetHit) {
                log(LOG_INFO, TAG,
                        "COSA PARSE RESULT CLASS = "
                                + (result == null
                                ? "null"
                                : result.getClass().getName()));

                log(LOG_INFO, TAG,
                        "COSA PARSE RESULT SIZE = "
                                + (result instanceof java.util.Map
                                ? ((java.util.Map<?, ?>) result).size()
                                : -1));
            }

            return result;
        }
    }

    private class CosaNetworkPackageHooker
            implements XposedInterface.Hooker {

        @Override
        public Object intercept(
                XposedInterface.Chain chain
        ) throws Throwable {

            String input =
                    String.valueOf(chain.getArg(0));

            boolean flag =
                    (Boolean) chain.getArg(1);

            boolean targetHit =
                    input.contains(TARGET_GAME);

            if (targetHit) {
                log(LOG_INFO, TAG,
                        "COSA PACKAGE CONFIG TARGET HIT");

                log(LOG_INFO, TAG,
                        "COSA PACKAGE CONFIG INPUT LENGTH = "
                                + input.length());

                log(LOG_INFO, TAG,
                        "COSA PACKAGE CONFIG FLAG = "
                                + flag);
            }

            Object result = chain.proceed();

            if (targetHit) {
                log(LOG_INFO, TAG,
                        "COSA PACKAGE CONFIG RESULT CLASS = "
                                + (result == null
                                ? "null"
                                : result.getClass().getName()));

                logPackageConfig(result);
            }

            return result;
        }
    }

    private void logPackageConfig(Object config) {
        if (config == null) {
            return;
        }

        try {
            String packageName =
                    (String) config.getClass()
                            .getMethod("D")
                            .invoke(config);

            String resv7 =
                    (String) config.getClass()
                            .getMethod("R")
                            .invoke(config);

            log(LOG_INFO, TAG,
                    "PARSED CONFIG PACKAGE = "
                            + packageName);

            log(LOG_INFO, TAG,
                    "PARSED CONFIG RESV7 LENGTH = "
                            + (resv7 == null
                            ? -1
                            : resv7.length()));

            log(LOG_INFO, TAG,
                    "PARSED CONFIG HAS LINGXI = "
                            + (resv7 != null
                            && resv7.contains("lingxi_boost")));

            log(LOG_INFO, TAG,
                    "PARSED CONFIG HAS TEMP TOUCH = "
                            + (resv7 != null
                            && resv7.contains(
                            "touchRateTemporaryChange"
                    )));

        } catch (Throwable e) {
            log(LOG_ERROR, TAG,
                    "PARSED CONFIG INSPECT ERROR: " + e);
        }
    }

    // ================================================================
    // DBARouterService.T(Lt9.s) tracing
    // ================================================================

    private void installDBARouterServiceHook(
            ClassLoader loader
    ) {
        try {
            Class<?> dbaClass = loader.loadClass(
                    "com.oplus.common.db.service.DBARouterService"
            );

            log(LOG_INFO, TAG,
                    "DBARouterService FOUND");

            Method target = null;

            for (Method method : dbaClass.getDeclaredMethods()) {
                if (!"T".equals(method.getName())) {
                    continue;
                }

                Class<?>[] params =
                        method.getParameterTypes();

                if (params.length == 1
                        && params[0].getName().equals("t9.s")) {

                    target = method;
                    break;
                }
            }

            if (target == null) {
                log(LOG_ERROR, TAG,
                        "DBARouterService.T(Lt9.s) NOT FOUND");
                return;
            }

            target.setAccessible(true);

            hook(target).intercept(
                    new DBARouterTHooker()
            );

            log(LOG_INFO, TAG,
                    "DBARouterService.T TRACE HOOK INSTALLED: "
                            + target);

        } catch (Throwable e) {
            log(LOG_ERROR, TAG,
                    "DBARouterService hook ERROR: " + e);
        }
    }

    private class DBARouterTHooker
            implements XposedInterface.Hooker {

        @Override
        public Object intercept(
                XposedInterface.Chain chain
        ) throws Throwable {

            Object config = chain.getArg(0);

            if (config != null) {
                logPackageConfig(config);
            }

            return chain.proceed();
        }
    }

    // ================================================================
    // Actual per-package config lookup tracing: u6.g.e(String)
    // ================================================================

    private void installGameConfigLookupHook(
            ClassLoader loader
    ) {
        try {
            Class<?> clazz = loader.loadClass("u6.g");

            Method target =
                    clazz.getDeclaredMethod(
                            "e",
                            String.class
                    );

            target.setAccessible(true);

            hook(target).intercept(
                    new GameConfigLookupHooker()
            );

            log(LOG_INFO, TAG,
                    "GAME CONFIG LOOKUP HOOK INSTALLED: "
                            + target);

        } catch (Throwable e) {
            log(LOG_ERROR, TAG,
                    "GAME CONFIG LOOKUP HOOK ERROR: " + e);
        }
    }

    private class GameConfigLookupHooker
            implements XposedInterface.Hooker {

        @Override
        public Object intercept(
                XposedInterface.Chain chain
        ) throws Throwable {

            String requestedPackage =
                    String.valueOf(chain.getArg(0));

            Object result = chain.proceed();

            if (!TARGET_GAME.equals(requestedPackage)) {
                return result;
            }

            if (result == null) {
                log(LOG_INFO, TAG,
                        "GAME CONFIG LOOKUP RESULT = null");
                return null;
            }

            log(LOG_INFO, TAG,
                    "GAME CONFIG REQUESTED = "
                            + requestedPackage);

            logPackageConfig(result);

            return result;
        }
    }

    // ================================================================
    // ExtremeTouch / Hyper Response tracing
    //
    // Observation only:
    // - Does NOT force a return value.
    // - Does NOT bypass game/anti-cheat logic.
    // - Logs gameStart/h methods and their arguments/results.
    // ================================================================

    private void installExtremeTouchHooks(
            ClassLoader loader
    ) {
        try {
            Class<?> clazz = loader.loadClass(
                    "com.oplus.feature.extremetouch.ExtremeTouchFeature"
            );

            log(LOG_INFO, TAG,
                    "ExtremeTouchFeature FOUND");

            int installed = 0;

            for (Method method : clazz.getDeclaredMethods()) {
                String name = method.getName();

                if (!"gameStart".equals(name)
                        && !"h".equals(name)) {
                    continue;
                }

                method.setAccessible(true);

                hook(method).intercept(
                        new ExtremeTouchHooker(name)
                );

                log(LOG_INFO, TAG,
                        "EXTREME TOUCH HOOK INSTALLED: "
                                + method);

                installed++;
            }

            log(LOG_INFO, TAG,
                    "EXTREME TOUCH HOOKS INSTALLED = "
                            + installed);

            if (installed == 0) {
                log(LOG_INFO, TAG,
                        "ExtremeTouchFeature loaded, "
                                + "but gameStart/h was not found");
            }

        } catch (Throwable e) {
            log(LOG_ERROR, TAG,
                    "ExtremeTouch hook ERROR: " + e);
        }
    }

    private class ExtremeTouchHooker
            implements XposedInterface.Hooker {

        private final String methodName;

        ExtremeTouchHooker(String methodName) {
            this.methodName = methodName;
        }

        @Override
        public Object intercept(
                XposedInterface.Chain chain
        ) throws Throwable {

            Method method =
                    (Method) chain.getExecutable();

            StringBuilder args =
                    new StringBuilder();

            boolean targetHit = false;
            String gameStartPackage = null;
            Boolean gameStartFlagC = null;

            for (int i = 0;
                 i < method.getParameterCount();
                 i++) {

                Object arg = chain.getArg(i);

                if (i > 0) {
                    args.append(", ");
                }

                args.append("arg")
                        .append(i)
                        .append("=")
                        .append(String.valueOf(arg));

                if (arg instanceof String
                        && TARGET_GAME.equals(arg)) {
                    targetHit = true;
                }

                if ("gameStart".equals(methodName)
                        && i == 0
                        && arg != null) {
                    gameStartPackage =
                            readFieldString(arg, "a");
                    gameStartFlagC =
                            readFieldBoolean(arg, "c");

                    if (TARGET_GAME.equals(gameStartPackage)) {
                        targetHit = true;
                    }

                    log(LOG_INFO, TAG,
                            "GAMESTART TARGET PACKAGE = "
                                    + gameStartPackage);

                    log(LOG_INFO, TAG,
                            "GAMESTART TARGET FLAG c = "
                                    + gameStartFlagC);
                }
            }

            log(LOG_INFO, TAG,
                    "EXTREME TOUCH CALL: "
                            + methodName
                            + "(" + args + ")"
                            + " targetHit=" + targetHit);

            if ("gameStart".equals(methodName)
                    && targetHit) {
                log(LOG_INFO, TAG,
                        "GAMESTART TARGET HIT: "
                                + TARGET_GAME
                                + " -> r6.q.a");
            }

            Object result = chain.proceed();

            log(LOG_INFO, TAG,
                    "EXTREME TOUCH RESULT: "
                            + methodName
                            + " = "
                            + String.valueOf(result));

            if ("gameStart".equals(methodName)) {
                dumpExtremeTouchArgument(chain.getArg(0));

                if (targetHit) {
                    log(LOG_INFO, TAG,
                            "GAMESTART TARGET PATH COMPLETED: "
                                    + TARGET_GAME);
                }
            }

            return result;
        }
    }

    private String readFieldString(Object object, String fieldName) {
        if (object == null) {
            return null;
        }

        try {
            Class<?> owner = object.getClass();

            while (owner != null && owner != Object.class) {
                try {
                    Field field = owner.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(object);
                    return value == null ? null : String.valueOf(value);
                } catch (NoSuchFieldException ignored) {
                    owner = owner.getSuperclass();
                }
            }
        } catch (Throwable e) {
            log(LOG_ERROR, TAG,
                    "GAMESTART FIELD READ ERROR: "
                            + fieldName + " = " + e);
        }

        return null;
    }

    private Boolean readFieldBoolean(Object object, String fieldName) {
        if (object == null) {
            return null;
        }

        try {
            Class<?> owner = object.getClass();

            while (owner != null && owner != Object.class) {
                try {
                    Field field = owner.getDeclaredField(fieldName);
                    field.setAccessible(true);

                    if (field.getType() != boolean.class
                            && field.getType() != Boolean.class) {
                        return null;
                    }

                    Object value = field.get(object);
                    return value == null ? null : (Boolean) value;
                } catch (NoSuchFieldException ignored) {
                    owner = owner.getSuperclass();
                }
            }
        } catch (Throwable e) {
            log(LOG_ERROR, TAG,
                    "GAMESTART FIELD READ ERROR: "
                            + fieldName + " = " + e);
        }

        return null;
    }

    // ================================================================
    // Read-only inspection of the object passed to gameStart().
    // ================================================================

    private void dumpExtremeTouchArgument(Object arg) {
        if (arg == null) {
            log(LOG_INFO, TAG, "EXTREME TOUCH ARG DUMP: null");
            return;
        }

        try {
            Class<?> c = arg.getClass();
            log(LOG_INFO, TAG,
                    "EXTREME TOUCH ARG CLASS = " + c.getName());

            Class<?> owner = c;
            int count = 0;

            while (owner != null && owner != Object.class && count < 80) {
                for (Field field : owner.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }

                    try {
                        field.setAccessible(true);
                        Object value = field.get(arg);
                        String text = String.valueOf(value);

                        if (text.length() > 300) {
                            text = text.substring(0, 300) + "...";
                        }

                        log(LOG_INFO, TAG,
                                "EXTREME TOUCH ARG FIELD: "
                                        + field.getName()
                                        + " = "
                                        + text);
                    } catch (Throwable ignored) {
                        log(LOG_INFO, TAG,
                                "EXTREME TOUCH ARG FIELD ERROR: "
                                        + field.getName());
                    }

                    count++;
                    if (count >= 80) {
                        break;
                    }
                }

                owner = owner.getSuperclass();
            }
        } catch (Throwable e) {
            log(LOG_ERROR, TAG,
                    "EXTREME TOUCH ARG DUMP ERROR: " + e);
        }
    }

    // ================================================================
    // ExtremeTouch h(String) force test
    //
    // Test only:
    // - Only com.pubg.krmobile is affected.
    // - Only h(String):boolean is forced to true.
    // - gameStart and all other methods remain untouched.
    // ================================================================

    private void installExtremeTouchForceHook(
            ClassLoader loader
    ) {
        try {
            Class<?> clazz = loader.loadClass(
                    "com.oplus.feature.extremetouch.ExtremeTouchFeature"
            );

            Method target = null;

            for (Method method : clazz.getDeclaredMethods()) {
                if ("h".equals(method.getName())
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == String.class
                        && method.getReturnType() == boolean.class) {

                    target = method;
                    break;
                }
            }

            if (target == null) {
                log(LOG_ERROR, TAG,
                        "EXTREME TOUCH FORCE: h(String):boolean NOT FOUND");
                return;
            }

            target.setAccessible(true);

            hook(target).intercept(
                    new ExtremeTouchForceHooker()
            );

            log(LOG_INFO, TAG,
                    "EXTREME TOUCH FORCE HOOK INSTALLED: " + target);

        } catch (Throwable e) {
            log(LOG_ERROR, TAG,
                    "ExtremeTouch force hook ERROR: " + e);
        }
    }

    private class ExtremeTouchForceHooker
            implements XposedInterface.Hooker {

        @Override
        public Object intercept(
                XposedInterface.Chain chain
        ) throws Throwable {

            Object arg = chain.getArg(0);
            String packageName = String.valueOf(arg);

            if (!TARGET_GAME.equals(packageName)) {
                return chain.proceed();
            }

            Object original = chain.proceed();

            log(LOG_INFO, TAG,
                    "EXTREME TOUCH FORCE BEFORE: h("
                            + packageName + ") original=" + original);

            log(LOG_INFO, TAG,
                    "EXTREME TOUCH FORCE AFTER: h("
                            + packageName + ") forced=true");

            return Boolean.TRUE;
        }
    }

    // ================================================================
    // games_10445.apk reverse-engineering trace
    // Read-only: does not force support results or alter UI state.
    // Confirmed APK path:
    // PerfModeFeature.D0() -> ICOSASDKManager.isSupportTouchResponse()
    // TouchRespondGunGodModeBiz.isSupport(String) -> PerfModeFeature.D0()
    // ================================================================

    private void installHyperResponseApkTraceHooks(ClassLoader loader) {
        traceApkMethods(
                loader,
                "com.coloros.gamespaceui.module.performancemode.PerfModeFeature",
                new String[]{"D0"},
                "HYPER SUPPORT GATE"
        );

        traceApkMethods(
                loader,
                "business.module.performance.settings.touch.TouchRespondGunGodModeBiz",
                new String[]{
                        "isSupport",
                        "defaultStateDuring",
                        "isExternalStateChangeAllowed",
                        "saveState",
                        "changeState"
                },
                "TOUCH RESPONSE BIZ"
        );

        // Obfuscated helper used by PerfTouchSettingFragment's visibility check.
        traceApkMethods(
                loader,
                "business.module.performance.settings.touch.d",
                new String[]{"c", "d", "e", "f", "g", "h", "i"},
                "TOUCH STATE HELPER"
        );

        // Parent page: E() starts checkRefreshRateAndTouchResponseVisibility.
        traceApkMethods(
                loader,
                "business.module.performance.settings.fragment.PerfTouchSettingFragment",
                new String[]{
                        "E",
                        "I",
                        "N",
                        "initRecyclerView",
                        "initObserver",
                        "K",
                        "onDestroy"
                },
                "TOUCH SETTINGS LIST"
        );

        // The RecyclerView row which leads into touch-response settings.
        traceApkMethods(
                loader,
                "business.module.performance.settings.touch.PerfTouchViewRespondVH",
                new String[]{"B", "C", "D", "E", "p", "q", "x", "y", "H", "r"},
                "TOUCH RESPONSE ROW"
        );

        // Final settings page lifecycle and state binding.
        traceApkMethods(
                loader,
                "business.module.performance.settings.fragment.TouchResponseUltimateSettingFragment",
                new String[]{
                        "onCreate",
                        "initView",
                        "onResume",
                        "bindSwitch",
                        "refreshSwitchState",
                        "observeTouchResponseChange",
                        "isPort",
                        "isPortPerfDesktopMode",
                        "getTitleText"
                },
                "TOUCH RESPONSE FRAGMENT"
        );
    }

    // ================================================================
    // Next-stage UI blocker trace:
    // PerfTouchSettingFragment.K(eya) is reached and D0() is TRUE,
    // but no Hyper Response row is created. Dump the K argument and
    // fragment fields before/after K so we can identify the false gate
    // or list/model object without changing behavior.
    // ================================================================

    private void installPerfTouchKDumpHook(ClassLoader loader) {
        try {
            Class<?> clazz = loader.loadClass(
                    "business.module.performance.settings.fragment.PerfTouchSettingFragment"
            );

            log(LOG_INFO, TAG, "TOUCH FRAGMENT METHOD INVENTORY BEGIN");
            for (Method declared : clazz.getDeclaredMethods()) {
                log(LOG_INFO, TAG, "TOUCH FRAGMENT METHOD DECL = " + declared
                        + " modifiers=" + Modifier.toString(declared.getModifiers()));
            }
            log(LOG_INFO, TAG, "TOUCH FRAGMENT METHOD INVENTORY END");

            int installed = 0;
            for (Method method : clazz.getDeclaredMethods()) {
                if (!"K".equals(method.getName())) continue;
                method.setAccessible(true);

                hook(method).intercept(new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        log(LOG_INFO, TAG, "K DUMP CALL: " + method);

                        Object thiz = chain.getThisObject();
                        Object arg0 = method.getParameterCount() > 0 ? chain.getArg(0) : null;

                        dumpObjectShallow("K ARG0", arg0, 2, 80);
                        dumpFieldsShallow("K THIS BEFORE", thiz, 1, 100);
                        // Capture the Lazy initializer before Kotlin clears it after evaluation.
                        inspectLazyField("K THIS BEFORE h", thiz, "h");

                        Object result = chain.proceed();

                        // h is a Kotlin Lazy; after K() it should hold the
                        // wih instance observed in previous diagnostics.
                        inspectLazyField("K THIS AFTER h", thiz, "h");
                        dumpFieldsShallow("K THIS AFTER", thiz, 2, 160);
                        if (result != null) {
                            dumpObjectShallow("K RESULT", result, 2, 120);
                        }
                        log(LOG_INFO, TAG, "K DUMP RESULT: " + String.valueOf(result));
                        return result;
                    }
                });

                log(LOG_INFO, TAG, "K DUMP HOOK INSTALLED: " + method);
                installed++;
            }

            log(LOG_INFO, TAG, "K DUMP HOOK COUNT = " + installed);
        } catch (Throwable e) {
            log(LOG_ERROR, TAG, "K DUMP HOOK ERROR: " + e);
        }
    }



    // ================================================================
    // Trace actual calls into PerfTouchSettingFragment.
    // K() already has a dedicated dump hook, so it is excluded here.
    // This is read-only: it never changes arguments, fields, or results.
    // ================================================================

    private void installPerfTouchFragmentCallTrace(ClassLoader loader) {
        try {
            Class<?> clazz = loader.loadClass(
                    "business.module.performance.settings.fragment.PerfTouchSettingFragment"
            );
            int installed = 0;
            for (Method method : clazz.getDeclaredMethods()) {
                if ("K".equals(method.getName())) continue;
                if (Modifier.isAbstract(method.getModifiers())
                        || Modifier.isNative(method.getModifiers())) continue;

                method.setAccessible(true);
                hook(method).intercept(new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        Object receiver = chain.getThisObject();
                        StringBuilder args = new StringBuilder();
                        for (int i = 0; i < method.getParameterCount(); i++) {
                            if (i > 0) args.append(", ");
                            Object arg = chain.getArg(i);
                            args.append("arg").append(i).append("=")
                                    .append(safeToString(arg));
                        }

                        String name = method.getName();
                        log(LOG_INFO, TAG, "TOUCH FRAGMENT TRACE CALL: " + method
                                + " receiver=" + safeToString(receiver)
                                + " args=[" + args + "]");

                        Object fragment = receiver != null
                                && clazz.isInstance(receiver) ? receiver : null;
                        if (fragment == null) {
                            for (int i = 0; i < method.getParameterCount(); i++) {
                                Object arg = chain.getArg(i);
                                if (arg != null && clazz.isInstance(arg)) {
                                    fragment = arg;
                                    break;
                                }
                            }
                        }

                        boolean dumpFragment = fragment != null &&
                                ("A".equals(name) || "B".equals(name) || "C".equals(name)
                                || "D".equals(name) || "E".equals(name) || "F".equals(name)
                                || "I".equals(name) || "L".equals(name) || "M".equals(name)
                                || "N".equals(name) || "O".equals(name) || "P".equals(name)
                                || "q".equals(name) || "r".equals(name)
                                || "s".equals(name) || "x".equals(name)
                                || "z".equals(name));
                        if (dumpFragment) {
                            dumpFieldsShallow("TOUCH FRAGMENT BEFORE " + name,
                                    fragment, 1, 100);
                            dumpNamedField("TOUCH FRAGMENT ADAPTER j BEFORE " + name,
                                    fragment, "j");
                            dumpNamedField("TOUCH FRAGMENT LIST o BEFORE " + name,
                                    fragment, "o");
                        }

                        Object result;
                        try {
                            result = chain.proceed();
                        } catch (Throwable t) {
                            log(LOG_ERROR, TAG, "TOUCH FRAGMENT TRACE THROW: " + method
                                    + " -> " + t.getClass().getName() + ": " + t.getMessage());
                            log(LOG_ERROR, TAG, "TOUCH FRAGMENT TRACE STACK: "
                                    + android.util.Log.getStackTraceString(t));
                            throw t;
                        }

                        log(LOG_INFO, TAG, "TOUCH FRAGMENT TRACE RESULT: " + method
                                + " -> " + safeToString(result));
                        if (dumpFragment) {
                            dumpFieldsShallow("TOUCH FRAGMENT AFTER " + name,
                                    fragment, 1, 100);
                            dumpNamedField("TOUCH FRAGMENT ADAPTER j AFTER " + name,
                                    fragment, "j");
                            dumpNamedField("TOUCH FRAGMENT LIST o AFTER " + name,
                                    fragment, "o");
                        }
                        if (result instanceof List) {
                            List<?> list = (List<?>) result;
                            log(LOG_INFO, TAG, "TOUCH FRAGMENT TRACE LIST " + name
                                    + " size=" + list.size() + " value=" + safeToString(list));
                            int limit = Math.min(list.size(), 12);
                            for (int i = 0; i < limit; i++) {
                                dumpObjectShallow("TOUCH FRAGMENT TRACE LIST " + name
                                        + " item[" + i + "]", list.get(i), 2, 60);
                            }
                        }
                        return result;
                    }
                });
                installed++;
                log(LOG_INFO, TAG, "TOUCH FRAGMENT TRACE HOOK INSTALLED: " + method);
            }
            log(LOG_INFO, TAG, "TOUCH FRAGMENT TRACE HOOK COUNT = " + installed);
        } catch (Throwable e) {
            log(LOG_ERROR, TAG, "TOUCH FRAGMENT TRACE HOOK ERROR: " + e);
        }
    }

    // ================================================================
    // wih provider inspection and read-only method tracing.
    // The fragment's Kotlin Lazy field "h" resolves to wih after K().
    // ================================================================

    private void installWihTraceHooks(ClassLoader loader) {
        try {
            Class<?> clazz = loader.loadClass("wih");
            log(LOG_INFO, TAG, "WIH CLASS FOUND = " + clazz.getName());
            log(LOG_INFO, TAG, "WIH SUPER = " + clazz.getSuperclass());

            int installed = 0;
            for (Method method : clazz.getDeclaredMethods()) {
                if (Modifier.isAbstract(method.getModifiers())
                        || Modifier.isNative(method.getModifiers())) {
                    continue;
                }
                if (method.getDeclaringClass() == Object.class) continue;

                method.setAccessible(true);
                hook(method).intercept(new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        Method called = (Method) chain.getExecutable();
                        StringBuilder args = new StringBuilder();
                        for (int i = 0; i < called.getParameterCount(); i++) {
                            if (i > 0) args.append(", ");
                            args.append("arg").append(i).append("=")
                                    .append(safeToString(chain.getArg(i)));
                        }

                        log(LOG_INFO, TAG, "WIH CALL: " + called
                                + " args=[" + args + "] this="
                                + safeToString(chain.getThisObject()));

                        Object result = chain.proceed();

                        log(LOG_INFO, TAG, "WIH RESULT: " + called.getName()
                                + " -> " + safeToString(result));

                        if (result instanceof List) {
                            List<?> list = (List<?>) result;
                            log(LOG_INFO, TAG, "WIH LIST RESULT SIZE="
                                    + list.size() + " value=" + safeToString(list));
                            for (int i = 0; i < Math.min(list.size(), 20); i++) {
                                Object item = list.get(i);
                                log(LOG_INFO, TAG, "WIH LIST ITEM[" + i + "] CLASS="
                                        + (item == null ? "null" : item.getClass().getName())
                                        + " VALUE=" + safeToString(item));
                                if (item != null) {
                                    dumpObjectShallow("WIH LIST ITEM[" + i + "]",
                                            item, 2, 40);
                                }
                            }
                        } else if (result != null
                                && !result.getClass().getName().startsWith("java.")
                                && !result.getClass().getName().startsWith("android.")) {
                            dumpObjectShallow("WIH RESULT OBJECT", result, 2, 60);
                        }

                        return result;
                    }
                });

                log(LOG_INFO, TAG, "WIH METHOD HOOK INSTALLED = " + method);
                installed++;
            }

            log(LOG_INFO, TAG, "WIH METHOD HOOK COUNT = " + installed);

            for (Field field : clazz.getDeclaredFields()) {
                log(LOG_INFO, TAG, "WIH FIELD DECL = " + field);
                if (Modifier.isStatic(field.getModifiers())) {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(null);
                        log(LOG_INFO, TAG, "WIH STATIC FIELD VALUE " + field.getName()
                                + " CLASS=" + (value == null ? "null" : value.getClass().getName())
                                + " VALUE=" + safeToString(value));
                        if (value != null) {
                            dumpObjectShallow("WIH STATIC " + field.getName(), value, 2, 80);
                        }
                    } catch (Throwable e) {
                        log(LOG_INFO, TAG, "WIH STATIC FIELD READ ERROR " + field.getName()
                                + " = " + e);
                    }
                }
            }
        } catch (Throwable e) {
            log(LOG_ERROR, TAG, "WIH TRACE INSTALL ERROR = " + e);
        }
    }

    private void inspectLazyField(String label, Object owner, String fieldName) {
        if (owner == null) {
            log(LOG_INFO, TAG, label + " OWNER=NULL");
            return;
        }

        try {
            Field target = null;
            Class<?> c = owner.getClass();
            while (c != null) {
                try {
                    target = c.getDeclaredField(fieldName);
                    break;
                } catch (NoSuchFieldException ignored) {
                    c = c.getSuperclass();
                }
            }

            if (target == null) {
                log(LOG_INFO, TAG, label + " FIELD NOT FOUND");
                return;
            }

            target.setAccessible(true);
            Object lazy = target.get(owner);
            log(LOG_INFO, TAG, label + " LAZY CLASS="
                    + (lazy == null ? "null" : lazy.getClass().getName()));
            dumpObjectShallow(label + " LAZY", lazy, 2, 80);

            if (lazy == null) return;

            Class<?> lazyClass = lazy.getClass();
            while (lazyClass != null && lazyClass != Object.class) {
                for (Field f : lazyClass.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    try {
                        f.setAccessible(true);
                        Object value = f.get(lazy);
                        log(LOG_INFO, TAG, label + " LAZY FIELD "
                                + lazyClass.getSimpleName() + "." + f.getName()
                                + " [" + f.getType().getName() + "] = "
                                + safeToString(value));

                        if (value != null && "wih".equals(value.getClass().getName())) {
                            dumpObjectShallow(label + " WIH VALUE", value, 3, 100);
                            Class<?> wc = value.getClass();
                            for (Method m : wc.getDeclaredMethods()) {
                                log(LOG_INFO, TAG, label + " WIH METHOD DECL = " + m);
                            }
                        }
                    } catch (Throwable e) {
                        log(LOG_INFO, TAG, label + " LAZY FIELD READ ERROR "
                                + f.getName() + " = " + e);
                    }
                }
                lazyClass = lazyClass.getSuperclass();
            }
        } catch (Throwable e) {
            log(LOG_ERROR, TAG, label + " INSPECT ERROR = " + e);
        }
    }

    // Trace methods on the eya list-builder and its registered touch row/delegate types.
    // Observation only: no arguments, return values, or fields are modified.
    private void installPerfTouchModelTraceHook(ClassLoader loader) {
        // eya constructor trace: identify who creates each adapter instance.
        installEyaConstructorTraceHooks(loader);

        String[] names = new String[] {
                "eya", "mgc", "sgc", "vfc", "bgc", "zfc", "wfc",
                "business.module.performance.settings.touch.e",
                "business.module.performance.settings.touch.g"
        };
        int total = 0;
        for (String name : names) {
            try {
                Class<?> clazz = loader.loadClass(name);
                log(LOG_INFO, TAG, "MODEL TRACE CLASS FOUND: " + name);
                if ("mgc".equals(name)) {
                    Class<?> parent = clazz;
                    while (parent != null) {
                        log(LOG_INFO, TAG, "MG C CLASS HIERARCHY: " + parent.getName());
                        for (Field field : parent.getDeclaredFields()) {
                            log(LOG_INFO, TAG, "MG C FIELD DECL: " + field);
                        }
                        for (Method declared : parent.getDeclaredMethods()) {
                            log(LOG_INFO, TAG, "MG C METHOD DECL: " + declared);
                        }
                        parent = parent.getSuperclass();
                    }
                }
                for (Method method : clazz.getDeclaredMethods()) {
                    if (Modifier.isAbstract(method.getModifiers())
                            || Modifier.isNative(method.getModifiers())) continue;
                    // Keep the trace focused on list/model-building methods.
                    String mn = method.getName().toLowerCase();
                    if (!(name.equals("eya") || name.equals("mgc") || mn.contains("touch")
                            || mn.contains("response") || mn.contains("perf")
                            || mn.contains("type") || mn.contains("item")
                            || mn.contains("list") || mn.equals("a")
                            || mn.equals("b") || mn.equals("c"))) continue;
                    method.setAccessible(true);
                    hook(method).intercept(new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(XposedInterface.Chain chain) throws Throwable {
                            Object thiz = chain.getThisObject();
                            StringBuilder args = new StringBuilder();
                            for (int i = 0; i < method.getParameterCount(); i++) {
                                if (i > 0) args.append(", ");
                                args.append(safeToString(chain.getArg(i)));
                            }
                            log(LOG_INFO, TAG, "MODEL TRACE CALL: " + method
                                    + " args=[" + args + "] this=" + safeToString(thiz));

                            // Capture every incoming list/model, not only mgc.
                            // eya.i(List) is the adapter's list replacement path.
                            for (int i = 0; i < method.getParameterCount(); i++) {
                                Object arg = chain.getArg(i);
                                if (arg instanceof List) {
                                    dumpModelList("MODEL TRACE " + clazz.getName()
                                            + "." + method.getName() + " ARG" + i, (List<?>) arg);
                                } else if (arg != null && isTouchModelClass(arg)) {
                                    dumpModelObject("MODEL TRACE ARG" + i, arg);
                                }
                            }
                            boolean eyaListSetter = "eya".equals(clazz.getName())
                                    && "i".equals(method.getName())
                                    && method.getParameterCount() == 1
                                    && chain.getArg(0) instanceof List;
                            if (eyaListSetter) {
                                log(LOG_INFO, TAG, "EYA LIST SETTER STACK BEFORE: "
                                        + android.util.Log.getStackTraceString(new Throwable()));
                                dumpNamedField("EYA i(List) FIELD j BEFORE", thiz, "j");
                                dumpModelList("EYA i(List) INPUT", (List<?>) chain.getArg(0));
                            }
                            if ("eya".equals(clazz.getName()) && thiz != null) {
                                dumpNamedField("MODEL TRACE eya.j BEFORE", thiz, "j");
                            }
                            if ("mgc".equals(clazz.getName())) {
                                dumpObjectShallow("MG C BEFORE THIS", thiz, 3, 100);
                                for (int i = 0; i < method.getParameterCount(); i++) {
                                    Object arg = chain.getArg(i);
                                    if (arg != null && "mgc".equals(arg.getClass().getName())) {
                                        dumpObjectShallow("MG C BEFORE ARG" + i, arg, 3, 100);
                                    }
                                }
                            }
                            Object result = chain.proceed();
                            log(LOG_INFO, TAG, "MODEL TRACE RESULT: " + method
                                    + " -> " + safeToString(result));
                            if ("mgc".equals(clazz.getName())) {
                                dumpObjectShallow("MG C AFTER THIS", thiz, 3, 100);
                                if (result != null && "mgc".equals(result.getClass().getName())) {
                                    dumpObjectShallow("MG C RESULT OBJECT", result, 3, 100);
                                }
                            }
                            if ("eya".equals(clazz.getName()) && thiz != null) {
                                dumpNamedField("MODEL TRACE eya.j AFTER", thiz, "j");
                            }
                            if (eyaListSetter) {
                                dumpNamedField("EYA i(List) FIELD j AFTER", thiz, "j");
                                dumpFieldsShallow("EYA i(List) THIS AFTER", thiz, 1, 100);
                            }
                            if (result instanceof List) {
                                dumpModelList("MODEL TRACE " + clazz.getName()
                                        + "." + method.getName() + " RESULT", (List<?>) result);
                            } else if (result != null && isTouchModelClass(result)) {
                                dumpModelObject("MODEL TRACE RESULT OBJECT", result);
                            }
                            return result;
                        }
                    });
                    log(LOG_INFO, TAG, "MODEL TRACE HOOK INSTALLED: " + method);
                    total++;
                }
            } catch (Throwable e) {
                log(LOG_INFO, TAG, "MODEL TRACE CLASS MISS: " + name + " : " + e);
            }
        }
        log(LOG_INFO, TAG, "MODEL TRACE HOOK COUNT = " + total);
    }

    // Trace every eya constructor to connect adapter identity to its creation path.
    private void installEyaConstructorTraceHooks(ClassLoader loader) {
        try {
            Class<?> clazz = loader.loadClass("eya");
            int installed = 0;
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                hook(constructor).intercept(new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        StringBuilder args = new StringBuilder();
                        for (int i = 0; i < constructor.getParameterCount(); i++) {
                            if (i > 0) args.append(", ");
                            args.append("arg").append(i).append("=")
                                    .append(safeToString(chain.getArg(i)));
                        }
                        log(LOG_INFO, TAG, "EYA CONSTRUCTOR CALL: " + constructor
                                + " args=[" + args + "]");
                        log(LOG_INFO, TAG, "EYA CONSTRUCTOR STACK: "
                                + android.util.Log.getStackTraceString(new Throwable()));

                        Object result = chain.proceed();
                        Object instance = chain.getThisObject();
                        log(LOG_INFO, TAG, "EYA CONSTRUCTOR COMPLETE: " + constructor
                                + " this=" + safeToString(instance));
                        if (instance != null) {
                            dumpFieldsShallow("EYA CONSTRUCTOR INSTANCE", instance, 2, 120);
                            dumpNamedField("EYA CONSTRUCTOR FIELD j", instance, "j");
                        }
                        return result;
                    }
                });
                log(LOG_INFO, TAG, "EYA CONSTRUCTOR HOOK INSTALLED: " + constructor);
                installed++;
            }
            log(LOG_INFO, TAG, "EYA CONSTRUCTOR HOOK COUNT = " + installed);
        } catch (Throwable e) {
            log(LOG_ERROR, TAG, "EYA CONSTRUCTOR TRACE ERROR: " + e);
        }
    }

    private void dumpNamedField(String label, Object owner, String fieldName) {
        if (owner == null) return;
        Class<?> c = owner.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                Object value = f.get(owner);
                log(LOG_INFO, TAG, label + " = " + safeToString(value));
                if (value instanceof List) {
                    List<?> list = (List<?>) value;
                    log(LOG_INFO, TAG, label + " SIZE = " + list.size());
                    for (int i = 0; i < Math.min(list.size(), 20); i++) {
                        Object item = list.get(i);
                        log(LOG_INFO, TAG, label + "[" + i + "] CLASS="
                                + (item == null ? "null" : item.getClass().getName())
                                + " VALUE=" + safeToString(item));
                        if (item != null) {
                            dumpModelObject(label + "[" + i + "] DETAILS", item);
                        }
                    }
                }
                return;
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            } catch (Throwable e) {
                log(LOG_INFO, TAG, label + " READ ERROR: " + e);
                return;
            }
        }
        log(LOG_INFO, TAG, label + " FIELD NOT FOUND");
    }

    private boolean isTouchModelClass(Object value) {
        if (value == null) return false;
        String name = value.getClass().getName();
        return "mgc".equals(name) || "sgc".equals(name) || "bgc".equals(name)
                || "fgc".equals(name) || "vfc".equals(name) || "zfc".equals(name)
                || "wfc".equals(name)
                || name.startsWith("business.module.performance.settings.touch.");
    }

    private void dumpModelList(String label, List<?> list) {
        if (list == null) {
            log(LOG_INFO, TAG, label + " = null");
            return;
        }
        log(LOG_INFO, TAG, label + " SIZE=" + list.size()
                + " CLASS=" + list.getClass().getName());
        int limit = Math.min(list.size(), 30);
        for (int i = 0; i < limit; i++) {
            Object item = list.get(i);
            log(LOG_INFO, TAG, label + "[" + i + "] CLASS="
                    + (item == null ? "null" : item.getClass().getName())
                    + " VALUE=" + safeToString(item));
            if (item != null) dumpModelObject(label + "[" + i + "]", item);
        }
    }

    private void dumpModelObject(String label, Object item) {
        if (item == null) {
            log(LOG_INFO, TAG, label + " = null");
            return;
        }
        dumpObjectShallow(label, item, 2, 80);
    }

    private void dumpObjectShallow(String label, Object obj, int depth, int maxFields) {
        if (obj == null) {
            log(LOG_INFO, TAG, label + " = null");
            return;
        }

        log(LOG_INFO, TAG, label + " CLASS = " + obj.getClass().getName());
        log(LOG_INFO, TAG, label + " STR = " + safeToString(obj));

        if (depth > 0) {
            dumpFieldsShallow(label, obj, depth, maxFields);
        }
    }

    private void dumpFieldsShallow(String label, Object obj, int depth, int maxFields) {
        if (obj == null) return;

        Class<?> owner = obj.getClass();
        int count = 0;

        while (owner != null && count < maxFields) {
            Field[] fields;
            try {
                fields = owner.getDeclaredFields();
            } catch (Throwable e) {
                break;
            }

            for (Field field : fields) {
                if (count++ >= maxFields) break;
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (field.isSynthetic()) continue;

                try {
                    field.setAccessible(true);
                    Object value = field.get(obj);
                    String valueText = safeToString(value);
                    log(LOG_INFO, TAG,
                            label + " FIELD " + owner.getSimpleName() + "."
                                    + field.getName() + " ["
                                    + field.getType().getName() + "] = " + valueText);

                    // Only recurse into small custom objects. Avoid Android UI,
                    // collections and strings because those can explode the log.
                    if (depth > 1 && value != null
                            && !field.getType().isPrimitive()
                            && !value.getClass().isArray()
                            && !(value instanceof String)
                            && !(value instanceof Number)
                            && !(value instanceof Boolean)
                            && !(value instanceof Enum)
                            && !(value instanceof List)
                            && !value.getClass().getName().startsWith("android.")
                            && !value.getClass().getName().startsWith("java.")) {
                        dumpFieldsShallow(
                                label + " -> " + field.getName(),
                                value,
                                depth - 1,
                                Math.min(30, maxFields - count + 1)
                        );
                    }
                } catch (Throwable ignored) {
                    log(LOG_INFO, TAG,
                            label + " FIELD " + owner.getSimpleName() + "."
                                    + field.getName() + " = <read error>");
                }
            }

            owner = owner.getSuperclass();
        }
    }

    private String safeToString(Object value) {
        if (value == null) return "null";
        try {
            String s = String.valueOf(value);
            if (s.length() > 500) {
                return s.substring(0, 500) + "...";
            }
            return s;
        } catch (Throwable e) {
            return "<toString error>";
        }
    }


    // Read-only inspection of RecyclerView ViewHolder.itemView and its descendants.
    // Reflection is used so the hook code does not depend on app-private view classes.
    private void dumpViewHolderState(String label, Object holder) {
        if (holder == null) {
            log(LOG_INFO, TAG, label + " HOLDER=null");
            return;
        }
        log(LOG_INFO, TAG, label + " HOLDER=" + holder.getClass().getName()
                + " value=" + safeToString(holder));
        Object itemView = null;
        Class<?> c = holder.getClass();
        while (c != null && itemView == null) {
            try {
                Field f = c.getDeclaredField("itemView");
                f.setAccessible(true);
                itemView = f.get(holder);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            } catch (Throwable e) {
                log(LOG_INFO, TAG, label + " ITEMVIEW READ ERROR=" + e);
                break;
            }
        }
        if (itemView == null) {
            log(LOG_INFO, TAG, label + " ITEMVIEW=null/not-found");
            dumpObjectShallow(label + " HOLDER FIELDS", holder, 2, 100);
            return;
        }
        dumpViewTree(label + " ITEMVIEW", itemView, 0, 5);
    }

    private void dumpViewTree(String label, Object view, int depth, int maxDepth) {
        if (view == null) return;
        try {
            Class<?> cls = view.getClass();
            StringBuilder line = new StringBuilder(label)
                    .append(" depth=").append(depth)
                    .append(" class=").append(cls.getName());
            String[] methods = new String[]{"getId", "getVisibility", "getAlpha", "getWidth",
                    "getHeight", "getMeasuredWidth", "getMeasuredHeight", "getTranslationY",
                    "getText", "isShown", "isEnabled"};
            for (String name : methods) {
                try {
                    Method m = cls.getMethod(name);
                    Object value = m.invoke(view);
                    line.append(" ").append(name).append("=").append(safeToString(value));
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable e) {
                    line.append(" ").append(name).append("=<").append(e.getClass().getSimpleName()).append(");");
                }
            }
            log(LOG_INFO, TAG, line.toString());
            if (depth >= maxDepth) return;
            try {
                Method countMethod = cls.getMethod("getChildCount");
                Method childMethod = cls.getMethod("getChildAt", int.class);
                int count = ((Number) countMethod.invoke(view)).intValue();
                log(LOG_INFO, TAG, label + " CHILD_COUNT=" + count);
                for (int i = 0; i < Math.min(count, 30); i++) {
                    Object child = childMethod.invoke(view, i);
                    dumpViewTree(label + "/" + i, child, depth + 1, maxDepth);
                }
            } catch (NoSuchMethodException ignored) {
                // Leaf view.
            }
        } catch (Throwable e) {
            log(LOG_INFO, TAG, label + " VIEW DUMP ERROR=" + e);
        }
    }

    private void traceApkMethods(
            ClassLoader loader,
            String className,
            String[] methodNames,
            String traceLabel
    ) {
        try {
            Class<?> clazz = loader.loadClass(className);
            int installed = 0;

            for (Method method : clazz.getDeclaredMethods()) {
                boolean match = false;
                for (String methodName : methodNames) {
                    if (methodName.equals(method.getName())) {
                        match = true;
                        break;
                    }
                }
                if (!match) continue;

                method.setAccessible(true);
                hook(method).intercept(new ApkMethodTraceHooker(traceLabel));
                log(LOG_INFO, TAG,
                        traceLabel + " HOOK INSTALLED: " + method);
                installed++;
            }

            log(LOG_INFO, TAG,
                    traceLabel + " HOOK COUNT = " + installed
                            + " class=" + className);

        } catch (Throwable e) {
            log(LOG_ERROR, TAG,
                    traceLabel + " HOOK ERROR class=" + className
                            + " error=" + e);
        }
    }

    private class ApkMethodTraceHooker implements XposedInterface.Hooker {

        private final String traceLabel;

        ApkMethodTraceHooker(String traceLabel) {
            this.traceLabel = traceLabel;
        }

        @Override
        public Object intercept(
                XposedInterface.Chain chain
        ) throws Throwable {

            Method method = (Method) chain.getExecutable();
            StringBuilder args = new StringBuilder();

            for (int i = 0; i < method.getParameterCount(); i++) {
                if (i > 0) args.append(", ");
                args.append("arg").append(i).append("=")
                        .append(String.valueOf(chain.getArg(i)));
            }

            Object thiz = chain.getThisObject();
            log(LOG_INFO, TAG,
                    traceLabel + " CALL: " + method.getDeclaringClass().getName()
                            + "." + method.getName() + "(" + args + ")");

            boolean inspectRow = "TOUCH RESPONSE ROW".equals(traceLabel);
            if (inspectRow) {
                dumpViewHolderState("ROW BEFORE " + method.getName(), thiz);
                for (int i = 0; i < method.getParameterCount(); i++) {
                    Object arg = chain.getArg(i);
                    if (arg != null) {
                        dumpObjectShallow("ROW ARG" + i + " " + method.getName(), arg, 2, 60);
                        dumpViewTree("ROW ARG" + i + " VIEW " + method.getName(), arg, 0, 3);
                    }
                }
            }

            Object result = chain.proceed();

            log(LOG_INFO, TAG,
                    traceLabel + " RESULT: " + method.getName()
                            + " = " + String.valueOf(result));
            if (inspectRow) {
                dumpViewHolderState("ROW AFTER " + method.getName(), thiz);
            }

            return result;
        }
    }

    // ================================================================
    // Reflection helper
    // ================================================================

    // ================================================================
    // Confirmed TouchRateConfigState force
    //
    // PerformanceSetting$b.l -> TouchRateConfigState
    // TouchRateConfigState.a -> isTouchModeTouchRateEnabled
    //
    // Only the PUBG KR package is forced.
    // ================================================================

    private void forceTouchRateEnabled(Object performanceSetting) {
        if (performanceSetting == null) {
            return;
        }

        try {
            Field stateField = null;
            Class<?> owner = performanceSetting.getClass();

            while (owner != null && stateField == null) {
                try {
                    stateField = owner.getDeclaredField("l");
                } catch (NoSuchFieldException ignored) {
                    owner = owner.getSuperclass();
                }
            }

            if (stateField == null) {
                log(LOG_ERROR, TAG,
                        "TOUCH FORCE: field l NOT FOUND in "
                                + performanceSetting.getClass().getName());
                return;
            }

            stateField.setAccessible(true);
            Object state = stateField.get(performanceSetting);

            if (state == null) {
                log(LOG_ERROR, TAG,
                        "TOUCH FORCE: l state=null");
                return;
            }

            Field enabledField = null;
            owner = state.getClass();

            while (owner != null && enabledField == null) {
                try {
                    enabledField = owner.getDeclaredField("a");
                } catch (NoSuchFieldException ignored) {
                    owner = owner.getSuperclass();
                }
            }

            if (enabledField == null
                    || enabledField.getType() != boolean.class) {
                log(LOG_ERROR, TAG,
                        "TOUCH FORCE: nested boolean field a NOT FOUND in "
                                + state.getClass().getName());
                return;
            }

            enabledField.setAccessible(true);

            boolean before = enabledField.getBoolean(state);

            if (TARGET_GAME.equals(getCurrentTouchForcePackage(performanceSetting))) {
                log(LOG_INFO, TAG,
                        "TOUCH FORCE BEFORE: l.a=" + before
                                + " nestedClass="
                                + state.getClass().getName());

                enabledField.setBoolean(state, true);

                boolean after = enabledField.getBoolean(state);

                log(LOG_INFO, TAG,
                        "TOUCH FORCE AFTER: l.a=" + after);

                log(LOG_INFO, TAG,
                        "TOUCH FORCE APPLIED: "
                                + TARGET_GAME
                                + " -> l.a=true");
            }

        } catch (Throwable e) {
            log(LOG_ERROR, TAG,
                    "TOUCH FORCE ERROR: " + e);
        }
    }

    /*
     * PerformanceSetting$a.a() does not pass the package directly in the
     * returned object. For this test, use the known target because the hook
     * is reached while the target game's configuration is being processed.
     *
     * The d(String) path is already target-filtered before this helper runs.
     */
    private String getCurrentTouchForcePackage(Object performanceSetting) {
        return TARGET_GAME;
    }

    private String describePerformanceSetting(
            Object object
    ) {
        if (object == null) {
            return "null";
        }

        try {
            Class<?> clazz =
                    object.getClass();

            StringBuilder out =
                    new StringBuilder();

            out.append(clazz.getName())
                    .append(" { ");

            Field[] fields =
                    clazz.getDeclaredFields();

            for (Field field : fields) {
                try {
                    field.setAccessible(true);

                    Object value =
                            field.get(object);

                    out.append(field.getName())
                            .append("=")
                            .append(String.valueOf(value))
                            .append(", ");

                } catch (Throwable e) {
                    out.append(field.getName())
                            .append("=<ERROR>, ");
                }
            }

            out.append("}");

            return out.toString();

        } catch (Throwable e) {
            return String.valueOf(object)
                    + " [reflection error: "
                    + e
                    + "]";
        }
    }
}

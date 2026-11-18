package dev.gyrokr.vectorhook;

import java.lang.reflect.Field;
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

    public GyroKrModule() { super(); }

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
            installGyroscopeHooks(loader);
            installTouchHooks(loader);
            installFeatureListHook(loader);
            return;
        }

        if (COSA.equals(packageName)) {
            log(LOG_INFO, TAG, "COSA detected");
            installCosaPerformanceValueHook(loader);
            installPerformanceSettingHooks(loader);
            installCosaNetworkParserHook(loader);
            installDBARouterServiceHook(loader);
            installGameConfigLookupHook(loader);
            installExtremeTouchHooks(loader);
        }
    }

    private void installGyroscopeHooks(ClassLoader loader) {
        try {
            Class<?> clazz = loader.loadClass("business.module.gyroscope.GyroscopeConfigFeature");
            log(LOG_INFO, TAG, "GyroscopeConfigFeature FOUND");
            int installed = 0;
            for (Method method : clazz.getDeclaredMethods()) {
                String name = method.getName();
                Class<?>[] params = method.getParameterTypes();

                if ("r".equals(name) && method.getReturnType() == boolean.class
                        && params.length == 1 && params[0] == String.class) {
                    method.setAccessible(true);
                    hook(method).intercept(new GyroGateHooker("r"));
                    log(LOG_INFO, TAG, "Gyroscope hook INSTALLED: " + method);
                    installed++;
                } else if ("s".equals(name) && method.getReturnType() == boolean.class
                        && params.length == 1 && params[0] == String.class) {
                    method.setAccessible(true);
                    hook(method).intercept(new GyroGateHooker("s"));
                    log(LOG_INFO, TAG, "Gyroscope hook INSTALLED: " + method);
                    installed++;
                }
            }
            log(LOG_INFO, TAG, "Gyroscope hooks installed = " + installed);
        } catch (Throwable e) {
            log(LOG_ERROR, TAG, "Gyroscope hook ERROR: " + e);
        }
    }

    private class GyroGateHooker implements XposedInterface.Hooker {
        private final String methodName;
        GyroGateHooker(String methodName) { this.methodName = methodName; }

        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            Object arg = null;
            if (chain.getExecutable().getParameterCount() > 0) arg = chain.getArg(0);
            String packageName = String.valueOf(arg);

            if (TARGET_GAME.equals(packageName)) {
                log(LOG_INFO, TAG, "GYRO TARGET HIT: " + methodName + "(" + packageName + ") -> true");
                return Boolean.TRUE;
            }
            return chain.proceed();
        }
    }

    private void installTouchHooks(ClassLoader loader) {
        try {
            Class<?> clazz = loader.loadClass("com.oplus.cosa.COSASDKManager");
            log(LOG_INFO, TAG, "COSASDKManager FOUND");
            int installed = 0;
            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.getName().toLowerCase().contains("touch")) continue;
                if (Modifier.isStatic(method.getModifiers())) continue;
                method.setAccessible(true);
                hook(method).intercept(new TouchTraceHooker());
                log(LOG_INFO, TAG, "TOUCH TRACE HOOK INSTALLED: " + method);
                installed++;
            }
            log(LOG_INFO, TAG, "TOUCH TRACE HOOKS INSTALLED = " + installed);
        } catch (Throwable e) {
            log(LOG_ERROR, TAG, "Touch trace hook ERROR: " + e);
        }
    }

    private class TouchTraceHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            Method method = (Method) chain.getExecutable();
            StringBuilder args = new StringBuilder();
            for (int i = 0; i < method.getParameterCount(); i++) {
                if (i > 0) args.append(", ");
                args.append("arg").append(i).append("=").append(String.valueOf(chain.getArg(i)));
            }
            log(LOG_INFO, TAG, "TOUCH CALL: " + method.getName() + "(" + args + ")");
            Object result = chain.proceed();
            log(LOG_INFO, TAG, "TOUCH RESULT: " + method.getName() + " = " + String.valueOf(result));
            return result;
        }
    }

    private void installFeatureListHook(ClassLoader loader) {
        try {
            Class<?> clazz = loader.loadClass("com.oplus.cosa.COSASDKManager");
            Method target = null;
            for (Method method : clazz.getDeclaredMethods()) {
                if ("getSupportFeatureList".equals(method.getName()) && method.getParameterCount() == 0) {
                    target = method; break;
                }
            }
            if (target == null) {
                log(LOG_ERROR, TAG, "getSupportFeatureList NOT FOUND");
                return;
            }
            target.setAccessible(true);
            hook(target).intercept(new FeatureListHooker());
            log(LOG_INFO, TAG, "FEATURE LIST HOOK INSTALLED: " + target);
        } catch (Throwable e) {
            log(LOG_ERROR, TAG, "Feature list hook ERROR: " + e);
        }
    }

    private class FeatureListHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            log(LOG_INFO, TAG, "FEATURE LIST CALL: getSupportFeatureList()");
            Object result = chain.proceed();
            log(LOG_INFO, TAG, "FEATURE LIST RESULT: " + String.valueOf(result));
            if (result instanceof List) {
                List<?> list = (List<?>) result;
                log(LOG_INFO, TAG, "MULTI TOUCH RATE SUPPORTED = " +
                        list.contains("command_multi_touch_rate"));
            }
            return result;
        }
    }

    private void installCosaPerformanceValueHook(ClassLoader loader) {
        try {
            Class<?> clazz = loader.loadClass("com.oplus.cosa.exported.COSAExportedImpl");
            log(LOG_INFO, TAG, "COSAExportedImpl FOUND");
            Method target = clazz.getDeclaredMethod("setPerformanceValue", int.class, int.class, int.class);
            target.setAccessible(true);
            hook(target).intercept(new CosaPerformanceValueHooker());
            log(LOG_INFO, TAG, "COSA setPerformanceValue HOOK INSTALLED: " + target);
        } catch (Throwable e) {
            log(LOG_ERROR, TAG, "COSA setPerformanceValue hook ERROR: " + e);
        }
    }

    private class CosaPerformanceValueHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            Object mode = chain.getArg(0);
            Object resolution = chain.getArg(1);
            Object touch = chain.getArg(2);
            log(LOG_INFO, TAG, "COSA setPerformanceValue CALL mode=" + mode +
                    " resolution=" + resolution + " touch=" + touch);
            Object result = chain.proceed();
            log(LOG_INFO, TAG, "COSA setPerformanceValue RESULT=" + result);
            return result;
        }
    }

    private void installPerformanceSettingHooks(ClassLoader loader) {
        try {
            Class<?> clazz = loader.loadClass("com.oplus.feature.gameopt.actionfeatures.PerformanceSetting$a");
            log(LOG_INFO, TAG, "PerformanceSetting$a FOUND");
            int installed = 0;

            for (Method method : clazz.getDeclaredMethods()) {
                if ("a".equals(method.getName()) && method.getParameterCount() == 2
                        && method.getParameterTypes()[1] == String.class
                        && method.getReturnType().getName().equals(
                        "com.oplus.feature.gameopt.actionfeatures.PerformanceSetting$b")) {
                    method.setAccessible(true);
                    hook(method).intercept(new PerformanceSettingAHooker());
                    log(LOG_INFO, TAG, "PERFORMANCE a() TRACE HOOK INSTALLED: " + method);
                    installed++;
                } else if ("d".equals(method.getName()) && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == String.class
                        && method.getReturnType().getName().equals(
                        "com.oplus.feature.gameopt.actionfeatures.PerformanceSetting$b")) {
                    method.setAccessible(true);
                    hook(method).intercept(new PerformanceSettingDHooker());
                    log(LOG_INFO, TAG, "PERFORMANCE d() TRACE HOOK INSTALLED: " + method);
                    installed++;
                }
            }
            log(LOG_INFO, TAG, "PerformanceSetting trace hooks installed = " + installed);
        } catch (Throwable e) {
            log(LOG_ERROR, TAG, "PerformanceSetting hook ERROR: " + e);
        }
    }

    private class PerformanceSettingAHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            String input = null;
            if (chain.getExecutable().getParameterCount() >= 2) input = String.valueOf(chain.getArg(1));
            log(LOG_INFO, TAG, "PERFORMANCE a() INPUT = " + input);
            Object result = chain.proceed();
            log(LOG_INFO, TAG, "PERFORMANCE a() RESULT = " + describePerformanceSetting(result));
            dumpNestedTouchRateState(result);
            return result;
        }
    }

    private class PerformanceSettingDHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            String packageName = String.valueOf(chain.getArg(0));
            Object result = chain.proceed();
            if (TARGET_GAME.equals(packageName)) {
                log(LOG_INFO, TAG, "PERFORMANCE d() PACKAGE = " + packageName);
                log(LOG_INFO, TAG, "PERFORMANCE d() RESULT = " + describePerformanceSetting(result));
                dumpNestedTouchRateState(result);
            }
            return result;
        }
    }

    private void dumpNestedTouchRateState(Object result) {
        if (result == null) {
            log(LOG_INFO, TAG, "TOUCH NESTED DUMP: result=null");
            return;
        }

        try {
            Field nested = findFieldRecursive(result.getClass(), "l");
            if (nested == null) {
                log(LOG_INFO, TAG, "TOUCH NESTED DUMP: field l NOT FOUND");
                return;
            }

            nested.setAccessible(true);
            Object state = nested.get(result);

            log(LOG_INFO, TAG, "TOUCH NESTED CLASS = " +
                    (state == null ? "null" : state.getClass().getName()));

            log(LOG_INFO, TAG, "TOUCH NESTED ID = " +
                    (state == null ? -1 : System.identityHashCode(state)));

            if (state == null) {
                return;
            }

            dumpFieldsWithPrefix(state, "TOUCH NESTED FIELD");
        } catch (Throwable e) {
            log(LOG_ERROR, TAG, "TOUCH NESTED DUMP ERROR: " + e);
        }
    }

    private Field findFieldRecursive(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private void dumpFieldsWithPrefix(Object object, String prefix) {
        Class<?> current = object.getClass();

        while (current != null) {
            Field[] fields = current.getDeclaredFields();

            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(object);

                    log(LOG_INFO, TAG,
                            prefix + ": owner=" + current.getName()
                                    + " name=" + field.getName()
                                    + " type=" + field.getType().getName()
                                    + " value=" + String.valueOf(value));
                } catch (Throwable e) {
                    log(LOG_INFO, TAG,
                            prefix + ": owner=" + current.getName()
                                    + " name=" + field.getName()
                                    + " <ERROR>");
                }
            }

            current = current.getSuperclass();
        }
    }

    private void installCosaNetworkParserHook(ClassLoader loader) {
        try {
            Class<?> clazz = loader.loadClass("v8.e");
            log(LOG_INFO, TAG, "v8.e FOUND");

            Method responseTarget = null;
            for (Method method : clazz.getDeclaredMethods()) {
                if ("d".equals(method.getName()) && method.getParameterCount() == 2
                        && method.getParameterTypes()[0] == String.class
                        && method.getParameterTypes()[1] == String.class
                        && java.util.HashMap.class.isAssignableFrom(method.getReturnType())) {
                    responseTarget = method; break;
                }
            }

            if (responseTarget == null) {
                log(LOG_ERROR, TAG, "v8.e.d(String,String):HashMap NOT FOUND");
            } else {
                responseTarget.setAccessible(true);
                hook(responseTarget).intercept(new CosaNetworkParserHooker());
                log(LOG_INFO, TAG, "COSA NETWORK PARSER TRACE HOOK INSTALLED: " + responseTarget);
            }

            Method packageConfigTarget = null;
            for (Method method : clazz.getDeclaredMethods()) {
                if ("b".equals(method.getName()) && method.getParameterCount() == 2
                        && method.getParameterTypes()[0] == String.class
                        && method.getParameterTypes()[1] == boolean.class) {
                    packageConfigTarget = method; break;
                }
            }

            if (packageConfigTarget == null) {
                log(LOG_ERROR, TAG, "v8.e.b(String,boolean) NOT FOUND");
            } else {
                packageConfigTarget.setAccessible(true);
                hook(packageConfigTarget).intercept(new CosaNetworkPackageHooker());
                log(LOG_INFO, TAG, "COSA PACKAGE CONFIG TRACE HOOK INSTALLED: " + packageConfigTarget);
            }
        } catch (Throwable e) {
            log(LOG_ERROR, TAG, "COSA network parser hook ERROR: " + e);
        }
    }

    private class CosaNetworkParserHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            String response = String.valueOf(chain.getArg(0));
            String source = String.valueOf(chain.getArg(1));
            boolean targetHit = response.contains(TARGET_GAME);

            if (targetHit) {
                log(LOG_INFO, TAG, "COSA RESPONSE CONTAINS PUBG CONFIG");
                log(LOG_INFO, TAG, "COSA RESPONSE SOURCE = " + source);
            }

            Object result = chain.proceed();

            if (targetHit) {
                log(LOG_INFO, TAG, "COSA PARSE RESULT CLASS = " +
                        (result == null ? "null" : result.getClass().getName()));
                log(LOG_INFO, TAG, "COSA PARSE RESULT SIZE = " +
                        (result instanceof java.util.Map ? ((java.util.Map<?, ?>) result).size() : -1));
            }
            return result;
        }
    }

    private class CosaNetworkPackageHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            String input = String.valueOf(chain.getArg(0));
            boolean flag = (Boolean) chain.getArg(1);
            boolean targetHit = input.contains(TARGET_GAME);

            if (targetHit) {
                log(LOG_INFO, TAG, "COSA PACKAGE CONFIG TARGET HIT");
                log(LOG_INFO, TAG, "COSA PACKAGE CONFIG INPUT LENGTH = " + input.length());
                log(LOG_INFO, TAG, "COSA PACKAGE CONFIG FLAG = " + flag);
            }

            Object result = chain.proceed();

            if (targetHit) {
                log(LOG_INFO, TAG, "COSA PACKAGE CONFIG RESULT CLASS = " +
                        (result == null ? "null" : result.getClass().getName()));
                logPackageConfig(result);
            }
            return result;
        }
    }

    private void logPackageConfig(Object config) {
        if (config == null) return;
        try {
            String packageName = (String) config.getClass().getMethod("D").invoke(config);
            String resv7 = (String) config.getClass().getMethod("R").invoke(config);

            log(LOG_INFO, TAG, "PARSED CONFIG PACKAGE = " + packageName);
            log(LOG_INFO, TAG, "PARSED CONFIG RESV7 LENGTH = " + (resv7 == null ? -1 : resv7.length()));
            log(LOG_INFO, TAG, "PARSED CONFIG HAS LINGXI = " +
                    (resv7 != null && resv7.contains("lingxi_boost")));
            log(LOG_INFO, TAG, "PARSED CONFIG HAS TEMP TOUCH = " +
                    (resv7 != null && resv7.contains("touchRateTemporaryChange")));
        } catch (Throwable e) {
            log(LOG_ERROR, TAG, "PARSED CONFIG INSPECT ERROR: " + e);
        }
    }

    private void installDBARouterServiceHook(ClassLoader loader) {
        try {
            Class<?> dbaClass = loader.loadClass("com.oplus.common.db.service.DBARouterService");
            log(LOG_INFO, TAG, "DBARouterService FOUND");
            Method target = null;

            for (Method method : dbaClass.getDeclaredMethods()) {
                if (!"T".equals(method.getName())) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 1 && params[0].getName().equals("t9.s")) {
                    target = method; break;
                }
            }

            if (target == null) {
                log(LOG_ERROR, TAG, "DBARouterService.T(Lt9.s) NOT FOUND");
                return;
            }

            target.setAccessible(true);
            hook(target).intercept(new DBARouterTHooker());
            log(LOG_INFO, TAG, "DBARouterService.T TRACE HOOK INSTALLED: " + target);
        } catch (Throwable e) {
            log(LOG_ERROR, TAG, "DBARouterService hook ERROR: " + e);
        }
    }

    private class DBARouterTHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            Object config = chain.getArg(0);
            if (config != null) logPackageConfig(config);
            return chain.proceed();
        }
    }

    private void installGameConfigLookupHook(ClassLoader loader) {
        try {
            Class<?> clazz = loader.loadClass("u6.g");
            Method target = clazz.getDeclaredMethod("e", String.class);
            target.setAccessible(true);
            hook(target).intercept(new GameConfigLookupHooker());
            log(LOG_INFO, TAG, "GAME CONFIG LOOKUP HOOK INSTALLED: " + target);
        } catch (Throwable e) {
            log(LOG_ERROR, TAG, "GAME CONFIG LOOKUP HOOK ERROR: " + e);
        }
    }

    private class GameConfigLookupHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            String requestedPackage = String.valueOf(chain.getArg(0));
            Object result = chain.proceed();

            if (!TARGET_GAME.equals(requestedPackage)) return result;

            if (result == null) {
                log(LOG_INFO, TAG, "GAME CONFIG LOOKUP RESULT = null");
                return null;
            }

            log(LOG_INFO, TAG, "GAME CONFIG REQUESTED = " + requestedPackage);
            logPackageConfig(result);
            return result;
        }
    }

    private void installExtremeTouchHooks(ClassLoader loader) {
        try {
            Class<?> clazz = loader.loadClass("com.oplus.feature.extremetouch.ExtremeTouchFeature");
            log(LOG_INFO, TAG, "ExtremeTouchFeature FOUND");
            int installed = 0;

            for (Method method : clazz.getDeclaredMethods()) {
                String name = method.getName();
                if (!"gameStart".equals(name) && !"h".equals(name)) continue;
                method.setAccessible(true);
                hook(method).intercept(new ExtremeTouchHooker(name));
                log(LOG_INFO, TAG, "EXTREME TOUCH HOOK INSTALLED: " + method);
                installed++;
            }

            log(LOG_INFO, TAG, "EXTREME TOUCH HOOKS INSTALLED = " + installed);
            if (installed == 0) log(LOG_INFO, TAG,
                    "ExtremeTouchFeature loaded, but gameStart/h was not found");
        } catch (Throwable e) {
            log(LOG_ERROR, TAG, "ExtremeTouch hook ERROR: " + e);
        }
    }

    private class ExtremeTouchHooker implements XposedInterface.Hooker {
        private final String methodName;
        ExtremeTouchHooker(String methodName) { this.methodName = methodName; }

        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            Method method = (Method) chain.getExecutable();
            StringBuilder args = new StringBuilder();
            boolean targetHit = false;

            for (int i = 0; i < method.getParameterCount(); i++) {
                Object arg = chain.getArg(i);
                if (i > 0) args.append(", ");
                args.append("arg").append(i).append("=").append(String.valueOf(arg));
                if (arg instanceof String && TARGET_GAME.equals(arg)) targetHit = true;
            }

            log(LOG_INFO, TAG, "EXTREME TOUCH CALL: " + methodName +
                    "(" + args + ") targetHit=" + targetHit);

            Object result = chain.proceed();

            log(LOG_INFO, TAG, "EXTREME TOUCH RESULT: " + methodName + " = " + String.valueOf(result));
            return result;
        }
    }

    private String describePerformanceSetting(Object object) {
        if (object == null) return "null";
        try {
            Class<?> clazz = object.getClass();
            StringBuilder out = new StringBuilder();
            out.append(clazz.getName()).append(" { ");
            Field[] fields = clazz.getDeclaredFields();

            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(object);
                    out.append(field.getName()).append("=").append(String.valueOf(value)).append(", ");
                } catch (Throwable e) {
                    out.append(field.getName()).append("=<ERROR>, ");
                }
            }

            out.append("}");
            return out.toString();
        } catch (Throwable e) {
            return String.valueOf(object) + " [reflection error: " + e + "]";
        }
    }
}

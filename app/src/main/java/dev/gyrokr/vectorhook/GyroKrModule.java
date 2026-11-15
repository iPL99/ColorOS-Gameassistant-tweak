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

    public GyroKrModule() {
        super();
    }

    @Override
    public void onModuleLoaded(
            XposedModuleInterface.ModuleLoadedParam param) {

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
    }

    @Override
    public void onPackageReady(
            XposedModuleInterface.PackageReadyParam param) {

        String packageName = param.getPackageName();

        log(
                LOG_INFO,
                TAG,
                "onPackageReady: " + packageName
        );

        ClassLoader loader = param.getClassLoader();

        /*
         * ============================================================
         * GameSpace
         * ============================================================
         */
        if (GAME_SPACE.equals(packageName)) {

            log(
                    LOG_INFO,
                    TAG,
                    "GameSpace detected"
            );

            installGyroscopeHooks(loader);
            installTouchHooks(loader);
            installFeatureListHook(loader);

            return;
        }

        /*
         * ============================================================
         * COSA
         * ============================================================
         */
        if (COSA.equals(packageName)) {

            log(
                    LOG_INFO,
                    TAG,
                    "COSA detected"
            );

            installPerformanceSettingHooks(loader);
            installCosaNetworkParserHook(loader);
            installDBARouterServiceHook(loader);

            return;
        }
    }

    /*
     * ================================================================
     * Existing Gyroscope hooks
     * ================================================================
     */

    private void installGyroscopeHooks(ClassLoader loader) {

        try {

            Class<?> clazz = loader.loadClass(
                    "business.module.gyroscope.GyroscopeConfigFeature"
            );

            log(
                    LOG_INFO,
                    TAG,
                    "GyroscopeConfigFeature FOUND"
            );

            int installed = 0;

            for (Method method : clazz.getDeclaredMethods()) {

                String name = method.getName();

                Class<?>[] params = method.getParameterTypes();

                /*
                 * r(String):boolean
                 * Chirality / AntiShake support gate
                 */
                if (
                        "r".equals(name)
                        && method.getReturnType() == boolean.class
                        && params.length == 1
                        && params[0] == String.class
                ) {

                    method.setAccessible(true);

                    hook(method).intercept(
                            new GyroGateHooker("r")
                    );

                    log(
                            LOG_INFO,
                            TAG,
                            "Gyroscope hook INSTALLED: " + method
                    );

                    installed++;
                }

                /*
                 * s(String):boolean
                 * Sensitivity support gate
                 */
                else if (
                        "s".equals(name)
                        && method.getReturnType() == boolean.class
                        && params.length == 1
                        && params[0] == String.class
                ) {

                    method.setAccessible(true);

                    hook(method).intercept(
                            new GyroGateHooker("s")
                    );

                    log(
                            LOG_INFO,
                            TAG,
                            "Gyroscope hook INSTALLED: " + method
                    );

                    installed++;
                }
            }

            log(
                    LOG_INFO,
                    TAG,
                    "Gyroscope hooks installed = " + installed
            );

        } catch (Throwable e) {

            log(
                    LOG_ERROR,
                    TAG,
                    "Gyroscope hook ERROR: " + e
            );
        }
    }

    /*
     * ================================================================
     * Existing COSASDKManager touch hooks
     * ================================================================
     */

    private void installTouchHooks(ClassLoader loader) {

        try {

            Class<?> clazz = loader.loadClass(
                    "com.oplus.cosa.COSASDKManager"
            );

            log(
                    LOG_INFO,
                    TAG,
                    "COSASDKManager FOUND"
            );

            int installed = 0;

            for (Method method : clazz.getDeclaredMethods()) {

                String name = method.getName();

                if (!name.toLowerCase().contains("touch")) {
                    continue;
                }

                if (Modifier.isStatic(method.getModifiers())) {
                    continue;
                }

                method.setAccessible(true);

                hook(method).intercept(
                        new TouchTraceHooker()
                );

                log(
                        LOG_INFO,
                        TAG,
                        "TOUCH HOOK INSTALLED: "
                                + method.getName()
                );

                installed++;
            }

            log(
                    LOG_INFO,
                    TAG,
                    "ALL TOUCH HOOKS INSTALLED = "
                            + installed
            );

        } catch (Throwable e) {

            log(
                    LOG_ERROR,
                    TAG,
                    "Touch hook ERROR: " + e
            );
        }
    }

    /*
     * ================================================================
     * Existing feature list hook
     * ================================================================
     */

    private void installFeatureListHook(ClassLoader loader) {

        try {

            Class<?> clazz = loader.loadClass(
                    "com.oplus.cosa.COSASDKManager"
            );

            Method target = null;

            for (Method method : clazz.getDeclaredMethods()) {

                if (
                        "getSupportFeatureList".equals(
                                method.getName()
                        )
                        && method.getParameterCount() == 0
                ) {

                    target = method;
                    break;
                }
            }

            if (target == null) {

                log(
                        LOG_ERROR,
                        TAG,
                        "getSupportFeatureList NOT FOUND"
                );

                return;
            }

            target.setAccessible(true);

            hook(target).intercept(
                    new FeatureListHooker()
            );

            log(
                    LOG_INFO,
                    TAG,
                    "FEATURE LIST HOOK INSTALLED: "
                            + target
            );

        } catch (Throwable e) {

            log(
                    LOG_ERROR,
                    TAG,
                    "Feature list hook ERROR: " + e
            );
        }
    }

    /*
     * ================================================================
     * Existing COSA PerformanceSetting hooks
     * ================================================================
     */

    private void installPerformanceSettingHooks(
            ClassLoader loader) {

        try {

            Class<?> clazz = loader.loadClass(
                    "com.oplus.feature.gameopt.actionfeatures.PerformanceSetting$a"
            );

            log(
                    LOG_INFO,
                    TAG,
                    "PerformanceSetting$a FOUND"
            );

            int installed = 0;

            for (Method method : clazz.getDeclaredMethods()) {

                /*
                 * a(
                 *   PerformanceSetting$b,
                 *   String
                 * ): PerformanceSetting$b
                 */
                if (
                        "a".equals(method.getName())
                        && method.getParameterCount() == 2
                        && method.getParameterTypes()[1] == String.class
                        && method.getReturnType().getName().equals(
                                "com.oplus.feature.gameopt.actionfeatures.PerformanceSetting$b"
                        )
                ) {

                    method.setAccessible(true);

                    hook(method).intercept(
                            new PerformanceSettingAHooker()
                    );

                    log(
                            LOG_INFO,
                            TAG,
                            "PERFORMANCE HOOK INSTALLED: "
                                    + method
                    );

                    installed++;
                }

                /*
                 * d(String):
                 * PerformanceSetting$b
                 */
                else if (
                        "d".equals(method.getName())
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == String.class
                        && method.getReturnType().getName().equals(
                                "com.oplus.feature.gameopt.actionfeatures.PerformanceSetting$b"
                        )
                ) {

                    method.setAccessible(true);

                    hook(method).intercept(
                            new PerformanceSettingDHooker()
                    );

                    log(
                            LOG_INFO,
                            TAG,
                            "PERFORMANCE d() HOOK INSTALLED: "
                                    + method
                    );

                    installed++;
                }
            }

            log(
                    LOG_INFO,
                    TAG,
                    "PerformanceSetting hooks installed = "
                            + installed
            );

        } catch (Throwable e) {

            log(
                    LOG_ERROR,
                    TAG,
                    "PerformanceSetting hook ERROR: "
                            + e
            );
        }
    }

    /*
     * ================================================================
     * NEW: COSA network response parser
     *
     * v8.e.d(String,String):HashMap
     *
     * First argument:
     *     full network response JSON
     *
     * Second argument:
     *     service / source string
     * ================================================================
     */

    private void installCosaNetworkParserHook(
        ClassLoader loader) {

    try {

        Class<?> clazz =
                loader.loadClass("v8.e");

        log(
                LOG_INFO,
                TAG,
                "v8.e FOUND"
        );

        Method target = null;

        for (Method method : clazz.getDeclaredMethods()) {

            if (
                    "d".equals(method.getName())
                    && method.getParameterCount() == 2
                    && method.getParameterTypes()[0] == String.class
                    && method.getParameterTypes()[1] == String.class
                    && java.util.HashMap.class.isAssignableFrom(
                            method.getReturnType()
                    )
            ) {

                target = method;
                break;
            }
        }

        if (target == null) {

            log(
                    LOG_ERROR,
                    TAG,
                    "v8.e.d(String,String):HashMap NOT FOUND"
            );

            return;
        }

        target.setAccessible(true);

        hook(target).intercept(
                new CosaNetworkParserHooker()
        );

        log(
                LOG_INFO,
                TAG,
                "COSA NETWORK PARSER HOOK INSTALLED: "
                        + target
        );


        /*
         * ============================================================
         * v8.e.b(String, boolean)
         * ============================================================
         */

        Method packageConfigTarget = null;

        for (Method method : clazz.getDeclaredMethods()) {

            if (
                    "b".equals(method.getName())
                    && method.getParameterCount() == 2
                    && method.getParameterTypes()[0] == String.class
                    && method.getParameterTypes()[1] == boolean.class
            ) {

                packageConfigTarget = method;
                break;
            }
        }

        if (packageConfigTarget == null) {

            log(
                    LOG_ERROR,
                    TAG,
                    "v8.e.b(String,boolean) NOT FOUND"
            );

        } else {

            packageConfigTarget.setAccessible(true);

            hook(packageConfigTarget).intercept(
                    new CosaNetworkPackageHooker()
            );

            log(
                    LOG_INFO,
                    TAG,
                    "COSA PACKAGE CONFIG HOOK INSTALLED: "
                            + packageConfigTarget
            );
        }

    } catch (Throwable e) {

        log(
                LOG_ERROR,
                TAG,
                "COSA network parser hook ERROR: "
                        + e
        );
    }
}

    /*
     * ================================================================
     * Existing Gyroscope hooker
     * ================================================================
     */

    private class GyroGateHooker
            implements XposedInterface.Hooker {

        private final String methodName;

        GyroGateHooker(String methodName) {
            this.methodName = methodName;
        }

        @Override
        public Object intercept(
                XposedInterface.Chain chain)
                throws Throwable {

            Object arg = null;

            if (
                    chain.getExecutable()
                            .getParameterCount() > 0
            ) {

                arg = chain.getArg(0);
            }

            String packageName =
                    String.valueOf(arg);

            if (
                    TARGET_GAME.equals(packageName)
            ) {

                log(
                        LOG_INFO,
                        TAG,
                        "GYRO TARGET HIT: "
                                + methodName
                                + "("
                                + packageName
                                + ") -> true"
                );

                return Boolean.TRUE;
            }

            return chain.proceed();
        }
    }

    /*
     * ================================================================
     * Existing Touch trace hooker
     * ================================================================
     */

    private class TouchTraceHooker
            implements XposedInterface.Hooker {

        @Override
        public Object intercept(
                XposedInterface.Chain chain)
                throws Throwable {

            Method method =
                    (Method) chain.getExecutable();

            String name =
                    method.getName();

            StringBuilder args =
                    new StringBuilder();

            for (
                    int i = 0;
                    i < method.getParameterCount();
                    i++
            ) {

                if (i > 0) {
                    args.append(", ");
                }

                args.append("arg")
                        .append(i)
                        .append("=")
                        .append(
                                String.valueOf(
                                        chain.getArg(i)
                                )
                        );
            }

            log(
                    LOG_INFO,
                    TAG,
                    "TOUCH CALL: "
                            + name
                            + "("
                            + args
                            + ")"
            );

            Object result =
                    chain.proceed();

            log(
                    LOG_INFO,
                    TAG,
                    "TOUCH RESULT: "
                            + name
                            + " = "
                            + String.valueOf(result)
            );

            return result;
        }
    }

    /*
     * ================================================================
     * Existing Feature list hooker
     * ================================================================
     */

    private class FeatureListHooker
            implements XposedInterface.Hooker {

        @Override
        public Object intercept(
                XposedInterface.Chain chain)
                throws Throwable {

            log(
                    LOG_INFO,
                    TAG,
                    "FEATURE LIST CALL: "
                            + "getSupportFeatureList()"
            );

            Object result =
                    chain.proceed();

            log(
                    LOG_INFO,
                    TAG,
                    "FEATURE LIST RESULT: "
                            + String.valueOf(result)
            );

            if (result instanceof List) {

                List<?> list =
                        (List<?>) result;

                boolean multiTouch =
                        list.contains(
                                "command_multi_touch_rate"
                        );

                log(
                        LOG_INFO,
                        TAG,
                        "MULTI TOUCH RATE SUPPORTED = "
                                + multiTouch
                );
            }

            return result;
        }
    }

    /*
     * ================================================================
     * Existing PerformanceSetting$a.a()
     * ================================================================
     */

    private class PerformanceSettingAHooker
            implements XposedInterface.Hooker {

        @Override
        public Object intercept(
                XposedInterface.Chain chain)
                throws Throwable {

            String input = null;

            if (
                    chain.getExecutable()
                            .getParameterCount() >= 2
            ) {

                Object arg1 =
                        chain.getArg(1);

                input =
                        String.valueOf(arg1);
            }

            log(
                    LOG_INFO,
                    TAG,
                    "PERFORMANCE a() INPUT = "
                            + input
            );

            Object result =
                    chain.proceed();

            log(
                    LOG_INFO,
                    TAG,
                    "PERFORMANCE a() RESULT = "
                            + describePerformanceSetting(
                                    result
                            )
            );

            return result;
        }
    }

    /*
     * ================================================================
     * Existing PerformanceSetting$a.d(String)
     * ================================================================
     */

    private class PerformanceSettingDHooker
        implements XposedInterface.Hooker {

    @Override
    public Object intercept(
            XposedInterface.Chain chain)
            throws Throwable {

        Object result = chain.proceed();

        try {

            log(
                    LOG_INFO,
                    TAG,
                    "PERFORMANCE d() RESULT = "
                            + describePerformanceSetting(result)
            );

            if (result == null) {

                log(
                        LOG_INFO,
                        TAG,
                        "PERFORMANCE d() RESULT CLASS = null"
                );

                return result;
            }

            Class<?> resultClass =
                    result.getClass();

            log(
                    LOG_INFO,
                    TAG,
                    "PERFORMANCE d() RESULT CLASS = "
                            + resultClass.getName()
            );

            for (
                    Field field :
                            resultClass.getDeclaredFields()
            ) {

                try {

                    field.setAccessible(true);

                    Object value =
                            field.get(result);

                    log(
                            LOG_INFO,
                            TAG,
                            "PERFORMANCE d() FIELD "
                                    + field.getName()
                                    + " = "
                                    + String.valueOf(value)
                    );

                } catch (Throwable e) {

                    log(
                            LOG_INFO,
                            TAG,
                            "PERFORMANCE d() FIELD "
                                    + field.getName()
                                    + " READ ERROR = "
                                    + e.getClass().getName()
                    );
                }
            }

        } catch (Throwable e) {

            log(
                    LOG_ERROR,
                    TAG,
                    "PERFORMANCE d() TRACE ERROR: "
                            + e
            );
        }

        return result;
    }
}

    /*
     * ================================================================
     * NEW: v8.e.d() hooker
     * ================================================================
     */

    /*
 * ================================================================
 * NEW: v8.e.b(String,boolean) hooker
 * ================================================================
 */

private class CosaNetworkPackageHooker
        implements XposedInterface.Hooker {

    @Override
    public Object intercept(
            XposedInterface.Chain chain)
            throws Throwable {

        Object arg0 = null;
        Object arg1 = null;

        if (chain.getExecutable().getParameterCount() >= 1) {

            arg0 = chain.getArg(0);
        }

        if (chain.getExecutable().getParameterCount() >= 2) {

            arg1 = chain.getArg(1);
        }

        String input =
                String.valueOf(arg0);

        boolean targetHit =
                input.contains(TARGET_GAME);

        if (targetHit) {

            log(
                    LOG_INFO,
                    TAG,
                    "COSA PACKAGE CONFIG TARGET HIT"
            );

            log(
                    LOG_INFO,
                    TAG,
                    "COSA PACKAGE CONFIG INPUT = "
                            + input
            );

            log(
                    LOG_INFO,
                    TAG,
                    "COSA PACKAGE CONFIG FLAG = "
                            + String.valueOf(arg1)
            );
        }

        Object result =
                chain.proceed();

        if (targetHit) {

            log(
                    LOG_INFO,
                    TAG,
                    "COSA PACKAGE CONFIG RESULT = "
                            + String.valueOf(result)
            );
        }

        return result;
    }
}
    
    private class CosaNetworkParserHooker
            implements XposedInterface.Hooker {

        @Override
        public Object intercept(
                XposedInterface.Chain chain)
                throws Throwable {

            String response =
                    String.valueOf(
                            chain.getArg(0)
                    );

            String source =
                    String.valueOf(
                            chain.getArg(1)
                    );

            boolean targetHit =
                    response.contains(
                            TARGET_GAME
                    );

            if (targetHit) {

                log(
                        LOG_INFO,
                        TAG,
                        "COSA RESPONSE TARGET HIT"
                );

                log(
                        LOG_INFO,
                        TAG,
                        "COSA RESPONSE = "
                                + response
                );

                log(
                        LOG_INFO,
                        TAG,
                        "COSA ARG1 = "
                                + source
                );
            }

            Object result =
                    chain.proceed();

            if (targetHit) {

                log(
                        LOG_INFO,
                        TAG,
                        "COSA PARSE RESULT = "
                                + String.valueOf(
                                        result
                                )
                );
            }

            return result;
        }
    }

    /*
     * ================================================================
     * PerformanceSetting$b reflection helper
     * ================================================================
     */

    private String describePerformanceSetting(Object object) {

    if (object == null) {
        return "null";
    }

    try {

        Class<?> clazz = object.getClass();

        StringBuilder out = new StringBuilder();

        out.append(clazz.getName());
        out.append(" { ");

        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {

            try {

                field.setAccessible(true);

                Object value = field.get(object);

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
   /*
 * ================================================================
 * NEW: DBARouterService.T(Lt9.s) hook
 * ================================================================
 */
private void installDBARouterServiceHook(ClassLoader loader) {

    try {

        Class<?> dbaClass =
                loader.loadClass(
                        "com.oplus.common.db.service.DBARouterService"
                );

        log(
                LOG_INFO,
                TAG,
                "DBARouterService FOUND"
        );

        Method target = null;

        for (Method method : dbaClass.getDeclaredMethods()) {

            if (!"T".equals(method.getName())) {
                continue;
            }

            Class<?>[] params =
                    method.getParameterTypes();

            if (
                    params.length == 1
                    && params[0].getName().equals("t9.s")
            ) {
                target = method;
                break;
            }
        }

        if (target == null) {

            log(
                    LOG_ERROR,
                    TAG,
                    "DBARouterService.T(Lt9.s) NOT FOUND"
            );

            return;
        }

        target.setAccessible(true);

        hook(target).intercept(
                new DBARouterTHooker()
        );

        log(
                LOG_INFO,
                TAG,
                "DBARouterService.T(Lt9.s) HOOK INSTALLED: "
                        + target
        );

    } catch (Throwable e) {

        log(
                LOG_ERROR,
                TAG,
                "DBARouterService hook ERROR: "
                        + e
        );
    }
}

private class DBARouterTHooker
        implements XposedInterface.Hooker {

    @Override
    public Object intercept(
            XposedInterface.Chain chain)
            throws Throwable {

        Object config = null;

        if (
                chain.getExecutable()
                        .getParameterCount() >= 1
        ) {
            config = chain.getArg(0);
        }

        String className =
                config == null
                        ? "null"
                        : config.getClass().getName();

        String packageName = null;
        String refreshRate = null;

        if (config != null) {

            try {
                Method d =
                        config.getClass()
                                .getDeclaredMethod("D");

                d.setAccessible(true);

                packageName =
                        String.valueOf(
                                d.invoke(config)
                        );

            } catch (Throwable ignored) {
            }

            try {
                Method g =
                        config.getClass()
                                .getDeclaredMethod("G");

                g.setAccessible(true);

                refreshRate =
                        String.valueOf(
                                g.invoke(config)
                        );

            } catch (Throwable ignored) {
            }
        }

        if (
                TARGET_GAME.equals(packageName)
        ) {

            log(
                    LOG_INFO,
                    TAG,
                    "DBA T TARGET HIT"
            );

            log(
                    LOG_INFO,
                    TAG,
                    "DBA T CONFIG CLASS = "
                            + className
            );

            log(
                    LOG_INFO,
                    TAG,
                    "DBA T PACKAGE = "
                            + packageName
            );

            log(
                    LOG_INFO,
                    TAG,
                    "DBA T REFRESH RATE = "
                            + refreshRate
            );
        }

        Object result =
                chain.proceed();

        if (
                TARGET_GAME.equals(packageName)
        ) {

            log(
                    LOG_INFO,
                    TAG,
                    "DBA T RESULT = "
                            + String.valueOf(result)
            );
        }

        return result;
    }
 } 
}

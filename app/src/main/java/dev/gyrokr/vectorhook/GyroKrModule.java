package dev.gyrokr.vectorhook;

import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

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
    private static final String COSA_CLASS =
            "com.oplus.cosa.COSASDKManager";

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "onModuleLoaded");

        try {
            log(Log.INFO, TAG,
                    "Process = " + param.getProcessName());
        } catch (Throwable t) {
            log(Log.ERROR, TAG,
                    "getProcessName failed: " + t);
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String packageName = param.getPackageName();

        log(Log.INFO, TAG,
                "onPackageReady: " + packageName);

        if (!GAME_SPACE_PACKAGE.equals(packageName)) {
            return;
        }

        log(Log.INFO, TAG, "GameSpace detected");

        ClassLoader classLoader = param.getClassLoader();

        try {
            installGyroHooks(classLoader);
        } catch (Throwable t) {
            log(Log.ERROR, TAG,
                    "Gyro hook installation failed: " + t);
            Log.e(TAG, "Gyro error", t);
        }

        try {
            installAllTouchHooks(classLoader);
        } catch (Throwable t) {
            log(Log.ERROR, TAG,
                    "Touch hook installation failed: " + t);
            Log.e(TAG, "Touch error", t);
        }
    }

    private void installGyroHooks(ClassLoader classLoader)
            throws Throwable {

        Class<?> gyroClass =
                Class.forName(GYRO_CLASS, false, classLoader);

        log(Log.INFO, TAG, "GyroscopeConfigFeature FOUND");

        Method chirality =
                gyroClass.getDeclaredMethod("r", String.class);
        chirality.setAccessible(true);

        hook(chirality).intercept(
                new GyroGateHooker("r"));

        log(Log.INFO, TAG,
                "Gyroscope chirality hook INSTALLED");

        Method sensitivity =
                gyroClass.getDeclaredMethod("s", String.class);
        sensitivity.setAccessible(true);

        hook(sensitivity).intercept(
                new GyroGateHooker("s"));

        log(Log.INFO, TAG,
                "Gyroscope sensitivity hook INSTALLED");
    }

    private void installAllTouchHooks(ClassLoader classLoader)
            throws Throwable {

        log(Log.INFO, TAG,
                "Searching COSASDKManager...");

        Class<?> cosaClass =
                Class.forName(COSA_CLASS, false, classLoader);

        log(Log.INFO, TAG, "COSASDKManager FOUND");

        Method[] methods = cosaClass.getDeclaredMethods();

        int installed = 0;

        for (Method method : methods) {

            String name = method.getName();

            if (!name.toLowerCase().contains("touch")) {
                continue;
            }

            if (method.isSynthetic()
                    || method.isBridge()
                    || Modifier.isAbstract(method.getModifiers())
                    || Modifier.isNative(method.getModifiers())) {
                continue;
            }

            try {
                method.setAccessible(true);

                hook(method).intercept(
                        new TouchTraceHooker(method));

                installed++;

                log(Log.INFO, TAG,
                        "TOUCH HOOK INSTALLED: " + method);

            } catch (Throwable t) {
                log(Log.ERROR, TAG,
                        "TOUCH HOOK FAILED: "
                                + method + " : " + t);
            }
        }

        log(Log.INFO, TAG,
                "ALL TOUCH HOOKS INSTALLED = " + installed);
    }

    private final class TouchTraceHooker
            implements XposedInterface.Hooker {

        private final Method method;

        TouchTraceHooker(Method method) {
            this.method = method;
        }

        @Override
        public Object intercept(XposedInterface.Chain chain)
                throws Throwable {

            String name = method.getName();
            StringBuilder args = new StringBuilder();

            int count = method.getParameterTypes().length;

            for (int i = 0; i < count; i++) {
                if (i > 0) args.append(", ");

                Object arg = chain.getArg(i);
                args.append("arg").append(i)
                        .append("=")
                        .append(String.valueOf(arg));
            }

            log(Log.INFO, TAG,
                    "TOUCH CALL: " + name
                            + "(" + args + ")");

            Object result = chain.proceed();

            log(Log.INFO, TAG,
                    "TOUCH RESULT: " + name
                            + " = " + String.valueOf(result));

            return result;
        }
    }

    private final class GyroGateHooker
            implements XposedInterface.Hooker {

        private final String methodName;

        GyroGateHooker(String methodName) {
            this.methodName = methodName;
        }

        @Override
        public Object intercept(XposedInterface.Chain chain)
                throws Throwable {

            Object arg0 = chain.getArg(0);

            if (arg0 instanceof String
                    && TARGET_GAME_PACKAGE.equals(arg0)) {

                Object original = chain.proceed();

                log(Log.INFO, TAG,
                        methodName + "(" + arg0
                                + ") ORIGINAL = " + original);

                log(Log.INFO, TAG,
                        methodName + "(" + arg0
                                + ") FORCED = true");

                return Boolean.TRUE;
            }

            return chain.proceed();
        }
    }
}

package dev.gyrokr.vectorhook;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class GyroKrModule extends XposedModule {

    private static final String TAG = "OplusGyroKR";
    private static final String GAME_SPACE = "com.oplus.games";

    // 기본 대상 게임 패키지 목록 (파일 없이도 항상 포함됨)
    private static final Set<String> DEFAULT_TARGET_GAMES = new LinkedHashSet<>(Arrays.asList(
            "com.pubg.krmobile",   // 배틀그라운드 모바일 (한국)
            "com.tencent.ig",      // PUBG MOBILE (글로벌)
            "com.rekoo.pubgm",     // PUBG MOBILE (대만)
            "com.vng.pubgmobile"   // PUBG MOBILE (베트남)
    ));

    // 사용자 추가 패키지 파일: 한 줄에 패키지명 하나, '#' 으로 시작하면 주석.
    // 게임 어시스턴트 프로세스가 자기 권한으로 읽을 수 있는 경로 두 곳을 모두 확인한다.
    private static final String[] CONFIG_PATHS = {
            "/sdcard/Android/data/com.oplus.games/files/gyrokr_packages.txt",
            "/data/data/com.oplus.games/files/gyrokr_packages.txt"
    };
    private static final long RELOAD_INTERVAL_MS = 3000;

    private volatile Set<String> cachedTargets = DEFAULT_TARGET_GAMES;
    private volatile long lastCheckMs = 0;
    private volatile String lastSignature = "";

    /** 기본 목록 + 설정 파일 목록. 파일이 바뀌면 자동으로 다시 읽는다. */
    private Set<String> getTargetGames() {
        long now = System.currentTimeMillis();
        if (now - lastCheckMs < RELOAD_INTERVAL_MS) {
            return cachedTargets;
        }
        lastCheckMs = now;

        try {
            StringBuilder signature = new StringBuilder();
            for (String path : CONFIG_PATHS) {
                File f = new File(path);
                signature.append(path).append(':')
                        .append(f.isFile() ? f.lastModified() + "/" + f.length() : "-")
                        .append(';');
            }
            if (signature.toString().equals(lastSignature)) {
                return cachedTargets;
            }

            Set<String> merged = new LinkedHashSet<>(DEFAULT_TARGET_GAMES);
            for (String path : CONFIG_PATHS) {
                File f = new File(path);
                if (!f.isFile() || !f.canRead()) {
                    continue;
                }
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        int hash = line.indexOf('#');
                        if (hash >= 0) {
                            line = line.substring(0, hash);
                        }
                        line = line.trim();
                        if (!line.isEmpty()) {
                            merged.add(line);
                        }
                    }
                } catch (Throwable e) {
                    log(LOG_ERROR, TAG, "config read ERROR (" + path + "): " + e);
                }
            }

            lastSignature = signature.toString();
            cachedTargets = merged;
            log(LOG_INFO, TAG, "target games = " + merged);
        } catch (Throwable e) {
            log(LOG_ERROR, TAG, "config load ERROR: " + e);
        }
        return cachedTargets;
    }

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

            if (getTargetGames().contains(packageName)) {
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
                Set<String> existing = new HashSet<>();

                for (Object item : source) {
                    if (item == null) {
                        continue;
                    }
                    String pkg = (String) getPkgName.invoke(item);
                    existing.add(pkg);

                    if (PEER_GAME.equals(pkg)) {
                        peer = item;
                    }
                }

                // 목록에 아직 없는 대상 게임만 추가
                List<String> missing = new ArrayList<>();
                for (String target : getTargetGames()) {
                    if (!existing.contains(target)) {
                        missing.add(target);
                    }
                }
                if (missing.isEmpty()) {
                    return result;
                }

                // BGMI 엔티티가 있으면 값을 복제, 없으면 기본 JSON 값(0, -1) 사용
                int state = 0;
                int brightness = -1;
                if (peer != null) {
                    state = (Integer) getState.invoke(peer);
                    brightness = (Integer) getBrightness.invoke(peer);
                }

                List<Object> merged =
                        new ArrayList<>(source.size() + missing.size());
                merged.addAll(source);
                for (String target : missing) {
                    merged.add(ctor.newInstance(target, state, brightness));
                }

                if (!logged) {
                    logged = true;
                    log(LOG_INFO, TAG,
                            "ADFR list: injected " + missing
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

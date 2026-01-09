package com.github.catvod.spider;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.lang.reflect.Field;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Init {

    private final ExecutorService executor;
    private final Handler handler;
    private Application app;

    private volatile Socket healthSocket;
    private volatile boolean isRunning = false;
    private volatile Thread healthCheckThread;
    private volatile boolean isFirstHealthCheck; // 新增：用于标记是否是首次健康检查

    private static final int HEALTH_PORT = 5575;
    private static final String HEALTH_PATH = "/health";
    private static final int HEALTH_TIMEOUT = 3000; // 3秒超时
    private static final long HEALTH_INTERVAL = 1000; // 1秒间隔


    private static class Loader {
        static volatile Init INSTANCE = new Init();
    }

    public static Init get() {
        return Loader.INSTANCE;
    }

    public Init() {
        this.handler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newFixedThreadPool(5);
    }

    public static Application context() {
        return get().app;
    }

    public static void init(Context context) {
        get().app = ((Application) context);
        Proxy.init();

        initGoProxy(context);
        
        DanmakuSpider.doInitWork(context,"");

        // 启动Hook监控
        DanmakuScanner.startHookMonitor();
        DanmakuSpider.log("Leo弹幕监控已启动");
    }

    private static void initGoProxy(Context context) {
        SpiderDebug.log("自定義爬蟲代碼載入成功！");

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            get().handler.post(() -> Toast.makeText(context, "安卓版本过低，无法启动goProxy", Toast.LENGTH_SHORT).show());
            return;
        }

        List<String> abs = Arrays.asList(Build.SUPPORTED_ABIS);
        execute(() -> {
            try {
                String goProxy = abs.contains("arm64-v8a") ? "goProxy-arm64" : "goProxy-arm";
                File file = new File(context.getCacheDir(), goProxy);

                Process exec = Runtime.getRuntime().exec("/system/bin/sh");
                try (DataOutputStream dos = new DataOutputStream(exec.getOutputStream())) {
                    if (!file.exists()) {
                        if (!file.createNewFile()) throw new Exception("创建文件失败 " + file);
                        try (FileOutputStream fos = new FileOutputStream(file);
                             InputStream is = Objects.requireNonNull(get().getClass().getClassLoader()).getResourceAsStream("assets/" + goProxy)) {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = is.read(buffer)) != -1) fos.write(buffer, 0, read);
                        }
                        if (!file.setExecutable(true)) throw new Exception(goProxy + " setExecutable is false");
                        dos.writeBytes("chmod 777 " + file.getAbsolutePath() + "\n");
                        dos.flush();
                    }

                    SpiderDebug.log("启动 " + file);
                    dos.writeBytes("kill $(ps -ef | grep '" + goProxy + "' | grep -v grep | awk '{print $2}')\n");
                    dos.flush();
                    dos.writeBytes("nohup " + file.getAbsolutePath() + "\n");
                    dos.flush();
                    dos.writeBytes("exit\n");
                    dos.flush();

                    // **优化点**: 创建一个Runnable，在首次健康检查成功后显示Toast
                    Runnable onFirstSuccess = () -> Toast.makeText(context, "加载：" + goProxy + "成功", Toast.LENGTH_SHORT).show();
                    
                    // 启动心跳检查，并传入成功回调
                    get().startHealthCheck(context, onFirstSuccess);
                }

                try (InputStream is = exec.getInputStream()) {
                    log(is, "input");
                }
                try (InputStream is = exec.getErrorStream()) {
                    log(is, "err");
                }
                SpiderDebug.log("exe ret " + exec.waitFor());
            } catch (Exception ex) {
                SpiderDebug.log("启动 goProxy异常：" + ex.getMessage());
                get().handler.post(() -> Toast.makeText(context, abs + "启动 goProxy异常：" + ex.getMessage(), Toast.LENGTH_SHORT).show());
                // 即使启动失败，也要尝试启动健康检查，以便检测服务状态并自动重启
                try {
                    // 启动失败时，不传递成功回调
                    get().startHealthCheck(context, null);
                } catch (Exception healthEx) {
                    SpiderDebug.log("Failed to start health check: " + healthEx.getMessage());
                }
            }
        });
    }

    /**
     * 启动健康检查线程
     * @param context
     * @param onFirstSuccess 首次健康检查成功时的回调任务
     */
    private void startHealthCheck(Context context, Runnable onFirstSuccess) {
        if (isRunning && healthCheckThread != null && healthCheckThread.isAlive()) {
            stopHealthCheck();
        }
        
        isRunning = true;
        isFirstHealthCheck = true; // 重置首次检查标记

        healthCheckThread = new Thread(() -> {
            while (isRunning) {
                boolean isHealthy = false;
                try {
                    JsonObject json = new Gson().fromJson(OkHttp.string("http://127.0.0.1:5575/health"), JsonObject.class);
                    if (json != null && json.has("status") && json.get("status").getAsString().equals("healthy")) {
                        SpiderDebug.log("Health check passed");
                        isHealthy = true;
                    } else {
                        SpiderDebug.log("Health check status not healthy");
                    }
                } catch (Exception e) {
                    SpiderDebug.log("Error during health check: " + e.getMessage());
                }

                if (isHealthy) {
                    // **优化点**: 如果是首次检查成功，并且有回调任务，则执行它
                    if (isFirstHealthCheck && onFirstSuccess != null) {
                        handler.post(onFirstSuccess);
                        isFirstHealthCheck = false; // 不再是首次
                    }
                } else {
                    closeHealthSocket();
                    try {
                        initGoProxy(context);
                        SpiderDebug.log("Health check failed, restarting goProxy");
                        Thread.sleep(3000);
                    } catch (Exception restartEx) {
                        SpiderDebug.log("Failed to restart goProxy: " + restartEx.getMessage());
                    }
                }
                
                try {
                    Thread.sleep(HEALTH_INTERVAL);
                } catch (InterruptedException ie) {
                    SpiderDebug.log("Health check thread interrupted");
                    break;
                }
            }
            closeHealthSocket();
            SpiderDebug.log("Health check thread stopped");
        });

        healthCheckThread.start();
        SpiderDebug.log("Health check thread started");
    }

    private void closeHealthSocket() {
        try {
            if (healthSocket != null && !healthSocket.isClosed()) {
                healthSocket.close();
            }
        } catch (IOException e) {
            SpiderDebug.log("Error closing health socket: " + e.getMessage());
        } finally {
            healthSocket = null;
        }
    }

    public static void stopHealthCheck() {
        Init instance = get();
        instance.isRunning = false;
        if (instance.healthCheckThread != null && instance.healthCheckThread.isAlive()) {
            instance.healthCheckThread.interrupt();
        }
        instance.closeHealthSocket();
        SpiderDebug.log("Health check stopped");
    }


    public static void log(InputStream stream, String type) throws IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
            String readLine;
            while ((readLine = br.readLine()) != null) {
                SpiderDebug.log(type + ": " + readLine);
            }
        }
    }

    public static void execute(Runnable runnable) {
        get().executor.execute(runnable);
    }

    public static void run(Runnable runnable) {
        get().handler.post(runnable);
    }

    public static void run(Runnable runnable, int delay) {
        get().handler.postDelayed(runnable, delay);
    }

    public static void checkPermission() {
        try {
            Activity activity = Init.getActivity();
            if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
            if (activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
                return;
            activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 9999);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Activity getActivity() throws Exception {
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
        Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
        activitiesField.setAccessible(true);
        Map<?, ?> activities = (Map<?, ?>) activitiesField.get(activityThread);
        for (Object activityRecord : activities.values()) {
            Class<?> activityRecordClass = activityRecord.getClass();
            Field pausedField = activityRecordClass.getDeclaredField("paused");
            pausedField.setAccessible(true);
            if (!pausedField.getBoolean(activityRecord)) {
                Field activityField = activityRecordClass.getDeclaredField("activity");
                activityField.setAccessible(true);
                return (Activity) activityField.get(activityRecord);
            }
        }
        return null;
    }

    public static void post(Runnable runnable) {
        get().handler.post(runnable);
    }

    public static void post(Runnable runnable, int delay) {
        get().handler.postDelayed(runnable, delay);
    }
}

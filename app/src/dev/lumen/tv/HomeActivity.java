package dev.lumen.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.tv.TvContract;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The home screen activity: hosts HomeView, loads apps and shelf content in the background, launches things. */
public final class HomeActivity extends Activity implements HomeView.Host {
    private HomeView view;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private boolean resumed;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            view.tick();
            main.postDelayed(this, 30_000);
        }
    };
    private final Runnable reloadShelf = this::loadShelf;

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { loadApps(); }
    };

    private final ContentObserver programs = new ContentObserver(main) {
        @Override public void onChange(boolean self) {
            main.removeCallbacks(reloadShelf);
            main.postDelayed(reloadShelf, 1500);  // apps often write several rows at once
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        view = new HomeView(this, this);
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(view);
        view.requestFocus();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);

        if (!ShelfStore.allowed(this)) requestPermissions(new String[]{ShelfStore.PERMISSION}, 1);
        else observePrograms();
    }

    private void observePrograms() {
        try {
            getContentResolver().registerContentObserver(TvContract.WatchNextPrograms.CONTENT_URI, true, programs);
            getContentResolver().registerContentObserver(TvContract.PreviewPrograms.CONTENT_URI, true, programs);
        } catch (RuntimeException ignored) { }
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (ShelfStore.allowed(this)) { observePrograms(); loadShelf(); }
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        loadApps();
        loadShelf();
        main.removeCallbacks(tick);
        main.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        resumed = false;
        main.removeCallbacks(tick);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (resumed) view.reset();  // Home pressed while already home
    }

    @Override protected void onDestroy() {
        unregisterReceiver(packages);
        getContentResolver().unregisterContentObserver(programs);
        bg.shutdownNow();
        super.onDestroy();
    }

    private void loadApps() {
        final float scale = getResources().getDisplayMetrics().widthPixels / Ui.W;
        bg.execute(() -> {
            AppStore a = new AppStore(this, scale).load(this);
            main.post(() -> view.setApps(a));
        });
    }

    private void loadShelf() {
        bg.execute(() -> {
            ShelfStore sh = new ShelfStore().load(this);
            main.post(() -> view.setShelf(sh));
        });
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (view.onKey(e.getKeyCode(), e)) return true;
        return super.dispatchKeyEvent(e);
    }

    // ---- HomeView.Host ----

    @Override public void launch(AppStore.App app) {
        try { startActivity(app.launchIntent()); } catch (ActivityNotFoundException | SecurityException ignored) { }
    }

    @Override public void appInfo(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) { }
    }

    @Override public void reloadApps() { loadApps(); }

    @Override public void open(ShelfStore.Item item, AppStore.App fallback) {
        try {
            Intent i = Intent.parseUri(item.intentUri, Intent.URI_INTENT_SCHEME);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            if (fallback != null) launch(fallback);
        }
    }
}

package com.nick.tvlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.Toast;

import java.util.List;

public class LauncherActivity extends Activity {
    private AppRepository repo;
    private AppAdapter adapter;
    private GridView grid;
    private boolean showHidden;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reload();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);
        repo = new AppRepository(this);
        adapter = new AppAdapter(this);

        grid = findViewById(R.id.grid);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> launch(adapter.getItem(position)));
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            showOptions(adapter.getItem(position));
            return true;
        });
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private View last;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (last != null) last.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                view.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start();
                last = view;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (last != null) last.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                last = null;
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
        grid.requestFocus();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        super.onDestroy();
    }

    /** Pressing Home while already home jumps back to the first tile. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        showHidden = false;
        reload();
        grid.setSelection(0);
    }

    @Override
    public void onBackPressed() {
        // This is the home screen; there is nowhere to go back to.
        if (showHidden) {
            showHidden = false;
            reload();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Menu (≡) button opens the options for the focused tile.
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            Object item = grid.getSelectedItem();
            if (item != null) showOptions((AppEntry) item);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void reload() {
        List<AppEntry> apps = repo.loadApps(showHidden);
        adapter.setApps(apps);
        findViewById(R.id.hidden_banner).setVisibility(showHidden ? View.VISIBLE : View.GONE);
    }

    private void launch(AppEntry app) {
        try {
            startActivity(app.launchIntent);
            if (!AppRepository.SETTINGS_PKG.equals(app.packageName)) {
                repo.recordLaunch(app.packageName);
            }
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, getString(R.string.launch_failed, app.label),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showOptions(AppEntry app) {
        if (AppRepository.SETTINGS_PKG.equals(app.packageName)) {
            showHidden = !showHidden;
            reload();
            return;
        }
        boolean isInput = app.packageName.startsWith(AppRepository.INPUT_PREFIX);
        String[] options = {
                getString(app.pinned ? R.string.unpin : R.string.pin),
                getString(repo.isHidden(app.packageName) ? R.string.unhide : R.string.hide),
                // Inputs are not packages, so they have no app info page.
                getString(isInput ? R.string.input_no_info : R.string.app_info),
                getString(showHidden ? R.string.hide_hidden_apps : R.string.show_hidden_apps),
        };
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(app.label)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            repo.togglePinned(app.packageName);
                            break;
                        case 1:
                            repo.toggleHidden(app.packageName);
                            break;
                        case 2:
                            if (isInput) return;
                            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", app.packageName, null)));
                            return;
                        case 3:
                            showHidden = !showHidden;
                            break;
                    }
                    reload();
                })
                .show();
    }
}

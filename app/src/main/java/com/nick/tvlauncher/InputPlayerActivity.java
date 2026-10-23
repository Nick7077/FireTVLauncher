package com.nick.tvlauncher;

import android.app.Activity;
import android.media.tv.TvContract;
import android.media.tv.TvView;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

/**
 * Shows an HDMI/composite input full screen. Amazon's own input player needs a
 * signature permission, so we play the passthrough input ourselves through a TvView.
 */
public class InputPlayerActivity extends Activity {
    static final String EXTRA_INPUT_ID = "input_id";
    private static final String TAG = "InputPlayer";

    private TvView tvView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tvView = new TvView(this);
        setContentView(tvView);
        tvView.setCallback(new TvView.TvInputCallback() {
            @Override
            public void onConnectionFailed(String inputId) {
                Log.w(TAG, "Connection failed: " + inputId);
                Toast.makeText(InputPlayerActivity.this, R.string.input_failed,
                        Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onVideoAvailable(String inputId) {
                Log.i(TAG, "Video available: " + inputId);
            }

            @Override
            public void onVideoUnavailable(String inputId, int reason) {
                Log.i(TAG, "Video unavailable: " + inputId + " reason=" + reason);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        String inputId = getIntent().getStringExtra(EXTRA_INPUT_ID);
        if (inputId == null) {
            finish();
            return;
        }
        Log.i(TAG, "Tuning " + inputId);
        tvView.tune(inputId, TvContract.buildChannelUriForPassthroughInput(inputId));
    }

    @Override
    protected void onStop() {
        // Release the input so it does not keep playing behind the launcher.
        tvView.reset();
        super.onStop();
    }
}

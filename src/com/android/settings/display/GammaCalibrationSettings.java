//Vmax

package com.android.settings.display;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.settings.SettingsEnums;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.Log;
import android.util.Spline;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.internal.display.BrightnessSynchronizer;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.widget.LayoutPreference;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntConsumer;

/** Draft calibration editor with a window-local brightness override and daemon-owned live preview. */
public class GammaCalibrationSettings extends SettingsPreferenceFragment {
    private static final String TAG = "GammaCalibration";
    private static final int IMPORT = 1;
    private static final int EXPORT = 2;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mIo = Executors.newSingleThreadExecutor();
    private final TreeMap<Integer, Integer> mPoints = new TreeMap<>();
    private GammaCalibrationSession mSession;
    private GammaCalibrationPattern mPattern;
    private SeekBar mBrightness;
    private Button mPercentLabel;
    private Button mValueLabel;
    private Button mAdd;
    private TextView mSource;
    private View mControls;
    private PreferenceCategory mPointList;
    private int mPercent;
    private int mValue;
    private int mRaw = -1;
    private int mMax;
    private GammaCalibrationBrightness mBrightnessMapping;
    private int mExpectedRaw;
    private int mBrightnessRequest;
    private boolean mLoaded;
    private boolean mDirty;
    private boolean mReady;
    private int mPreviewValue = Integer.MIN_VALUE;
    private volatile int mPreviewRequest;
    private boolean mPreviewConfirmed;
    private boolean mHasPreview;
    private boolean mHasBrightness;
    private boolean mDragging;
    private boolean mAdjusted;
    private boolean mRestoreAdjusted;
    private int mRestoreValue;
    private float mPreviousBrightness;
    private boolean mOverridingBrightness;
    private long mSettleUntil;
    private AlertDialog mDialog;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!getResources().getBoolean(R.bool.config_showGammaCorrection)) {
            finish();
            return;
        }
        addPreferencesFromResource(R.xml.gamma_calibration_settings);
        setHasOptionsMenu(true);
        LayoutPreference controls = findPreference("calibration_controls");
        mPattern = controls.findViewById(R.id.calibration_pattern);
        mControls = (View) mPattern.getParent();
        mBrightness = controls.findViewById(R.id.calibration_brightness);
        mPercentLabel = controls.findViewById(R.id.calibration_percent);
        mValueLabel = controls.findViewById(R.id.calibration_value);
        mSource = controls.findViewById(R.id.calibration_source);
        mAdd = controls.findViewById(R.id.calibration_add);
        mPointList = findPreference("calibration_points");
        try {
            if (state != null && state.containsKey("points")) {
                mPoints.putAll(GammaCalibration.parse(state.getString("points"), false));
                mPercent = state.getInt("percent");
                mValue = state.getInt("value");
                mDirty = state.getBoolean("dirty");
                mAdjusted = state.getBoolean("adjusted");
                mHasBrightness = state.getBoolean("hasBrightness");
            } else {
                mPoints.putAll(GammaCalibration.load().points);
            }
            mLoaded = true;
        } catch (IOException e) {
            error(R.string.gamma_calibration_error, e);
        }
        controls.findViewById(R.id.calibration_brightness_minus).setOnClickListener(
                v -> stepBrightness(-1));
        controls.findViewById(R.id.calibration_brightness_plus).setOnClickListener(
                v -> stepBrightness(1));
        mPercentLabel.setOnClickListener(v -> numberDialog(R.string.gamma_calibration_brightness,
                mPercent, 0, 100, this::setBrightness));
        controls.findViewById(R.id.calibration_value_minus).setOnClickListener(
                v -> adjustValue(mValue - 1));
        controls.findViewById(R.id.calibration_value_plus).setOnClickListener(
                v -> adjustValue(mValue + 1));
        mValueLabel.setOnClickListener(v -> numberDialog(R.string.gamma_calibration_value_title,
                mValue, -255, 255, this::adjustValue));
        mBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser) setBrightness(value);
            }
            @Override
            public void onStartTrackingTouch(SeekBar bar) { mDragging = true; }
            @Override
            public void onStopTrackingTouch(SeekBar bar) { mDragging = false; }
        });
        mAdd.setOnClickListener(v -> {
            mPoints.put(mPercent, mValue);
            mDirty = true;
            mAdjusted = false;
            refreshPoints();
            refreshControls();
        });
        controls.findViewById(R.id.calibration_save).setOnClickListener(v -> save());
        controls.findViewById(R.id.calibration_cancel).setOnClickListener(v -> discard());
        findPreference("calibration_import").setOnPreferenceClickListener(p -> {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .setType("text/*").addCategory(Intent.CATEGORY_OPENABLE), IMPORT);
            return true;
        });
        findPreference("calibration_export").setOnPreferenceClickListener(p -> {
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_TITLE, "gamma-calibration.txt"), EXPORT);
            return true;
        });
        findPreference("calibration_reset").setOnPreferenceClickListener(p -> {
            mDialog = new AlertDialog.Builder(getContext())
                    .setTitle(R.string.gamma_calibration_reset)
                    .setMessage(R.string.gamma_calibration_reset_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.gamma_calibration_reset, (dialog, which) -> reset())
                    .show();
            return true;
        });
        requireActivity().getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() { discard(); }
                });
        refreshPoints();
        refreshControls();
    }

    @Override
    public int getMetricsCategory() { return SettingsEnums.DISPLAY; }

    @Override
    public void onResume() {
        super.onResume();
        if (!mLoaded) return;
        mPreviousBrightness = requireActivity().getWindow().getAttributes().screenBrightness;
        GammaCalibrationSession session = new GammaCalibrationSession();
        mSession = session;
        mPreviewValue = Integer.MIN_VALUE;
        mPreviewConfirmed = false;
        mHasPreview = false;
        mIo.execute(() -> {
            try {
                int[] status = session.connect();
                mHandler.post(() -> {
                    if (mSession != session) return;
                    mMax = status[0];
                    float minimum = getResources().getFloat(com.android.internal.R.dimen
                            .config_screenBrightnessSettingMinimumFloat);
                    float maximum = getResources().getFloat(com.android.internal.R.dimen
                            .config_screenBrightnessSettingMaximumFloat);
                    if (minimum == -2f || maximum == -2f) {
                        minimum = BrightnessSynchronizer.brightnessIntToFloat(getResources()
                                .getInteger(com.android.internal.R.integer
                                        .config_screenBrightnessSettingMinimum));
                        maximum = BrightnessSynchronizer.brightnessIntToFloat(getResources()
                                .getInteger(com.android.internal.R.integer
                                        .config_screenBrightnessSettingMaximum));
                    }
                    mBrightnessMapping = new GammaCalibrationBrightness(mMax,
                            backlightMapping(minimum, maximum));
                    mReady = true;
                    if (mHasBrightness) {
                        mRestoreAdjusted = mAdjusted;
                        mRestoreValue = mValue;
                        setBrightness(mPercent);
                        if (mRestoreAdjusted) adjustValue(mRestoreValue);
                    } else {
                        updateStatus(status[1]);
                        mHasBrightness = true;
                        setBrightness(mPercent);
                    }
                    refreshControls();
                    pollLater(session);
                });
            } catch (IOException e) {
                sessionFailed(session, e);
            }
        });
    }

    private void pollLater(GammaCalibrationSession session) {
        mHandler.postDelayed(() -> {
            if (mSession != session) return;
            int request = mBrightnessRequest;
            mIo.execute(() -> {
                try {
                    int[] status = session.status();
                    mHandler.post(() -> {
                        if (mSession != session) return;
                        // Ignore reads queued before the latest tap and intermediate ramp values.
                        long now = SystemClock.uptimeMillis();
                        if (request == mBrightnessRequest && !mDragging
                                && now >= mSettleUntil
                                && (status[1] == mExpectedRaw || now >= mSettleUntil + 2400)) {
                            updateStatus(status[1]);
                        }
                        refreshControls();
                        pollLater(session);
                    });
                } catch (IOException e) {
                    sessionFailed(session, e);
                }
            });
        }, 250);
    }

    private void sessionFailed(GammaCalibrationSession session, IOException e) {
        session.close();
        mHandler.post(() -> {
            if (mSession != session) return;
            mSession = null;
            mReady = false;
            restoreBrightness();
            refreshControls();
            error(R.string.gamma_calibration_session_error, e);
        });
    }

    @Override
    public void onPause() {
        mReady = false;
        // Hide the patches until the new session has applied the draft preview on return.
        if (mPattern != null) refreshControls();
        GammaCalibrationSession session = mSession;
        mSession = null;
        if (session != null) session.close();
        mHandler.removeCallbacksAndMessages(null);
        restoreBrightness();
        super.onPause();
    }

    private void restoreBrightness() {
        if (!mOverridingBrightness) return;
        WindowManager.LayoutParams params = requireActivity().getWindow().getAttributes();
        params.screenBrightness = mPreviousBrightness;
        requireActivity().getWindow().setAttributes(params);
        mOverridingBrightness = false;
    }

    @Override
    public void onDestroy() {
        if (mDialog != null) mDialog.dismiss();
        mIo.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (!mLoaded) return;
        state.putString("points", GammaCalibration.format(mPoints));
        state.putInt("percent", mPercent);
        state.putInt("value", mValue);
        state.putBoolean("dirty", mDirty);
        state.putBoolean("adjusted", mAdjusted);
        state.putBoolean("hasBrightness", mHasBrightness);
    }

    private void stepBrightness(int direction) {
        if (!mReady) return;
        // Skip percentages the HAL cannot represent, without reversing direction or getting stuck.
        for (int percent = mPercent + direction; percent >= 0 && percent <= 100;
                percent += direction) {
            int actual = mBrightnessMapping.percent(
                    mBrightnessMapping.raw(mBrightnessMapping.level(percent)));
            if ((actual - mPercent) * direction > 0) {
                setBrightness(percent);
                return;
            }
        }
    }

    private void setBrightness(int percent) {
        if (!mReady) return;
        int level = mBrightnessMapping.level(percent);
        int target = mBrightnessMapping.raw(level);
        mExpectedRaw = target;
        mBrightnessRequest++;
        mPercent = mBrightnessMapping.percent(target);
        WindowManager.LayoutParams params = requireActivity().getWindow().getAttributes();
        params.screenBrightness = mBrightnessMapping.windowBrightness(level);
        requireActivity().getWindow().setAttributes(params);
        mOverridingBrightness = true;
        mSettleUntil = SystemClock.uptimeMillis() + 600;
        mRaw = -1; // Recompute from actual panel readback after the brightness ramp settles.
        mAdjusted = false;
        selectValue(target);
        refreshControls();
    }

    private void updateStatus(int raw) {
        // Readback initializes the selection, but must not clamp it to the panel limit.
        int percent = mHasBrightness ? mPercent : mBrightnessMapping.percent(raw);
        if (raw != mRaw) {
            mRaw = raw;
            boolean keepAdjustment = mAdjusted && percent == mPercent;
            mPercent = percent;
            // Do not overwrite a value adjusted while the panel brightness was settling.
            if (!keepAdjustment) {
                mAdjusted = false;
                selectValue(mHasBrightness ? mExpectedRaw : raw);
            }
            if (mRestoreAdjusted) {
                mValue = mRestoreValue;
                mAdjusted = true;
                mRestoreAdjusted = false;
            }
        }
    }

    private void selectValue(int raw) {
        Integer exact = mPoints.get(mPercent);
        mValue = exact != null ? exact : GammaCalibration.interpolate(mPoints, raw, mMax);
    }

    private void adjustValue(int value) {
        mValue = Math.max(-255, Math.min(255, value));
        mAdjusted = true;
        refreshControls();
    }

    private void requestPreview() {
        GammaCalibrationSession session = mSession;
        int value = mValue;
        mPreviewValue = value;
        mPreviewConfirmed = false;
        int request = ++mPreviewRequest;
        mIo.execute(() -> {
            // Coalesce rapid taps instead of applying obsolete values queued behind I/O.
            if (request != mPreviewRequest) return;
            try {
                session.preview(value);
                mHandler.post(() -> {
                    if (mSession != session || request != mPreviewRequest) return;
                    mPreviewConfirmed = true;
                    mHasPreview = true;
                    refreshControls();
                });
            } catch (IOException e) {
                sessionFailed(session, e);
            }
        });
    }

    private void refreshControls() {
        if (mPattern == null) return;
        if (mReady && mPreviewValue != mValue) requestPreview();
        int[] controls = {R.id.calibration_percent, R.id.calibration_brightness,
                R.id.calibration_brightness_minus, R.id.calibration_brightness_plus,
                R.id.calibration_value, R.id.calibration_value_minus, R.id.calibration_value_plus};
        for (int id : controls) mControls.findViewById(id).setEnabled(mReady);
        mControls.findViewById(R.id.calibration_save).setEnabled(mLoaded && !mPoints.isEmpty());
        mPercentLabel.setText(getString(R.string.gamma_calibration_percent, mPercent));
        mValueLabel.setText(Integer.toString(mValue));
        mValueLabel.setContentDescription(getString(
                R.string.gamma_calibration_value_description, mValue));
        if (!mDragging) mBrightness.setProgress(mPercent);
        mPattern.setVisibility(mReady && mHasPreview ? View.VISIBLE : View.INVISIBLE);
        mAdd.setEnabled(mReady && mPreviewConfirmed && !mDragging && mRaw >= 0
                && SystemClock.uptimeMillis() >= mSettleUntil);
        mAdd.setText(mPoints.containsKey(mPercent)
                ? R.string.gamma_calibration_update : R.string.gamma_calibration_add);
        mSource.setText(mValue < 0 ? R.string.gamma_calibration_negative
                : mAdjusted ? R.string.gamma_calibration_adjusted
                : mPoints.containsKey(mPercent) ? R.string.gamma_calibration_saved_point
                : R.string.gamma_calibration_interpolated);
        findPreference("calibration_export").setEnabled(mLoaded && !mPoints.isEmpty());
    }

    private void refreshPoints() {
        mPointList.removeAll();
        for (Map.Entry<Integer, Integer> entry : mPoints.entrySet()) {
            int percent = entry.getKey();
            int value = entry.getValue();
            Preference point = new Preference(getContext());
            point.setTitle(getString(R.string.gamma_calibration_point, percent, value));
            point.setOnPreferenceClickListener(p -> {
                mDialog = new AlertDialog.Builder(getContext())
                        .setTitle(point.getTitle())
                        .setItems(new CharSequence[]{getText(R.string.gamma_calibration_edit_point),
                                getText(R.string.gamma_calibration_remeasure),
                                getText(R.string.gamma_calibration_delete)}, (dialog, which) -> {
                            if (which == 0) {
                                numberDialog(R.string.gamma_calibration_value_title, value,
                                        -255, 255, edited -> {
                                            mPoints.put(percent, edited);
                                            draftChanged();
                                        });
                            } else if (which == 1) {
                                setBrightness(percent);
                                getListView().smoothScrollToPosition(0);
                            } else {
                                mPoints.remove(percent);
                                draftChanged();
                            }
                        }).show();
                return true;
            });
            mPointList.addPreference(point);
        }
    }

    private void draftChanged() {
        mDirty = true;
        mAdjusted = false;
        selectValue(mHasBrightness ? mExpectedRaw : Math.max(0, mRaw));
        refreshPoints();
        refreshControls();
    }

    private void numberDialog(int title, int value, int min, int max, IntConsumer result) {
        EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | (min < 0
                ? InputType.TYPE_NUMBER_FLAG_SIGNED : 0));
        input.setSingleLine(true);
        input.setText(Integer.toString(value));
        input.selectAll();
        input.setContentDescription(getString(R.string.gamma_calibration_range, min, max));
        mDialog = new AlertDialog.Builder(getContext()).setTitle(title).setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null).create();
        AlertDialog dialog = mDialog;
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    try {
                        int entered = Integer.parseInt(input.getText().toString());
                        if (entered < min || entered > max) throw new NumberFormatException();
                        result.accept(entered);
                        dialog.dismiss();
                    } catch (NumberFormatException e) {
                        input.setError(getString(R.string.gamma_calibration_range, min, max));
                    }
                }));
        dialog.show();
    }

    private void save() {
        try {
            if (mDirty) GammaCalibration.save(mPoints);
            mDirty = false;
            finish();
        } catch (IOException e) {
            error(R.string.gamma_calibration_error, e);
        }
    }

    private void reset() {
        try {
            TreeMap<Integer, Integer> defaults = GammaCalibration.defaults();
            GammaCalibration.reset();
            mPoints.clear();
            mPoints.putAll(defaults);
            mLoaded = true;
            draftChanged();
            mDirty = false;
        } catch (IOException e) {
            error(R.string.gamma_calibration_error, e);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            discard();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void discard() {
        if (!mDirty) {
            finish();
            return;
        }
        mDialog = new AlertDialog.Builder(getContext())
                .setMessage(R.string.gamma_calibration_discard)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.gamma_calibration_discard_action,
                        (dialog, which) -> finish()).show();
    }

    @Override
    public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return;
        try {
            if (request == IMPORT) {
                try (InputStream in = getContentResolver().openInputStream(data.getData())) {
                    if (in == null) throw new IOException("No input stream");
                    TreeMap<Integer, Integer> points = GammaCalibration.parse(
                            GammaCalibration.readText(in), true);
                    mPoints.clear();
                    mPoints.putAll(points);
                    mLoaded = true;
                    draftChanged();
                }
            } else if (request == EXPORT) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData(), "wt")) {
                    if (out == null) throw new IOException("No output stream");
                    out.write(GammaCalibration.format(mPoints).getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (IOException | SecurityException e) {
            error(request == IMPORT ? R.string.gamma_calibration_import_error
                    : R.string.gamma_calibration_error, e);
        }
    }

    /** Mirror DisplayDeviceConfig's brightness-to-backlight spline for the window override. */
    private DoubleUnaryOperator backlightMapping(float minimum, float maximum) {
        Resources res = getResources();
        DoubleUnaryOperator linear = GammaCalibrationBrightness.linear(minimum, maximum);
        if (!res.getBoolean(com.android.internal.R.bool.config_screenBrightnessLinearInNits)) {
            return linear;
        }
        int[] levels = res.getIntArray(com.android.internal.R.array.config_screenBrightnessBacklight);
        TypedArray array = res.obtainTypedArray(com.android.internal.R.array.config_screenBrightnessNits);
        float[] rawNits = new float[array.length()];
        for (int i = 0; i < rawNits.length; i++) rawNits[i] = array.getFloat(i, 0f);
        array.recycle();
        if (levels.length < 2 || levels.length != rawNits.length) return linear;
        float[] raw = new float[levels.length];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = BrightnessSynchronizer.brightnessIntToFloat(levels[i]);
        }
        // Clamp the table to [minimum, maximum] like constrainNitsAndBacklightArrays().
        int start = 0;
        while (start < raw.length - 2 && raw[start + 1] <= minimum) start++;
        float[] backlight = new float[raw.length];
        float[] nits = new float[raw.length];
        int count = 0;
        for (int i = start; i < raw.length; i++) {
            boolean last = raw[i] >= maximum || i == raw.length - 1;
            float value = count == 0 ? Math.max(raw[i], minimum)
                    : last ? Math.min(raw[i], maximum) : raw[i];
            int segment = count == 0 ? i : last ? i - 1 : -1;
            backlight[count] = value;
            nits[count] = segment < 0 ? rawNits[i] : rawNits[segment]
                    + (rawNits[segment + 1] - rawNits[segment]) * (value - raw[segment])
                    / (raw[segment + 1] - raw[segment]);
            count++;
            if (last) break;
        }
        float[] brightness = new float[count];
        for (int i = 0; i < count; i++) {
            brightness[i] = (nits[i] - nits[0]) / (nits[count - 1] - nits[0]);
        }
        Spline spline = Spline.createSpline(brightness, Arrays.copyOf(backlight, count));
        return value -> spline.interpolate((float) value);
    }

    private void error(int message, Exception e) {
        Log.e(TAG, "Calibration operation failed", e);
        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
    }
}

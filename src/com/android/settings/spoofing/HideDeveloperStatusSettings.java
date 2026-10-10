//Vmax
package com.android.settings.spoofing;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.IconDrawableFactory;
import android.view.View;
import android.widget.SearchView;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.utils.ThreadUtils;
import com.android.settingslib.widget.AppSwitchPreference;
import com.android.settingslib.widget.TopIntroPreference;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class HideDeveloperStatusSettings extends SettingsPreferenceFragment {
    private final List<AppSwitchPreference> mApps = new ArrayList<>();
    private Set<String> mSelected;
    private Preference mNoMatch;
    private String mQuery = "";

    private static final class AppEntry {
        final String packageName;
        final String label;
        final Drawable icon;

        AppEntry(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    static Set<String> getSelectedApps(Context context) {
        String saved = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.HIDE_DEVELOPER_STATUS);
        Set<String> apps = new TreeSet<>();
        if (!TextUtils.isEmpty(saved)) apps.addAll(Arrays.asList(saved.split(",")));
        apps.remove("");
        return apps;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mSelected = getSelectedApps(getContext());
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getPrefContext());
        TopIntroPreference intro = new TopIntroPreference(getPrefContext());
        intro.setTitle(R.string.hide_developer_status_intro);
        screen.addPreference(intro);
        mNoMatch = new Preference(getPrefContext());
        mNoMatch.setTitle(R.string.hide_developer_status_no_match);
        mNoMatch.setSelectable(false);
        mNoMatch.setVisible(false);
        screen.addPreference(mNoMatch);
        setPreferenceScreen(screen);
        loadApps();
    }

    @Override
    public void onViewCreated(View view, Bundle icicle) {
        super.onViewCreated(view, icicle);
        SearchView search = setPinnedHeaderView(R.layout.hide_developer_status_search)
                .findViewById(R.id.hide_developer_status_search);
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                search.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                mQuery = query.trim().toLowerCase(Locale.ROOT);
                filterApps();
                return true;
            }
        });
    }

    private void loadApps() {
        Context context = getPrefContext();
        Set<String> selected = new TreeSet<>(mSelected);
        ThreadUtils.postOnBackgroundThread(() -> {
            PackageManager manager = context.getPackageManager();
            IconDrawableFactory icons = IconDrawableFactory.newInstance(context);
            List<AppEntry> apps = new ArrayList<>();
            for (ApplicationInfo app : manager.getInstalledApplications(0)) {
                if (!UserHandle.isApp(app.uid)) continue;
                apps.add(new AppEntry(app.packageName, app.loadLabel(manager).toString(),
                        icons.getBadgedIcon(app)));
            }
            // Selected apps first, then alphabetical
            Collator collator = Collator.getInstance();
            apps.sort(Comparator.comparing((AppEntry app) -> !selected.contains(app.packageName))
                    .thenComparing(app -> app.label, collator));
            ThreadUtils.postOnMainThread(() -> {
                if (!isAdded()) return;
                for (AppEntry app : apps) addApp(app);
                filterApps();
            });
        });
    }

    private void addApp(AppEntry app) {
        AppSwitchPreference preference = new AppSwitchPreference(getPrefContext());
        preference.setKey(app.packageName);
        preference.setPersistent(false);
        preference.setIcon(app.icon);
        preference.setTitle(app.label);
        preference.setSummary(app.packageName);
        preference.setChecked(mSelected.contains(app.packageName));
        preference.setOnPreferenceChangeListener((unused, value) -> {
            Set<String> selected = new TreeSet<>(mSelected);
            if ((Boolean) value) {
                selected.add(app.packageName);
            } else {
                selected.remove(app.packageName);
            }
            if (!Settings.Secure.putString(getContentResolver(),
                    Settings.Secure.HIDE_DEVELOPER_STATUS, String.join(",", selected))) {
                return false;
            }
            mSelected = selected;
            return true;
        });
        getPreferenceScreen().addPreference(preference);
        mApps.add(preference);
    }

    private void filterApps() {
        boolean anyMatch = false;
        for (AppSwitchPreference preference : mApps) {
            boolean match = mQuery.isEmpty()
                    || preference.getTitle().toString().toLowerCase(Locale.ROOT).contains(mQuery)
                    || preference.getKey().toLowerCase(Locale.ROOT).contains(mQuery);
            preference.setVisible(match);
            anyMatch |= match;
        }
        mNoMatch.setVisible(!mApps.isEmpty() && !anyMatch);
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_SYSTEM_CATEGORY;
    }
}

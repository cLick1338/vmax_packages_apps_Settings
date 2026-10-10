//Vmax
package com.android.settings.spoofing;

import android.app.settings.SettingsEnums;
import android.provider.Settings;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable
public class SpoofingSettings extends DashboardFragment {
    @Override
    public void onResume() {
        super.onResume();
        int count = HideDeveloperStatusSettings.getSelectedApps(getContext()).size();
        findPreference(Settings.Secure.HIDE_DEVELOPER_STATUS).setSummary(count == 0
                ? getString(R.string.hide_developer_status_summary)
                : getResources().getQuantityString(R.plurals.hide_developer_status_count,
                        count, count));
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_SYSTEM_CATEGORY;
    }

    @Override
    protected String getLogTag() {
        return "SpoofingSettings";
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.spoofing_settings;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.spoofing_settings);
}

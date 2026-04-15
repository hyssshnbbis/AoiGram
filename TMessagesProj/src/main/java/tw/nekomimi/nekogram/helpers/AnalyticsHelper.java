package tw.nekomimi.nekogram.helpers;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.HashMap;

import io.sentry.Breadcrumb;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.android.core.SentryAndroid;
import io.sentry.protocol.User;
import tw.nekomimi.nekogram.Extra;

public class AnalyticsHelper {
    private static SharedPreferences preferences;

    private static FirebaseAnalytics firebaseAnalytics;

    public static boolean sendBugReport = true;
    public static boolean analyticsDisabled = false;
    public static String userId = null;

    public static void start(Application application) {
        preferences = application.getSharedPreferences("nekoanalytics", Application.MODE_PRIVATE);

        analyticsDisabled = !Extra.FORCE_ANALYTICS && preferences.getBoolean("analyticsDisabled", false);
        sendBugReport = Extra.FORCE_ANALYTICS || preferences.getBoolean("sendBugReport", true);

        if (analyticsDisabled) {
            FileLog.d("Analytics: disabled by user");
            return;
        }

        userId = preferences.getString("userId", null);
        if (userId == null || userId.length() < 32) {
            userId = generateUserID();
            preferences.edit().putString("userId", userId).apply();
        }

        firebaseAnalytics = FirebaseAnalytics.getInstance(application);
        firebaseAnalytics.setAnalyticsCollectionEnabled(true);
        firebaseAnalytics.setUserId(userId);

        String dsn = Extra.SENTRY_DSN;

        boolean validDsn = false;
        if (dsn != null && !dsn.isEmpty() && !dsn.contains("example.com")) {
            try {
                java.net.URI uri = new java.net.URI(dsn);
                validDsn = uri.getHost() != null;
            } catch (Exception ignore) {
                validDsn = false;
            }
        }

        if (validDsn) {
            SentryAndroid.init(application, options -> {
                options.setDsn(dsn);
                options.setEnvironment(BuildConfig.BUILD_TYPE);
                options.setPrintUncaughtStackTrace(true);
                options.setSendDefaultPii(true);
                options.setEnableUserInteractionTracing(true);
                options.setAttachViewHierarchy(true);
                options.setEnableSystemEventBreadcrumbsExtras(true);
                options.setTracesSampleRate(0.01);
            });

            User user = new User();
            user.setId(userId);
            Sentry.setUser(user);

            FileLog.d("Sentry initialized");
        } else {
            FileLog.d("Sentry disabled: invalid DSN");
        }

        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("Analytics: userId = " + userId);
        }
    }

    private static String generateUserID() {
        return Utilities.generateRandomString(32);
    }

    public static void trackFragmentLifecycle(String lifecycle, BaseFragment fragment) {
        if (analyticsDisabled || fragment == null) return;
        var breadcrumb = new Breadcrumb();
        breadcrumb.setType("navigation");
        breadcrumb.setCategory("ui.fragment.lifecycle");
        breadcrumb.setLevel(SentryLevel.INFO);
        breadcrumb.setData("state", lifecycle);
        breadcrumb.setData("screen", getFragmentName(fragment));
        Sentry.addBreadcrumb(breadcrumb);
        if ("created".equals(lifecycle)) {
            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, null);
        }
    }

    private static String getFragmentName(BaseFragment fragment) {
        var canonicalName = fragment.getClass().getCanonicalName();
        return canonicalName != null ? canonicalName : fragment.getClass().getSimpleName();
    }

    public static void trackEvent(String event, HashMap<String, String> map) {
        if (analyticsDisabled) return;
        Bundle bundle = new Bundle();
        for (String key : map.keySet()) {
            bundle.putString(key, map.get(key));
        }
        firebaseAnalytics.logEvent(event, bundle);
    }

    public static boolean isSettingsAvailable() {
        return !Extra.FORCE_ANALYTICS;
    }

    public static void setAnalyticsDisabled() {
        AnalyticsHelper.analyticsDisabled = true;
        if (BuildConfig.DEBUG) return;
        FirebaseAnalytics.getInstance(ApplicationLoader.applicationContext).setAnalyticsCollectionEnabled(false);
        preferences.edit().putBoolean("analyticsDisabled", true).apply();
    }

    public static void toggleSendBugReport() {
        AnalyticsHelper.sendBugReport = !AnalyticsHelper.sendBugReport;
        if (BuildConfig.DEBUG) return;
        preferences.edit().putBoolean("sendBugReport", sendBugReport).apply();
    }
}

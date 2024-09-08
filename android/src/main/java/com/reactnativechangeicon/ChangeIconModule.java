package com.reactnativechangeicon;

import androidx.annotation.NonNull;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.ComponentName;
import android.os.Bundle;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.module.annotations.ReactModule;

import java.util.ArrayList;
import java.util.List;

@ReactModule(name = "ChangeIcon")
public class ChangeIconModule extends ReactContextBaseJavaModule implements Application.ActivityLifecycleCallbacks {
    public static final String NAME = "ChangeIcon";
    private final String packageName;
    private final List<String> classesToKill = new ArrayList<>();
    private Boolean iconChanged = false;
    private String componentClass = "";

    public ChangeIconModule(ReactApplicationContext reactContext, String packageName) {
        super(reactContext);
        this.packageName = packageName;
    }

    @Override
    @NonNull
    public String getName() {
        return NAME;
    }

    @ReactMethod
    public void getIcon(Promise promise) {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            promise.reject("ANDROID:ACTIVITY_NOT_FOUND");
            return;
        }

        final String activityName = activity.getComponentName().getClassName();

        if (activityName.endsWith("MainActivity")) {
            promise.resolve("Default");
            return;
        }
        String[] activityNameSplit = activityName.split("MainActivity");
        if (activityNameSplit.length != 2) {
            promise.reject("ANDROID:UNEXPECTED_COMPONENT_CLASS:" + this.componentClass);
            return;
        }
        promise.resolve(activityNameSplit[1]);
        return;
    }

    @ReactMethod
public void changeIcon(String iconName, Promise promise) {
    final Activity activity = getCurrentActivity();
    if (activity == null) {
        promise.reject("ANDROID:ACTIVITY_NOT_FOUND");
        return;
    }

    if (this.componentClass.isEmpty()) {
        this.componentClass = activity.getComponentName().getClassName();
    }

    final String newIconName = (iconName == null || iconName.isEmpty()) ? "Default" : iconName;
    final String activeClass = this.packageName + ".MainActivity" + newIconName;

    if (this.componentClass.equals(activeClass)) {
        promise.reject("ANDROID:ICON_ALREADY_USED:" + this.componentClass);
        return;
    }

    try {
        // Enable the new icon's activity
        activity.getPackageManager().setComponentEnabledSetting(
                new ComponentName(this.packageName, activeClass),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);

        // Disable the old icon's activity
        activity.getPackageManager().setComponentEnabledSetting(
                new ComponentName(this.packageName, this.componentClass),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);

        // Prepare the app restart intent
        Intent restartIntent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
        if (restartIntent != null) {
            restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(restartIntent);
        }

        // Finish the current activity to apply changes
        activity.finish();

        // Add the old icon class to the list of classes to disable
        this.classesToKill.add(this.componentClass);
        this.componentClass = activeClass;

        // Register activity lifecycle callbacks to disable old icon
        activity.getApplication().registerActivityLifecycleCallbacks(this);
        iconChanged = true;
        promise.resolve(newIconName);
    } catch (Exception e) {
        promise.reject("ANDROID:ICON_INVALID", e);
    }
}



    private void completeIconChange() {
        if (!iconChanged)
            return;
        final Activity activity = getCurrentActivity();
        if (activity == null)
            return;
        classesToKill.forEach((cls) -> activity.getPackageManager().setComponentEnabledSetting(
                new ComponentName(this.packageName, cls),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP));
        classesToKill.clear();
        iconChanged = false;
    }

    @Override
    public void onActivityPaused(Activity activity) {
        completeIconChange();
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}

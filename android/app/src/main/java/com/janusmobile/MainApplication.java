package com.janusmobile;

import android.app.Application;

import com.facebook.react.ReactApplication;
import com.learnium.RNDeviceInfo.RNDeviceInfo;
import com.oney.WebRTCModule.WebRTCModulePackage;
import com.corbt.keepawake.KCKeepAwakePackage;
import com.slowpath.hockeyapp.RNHockeyAppPackage;
import com.remobile.toast.RCTToastPackage;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.ReactPackage;
import com.facebook.react.shell.MainReactPackage;
import com.facebook.soloader.SoLoader;

import java.util.Arrays;
import java.util.List;

import com.janusmobile.WebRTCModule.DualFisheyeStitcher;
import com.syarul.rnlocation.RNLocation;

public class MainApplication extends Application implements ReactApplication {

  private final ReactNativeHost mReactNativeHost = new ReactNativeHost(this) {
    @Override
    public boolean getUseDeveloperSupport() {
      return BuildConfig.DEBUG;
    }

    @Override
    protected List<ReactPackage> getPackages() {
      return Arrays.<ReactPackage>asList(
          new MainReactPackage(),
            new RNDeviceInfo(),
            new WebRTCModulePackage(),
            new KCKeepAwakePackage(),
            new RNHockeyAppPackage(MainApplication.this),
            new RCTToastPackage(),
            new RNLocation()
      );
    }
  };

  @Override
  public ReactNativeHost getReactNativeHost() {
    return mReactNativeHost;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    SoLoader.init(this, /* native exopackage */ false);

    DualFisheyeStitcher stitcher = DualFisheyeStitcher.getInstance();
    stitcher.setConfig(318.6, 318.8, 959.5, 318.9, 283.5, 1280, 720);
    stitcher.setRotation(Math.PI/2.0, Math.PI/2.0, 0);
    stitcher.generateMapAsync();
  }
}

package com.janusmobile;

import android.app.Application;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.ReactPackage;
import com.facebook.react.shell.MainReactPackage;
import com.facebook.soloader.SoLoader;

import java.util.Arrays;
import java.util.List;

import com.janusmobile.WebRTCModule.DualFisheyeStitcher;
import com.remobile.toast.RCTToastPackage;
import com.janusmobile.WebRTCModule.WebRTCModulePackage;

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
          new WebRTCModulePackage(),
          new RCTToastPackage()
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

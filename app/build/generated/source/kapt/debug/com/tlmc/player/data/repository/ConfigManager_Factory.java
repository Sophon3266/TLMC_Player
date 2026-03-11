package com.tlmc.player.data.repository;

import android.content.SharedPreferences;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava"
})
public final class ConfigManager_Factory implements Factory<ConfigManager> {
  private final Provider<SharedPreferences> prefsProvider;

  public ConfigManager_Factory(Provider<SharedPreferences> prefsProvider) {
    this.prefsProvider = prefsProvider;
  }

  @Override
  public ConfigManager get() {
    return newInstance(prefsProvider.get());
  }

  public static ConfigManager_Factory create(Provider<SharedPreferences> prefsProvider) {
    return new ConfigManager_Factory(prefsProvider);
  }

  public static ConfigManager newInstance(SharedPreferences prefs) {
    return new ConfigManager(prefs);
  }
}

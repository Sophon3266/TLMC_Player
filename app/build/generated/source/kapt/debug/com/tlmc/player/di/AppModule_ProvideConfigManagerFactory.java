package com.tlmc.player.di;

import android.content.SharedPreferences;
import com.tlmc.player.data.repository.ConfigManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_ProvideConfigManagerFactory implements Factory<ConfigManager> {
  private final Provider<SharedPreferences> prefsProvider;

  public AppModule_ProvideConfigManagerFactory(Provider<SharedPreferences> prefsProvider) {
    this.prefsProvider = prefsProvider;
  }

  @Override
  public ConfigManager get() {
    return provideConfigManager(prefsProvider.get());
  }

  public static AppModule_ProvideConfigManagerFactory create(
      Provider<SharedPreferences> prefsProvider) {
    return new AppModule_ProvideConfigManagerFactory(prefsProvider);
  }

  public static ConfigManager provideConfigManager(SharedPreferences prefs) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideConfigManager(prefs));
  }
}

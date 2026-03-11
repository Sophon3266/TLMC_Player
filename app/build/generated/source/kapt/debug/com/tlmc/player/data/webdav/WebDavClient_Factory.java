package com.tlmc.player.data.webdav;

import com.tlmc.player.data.repository.ConfigManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;

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
public final class WebDavClient_Factory implements Factory<WebDavClient> {
  private final Provider<OkHttpClient> okHttpClientProvider;

  private final Provider<ConfigManager> configManagerProvider;

  public WebDavClient_Factory(Provider<OkHttpClient> okHttpClientProvider,
      Provider<ConfigManager> configManagerProvider) {
    this.okHttpClientProvider = okHttpClientProvider;
    this.configManagerProvider = configManagerProvider;
  }

  @Override
  public WebDavClient get() {
    return newInstance(okHttpClientProvider.get(), configManagerProvider.get());
  }

  public static WebDavClient_Factory create(Provider<OkHttpClient> okHttpClientProvider,
      Provider<ConfigManager> configManagerProvider) {
    return new WebDavClient_Factory(okHttpClientProvider, configManagerProvider);
  }

  public static WebDavClient newInstance(OkHttpClient okHttpClient, ConfigManager configManager) {
    return new WebDavClient(okHttpClient, configManager);
  }
}

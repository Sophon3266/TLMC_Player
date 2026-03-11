package com.tlmc.player.di;

import com.tlmc.player.data.repository.ConfigManager;
import com.tlmc.player.data.webdav.WebDavClient;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class NetworkModule_ProvideWebDavClientFactory implements Factory<WebDavClient> {
  private final Provider<OkHttpClient> okHttpClientProvider;

  private final Provider<ConfigManager> configManagerProvider;

  public NetworkModule_ProvideWebDavClientFactory(Provider<OkHttpClient> okHttpClientProvider,
      Provider<ConfigManager> configManagerProvider) {
    this.okHttpClientProvider = okHttpClientProvider;
    this.configManagerProvider = configManagerProvider;
  }

  @Override
  public WebDavClient get() {
    return provideWebDavClient(okHttpClientProvider.get(), configManagerProvider.get());
  }

  public static NetworkModule_ProvideWebDavClientFactory create(
      Provider<OkHttpClient> okHttpClientProvider, Provider<ConfigManager> configManagerProvider) {
    return new NetworkModule_ProvideWebDavClientFactory(okHttpClientProvider, configManagerProvider);
  }

  public static WebDavClient provideWebDavClient(OkHttpClient okHttpClient,
      ConfigManager configManager) {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideWebDavClient(okHttpClient, configManager));
  }
}

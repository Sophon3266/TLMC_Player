package com.tlmc.player.ui.player;

import com.tlmc.player.data.repository.ConfigManager;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;

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
public final class PlayerService_MembersInjector implements MembersInjector<PlayerService> {
  private final Provider<OkHttpClient> okHttpClientProvider;

  private final Provider<ConfigManager> configManagerProvider;

  public PlayerService_MembersInjector(Provider<OkHttpClient> okHttpClientProvider,
      Provider<ConfigManager> configManagerProvider) {
    this.okHttpClientProvider = okHttpClientProvider;
    this.configManagerProvider = configManagerProvider;
  }

  public static MembersInjector<PlayerService> create(Provider<OkHttpClient> okHttpClientProvider,
      Provider<ConfigManager> configManagerProvider) {
    return new PlayerService_MembersInjector(okHttpClientProvider, configManagerProvider);
  }

  @Override
  public void injectMembers(PlayerService instance) {
    injectOkHttpClient(instance, okHttpClientProvider.get());
    injectConfigManager(instance, configManagerProvider.get());
  }

  @InjectedFieldSignature("com.tlmc.player.ui.player.PlayerService.okHttpClient")
  public static void injectOkHttpClient(PlayerService instance, OkHttpClient okHttpClient) {
    instance.okHttpClient = okHttpClient;
  }

  @InjectedFieldSignature("com.tlmc.player.ui.player.PlayerService.configManager")
  public static void injectConfigManager(PlayerService instance, ConfigManager configManager) {
    instance.configManager = configManager;
  }
}

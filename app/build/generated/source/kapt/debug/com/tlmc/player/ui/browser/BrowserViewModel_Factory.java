package com.tlmc.player.ui.browser;

import com.tlmc.player.data.repository.ConfigManager;
import com.tlmc.player.data.repository.WebDavRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class BrowserViewModel_Factory implements Factory<BrowserViewModel> {
  private final Provider<WebDavRepository> repositoryProvider;

  private final Provider<ConfigManager> configManagerProvider;

  public BrowserViewModel_Factory(Provider<WebDavRepository> repositoryProvider,
      Provider<ConfigManager> configManagerProvider) {
    this.repositoryProvider = repositoryProvider;
    this.configManagerProvider = configManagerProvider;
  }

  @Override
  public BrowserViewModel get() {
    return newInstance(repositoryProvider.get(), configManagerProvider.get());
  }

  public static BrowserViewModel_Factory create(Provider<WebDavRepository> repositoryProvider,
      Provider<ConfigManager> configManagerProvider) {
    return new BrowserViewModel_Factory(repositoryProvider, configManagerProvider);
  }

  public static BrowserViewModel newInstance(WebDavRepository repository,
      ConfigManager configManager) {
    return new BrowserViewModel(repository, configManager);
  }
}

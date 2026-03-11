package com.tlmc.player.ui.player;

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
public final class PlayerViewModel_Factory implements Factory<PlayerViewModel> {
  private final Provider<WebDavRepository> repositoryProvider;

  public PlayerViewModel_Factory(Provider<WebDavRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public PlayerViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static PlayerViewModel_Factory create(Provider<WebDavRepository> repositoryProvider) {
    return new PlayerViewModel_Factory(repositoryProvider);
  }

  public static PlayerViewModel newInstance(WebDavRepository repository) {
    return new PlayerViewModel(repository);
  }
}

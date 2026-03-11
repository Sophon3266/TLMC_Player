package com.tlmc.player.ui.text;

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
public final class TextViewModel_Factory implements Factory<TextViewModel> {
  private final Provider<WebDavRepository> repositoryProvider;

  public TextViewModel_Factory(Provider<WebDavRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public TextViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static TextViewModel_Factory create(Provider<WebDavRepository> repositoryProvider) {
    return new TextViewModel_Factory(repositoryProvider);
  }

  public static TextViewModel newInstance(WebDavRepository repository) {
    return new TextViewModel(repository);
  }
}

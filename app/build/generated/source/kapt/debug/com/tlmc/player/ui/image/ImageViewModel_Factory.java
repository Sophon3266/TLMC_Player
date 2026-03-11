package com.tlmc.player.ui.image;

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
public final class ImageViewModel_Factory implements Factory<ImageViewModel> {
  private final Provider<WebDavRepository> repositoryProvider;

  public ImageViewModel_Factory(Provider<WebDavRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public ImageViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static ImageViewModel_Factory create(Provider<WebDavRepository> repositoryProvider) {
    return new ImageViewModel_Factory(repositoryProvider);
  }

  public static ImageViewModel newInstance(WebDavRepository repository) {
    return new ImageViewModel(repository);
  }
}

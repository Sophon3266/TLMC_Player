package com.tlmc.player.data.repository;

import com.tlmc.player.data.webdav.WebDavClient;
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
public final class WebDavRepository_Factory implements Factory<WebDavRepository> {
  private final Provider<WebDavClient> webDavClientProvider;

  public WebDavRepository_Factory(Provider<WebDavClient> webDavClientProvider) {
    this.webDavClientProvider = webDavClientProvider;
  }

  @Override
  public WebDavRepository get() {
    return newInstance(webDavClientProvider.get());
  }

  public static WebDavRepository_Factory create(Provider<WebDavClient> webDavClientProvider) {
    return new WebDavRepository_Factory(webDavClientProvider);
  }

  public static WebDavRepository newInstance(WebDavClient webDavClient) {
    return new WebDavRepository(webDavClient);
  }
}

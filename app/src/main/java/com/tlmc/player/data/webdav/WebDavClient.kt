package com.tlmc.player.data.webdav

import com.tlmc.player.data.model.WebDavFile
import com.tlmc.player.data.repository.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val configManager: ConfigManager
) {
    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)

    private fun getAuthHeader(): String {
        val config = configManager.getConfig()
        return Credentials.basic(config.username, config.password)
    }

    private fun buildUrl(path: String): String {
        val config = configManager.getConfig()
        val baseUrl = config.url.trimEnd('/')
        val cleanPath = path.trimStart('/')
        return if (cleanPath.isEmpty()) baseUrl else "$baseUrl/$cleanPath"
    }

    suspend fun listFiles(path: String): Result<List<WebDavFile>> = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(path)
            val propfindBody = """<?xml version="1.0" encoding="utf-8"?>
                <D:propfind xmlns:D="DAV:">
                    <D:prop>
                        <D:displayname/>
                        <D:resourcetype/>
                        <D:getcontentlength/>
                        <D:getcontenttype/>
                        <D:getlastmodified/>
                    </D:prop>
                </D:propfind>""".trimIndent()

            val request = Request.Builder()
                .url(url.let { if (!it.endsWith("/")) "$it/" else it })
                .method("PROPFIND", propfindBody.toRequestBody("application/xml".toMediaType()))
                .header("Authorization", getAuthHeader())
                .header("Depth", "1")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful && response.code != 207) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: return@withContext Result.success(emptyList())
            val files = parseMultiStatus(body, path)
            Result.success(files.sortedWith(compareByDescending<WebDavFile> { it.isDirectory }.thenBy { it.name }))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFile(path: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(path)
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", getAuthHeader())
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val bytes = response.body?.bytes() ?: ByteArray(0)
            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFileAsString(path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(path)
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", getAuthHeader())
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val bytes = response.body?.bytes() ?: ByteArray(0)
            Result.success(String(bytes))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getAuthenticatedUrl(path: String): String {
        return buildUrl(path)
    }

    fun getOkHttpClientWithAuth(): OkHttpClient {
        return okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", getAuthHeader())
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private fun parseMultiStatus(xml: String, currentPath: String): List<WebDavFile> {
        val files = mutableListOf<WebDavFile>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var href = ""
        var displayName = ""
        var isDirectory = false
        var contentLength: Long = 0
        var contentType = ""
        var lastModified: Date? = null
        var inResponse = false
        var currentTag = ""

        val normalizedCurrentPath = normalizePath(currentPath)

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val localName = parser.name
                    currentTag = localName
                    when (localName) {
                        "response" -> {
                            inResponse = true
                            href = ""
                            displayName = ""
                            isDirectory = false
                            contentLength = 0
                            contentType = ""
                            lastModified = null
                        }
                        "collection" -> {
                            if (inResponse) isDirectory = true
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inResponse) {
                        val text = parser.text?.trim() ?: ""
                        when (currentTag) {
                            "href" -> href = text
                            "displayname" -> displayName = text
                            "getcontentlength" -> contentLength = text.toLongOrNull() ?: 0
                            "getcontenttype" -> contentType = text
                            "getlastmodified" -> {
                                try {
                                    lastModified = dateFormat.parse(text)
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val localName = parser.name
                    if (localName == "response" && inResponse) {
                        inResponse = false
                        val decodedHref = try {
                            URLDecoder.decode(href, "UTF-8")
                        } catch (_: Exception) {
                            href
                        }
                        val filePath = normalizePath(decodedHref)

                        // Skip current directory entry
                        if (filePath != normalizedCurrentPath && filePath != "$normalizedCurrentPath/") {
                            val name = if (displayName.isNotEmpty()) {
                                displayName
                            } else {
                                val trimmed = decodedHref.trimEnd('/')
                                trimmed.substringAfterLast('/')
                            }

                            if (name.isNotEmpty()) {
                                files.add(
                                    WebDavFile(
                                        name = name,
                                        path = decodedHref.trimEnd('/'),
                                        isDirectory = isDirectory,
                                        size = contentLength,
                                        contentType = contentType,
                                        lastModified = lastModified
                                    )
                                )
                            }
                        }
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        return files
    }

    private fun normalizePath(path: String): String {
        return path.trimEnd('/').let {
            if (it.contains("://")) {
                val afterScheme = it.substringAfter("://")
                "/" + afterScheme.substringAfter("/", "")
            } else {
                it
            }
        }
    }
}


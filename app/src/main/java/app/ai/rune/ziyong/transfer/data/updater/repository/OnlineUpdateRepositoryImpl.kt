// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.data.updater.repository

import android.content.Context
import app.ai.rune.ziyong.transfer.core.device.model.Level
import app.ai.rune.ziyong.transfer.core.env.AppConfig
import app.ai.rune.ziyong.transfer.data.updater.model.GithubRelease
import app.ai.rune.ziyong.transfer.domain.settings.repository.AppSettingsRepository
import app.ai.rune.ziyong.transfer.domain.updater.model.UpdateInfo
import app.ai.rune.ziyong.transfer.domain.updater.repository.UpdateRepository
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

class OnlineUpdateRepositoryImpl(
    private val context: Context,
    private val client: OkHttpClient,
    private val json: Json,
    private val appSettingsRepository: AppSettingsRepository,
) : UpdateRepository {
    companion object {
        // Shell app tracks its own repository as the update source.
        private const val REPO_OWNER = "Rune-cn"
        private const val REPO_NAME = "miuix-example"
    }

    private val _updateInfoFlow = MutableStateFlow<UpdateInfo?>(null)
    override val updateInfoFlow: StateFlow<UpdateInfo?> = _updateInfoFlow.asStateFlow()

    private var hasChecked = false
    private val updateMutex = Mutex()

    override suspend fun checkUpdate(force: Boolean): UpdateInfo? = withContext(Dispatchers.IO) {
        // If force check not required, and has checked, return cached value
        if (!force && hasChecked) {
            Timber.d("checkUpdate: Returning cached UpdateInfo")
            return@withContext _updateInfoFlow.value
        }

        // Use mutex lock to prevent concurrent execution
        updateMutex.withLock {
            // Check again after acquiring lock
            if (!force && hasChecked) {
                return@withContext _updateInfoFlow.value
            }

            try {
                val prefs = appSettingsRepository.preferencesFlow.first()
                if (!OnlineUpdatePolicy.canCheckUpdates(
                        allowInternetAccess = prefs.allowInternetAccess,
                        isDebug = AppConfig.isDebug,
                        level = AppConfig.LEVEL,
                        packageName = context.packageName,
                        officialPackageName = context.packageName,
                                            )
                ) {
                    Timber.d(
                        "checkUpdate: Skipped. allowInternetAccess=${prefs.allowInternetAccess}, " +
                            "isDebug=${AppConfig.isDebug}, LEVEL=${AppConfig.LEVEL}, " +
                            "package=${context.packageName}",
                    )
                    _updateInfoFlow.value = null
                    hasChecked = false
                    return@withLock null
                }

                val remoteRelease = fetchRemoteRelease()
                if (remoteRelease == null) {
                    // Log if network request or parsing failed
                    Timber.e("checkUpdate: fetchRemoteRelease() returned null")
                    return@withContext null
                }
                // Log successful fetch
                Timber.d("checkUpdate: Successfully fetched release. TagName=${remoteRelease.tagName}")

                val apkAsset = OnlineUpdatePolicy.selectOnlineApkAsset(remoteRelease)

                if (apkAsset == null) {
                    // Log if no matching asset was found
                    Timber.e("checkUpdate: No matching online APK asset found in release")
                } else {
                    // Log the found asset name
                    Timber.d("checkUpdate: Found APK asset: ${apkAsset.name}")
                }

                val downloadUrl = OnlineUpdatePolicy.resolveDownloadUrl(
                    asset = apkAsset,
                    channel = prefs.githubUpdateChannel,
                    customProxyUrl = prefs.customGithubProxyUrl,
                )
                val remoteVersion = OnlineUpdatePolicy.resolveRemoteVersion(remoteRelease, apkAsset)

                val currentVersion = AppConfig.VERSION_NAME

                // Log versions before comparison to catch potential parsing issues
                Timber.d("checkUpdate: Preparing to compare. Local=$currentVersion, Remote=$remoteVersion")

                val hasUpdate = OnlineUpdatePolicy.compareVersions(remoteVersion, currentVersion) > 0

                Timber.i("Update check: Local=$currentVersion, Remote=$remoteVersion, HasUpdate=$hasUpdate")

                val updateInfo = UpdateInfo(
                    hasUpdate = hasUpdate,
                    remoteVersion = remoteVersion,
                    releaseUrl = remoteRelease.htmlUrl ?: "",
                    downloadUrl = downloadUrl,
                    updateLog = remoteRelease.body,
                )

                // Cache result and mark as checked
                _updateInfoFlow.value = updateInfo
                hasChecked = true

                return@withContext updateInfo
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Log the stacktrace to identify crashes like NullPointerException or Regex errors
                Timber.e(e, "checkUpdate: Exception caught during execution")
                null
            }
        }
    }

    private fun fetchRemoteRelease(): GithubRelease? {
        val url = if (AppConfig.LEVEL == Level.STABLE) {
            "https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/latest"
        } else {
            "https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases"
        }

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyString = response.body.string()

            return if (AppConfig.LEVEL == Level.STABLE) {
                json.decodeFromString<GithubRelease>(bodyString)
            } else {
                val releases = json.decodeFromString<List<GithubRelease>>(bodyString)
                OnlineUpdatePolicy.selectPreviewRelease(releases)
            }
        }
    }

    override suspend fun downloadUpdate(url: String): Pair<InputStream, Long>? = withContext(Dispatchers.IO) {
        if (!appSettingsRepository.preferencesFlow.first().allowInternetAccess) {
            Timber.d("downloadUpdate: Skipped because internet access is disabled")
            return@withContext null
        }

        Timber.d("Starting download stream from: $url")
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.e("Download failed: ${response.code}")
                response.close()
                return@withContext null
            }
            val body = response.body
            val contentLength = body.contentLength()

            Pair(body.byteStream(), contentLength)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Exception during download request")
            null
        }
    }
}

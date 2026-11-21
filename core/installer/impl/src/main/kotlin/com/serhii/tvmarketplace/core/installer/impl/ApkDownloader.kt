package com.serhii.tvmarketplace.core.installer.impl

import com.serhii.tvmarketplace.core.domain.AppPackage
import com.serhii.tvmarketplace.core.domain.FailureReason
import com.serhii.tvmarketplace.core.installer.ApkInfo
import com.serhii.tvmarketplace.core.installer.PackageInspector
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException
import java.security.MessageDigest

// release files are served by GitHub itself after a redirect or two; anything else is not what the release lists
private val ALLOWED_HOSTS = setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")
private const val MAX_REDIRECTS = 5
private const val BUFFER_SIZE = 64 * 1024

// a stalled connection fails after this long without a byte; a slow but moving one never times out
private const val SOCKET_TIMEOUT_MS = 30_000L

// fetches a package into app storage and checks it is complete, intact and the app it should be
class ApkDownloader(
  client: HttpClient,
  private val inspector: PackageInspector,
  private val directory: File,
  private val freeBytes: (File) -> Long,
) {

  sealed interface Outcome {
    data class Done(
      val file: File,
      val apk: ApkInfo,
    ) : Outcome

    data class Failed(val reason: FailureReason) : Outcome
  }

  // every redirect is checked against the allowed hosts, so redirects are followed here, not by the client
  private val client = client.config {
    followRedirects = false
    expectSuccess = false
    install(HttpTimeout) {
      requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
      socketTimeoutMillis = SOCKET_TIMEOUT_MS
    }
  }

  fun file(
    appId: Long,
    pkg: AppPackage,
  ) = File(directory, "$appId-${pkg.id}.apk")

  // what a download of this app left behind, finished or not
  fun deleteFiles(appId: Long) {
    directory.listFiles { file -> file.name.startsWith("$appId-") }?.forEach { it.delete() }
  }

  suspend fun download(
    appId: Long,
    pkg: AppPackage,
    expectedPackage: String?,
    onProgress: suspend (bytes: Long, total: Long) -> Unit,
  ): Outcome {
    directory.mkdirs()
    deleteFiles(appId)
    // a little room for the system to copy the package while installing it
    val needed = pkg.sizeBytes + pkg.sizeBytes / 10
    if (freeBytes(directory) < needed) return Outcome.Failed(FailureReason.NoSpace(needed))
    val target = file(appId, pkg)
    val part = File(directory, target.name + ".part")
    var outcome: Outcome? = null
    try {
      outcome = fetch(pkg, part, onProgress)
      if (outcome != null) return outcome
      outcome = verify(pkg, part, expectedPackage)
      if (outcome is Outcome.Done) {
        if (!part.renameTo(target)) return Outcome.Failed(FailureReason.System(null)).also { outcome = it }
        return Outcome.Done(target, outcome.apk).also { outcome = it }
      }
      return outcome
    } catch (e: CancellationException) {
      throw e
    } catch (_: IOException) {
      return Outcome.Failed(FailureReason.Network).also { outcome = it }
    } finally {
      if (outcome !is Outcome.Done) {
        part.delete()
        target.delete()
      }
    }
  }

  // null when the body was written to part in full
  private suspend fun fetch(
    pkg: AppPackage,
    part: File,
    onProgress: suspend (Long, Long) -> Unit,
  ): Outcome? {
    var url = Url(pkg.downloadUrl)
    repeat(MAX_REDIRECTS) {
      if (url.protocol.name != "https" || url.host !in ALLOWED_HOSTS) return Outcome.Failed(FailureReason.Corrupt)
      val next = client.prepareGet(url) { header(HttpHeaders.Accept, "application/octet-stream") }.execute { response ->
        when {
          response.status.value in 300..399 -> {
            val location = response.headers[HttpHeaders.Location] ?: return@execute Outcome.Failed(FailureReason.Corrupt)
            URLBuilder(url).takeFrom(location).build()
          }
          !response.status.isSuccess() -> Outcome.Failed(FailureReason.Network)
          else -> {
            write(response, part, pkg.sizeBytes, onProgress)
            null
          }
        }
      }
      when (next) {
        is Url -> url = next
        is Outcome -> return next
        else -> return null
      }
    }
    return Outcome.Failed(FailureReason.Corrupt)
  }

  private suspend fun write(
    response: HttpResponse,
    part: File,
    expectedSize: Long,
    onProgress: suspend (Long, Long) -> Unit,
  ) {
    val channel = response.bodyAsChannel()
    val total = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: expectedSize
    val buffer = ByteArray(BUFFER_SIZE)
    var bytes = 0L
    var reportedPercent = -1L
    part.outputStream().use { out ->
      while (true) {
        val read = channel.readAvailable(buffer)
        if (read < 0) break
        if (read == 0) continue
        out.write(buffer, 0, read)
        bytes += read
        // at most one update per percent: progress crosses process boundaries through WorkManager
        val percent = if (total > 0) bytes * 100 / total else -1
        if (percent != reportedPercent) {
          reportedPercent = percent
          onProgress(bytes, total)
        }
      }
    }
    onProgress(bytes, total)
  }

  private fun verify(
    pkg: AppPackage,
    file: File,
    expectedPackage: String?,
  ): Outcome {
    if (file.length() != pkg.sizeBytes) return Outcome.Failed(FailureReason.Corrupt)
    if (pkg.sha256 != null && !sha256(file).equals(pkg.sha256, ignoreCase = true)) return Outcome.Failed(FailureReason.Corrupt)
    val apk = inspector.inspect(file) ?: return Outcome.Failed(FailureReason.Corrupt)
    if (expectedPackage != null && apk.packageName != expectedPackage) return Outcome.Failed(FailureReason.DifferentApp)
    return Outcome.Done(file, apk)
  }

  private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(BUFFER_SIZE)
      while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }
}

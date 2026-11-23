package io.github.nanunic9379.rideplan.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import io.github.nanunic9379.rideplan.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Una Release piu' nuova di quella installata, con l'APK giusto per questo telefono. */
data class ReleaseInfo(
    val tag: String,
    val apkName: String,
    val apkUrl: String,
    val sha256: String,
    val size: Long,
) {
    val versionName: String get() = tag.removePrefix("v")
}

/**
 * Aggiornamento dalle Release di GitHub (#30).
 * Unica richiesta di rete: l'ultima Release pubblica del repo. Nessun dato inviato oltre
 * a quelli di ogni richiesta web (indirizzo IP, "RidePlan-Android" come client).
 */
object GithubUpdater {
    private const val LATEST = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"
    private const val TIMEOUT_MS = 15_000

    /** Ultima Release se e' piu' nuova, altrimenti null. Da chiamare fuori dal thread principale. */
    fun latestNewer(): ReleaseInfo? {
        val conn = open(LATEST).apply { setRequestProperty("Accept", "application/vnd.github+json") }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            if (!UpdateRules.isNewer(tag, BuildConfig.VERSION_CODE)) return null
            val assets = json.getJSONArray("assets")
            val byName = (0 until assets.length()).map { assets.getJSONObject(it) }.associateBy { it.getString("name") }
            val name = UpdateRules.pickApk(byName.keys.toList(), Build.SUPPORTED_ABIS.toList()) ?: return null
            val asset = byName.getValue(name)
            // Senza impronta non si installa: e' l'unica garanzia che il file sia quello pubblicato.
            val sha = UpdateRules.sha256FromDigest(asset.optString("digest").ifEmpty { null }) ?: return null
            return ReleaseInfo(tag, name, asset.getString("browser_download_url"), sha, asset.optLong("size", -1))
        } finally {
            conn.disconnect()
        }
    }

    /** Scarica l'APK nella cache e ne controlla lo SHA-256. [onProgress] riceve 0..100 o null. */
    fun download(context: Context, release: ReleaseInfo, onProgress: (Int?) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, release.apkName)
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = open(release.apkUrl)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Download non riuscito (HTTP ${conn.responseCode})")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.size
            var done = 0L
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        onProgress(if (total > 0) (done * 100 / total).toInt() else null)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != release.sha256) {
            file.delete()
            throw IOException("Il file scaricato non corrisponde a quello pubblicato. Non l'ho installato.")
        }
        return file
    }

    /** Passa l'APK ad Android. La conferma la chiede il sistema (vedi [InstallResultReceiver]). */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("rideplan.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(
                context, sessionId, Intent(context, InstallResultReceiver::class.java), flags,
            )
            session.commit(callback.intentSender)
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "RidePlan-Android")
        }
}

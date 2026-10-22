package com.teamsync.pleos.log

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 앱 공통 로그 래퍼 (CLAUDE.md §0 로깅 규칙).
 *
 * - logcat: 태그 `PLEOS/<tag>`
 * - 파일: `filesDir/logs/app-YYYYMMDD.log`, 파일당 [MAX_FILE_BYTES] 초과 시 `app-YYYYMMDD-N.log`로 롤링,
 *   디렉터리 합계 [MAX_DIR_BYTES] 초과 시 오래된 파일부터 삭제
 * - 화면 표시용으로 최근 [TAIL_SIZE]줄을 [tail]로 노출
 *
 * API 키/시크릿은 절대 메시지에 넣지 말 것.
 */
object AppLog {
    const val PREFIX = "PLEOS/"
    private const val MAX_FILE_BYTES = 2L * 1024 * 1024
    private const val MAX_DIR_BYTES = 20L * 1024 * 1024
    private const val TAIL_SIZE = 400

    data class Entry(val time: Long, val level: Char, val tag: String, val message: String)

    private val _tail = MutableStateFlow<List<Entry>>(emptyList())
    val tail: StateFlow<List<Entry>> = _tail.asStateFlow()

    @Volatile
    private var logDir: File? = null

    // 파일 I/O는 전용 스레드 하나에서만 → 순서 보장, 호출 스레드(binder/main) 블로킹 없음
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "AppLog-io").apply { isDaemon = true } }
    private var openFile: File? = null
    private var out: BufferedWriter? = null

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        logDir = dir
        i("App", "AppLog init dir=${dir.absolutePath}")
    }

    fun d(tag: String, msg: String) = log(Log.DEBUG, tag, msg, null)
    fun i(tag: String, msg: String) = log(Log.INFO, tag, msg, null)
    fun w(tag: String, msg: String, tr: Throwable? = null) = log(Log.WARN, tag, msg, tr)
    fun e(tag: String, msg: String, tr: Throwable? = null) = log(Log.ERROR, tag, msg, tr)

    /** 현재 로그 파일 경로 (adb pull 안내용). */
    fun currentFilePath(): String? = logDir?.let { File(it, "app-${day(System.currentTimeMillis())}.log").absolutePath }

    private fun log(level: Int, tag: String, msg: String, tr: Throwable?) {
        val fullTag = PREFIX + tag
        val body = if (tr != null) msg + "\n" + Log.getStackTraceString(tr) else msg
        Log.println(level, fullTag, body)

        val now = System.currentTimeMillis()
        val levelChar = levelChar(level)
        val shown = if (tr != null) "$msg | ${tr.javaClass.simpleName}: ${tr.message}" else msg
        _tail.update { (it + Entry(now, levelChar, fullTag, shown)).takeLast(TAIL_SIZE) }

        val dir = logDir ?: return
        val thread = Thread.currentThread().name
        io.execute { write(dir, now, levelChar, fullTag, thread, body) }
    }

    private fun write(dir: File, time: Long, level: Char, tag: String, thread: String, body: String) {
        try {
            val target = File(dir, "app-${day(time)}.log")
            if (openFile != target || !target.exists()) reopen(target)
            if (target.length() >= MAX_FILE_BYTES) {
                roll(dir, target)
                reopen(target)
            }
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(time))
            out?.apply {
                write("$ts $level/$tag [$thread] $body\n")
                flush()
            }
        } catch (e: Exception) {
            // 파일 기록 실패는 logcat에만 남김 (재귀 방지)
            Log.e(PREFIX + "Log", "file write failed: ${e.message}", e)
        }
    }

    private fun reopen(target: File) {
        out?.close()
        out = BufferedWriter(OutputStreamWriter(FileOutputStream(target, true), Charsets.UTF_8))
        openFile = target
    }

    private fun roll(dir: File, target: File) {
        out?.close()
        out = null
        openFile = null
        val base = target.nameWithoutExtension
        var n = 1
        while (File(dir, "$base-$n.log").exists()) n++
        target.renameTo(File(dir, "$base-$n.log"))
        prune(dir)
    }

    private fun prune(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".log") }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= MAX_DIR_BYTES) break
            total -= f.length()
            f.delete()
        }
    }

    private fun day(time: Long) = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(time))

    private fun levelChar(level: Int) = when (level) {
        Log.VERBOSE -> 'V'
        Log.DEBUG -> 'D'
        Log.INFO -> 'I'
        Log.WARN -> 'W'
        Log.ERROR -> 'E'
        else -> '?'
    }
}

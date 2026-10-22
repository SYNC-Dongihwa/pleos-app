package com.teamsync.pleos.probe

enum class ProbeKind(val badge: String) {
    CAPABILITY("CAP"),
    GET("GET"),
    LISTENER("LIVE"),
    NAVI_EVENT("NAVI"),
}

enum class ProbeStatus {
    PENDING,
    OK,
    LISTENING,
    FAILED,
    TIMEOUT,
    SKIPPED,
}

/** Probe 화면 한 줄 = SDK 호출 하나(또는 리스너/이벤트 하나)의 최신 결과. */
data class ProbeRow(
    val key: String,
    val domain: String,
    val kind: ProbeKind,
    val label: String,
    val permissions: List<String> = emptyList(),
    val status: ProbeStatus = ProbeStatus.PENDING,
    val value: String = "",
    val updates: Int = 0,
    val updatedAt: Long = 0L,
)

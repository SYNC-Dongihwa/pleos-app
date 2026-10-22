package com.teamsync.pleos.probe

import ai.pleos.playground.vehicle.api.model.area.Area

private const val MAX_LEN = 600

/** SDK가 돌려준 임의의 값을 사람이 읽을 수 있는 한 줄로. */
fun describe(value: Any?): String = describeInner(value, 0).let {
    if (it.length > MAX_LEN) it.take(MAX_LEN) + "…" else it
}

private fun describeInner(value: Any?, depth: Int): String {
    if (depth > 4) return "…"
    return when (value) {
        null -> "null"
        is Area -> describeArea(value)
        is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") {
            "${describeInner(it.key, depth + 1)}=${describeInner(it.value, depth + 1)}"
        }
        is Collection<*> -> value.joinToString(prefix = "[", postfix = "]") { describeInner(it, depth + 1) }
        is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { describeInner(it, depth + 1) }
        is Enum<*> -> value.name
        else -> value.toString()
    }
}

/** SeatArea(ROW_1_LEFT) 처럼. Area 하위 클래스는 getValues()로 AreaValue 배열을 준다. */
fun describeArea(area: Area): String {
    val values = runCatching {
        area.javaClass.getMethod("getValues").invoke(area) as? Array<*>
    }.getOrNull()
    val name = area.javaClass.simpleName
    return if (values != null) "$name(${values.joinToString(",") { (it as? Enum<*>)?.name ?: it.toString() }})" else name
}

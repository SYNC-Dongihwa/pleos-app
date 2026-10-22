package com.teamsync.pleos.probe

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.Area
import ai.pleos.playground.vehicle.listener.VehicleListener
import android.content.Context
import com.teamsync.pleos.log.AppLog
import com.teamsync.pleos.vehicle.VehicleErrors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Vehicle SDK 전 도메인을 리플렉션으로 훑어서 실제로 호출한다 (M1 SDK Probe).
 *
 * 1. `Vehicle.getXxx()`로 도메인 객체를 얻는다 (도메인 목록도 AAR 그대로 따라감).
 * 2. 모든 `checkXxxCapabilities` 호출 → 결과에서 지원 Area(SeatArea, DoorArea…)를 수집.
 * 3. 모든 get/is/has 조회를 수집된 Area마다 호출.
 * 4. 모든 register 리스너를 동적 프록시로 등록해 값 변화를 실시간 반영.
 *
 * set/move/select(제어 계열)는 호출하지 않는다 — Vehicle Control SDK는 준비 중이고 Probe는 읽기 전용.
 */
class VehicleProbe(
    context: Context,
    private val scope: CoroutineScope,
    shared: Vehicle? = null,
    /** 리스너를 해제한 직후 호출 — 같은 속성을 구독하던 앱의 다른 곳이 재구독해야 한다 (VehicleMonitor.resubscribe). */
    private val onListenersRemoved: () -> Unit = {},
) {

    // 같은 프로세스에서 Vehicle 인스턴스를 따로 만들어 release하면 다른 인스턴스(CarSession)의 콜백까지 끊긴다 (실측)
    // → MonitorService가 떠 있으면 그 인스턴스를 빌려 쓰고 release하지 않는다
    private val vehicle = shared ?: Vehicle(context.applicationContext)
    private val ownsVehicle = shared == null

    private val _rows = MutableStateFlow<Map<String, ProbeRow>>(emptyMap())
    val rows: StateFlow<Map<String, ProbeRow>> = _rows.asStateFlow()

    private val _state = MutableStateFlow("NOT_INITIALIZED")
    val state: StateFlow<String> = _state.asStateFlow()

    private class Domain(val name: String, val api: Any, val iface: Class<*>)
    private class Registration(val label: String, val api: Any, val unregister: Method?, val args: Array<Any?>)

    // domain → check 메서드 이름 → 그 capability가 보고한 Area 목록.
    // Area는 속성마다 다르다 (예: 도어 잠금은 개별 도어가 아니라 4도어 묶음 Area 하나) → getter는 짝이 되는 capability의 Area만 써야 함.
    private val capabilityAreas = ConcurrentHashMap<String, ConcurrentHashMap<String, List<Area>>>()
    private val registrations = CopyOnWriteArrayList<Registration>()
    private val pendingCallbacks = AtomicInteger(0)
    private var job: Job? = null

    @Volatile private var initialized = false
    // 재실행 시 이전 회차의 늦은 콜백이 새 결과를 덮어쓰지 않도록
    @Volatile private var generation = 0

    fun start() {
        if (!initialized && !ownsVehicle) {
            AppLog.i(TAG, "using shared Vehicle instance (CarSession)")
            initialized = true
            _state.value = "INITIALIZED (shared)"
        }
        if (!initialized) {
            AppLog.i(TAG, "initialize() call")
            try {
                vehicle.initialize()
                initialized = true
                _state.value = "INITIALIZED"
                AppLog.i(TAG, "initialize() returned")
            } catch (e: Exception) {
                _state.value = "INIT_FAILED: ${VehicleErrors.describe(e)}"
                AppLog.e(TAG, "initialize() threw ${VehicleErrors.describe(e)}", e)
                return
            }
        }
        job?.cancel()
        val gen = ++generation
        job = scope.launch(Dispatchers.Default) { runProbe(gen) }
    }

    fun release() {
        job?.cancel()
        generation++
        unregisterAll()
        val pending = pendingCallbacks.get()
        if (pending > 0) AppLog.w(TAG, "release() while $pending callbacks still pending")
        if (initialized && ownsVehicle) {
            try {
                vehicle.release()
                AppLog.i(TAG, "release() ok")
            } catch (e: Exception) {
                AppLog.e(TAG, "release() threw ${VehicleErrors.describe(e)}", e)
            }
            initialized = false
        }
        _state.value = "RELEASED"
    }

    private suspend fun runProbe(gen: Int) {
        unregisterAll()
        _rows.value = emptyMap()
        capabilityAreas.clear()

        val domains = discoverDomains()
        AppLog.i(TAG, "domains(${domains.size})=${domains.joinToString { it.name }}")
        val limiter = Semaphore(CONCURRENCY)

        _state.value = "PROBING capabilities"
        coroutineScope {
            domains.flatMap { d -> capabilityMethods(d).map { d to it } }
                .map { (d, m) -> async { limiter.withPermit { call(gen, d, ProbeKind.CAPABILITY, m, emptyArray()) } } }
                .awaitAll()
        }
        capabilityAreas.forEach { (domain, caps) ->
            caps.forEach { (cap, list) -> AppLog.i(TAG, "areas $domain.$cap = ${list.map(::describeArea)}") }
        }

        _state.value = "PROBING get"
        coroutineScope {
            domains.flatMap { d ->
                getterMethods(d).flatMap { m ->
                    val inputTypes = m.parameterTypes.dropLast(2)
                    val (combos, skipReason) = inputCombos(d, m, inputTypes)
                    if (skipReason != null) {
                        skip(d, ProbeKind.GET, m, skipReason)
                        emptyList()
                    } else combos.map { args -> Triple(d, m, args) }
                }
            }.map { (d, m, args) -> async { limiter.withPermit { call(gen, d, ProbeKind.GET, m, args) } } }
                .awaitAll()
        }

        _state.value = "PROBING listeners"
        for (d in domains) {
            for (m in registerMethods(d)) {
                val (combos, skipReason) = inputCombos(d, m, m.parameterTypes.dropLast(1))
                if (skipReason != null) {
                    skip(d, ProbeKind.LISTENER, m, skipReason)
                    continue
                }
                combos.forEach { args -> register(gen, d, m, args) }
            }
        }

        val all = _rows.value.values
        val summary = ProbeStatus.entries.joinToString { s -> "$s=${all.count { it.status == s }}" }
        AppLog.i(TAG, "probe done gen=$gen rows=${all.size} $summary")
        _state.value = "READY ($summary)"
    }

    // ---- 호출 ------------------------------------------------------------------------------

    private suspend fun call(gen: Int, d: Domain, kind: ProbeKind, m: Method, inputs: Array<Any?>) {
        val label = label(m, inputs)
        val key = key(d, m, label)
        val api = "${d.name}.${m.name}"
        put(ProbeRow(key, d.name, kind, label, permissionsOf(d, m)))

        val done = CompletableDeferred<Unit>()
        val settled = AtomicBoolean(false)
        fun settle() {
            if (settled.compareAndSet(false, true)) pendingCallbacks.decrementAndGet()
            done.complete(Unit)
        }

        val onComplete: Any = when (arityOf(m.parameterTypes[inputs.size])) {
            1 -> { a: Any? -> onResult(gen, d, m, key, kind, label, listOf(a)); settle() }
            2 -> { a: Any?, b: Any? -> onResult(gen, d, m, key, kind, label, listOf(a, b)); settle() }
            3 -> { a: Any?, b: Any?, c: Any? -> onResult(gen, d, m, key, kind, label, listOf(a, b, c)); settle() }
            else -> {
                skip(d, kind, m, "unsupported callback type ${m.parameterTypes[inputs.size].name}")
                return
            }
        }
        val onFailed = { e: Exception -> onFailure(gen, key, api, inputs, e); settle() }

        pendingCallbacks.incrementAndGet()
        try {
            m.invoke(d.api, *inputs, onComplete, onFailed)
        } catch (e: InvocationTargetException) {
            onFailure(gen, key, api, inputs, e.targetException)
            settle()
        } catch (e: Exception) {
            onFailure(gen, key, api, inputs, e)
            settle()
        }

        if (withTimeoutOrNull(CALL_TIMEOUT_MS) { done.await() } == null) {
            update(key) { it.copy(status = ProbeStatus.TIMEOUT, value = "no callback in ${CALL_TIMEOUT_MS}ms") }
            AppLog.w(TAG, "TIMEOUT ${kind.badge} $api args=${inputs.map(::describe)} (no onComplete/onFailed in ${CALL_TIMEOUT_MS}ms)")
        }
    }

    private fun register(gen: Int, d: Domain, m: Method, inputs: Array<Any?>) {
        val listenerType = m.parameterTypes.last()
        val label = label(m, inputs) + " [${listenerType.simpleName}]"
        val key = key(d, m, label)
        val api = "${d.name}.${m.name}"
        put(ProbeRow(key, d.name, ProbeKind.LISTENER, label, permissionsOf(d, m)))

        val proxy = Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType)) { self, method, args ->
            when (method.name) {
                "equals" -> self === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(self)
                "toString" -> "ProbeListener($api)"
                "onFailed" -> {
                    onFailure(gen, key, "$api#onFailed", inputs, args?.getOrNull(0) as? Throwable ?: IllegalStateException("onFailed(null)"))
                    null
                }
                else -> {
                    onListenerUpdate(gen, key, "${d.name}.${label(m, inputs)}#${method.name}", args?.toList().orEmpty())
                    null
                }
            }
        }

        try {
            m.invoke(d.api, *inputs, proxy)
            update(key) { if (it.status == ProbeStatus.PENDING) it.copy(status = ProbeStatus.LISTENING) else it }
            registrations += Registration(api, d.api, unregisterMethodFor(d, m), arrayOf(*inputs, proxy))
            AppLog.i(TAG, "register ok $api args=${inputs.map(::describe)}")
        } catch (e: InvocationTargetException) {
            onFailure(gen, key, api, inputs, e.targetException)
        } catch (e: Exception) {
            onFailure(gen, key, api, inputs, e)
        }
    }

    private fun unregisterAll() {
        if (registrations.isEmpty()) return
        var ok = 0
        for (r in registrations) {
            try {
                if (r.unregister == null) {
                    AppLog.w(TAG, "no unregister method for ${r.label}")
                } else {
                    r.unregister.invoke(r.api, *r.args)
                    ok++
                }
            } catch (e: Exception) {
                val cause = (e as? InvocationTargetException)?.targetException ?: e
                AppLog.e(TAG, "unregister failed ${r.label} ${VehicleErrors.describe(cause)}", cause)
            }
        }
        AppLog.i(TAG, "unregistered $ok/${registrations.size} listeners")
        registrations.clear()
        onListenersRemoved()
    }

    // ---- 결과 처리 --------------------------------------------------------------------------

    private fun onResult(gen: Int, d: Domain, m: Method, key: String, kind: ProbeKind, label: String, args: List<Any?>) {
        if (gen != generation) return
        if (kind == ProbeKind.CAPABILITY) {
            val found = args.flatMap(::areasIn)
            if (found.isNotEmpty()) capabilityAreas.getOrPut(d.name) { ConcurrentHashMap() }[m.name] = found
        }
        val value = args.joinToString(" | ") { describe(it) }
        val wasTimeout = _rows.value[key]?.status == ProbeStatus.TIMEOUT
        update(key) { it.copy(status = ProbeStatus.OK, value = value, updates = it.updates + 1, updatedAt = System.currentTimeMillis()) }
        AppLog.i(TAG, "${kind.badge} ${key.substringBefore('.')}.$label → $value${if (wasTimeout) " (late, after timeout)" else ""}")
    }

    private fun onListenerUpdate(gen: Int, key: String, callback: String, args: List<Any?>) {
        if (gen != generation) return
        val value = args.joinToString(" | ") { describe(it) }
        val previous = _rows.value[key]
        update(key) { it.copy(status = ProbeStatus.OK, value = value, updates = it.updates + 1, updatedAt = System.currentTimeMillis()) }
        // 같은 값 반복 수신은 카운트만 올리고 로그는 값이 바뀔 때만
        if (previous?.value != value) AppLog.i(TAG, "LIVE $callback → $value (update #${(previous?.updates ?: 0) + 1})")
    }

    private fun onFailure(gen: Int, key: String, api: String, inputs: Array<Any?>, e: Throwable) {
        if (gen != generation) return
        val code = VehicleErrors.errorCodeOf(e)
        update(key) {
            it.copy(
                status = ProbeStatus.FAILED,
                value = "${code?.name ?: e.javaClass.simpleName}: ${e.message}",
                updatedAt = System.currentTimeMillis(),
            )
        }
        AppLog.e(TAG, "onFailed api=$api args=${inputs.map(::describe)} ${VehicleErrors.describe(e)}", e)
    }

    private fun areasIn(value: Any?): List<Area> = when (value) {
        is Area -> listOf(value)
        is Map<*, *> -> value.keys.filterIsInstance<Area>()
        is Collection<*> -> value.filterIsInstance<Area>()
        else -> emptyList()
    }

    // ---- 리플렉션 헬퍼 -----------------------------------------------------------------------

    private fun discoverDomains(): List<Domain> = Vehicle::class.java.methods
        .filter {
            it.parameterCount == 0 && it.name.startsWith("get") &&
                it.returnType.isInterface && it.returnType.name.startsWith("ai.pleos.playground.vehicle.api.")
        }
        .sortedBy { it.name }
        .mapNotNull { getter ->
            try {
                Domain(getter.name.removePrefix("get"), getter.invoke(vehicle)!!, getter.returnType)
            } catch (e: Exception) {
                val cause = (e as? InvocationTargetException)?.targetException ?: e
                AppLog.e(TAG, "domain getter ${getter.name} threw ${VehicleErrors.describe(cause)}", cause)
                null
            }
        }

    private fun Domain.abstractMethods() = iface.methods.filter { Modifier.isAbstract(it.modifiers) }

    /** 같은 이름의 오버로드 중 정보가 가장 많은 것(Function3 > Function2 > Function1) 하나만. */
    private fun capabilityMethods(d: Domain) = d.abstractMethods()
        .filter { it.name.startsWith("check") && it.name.endsWith("Capabilities") && it.parameterCount == 2 }
        .groupBy { it.name }.values
        .map { overloads -> overloads.maxBy { arityOf(it.parameterTypes[0]) } }
        .sortedBy { it.name }

    private fun getterMethods(d: Domain) = d.abstractMethods()
        .filter {
            GETTER.matches(it.name) && it.parameterCount >= 2 &&
                it.parameterTypes.takeLast(2).all { t -> t == Function1::class.java }
        }
        .sortedBy { it.name }

    private fun registerMethods(d: Domain) = d.abstractMethods()
        .filter {
            it.name.startsWith("register") && it.parameterCount >= 1 &&
                VehicleListener::class.java.isAssignableFrom(it.parameterTypes.last())
        }
        .sortedBy { it.name }

    private fun unregisterMethodFor(d: Domain, register: Method): Method? = d.iface.methods.firstOrNull {
        it.name == "un" + register.name && it.parameterTypes.contentEquals(register.parameterTypes)
    }

    /**
     * Area 파라미터 → 이름이 가장 비슷한 capability(같은 도메인, 해당 Area 타입을 보고한 것)의 Area들,
     * enum 파라미터 → 첫 값(TemperatureUnit.CELSIUS 등).
     */
    private fun inputCombos(d: Domain, m: Method, types: List<Class<*>>): Pair<List<Array<Any?>>, String?> {
        var combos = listOf(emptyList<Any?>())
        for (t in types) {
            val options: List<Any?> = when {
                Area::class.java.isAssignableFrom(t) -> {
                    val cap = matchCapability(d, m, t)
                        ?: return emptyList<Array<Any?>>() to "no capability in ${d.name} reported ${t.simpleName}"
                    AppLog.d(TAG, "area source ${d.name}.${m.name} ← ${cap.first}")
                    cap.second
                }
                t.isEnum -> listOf(t.enumConstants!!.first())
                else -> return emptyList<Array<Any?>>() to "unsupported param type ${t.simpleName}"
            }
            combos = combos.flatMap { c -> options.map { c + it } }
        }
        return combos.map { it.toTypedArray() } to null
    }

    /** getDoorLockState ↔ checkDoorLockCapabilities 처럼 CamelCase 토큰 Jaccard 유사도가 가장 높은 capability. */
    private fun matchCapability(d: Domain, m: Method, areaType: Class<*>): Pair<String, List<Area>>? {
        val target = tokens(m.name.replace(Regex("^(get|is|has|register)(Current)?"), "").removeSuffix("Listener"))
        return capabilityAreas[d.name].orEmpty()
            .mapValues { (_, list) -> list.filter { areaType.isInstance(it) } }
            .filterValues { it.isNotEmpty() }
            .maxByOrNull { (cap, _) ->
                val stem = tokens(cap.removePrefix("check").removeSuffix("Capabilities"))
                (target intersect stem).size.toDouble() / (target union stem).size
            }
            ?.toPair()
    }

    private fun tokens(name: String) = Regex("[A-Z][a-z0-9]*").findAll(name).map { it.value }.toSet()

    /** Impl 메서드의 @RequiresCustomPermission(RUNTIME 보존, 단 Kotlin internal이라 이름으로 조회). */
    private fun permissionsOf(d: Domain, m: Method): List<String> = runCatching {
        val annotationType = PERMISSION_ANNOTATION ?: return emptyList()
        val annotation = d.api.javaClass.getMethod(m.name, *m.parameterTypes).getAnnotation(annotationType)
            ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val names = annotationType.getMethod("permissions").invoke(annotation) as Array<String>
        names.map { it.removePrefix("pleos.car.permission.") }
    }.getOrNull().orEmpty()

    private fun skip(d: Domain, kind: ProbeKind, m: Method, reason: String) {
        val label = m.name
        put(ProbeRow(key(d, m, label), d.name, kind, label, permissionsOf(d, m), ProbeStatus.SKIPPED, reason))
        AppLog.w(TAG, "SKIP ${kind.badge} ${d.name}.${m.name}: $reason")
    }

    private fun label(m: Method, inputs: Array<Any?>) =
        if (inputs.isEmpty()) m.name else "${m.name}(${inputs.joinToString(", ") { describe(it) }})"

    private fun key(d: Domain, m: Method, label: String) =
        "${d.name}.$label#${m.parameterTypes.joinToString(",") { it.simpleName }}"

    private fun put(row: ProbeRow) = _rows.update { it + (row.key to row) }

    private fun update(key: String, f: (ProbeRow) -> ProbeRow) =
        _rows.update { rows -> rows[key]?.let { rows + (key to f(it)) } ?: rows }

    private fun arityOf(t: Class<*>) = when (t) {
        Function1::class.java -> 1
        Function2::class.java -> 2
        Function3::class.java -> 3
        else -> 0
    }

    companion object {
        private const val TAG = "Vehicle"
        private const val CALL_TIMEOUT_MS = 5_000L
        private const val CONCURRENCY = 8
        private val GETTER = Regex("^(get|is|has)[A-Z].*")

        @Suppress("UNCHECKED_CAST")
        private val PERMISSION_ANNOTATION: Class<out Annotation>? = runCatching {
            Class.forName("ai.pleos.playground.vehicle.permission.RequiresCustomPermission") as Class<out Annotation>
        }.getOrNull()
    }
}

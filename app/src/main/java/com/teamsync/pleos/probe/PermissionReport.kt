package com.teamsync.pleos.probe

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import com.teamsync.pleos.log.AppLog

data class PermissionState(
    val name: String,
    val granted: Boolean,
    /** 기기에 정의된 보호 수준 (normal/dangerous/signature…), 정의 자체가 없으면 "undefined". */
    val protection: String,
)

/** 매니페스트에 선언한 `pleos.car.permission.*`의 부여 상태와 보호 수준을 조사·로그. */
object PermissionReport {
    private const val TAG = "Perm"
    const val PREFIX = "pleos.car.permission."

    fun declared(context: Context): List<String> {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        return info.requestedPermissions.orEmpty().filter { it.startsWith(PREFIX) }.sorted()
    }

    fun inspect(context: Context): List<PermissionState> = declared(context).map { name ->
        val granted = context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
        PermissionState(name, granted, protectionOf(context, name))
    }.also { states ->
        states.forEach { AppLog.i(TAG, "${it.name.removePrefix(PREFIX)} granted=${it.granted} protection=${it.protection}") }
    }

    private fun protectionOf(context: Context, name: String): String = try {
        val info = context.packageManager.getPermissionInfo(name, 0)
        val base = when (info.protection) {
            PermissionInfo.PROTECTION_NORMAL -> "normal"
            PermissionInfo.PROTECTION_DANGEROUS -> "dangerous"
            PermissionInfo.PROTECTION_SIGNATURE -> "signature"
            PermissionInfo.PROTECTION_SIGNATURE_OR_SYSTEM -> "signatureOrSystem"
            else -> "protection=${info.protection}"
        }
        val flags = info.protectionFlags
        buildList {
            add(base)
            if (flags and PermissionInfo.PROTECTION_FLAG_PRIVILEGED != 0) add("privileged")
            if (flags and PermissionInfo.PROTECTION_FLAG_DEVELOPMENT != 0) add("development")
            if (flags and PermissionInfo.PROTECTION_FLAG_APPOP != 0) add("appop")
        }.joinToString("|") + " (owner=${info.packageName})"
    } catch (e: PackageManager.NameNotFoundException) {
        "undefined"
    }
}

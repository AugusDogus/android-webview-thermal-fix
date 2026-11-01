package local.px6.thermal

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference

private const val POWER_MANAGER = "Landroid/os/PowerManager;"
private const val HELPER = "Llocal/px6/thermal/ThermalCompatibility;"

@Suppress("unused")
val thermalCompatibilityPatch = bytecodePatch(
    name = "WebView: tolerate missing thermal service",
    description = "Prevents PX6 firmware's missing thermal service from crashing WebView clients.",
) {
    compatibleWith(Compatibility(
        name = "Android System WebView",
        packageName = "com.google.android.webview",
        apkFileType = ApkFileType.APK,
        targets = listOf(AppTarget(version = "156.0.8078.48"), AppTarget(version = "156.0.8078.49")),
    ))
    extendWith("extensions/thermal-compatibility.dex")
    execute {
        val monitor = mutableClassDefBy("Lorg/chromium/base/PowerMonitor;")
        val expected = mapOf(
            "addThermalStatusListener" to listOf("Landroid/os/PowerManager\$OnThermalStatusChangedListener;"),
            "getCurrentThermalStatus" to emptyList(),
        )
        val targets = monitor.methods.flatMap { method ->
            method.implementation?.instructions?.mapIndexedNotNull { index, instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    ?: return@mapIndexedNotNull null
                val parameters = expected[reference.name] ?: return@mapIndexedNotNull null
                if (reference.definingClass != POWER_MANAGER || reference.parameterTypes != parameters) {
                    return@mapIndexedNotNull null
                }
                Triple(method, index, reference)
            }.orEmpty()
        }
        if (targets.size != 2 || targets.map { it.third.name }.toSet() != expected.keys) {
            throw PatchException("Expected exactly one listener call and one status call in PowerMonitor; found ${targets.size}. WebView was not modified.")
        }
        for ((method, index, reference) in targets) {
            val instruction = method.implementation?.instructions?.get(index)
                ?: throw PatchException("Missing thermal call instruction in ${method.name}")
            val replacement = ImmutableMethodReference(
                HELPER, reference.name, listOf(POWER_MANAGER) + reference.parameterTypes,
                reference.returnType,
            )
            when (instruction) {
                is FiveRegisterInstruction -> method.replaceInstruction(index, BuilderInstruction35c(
                    Opcode.INVOKE_STATIC, instruction.registerCount,
                    instruction.registerC, instruction.registerD, instruction.registerE,
                    instruction.registerF, instruction.registerG, replacement,
                ))
                is RegisterRangeInstruction -> method.replaceInstruction(index, BuilderInstruction3rc(
                    Opcode.INVOKE_STATIC_RANGE, instruction.startRegister, instruction.registerCount,
                    replacement,
                ))
                else -> throw PatchException("Unsupported thermal-call instruction in ${method.name}")
            }
        }
    }
}

package com.terminal.universe.core.security

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.terminal.universe.domain.model.SafetyCheck
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class SafetyManager @Inject constructor(
    private val context: Context
) {
    
    private val dangerousPatterns = listOf(
        DangerousPattern(
            pattern = Regex("rm\\s+-[rf]+\\s+/|rm\\s+-[rf]+\\s+\\*"),
            severity = Severity.CRITICAL,
            message = "⚠️ DANGER: This will delete files permanently!"
        ),
        DangerousPattern(
            pattern = Regex("mkfs|format|dd\\s+if=.*\\s+of=/dev/"),
            severity = Severity.CRITICAL,
            message = "⛔ Format operations are blocked for safety"
        ),
        DangerousPattern(
            pattern = Regex("chmod\\s+777"),
            severity = Severity.HIGH,
            message = "⚠️ WARNING: chmod 777 gives full permissions to everyone"
        ),
        DangerousPattern(
            pattern = Regex(":(){ :\\|:& };:"),
            severity = Severity.CRITICAL,
            message = "⛔ Fork bomb detected and blocked"
        ),
        DangerousPattern(
            pattern = Regex(">\\s*/dev/(mem|port|kmem)"),
            severity = Severity.CRITICAL,
            message = "⛔ Direct hardware access is blocked"
        ),
        DangerousPattern(
            pattern = Regex("wget.*\\|.*sh|curl.*\\|.*sh"),
            severity = Severity.HIGH,
            message = "⚠️ Downloading and executing scripts can be dangerous"
        )
    )
    
    suspend fun checkCommand(command: String): SafetyCheck {
        val lowerCommand = command.lowercase()
        
        // Check against dangerous patterns
        dangerousPatterns.forEach { pattern ->
            if (pattern.pattern.containsMatchIn(lowerCommand)) {
                return SafetyCheck(
                    isSafe = false,
                    reason = pattern.message,
                    requiresBiometric = pattern.severity == Severity.CRITICAL,
                    severity = pattern.severity
                )
            }
        }
        
        // Check for destructive operations on system files
        if (command.contains("rm") || command.contains("mv")) {
            val args = command.split("\\s+".toRegex())
            args.forEach { arg ->
                if (arg.startsWith("/") && isSystemPath(arg)) {
                    return SafetyCheck(
                        isSafe = false,
                        reason = "⚠️ Modifying system files can break your device",
                        requiresBiometric = true,
                        severity = Severity.HIGH
                    )
                }
            }
        }
        
        return SafetyCheck(isSafe = true)
    }
    
    suspend fun requireBiometricAuth(
        title: String,
        subtitle: String
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(
            context as androidx.fragment.app.FragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    continuation.resume(true)
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Don't resume, just wait for another attempt
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    continuation.resume(false)
                }
            }
        )
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricPrompt.AUTHENTICATORS_BIOMETRIC)
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
    
    fun createSandboxedFileSystem(baseDir: File): SandboxedFileSystem {
        return SandboxedFileSystem(baseDir)
    }
    
    private fun isSystemPath(path: String): Boolean {
        val systemPaths = listOf(
            "/system", "/vendor", "/data", "/cache",
            "/sbin", "/etc", "/bin", "/dev"
        )
        return systemPaths.any { path.startsWith(it) }
    }
}

class SandboxedFileSystem(private val baseDir: File) {
    
    fun resolvePath(path: String): File {
        val resolved = if (path.startsWith("/")) {
            File(path)
        } else {
            File(baseDir, path)
        }
        
        return if (isWithinBase(resolved)) resolved else baseDir
    }
    
    fun createFile(path: String): Boolean {
        val file = resolvePath(path)
        return if (isWithinBase(file)) {
            file.createNewFile()
        } else {
            false
        }
    }
    
    fun deleteFile(path: String): Boolean {
        val file = resolvePath(path)
        return if (isWithinBase(file) && file.exists()) {
            file.delete()
        } else {
            false
        }
    }
    
    fun listFiles(path: String): List<File> {
        val dir = resolvePath(path)
        return if (isWithinBase(dir) && dir.isDirectory) {
            dir.listFiles()?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    private fun isWithinBase(file: File): Boolean {
        return file.absolutePath.startsWith(baseDir.absolutePath)
    }
}

data class DangerousPattern(
    val pattern: Regex,
    val severity: Severity,
    val message: String
)

enum class Severity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

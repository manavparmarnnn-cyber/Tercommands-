package com.terminal.universe.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File
import java.util.UUID

@Parcelize
data class TerminalSession(
    val id: String,
    val workingDirectory: File,
    val environment: Map<String, String>,
    val createdAt: Long,
    val tabs: MutableList<TerminalTab> = mutableListOf(),
    var isSplitMode: Boolean = false,
    var splitOrientation: SplitOrientation = SplitOrientation.HORIZONTAL,
    var stats: SessionStats = SessionStats()
) : Parcelable

@Parcelize
data class TerminalTab(
    val id: String,
    val name: String,
    val commandHistory: MutableList<String> = mutableListOf(),
    var currentInput: String = "",
    var fontSize: Float = 14f,
    var colorScheme: ColorScheme = ColorScheme.CLASSIC_GREEN
) : Parcelable

data class TerminalLine(
    val id: String = UUID.randomUUID().toString(),
    val segments: List<TerminalSegment>,
    val timestamp: Long = System.currentTimeMillis()
)

data class TerminalSegment(
    val text: String,
    val style: TextStyle = TextStyle.Default
)

data class Command(
    val name: String,
    val description: String,
    val category: CommandCategory,
    val syntax: String,
    val example: String,
    val flags: List<CommandFlag> = emptyList(),
    val safetyLevel: SafetyLevel = SafetyLevel.SAFE,
    val requiresPackage: String? = null
)

data class CommandFlag(
    val flag: String,
    val description: String,
    val example: String? = null
)

enum class CommandCategory {
    FILE_SYSTEM,
    VIEWING_EDITING,
    PACKAGE_MANAGEMENT,
    DEVELOPMENT,
    NETWORKING,
    PROCESSES,
    COMPRESSION,
    SYSTEM_INFO,
    MISCELLANEOUS
}

enum class SafetyLevel {
    SAFE,
    CAUTION,
    DANGEROUS,
    CRITICAL
}

data class SessionStats(
    val cpuUsage: Float = 0f,
    val memoryUsage: Long = 0,
    val uptime: Long = 0
) {
    val memoryUsagePercent: Float
        get() = memoryUsage.toFloat() / Runtime.getRuntime().maxMemory()
}

data class CommandSuggestion(
    val command: String,
    val description: String,
    val confidence: Float
)

sealed class AIResponse {
    data class TypoCorrection(
        val original: String,
        val correction: String,
        val confidence: Float
    ) : AIResponse()
    
    data class Suggestions(
        val suggestions: List<CommandSuggestion>
    ) : AIResponse()
    
    data class Warning(
        val message: String,
        val severity: Severity
    ) : AIResponse()
    
    data class Explanation(
        val explanation: String
    ) : AIResponse()
    
    data class LearningTip(
        val tips: List<String>
    ) : AIResponse()
}

data class SafetyCheck(
    val isSafe: Boolean,
    val reason: String = "",
    val requiresBiometric: Boolean = false,
    val severity: Severity = Severity.LOW
)

enum class Severity {
    LOW, MEDIUM, HIGH, CRITICAL
}

@Parcelize
data class TextStyle(
    val color: Long = 0xFF00FF00,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val fontSize: Float = 14f
) : Parcelable {
    companion object {
        val Default = TextStyle()
        val Error = TextStyle(color = 0xFFFF0000, isBold = true)
        val Success = TextStyle(color = 0xFF00FF00)
        val Warning = TextStyle(color = 0xFFFFFF00)
        val Prompt = TextStyle(color = 0xFF00FFFF, isBold = true)
    }
}

@Parcelize
enum class ColorScheme {
    CLASSIC_GREEN,
    AMOLED_BLACK,
    GLASSMORPHISM,
    CYBERPUNK,
    PROFESSIONAL_WHITE
}

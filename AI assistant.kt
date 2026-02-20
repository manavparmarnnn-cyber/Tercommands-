package com.terminal.universe.core.ai

import android.content.Context
import com.terminal.universe.domain.model.CommandSuggestion
import com.terminal.universe.domain.model.AIResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class AIAssistant @Inject constructor(
    private val context: Context,
    private val commandDatabase: CommandDatabase,
    private val learningSystem: LearningSystem
) {
    
    private var tflite: Interpreter? = null
    private val commandPatterns = loadCommandPatterns()
    private val dangerousPatterns = loadDangerousPatterns()
    
    init {
        loadModel()
    }
    
    fun analyzeCommand(input: String): Flow<AIResponse> = flow {
        // Check for typos
        val typoCheck = checkForTypos(input)
        if (typoCheck.hasCorrection) {
            emit(AIResponse.TypoCorrection(
                original = input,
                correction = typoCheck.correction,
                confidence = typoCheck.confidence
            ))
        }
        
        // Get suggestions
        val suggestions = suggestCommands(input)
        if (suggestions.isNotEmpty()) {
            emit(AIResponse.Suggestions(suggestions))
        }
        
        // Safety check
        val safetyCheck = checkSafety(input)
        if (!safetyCheck.isSafe) {
            emit(AIResponse.Warning(
                message = safetyCheck.message,
                severity = safetyCheck.severity
            ))
        }
        
        // Explain if needed
        if (shouldExplain(input)) {
            val explanation = explainCommand(input)
            emit(AIResponse.Explanation(explanation))
        }
        
        // Learning suggestions
        val learningTips = learningSystem.getTipsForCommand(input)
        if (learningTips.isNotEmpty()) {
            emit(AIResponse.LearningTip(learningTips))
        }
    }
    
    private fun checkForTypos(input: String): TypoCheckResult {
        val words = input.split("\\s+".toRegex())
        if (words.isEmpty()) return TypoCheckResult()
        
        val command = words[0]
        val knownCommands = commandDatabase.getAllCommands()
        
        // Find closest match
        var bestMatch = ""
        var bestDistance = Int.MAX_VALUE
        
        knownCommands.forEach { known ->
            val distance = levenshteinDistance(command, known.name)
            if (distance < bestDistance && distance <= 2) {
                bestDistance = distance
                bestMatch = known.name
            }
        }
        
        return if (bestMatch.isNotEmpty() && bestMatch != command) {
            TypoCheckResult(
                hasCorrection = true,
                correction = bestMatch + input.substring(command.length),
                confidence = calculateConfidence(bestDistance)
            )
        } else {
            TypoCheckResult()
        }
    }
    
    private fun suggestCommands(input: String): List<CommandSuggestion> {
        val suggestions = mutableListOf<CommandSuggestion>()
        val words = input.split("\\s+".toRegex())
        
        if (words.size == 1 && !input.endsWith(" ")) {
            // Suggest commands based on partial input
            val partial = words[0]
            commandDatabase.searchCommands(partial).forEach { cmd ->
                suggestions.add(
                    CommandSuggestion(
                        command = cmd.name,
                        description = cmd.description,
                        confidence = calculateMatchConfidence(partial, cmd.name)
                    )
                )
            }
        } else if (words.size > 1) {
            // Suggest flags/arguments
            val command = words[0]
            val cmdInfo = commandDatabase.getCommand(command)
            cmdInfo?.commonFlags?.forEach { flag ->
                suggestions.add(
                    CommandSuggestion(
                        command = "$command $flag",
                        description = flag.description,
                        confidence = 0.8f
                    )
                )
            }
        }
        
        return suggestions.sortedByDescending { it.confidence }.take(5)
    }
    
    private fun checkSafety(input: String): SafetyCheck {
        val lowerInput = input.lowercase()
        
        // Check dangerous patterns
        dangerousPatterns.forEach { pattern ->
            if (pattern.regex.containsMatchIn(lowerInput)) {
                return SafetyCheck(
                    isSafe = false,
                    message = pattern.warning,
                    severity = pattern.severity
                )
            }
        }
        
        // Check for recursive deletion
        if (lowerInput.contains("rm -rf /") || lowerInput.contains("rm -rf /*")) {
            return SafetyCheck(
                isSafe = false,
                message = "⚠️ DANGER: This will delete all files! Use with extreme caution.",
                severity = Severity.CRITICAL
            )
        }
        
        // Check for format commands
        if (lowerInput.contains("mkfs") || lowerInput.contains("format")) {
            return SafetyCheck(
                isSafe = false,
                message = "⚠️ WARNING: Formatting operations are blocked for safety",
                severity = Severity.HIGH
            )
        }
        
        return SafetyCheck(isSafe = true)
    }
    
    private fun explainCommand(input: String): String {
        val words = input.split("\\s+".toRegex())
        if (words.isEmpty()) return ""
        
        val command = words[0]
        val cmdInfo = commandDatabase.getCommand(command)
        
        return buildString {
            appendLine("📌 **${cmdInfo?.name ?: command}**")
            appendLine()
            
            if (cmdInfo != null) {
                appendLine("${cmdInfo.description}")
                appendLine()
                
                if (words.size > 1) {
                    appendLine("**Arguments:**")
                    words.drop(1).forEach { arg ->
                        val flagInfo = cmdInfo.flags.find { it.flag == arg }
                        if (flagInfo != null) {
                            appendLine("  • $arg: ${flagInfo.description}")
                        } else {
                            appendLine("  • $arg: (argument)")
                        }
                    }
                }
                
                appendLine()
                appendLine("**Example:** ${cmdInfo.example}")
            } else {
                appendLine("System command - executing in shell")
            }
        }
    }
    
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i-1] == s2[j-1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i-1][j] + 1, dp[i][j-1] + 1),
                    dp[i-1][j-1] + cost
                )
            }
        }
        
        return dp[s1.length][s2.length]
    }
    
    private fun loadModel() {
        try {
            val modelFile = context.assets.openFd("model.tflite")
            val inputStream = FileInputStream(modelFile.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = modelFile.startOffset
            val declaredLength = modelFile.declaredLength
            val modelBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                startOffset,
                declaredLength
            )
            
            tflite = Interpreter(modelBuffer)
        } catch (e: Exception) {
            // Fallback to rule-based system
            e.printStackTrace()
        }
    }
    
    data class TypoCheckResult(
        val hasCorrection: Boolean = false,
        val correction: String = "",
        val confidence: Float = 0f
    )
    
    data class SafetyCheck(
        val isSafe: Boolean,
        val message: String = "",
        val severity: Severity = Severity.LOW
    )
    
    enum class Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}

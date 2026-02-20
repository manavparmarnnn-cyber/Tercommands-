package com.terminal.universe.core.terminal

import android.content.Context
import com.terminal.universe.domain.model.TerminalSession
import com.terminal.universe.domain.model.TerminalTab
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TerminalSessionManager @Inject constructor(
    private val context: Context,
    private val commandExecutor: CommandExecutor,
    private val sessionRestorer: SessionRestorer,
    private val safetyManager: SafetyManager
) {
    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()
    
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()
    
    private val sessionMap = mutableMapOf<String, TerminalSession>()
    private val sessionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        restoreSessions()
    }
    
    fun createSession(
        workingDirectory: File = context.filesDir,
        environment: Map<String, String> = emptyMap()
    ): TerminalSession {
        val sessionId = UUID.randomUUID().toString()
        val session = TerminalSession(
            id = sessionId,
            workingDirectory = workingDirectory,
            environment = environment,
            createdAt = System.currentTimeMillis(),
            tabs = mutableListOf(
                TerminalTab(
                    id = UUID.randomUUID().toString(),
                    name = "terminal",
                    commandHistory = mutableListOf()
                )
            )
        )
        
        sessionMap[sessionId] = session
        _sessions.update { it + session }
        _currentSessionId.value = sessionId
        
        startSessionMonitoring(sessionId)
        return session
    }
    
    fun createTab(sessionId: String, name: String = "terminal"): TerminalTab {
        val session = sessionMap[sessionId] ?: return createSession().tabs.first()
        
        val newTab = TerminalTab(
            id = UUID.randomUUID().toString(),
            name = name,
            commandHistory = mutableListOf()
        )
        
        session.tabs.add(newTab)
        return newTab
    }
    
    fun executeCommand(
        sessionId: String,
        tabId: String,
        command: String
    ): Flow<CommandResult> = flow {
        val session = sessionMap[sessionId] 
            ?: throw IllegalStateException("Session not found")
        
        val tab = session.tabs.find { it.id == tabId }
            ?: throw IllegalStateException("Tab not found")
        
        // Safety check
        val safetyCheck = safetyManager.checkCommand(command)
        if (!safetyCheck.isSafe) {
            emit(CommandResult.Blocked(command, safetyCheck.reason))
            return@flow
        }
        
        // Add to history
        tab.commandHistory.add(command)
        
        // Execute with monitoring
        commandExecutor.execute(command, session.workingDirectory)
            .cancellable()
            .collect { result ->
                emit(result)
            }
    }.flowOn(Dispatchers.IO)
    
    fun closeSession(sessionId: String) {
        sessionMap.remove(sessionId)?.let {
            _sessions.update { sessions -> sessions.filter { it.id != sessionId } }
            if (_currentSessionId.value == sessionId) {
                _currentSessionId.value = _sessions.value.firstOrNull()?.id
            }
        }
    }
    
    fun splitTerminal(
        sessionId: String,
        orientation: SplitOrientation = SplitOrientation.HORIZONTAL
    ) {
        val session = sessionMap[sessionId] ?: return
        session.isSplitMode = true
        session.splitOrientation = orientation
    }
    
    private fun startSessionMonitoring(sessionId: String) {
        sessionScope.launch {
            while (isActive) {
                delay(1000) // Monitor every second
                val session = sessionMap[sessionId] ?: break
                
                // Update session stats
                updateSessionStats(session)
                
                // Check for idle timeout
                if (session.isIdleTimeout) {
                    // Handle idle session
                }
            }
        }
    }
    
    private fun updateSessionStats(session: TerminalSession) {
        // Update CPU, memory usage etc.
        session.stats = SessionStats(
            cpuUsage = getCpuUsage(),
            memoryUsage = getMemoryUsage(),
            uptime = System.currentTimeMillis() - session.createdAt
        )
    }
    
    private fun restoreSessions() {
        sessionScope.launch {
            val restoredSessions = sessionRestorer.restoreSessions()
            restoredSessions.forEach { session ->
                sessionMap[session.id] = session
                _sessions.update { it + session }
            }
        }
    }
    
    private fun getCpuUsage(): Float {
        return try {
            val process = Runtime.getRuntime().exec("top -bn1")
            val reader = process.inputStream.bufferedReader()
            val output = reader.readText()
            // Parse CPU usage
            0.0f
        } catch (e: Exception) {
            0.0f
        }
    }
    
    private fun getMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }
}

enum class SplitOrientation {
    HORIZONTAL, VERTICAL
}

sealed class CommandResult {
    data class Output(val text: String) : CommandResult()
    data class Error(val message: String) : CommandResult()
    data class Blocked(val command: String, val reason: String) : CommandResult()
    object Complete : CommandResult()
}

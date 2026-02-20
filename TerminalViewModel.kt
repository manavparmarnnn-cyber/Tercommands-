package com.terminal.universe.presentation.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terminal.universe.core.ai.AIAssistant
import com.terminal.universe.core.learning.LearningSystem
import com.terminal.universe.core.security.SafetyManager
import com.terminal.universe.core.terminal.TerminalSessionManager
import com.terminal.universe.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val sessionManager: TerminalSessionManager,
    private val aiAssistant: AIAssistant,
    private val learningSystem: LearningSystem,
    private val safetyManager: SafetyManager
) : ViewModel() {
    
    private val _terminalState = MutableStateFlow(TerminalUiState())
    val terminalState: StateFlow<TerminalUiState> = _terminalState.asStateFlow()
    
    private val _suggestions = MutableStateFlow<List<CommandSuggestion>>(emptyList())
    val suggestions: StateFlow<List<CommandSuggestion>> = _suggestions.asStateFlow()
    
    private val _aiResponse = MutableStateFlow<AIResponse?>(null)
    val aiResponse: StateFlow<AIResponse?> = _aiResponse.asStateFlow()
    
    init {
        initializeTerminal()
    }
    
    private fun initializeTerminal() {
        viewModelScope.launch {
            // Create initial session
            val session = sessionManager.createSession()
            _terminalState.update { it.copy(
                sessions = listOf(session),
                currentSessionId = session.id
            )}
            
            // Monitor sessions
            sessionManager.sessions
                .onEach { sessions ->
                    _terminalState.update { state ->
                        state.copy(sessions = sessions)
                    }
                }
                .launchIn(viewModelScope)
            
            // Monitor current session
            sessionManager.currentSessionId
                .onEach { sessionId ->
                    _terminalState.update { state ->
                        state.copy(currentSessionId = sessionId)
                    }
                }
                .launchIn(viewModelScope)
            
            // Start performance monitoring
            startPerformanceMonitoring()
        }
    }
    
    fun updateInput(input: String) {
        _terminalState.update { state ->
            val updatedSessions = state.sessions.map { session ->
                if (session.id == state.currentSessionId) {
                    session.tabs.find { it.id == state.currentTabId }?.currentInput = input
                }
                session
            }
            state.copy(sessions = updatedSessions)
        }
        
        // Get AI suggestions
        if (input.isNotBlank()) {
            viewModelScope.launch {
                aiAssistant.analyzeCommand(input).collect { response ->
                    when (response) {
                        is AIResponse.Suggestions -> {
                            _suggestions.value = response.suggestions
                        }
                        is AIResponse.TypoCorrection,
                        is AIResponse.Warning,
                        is AIResponse.Explanation,
                        is AIResponse.LearningTip -> {
                            _aiResponse.value = response
                        }
                    }
                }
            }
        } else {
            _suggestions.value = emptyList()
        }
    }
    
    fun executeCommand() {
        viewModelScope.launch {
            val state = _terminalState.value
            val sessionId = state.currentSessionId ?: return@launch
            val session = state.sessions.find { it.id == sessionId } ?: return@launch
            val currentTab = session.tabs.find { it.id == state.currentTabId } ?: return@launch
            val command = currentTab.currentInput
            
            if (command.isBlank()) return@launch
            
            // Add to output
            addTerminalLine(
                listOf(
                    TerminalSegment("$ ", TextStyle.Prompt),
                    TerminalSegment(command, TextStyle.Default)
                )
            )
            
            // Safety check
            val safetyCheck = safetyManager.checkCommand(command)
            if (!safetyCheck.isSafe) {
                if (safetyCheck.requiresBiometric) {
                    val authenticated = safetyManager.requireBiometricAuth(
                        "Confirm Action",
                        safetyCheck.reason
                    )
                    if (!authenticated) {
                        addTerminalLine(
                            listOf(
                                TerminalSegment("⛔ Action blocked: ", TextStyle.Error),
                                TerminalSegment("Biometric authentication failed", TextStyle.Error)
                            )
                        )
                        return@launch
                    }
                } else {
                    addTerminalLine(
                        listOf(
                            TerminalSegment("⚠️ Safety Warning: ", TextStyle.Warning),
                            TerminalSegment(safetyCheck.reason, TextStyle.Warning)
                        )
                    )
                    return@launch
                }
            }
            
            // Execute command
            sessionManager.executeCommand(sessionId, currentTab.id, command)
                .catch { error ->
                    addTerminalLine(
                        listOf(
                            TerminalSegment("Error: ", TextStyle.Error),
                            TerminalSegment(error.message ?: "Unknown error", TextStyle.Error)
                        )
                    )
                }
                .collect { result ->
                    when (result) {
                        is CommandResult.Output -> {
                            addTerminalLine(listOf(TerminalSegment(result.text)))
                        }
                        is CommandResult.Error -> {
                            addTerminalLine(
                                listOf(
                                    TerminalSegment("Error: ", TextStyle.Error),
                                    TerminalSegment(result.message, TextStyle.Error)
                                )
                            )
                        }
                        is CommandResult.Blocked -> {
                            addTerminalLine(
                                listOf(
                                    TerminalSegment("⛔ Blocked: ", TextStyle.Error),
                                    TerminalSegment(result.reason, TextStyle.Error)
                                )
                            )
                        }
                        is CommandResult.Complete -> {
                            // Command completed successfully
                            learningSystem.recordCommandExecution(command, true)
                            addTerminalLine(
                                listOf(
                                    TerminalSegment("✓ Command completed", TextStyle.Success)
                                )
                            )
                        }
                    }
                }
            
            // Clear input
            clearInput()
            
            // Get learning tips
            val tips = learningSystem.getTipsForCommand(command)
            if (tips.isNotEmpty()) {
                _aiResponse.value = AIResponse.LearningTip(tips)
            }
        }
    }
    
    fun previousCommand() {
        val state = _terminalState.value
        val session = state.sessions.find { it.id == state.currentSessionId } ?: return
        val tab = session.tabs.find { it.id == state.currentTabId } ?: return
        
        if (tab.commandHistory.isNotEmpty()) {
            val lastCommand = tab.commandHistory.lastOrNull()
            lastCommand?.let {
                tab.currentInput = it
            }
        }
    }
    
    fun nextCommand() {
        // Implement forward history navigation
    }
    
    fun createSession() {
        viewModelScope.launch {
            sessionManager.createSession()
        }
    }
    
    fun switchSession(sessionId: String) {
        _terminalState.update { state ->
            state.copy(currentSessionId = sessionId)
        }
    }
    
    fun createTab() {
        val state = _terminalState.value
        val sessionId = state.currentSessionId ?: return
        viewModelScope.launch {
            sessionManager.createTab(sessionId, "terminal")
        }
    }
    
    fun splitTerminal() {
        val state = _terminalState.value
        val sessionId = state.currentSessionId ?: return
        viewModelScope.launch {
            sessionManager.splitTerminal(sessionId)
            _terminalState.update { it.copy(isSplitMode = true) }
        }
    }
    
    fun togglePerformanceOverlay() {
        _terminalState.update { state ->
            state.copy(showPerformanceOverlay = !state.showPerformanceOverlay)
        }
    }
    
    fun applySuggestion(suggestion: String) {
        _terminalState.update { state ->
            val updatedSessions = state.sessions.map { session ->
                if (session.id == state.currentSessionId) {
                    session.tabs.find { it.id == state.currentTabId }?.currentInput = suggestion
                }
                session
            }
            state.copy(sessions = updatedSessions)
        }
        _aiResponse.value = null
    }
    
    fun insertText(text: String) {
        val state = _terminalState.value
        val session = state.sessions.find { it.id == state.currentSessionId } ?: return
        val tab = session.tabs.find { it.id == state.currentTabId } ?: return
        
        tab.currentInput += when (text) {
            "ESC" -> "\u001B"
            "CTRL" -> "" // Handle as modifier
            "TAB" -> "\t"
            "↑" -> "" // Handle navigation
            "↓" -> "" // Handle navigation
            "←" -> "" // Handle cursor movement
            "→" -> "" // Handle cursor movement
            else -> text.lowercase()
        }
        
        _terminalState.update { state.copy(sessions = state.sessions) }
    }
    
    fun clearAIResponse() {
        _aiResponse.value = null
    }
    
    private fun addTerminalLine(segments: List<TerminalSegment>) {
        _terminalState.update { state ->
            val updatedOutput = state.output.toMutableList()
            updatedOutput.add(TerminalLine(segments = segments))
            state.copy(output = updatedOutput)
        }
    }
    
    private fun clearInput() {
        val state = _terminalState.value
        val session = state.sessions.find { it.id == state.currentSessionId } ?: return
        val tab = session.tabs.find { it.id == state.currentTabId } ?: return
        
        tab.currentInput = ""
        _terminalState.update { state.copy(sessions = state.sessions) }
        _suggestions.value = emptyList()
    }
    
    private fun startPerformanceMonitoring() {
        viewModelScope.launch {
            while (true) {
                val stats = SessionStats(
                    cpuUsage = getCpuUsage(),
                    memoryUsage = getMemoryUsage(),
                    uptime = System.currentTimeMillis() - (terminalState.value.sessions.firstOrNull()?.createdAt ?: 0)
                )
                
                _terminalState.update { state ->
                    state.copy(stats = stats)
                }
                
                delay(1000) // Update every second
            }
        }
    }
    
    private fun getCpuUsage(): Float {
        return try {
            val process = Runtime.getRuntime().exec("top -bn1")
            val reader = process.inputStream.bufferedReader()
            val output = reader.readText()
            // Parse CPU usage - simplified
            0.5f
        } catch (e: Exception) {
            0f
        }
    }
    
    private fun getMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }
}

data class TerminalUiState(
    val sessions: List<TerminalSession> = emptyList(),
    val currentSessionId: String? = null,
    val currentTabId: String? = null,
    val output: List<TerminalLine> = emptyList(),
    val secondaryOutput: List<TerminalLine> = emptyList(),
    val isSplitMode: Boolean = false,
    val showPerformanceOverlay: Boolean = false,
    val stats: SessionStats = SessionStats()
)

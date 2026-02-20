package com.terminal.universe.core.terminal

import android.content.Context
import com.terminal.universe.domain.model.Command
import com.terminal.universe.domain.model.CommandCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandExecutor @Inject constructor(
    private val context: Context,
    private val commandRegistry: CommandRegistry,
    private val aiAssistant: AIAssistant,
    private val safetyManager: SafetyManager
) {
    
    fun execute(
        command: String,
        workingDirectory: File
    ): Flow<CommandResult> = flow {
        val parsed = parseCommand(command)
        
        // Check if it's a built-in command
        val builtIn = commandRegistry.getBuiltIn(parsed.name)
        if (builtIn != null) {
            executeBuiltIn(builtIn, parsed.args, workingDirectory).collect { result ->
                emit(result)
            }
            return@flow
        }
        
        // Execute system command
        executeSystemCommand(command, workingDirectory).collect { result ->
            emit(result)
        }
    }
    
    private fun executeBuiltIn(
        command: Command,
        args: List<String>,
        workingDirectory: File
    ): Flow<CommandResult> = flow {
        emit(CommandResult.Output("Executing ${command.name}..."))
        
        when (command.name) {
            "cd" -> {
                val path = args.firstOrNull() ?: "~"
                val newDir = resolvePath(path, workingDirectory)
                if (newDir.exists() && newDir.isDirectory) {
                    // Update working directory
                    emit(CommandResult.Output("Changed directory to ${newDir.absolutePath}"))
                } else {
                    emit(CommandResult.Error("Directory not found: $path"))
                }
            }
            "clear" -> {
                emit(CommandResult.Output("\u001b[2J\u001b[H")) // ANSI clear screen
            }
            "exit" -> {
                emit(CommandResult.Output("Session terminated"))
            }
            // Add more built-ins
        }
        
        emit(CommandResult.Complete)
    }
    
    private suspend fun executeSystemCommand(
        command: String,
        workingDirectory: File
    ): Flow<CommandResult> = withContext(Dispatchers.IO) {
        flow {
            val process = ProcessBuilder()
                .command("/system/bin/sh", "-c", command)
                .directory(workingDirectory)
                .redirectErrorStream(true)
                .start()
            
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    emit(CommandResult.Output(line))
                }
            }
            
            val exitCode = process.waitFor(60, TimeUnit.SECONDS)
            if (!process.isAlive) {
                if (exitCode == 0) {
                    emit(CommandResult.Complete)
                } else {
                    emit(CommandResult.Error("Command failed with exit code: $exitCode"))
                }
            }
        }
    }
    
    private fun parseCommand(input: String): ParsedCommand {
        val parts = input.trim().split("\\s+".toRegex())
        return ParsedCommand(
            name = parts.firstOrNull() ?: "",
            args = parts.drop(1)
        )
    }
    
    private fun resolvePath(path: String, currentDir: File): File {
        return when {
            path.startsWith("/") -> File(path)
            path == "~" -> context.filesDir
            path.startsWith("~/") -> File(context.filesDir, path.substring(2))
            else -> File(currentDir, path)
        }
    }
    
    data class ParsedCommand(
        val name: String,
        val args: List<String>
    )
}

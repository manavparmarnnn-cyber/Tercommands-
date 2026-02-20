package com.terminal.universe.core.plugin

import dalvik.system.DexClassLoader
import java.io.File
import java.net.URLClassLoader

class PluginManager(private val context: Context) {
    
    private val plugins = mutableMapOf<String, Plugin>()
    private val classLoaders = mutableMapOf<String, ClassLoader>()
    
    fun loadPlugin(pluginFile: File): Plugin? {
        return try {
            val dexLoader = DexClassLoader(
                pluginFile.absolutePath,
                context.cacheDir.absolutePath,
                null,
                context.classLoader
            )
            
            // Load plugin class
            val pluginClass = dexLoader.loadClass("com.plugin.MainPlugin")
            val plugin = pluginClass.newInstance() as Plugin
            
            plugins[plugin.id] = plugin
            classLoaders[plugin.id] = dexLoader
            
            plugin.onLoad()
            plugin
        } catch (e: Exception) {
            Timber.e(e, "Failed to load plugin")
            null
        }
    }
    
    fun getPlugin(id: String): Plugin? = plugins[id]
    
    fun unloadPlugin(id: String) {
        plugins[id]?.onUnload()
        plugins.remove(id)
        classLoaders.remove(id)
    }
    
    fun executePluginHook(
        hookPoint: HookPoint,
        context: PluginContext
    ): PluginResult {
        val results = mutableListOf<Any>()
        
        plugins.values.forEach { plugin ->
            try {
                when (hookPoint) {
                    HookPoint.BEFORE_COMMAND -> {
                        plugin.beforeCommand(context)?.let { results.add(it) }
                    }
                    HookPoint.AFTER_COMMAND -> {
                        plugin.afterCommand(context)?.let { results.add(it) }
                    }
                    HookPoint.ON_OUTPUT -> {
                        plugin.onOutput(context)?.let { results.add(it) }
                    }
                    HookPoint.ON_ERROR -> {
                        plugin.onError(context)?.let { results.add(it) }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Plugin execution failed")
            }
        }
        
        return PluginResult(results)
    }
}

interface Plugin {
    val id: String
    val name: String
    val version: String
    
    fun onLoad()
    fun onUnload()
    fun beforeCommand(context: PluginContext): Any?
    fun afterCommand(context: PluginContext): Any?
    fun onOutput(context: PluginContext): Any?
    fun onError(context: PluginContext): Any?
    fun getSettingsScreen(): @Composable (() -> Unit)?
}

enum class HookPoint {
    BEFORE_COMMAND,
    AFTER_COMMAND,
    ON_OUTPUT,
    ON_ERROR
}

data class PluginContext(
    val command: String,
    val sessionId: String,
    val workingDirectory: File,
    val environment: Map<String, String>
)

data class PluginResult(
    val data: List<Any>
)

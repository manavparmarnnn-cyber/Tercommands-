package com.terminal.universe.core.learning

import com.terminal.universe.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LearningSystem @Inject constructor(
    private val userProgress: UserProgressRepository,
    private val achievementManager: AchievementManager
) {
    
    private val _currentLevel = MutableStateFlow(UserLevel.BEGINNER)
    val currentLevel: StateFlow<UserLevel> = _currentLevel.asStateFlow()
    
    private val _experience = MutableStateFlow(0L)
    val experience: StateFlow<Long> = _experience.asStateFlow()
    
    private val _achievements = MutableStateFlow<List<Achievement>>(emptyList())
    val achievements: StateFlow<List<Achievement>> = _achievements.asStateFlow()
    
    private val _learningPath = MutableStateFlow<LearningPath?>(null)
    val learningPath: StateFlow<LearningPath?> = _learningPath.asStateFlow()
    
    suspend fun recordCommandExecution(command: String, success: Boolean) {
        // Calculate XP
        val xpGain = when {
            command.contains("--help") -> 5
            command.startsWith("git") -> 20
            command.startsWith("docker") -> 30
            else -> 10
        }
        
        addExperience(xpGain)
        
        // Check for achievements
        checkCommandAchievements(command)
        
        // Update learning path
        updateLearningPath(command)
    }
    
    suspend fun addExperience(amount: Long) {
        val newXP = _experience.value + amount
        _experience.value = newXP
        
        // Check for level up
        val newLevel = determineLevel(newXP)
        if (newLevel != _currentLevel.value) {
            levelUp(newLevel)
        }
    }
    
    private fun determineLevel(xp: Long): UserLevel {
        return when {
            xp >= 10000 -> UserLevel.UNIVERSE_LORD
            xp >= 5000 -> UserLevel.GRAND_MASTER
            xp >= 2500 -> UserLevel.ARCHITECT
            xp >= 1000 -> UserLevel.ENGINEER
            xp >= 500 -> UserLevel.DEVELOPER
            xp >= 100 -> UserLevel.HACKER
            else -> UserLevel.BEGINNER
        }
    }
    
    private suspend fun levelUp(newLevel: UserLevel) {
        _currentLevel.value = newLevel
        
        // Unlock features based on level
        when (newLevel) {
            UserLevel.HACKER -> {
                achievementManager.unlockTheme("hacker_green")
            }
            UserLevel.DEVELOPER -> {
                achievementManager.unlockFeature("split_terminal")
            }
            UserLevel.ENGINEER -> {
                achievementManager.unlockFeature("ssh_manager")
            }
            UserLevel.ARCHITECT -> {
                achievementManager.unlockTheme("cyberpunk")
            }
            UserLevel.GRAND_MASTER -> {
                achievementManager.unlockFeature("plugin_system")
            }
            UserLevel.UNIVERSE_LORD -> {
                achievementManager.unlockTheme("universe_lord")
                achievementManager.grantBadge("👑")
            }
            else -> {}
        }
    }
    
    fun getTipsForCommand(command: String): List<String> {
        val tips = mutableListOf<String>()
        
        when {
            command.startsWith("apt install") -> {
                tips.add("💡 Pro tip: Use 'apt search <package>' to find packages")
                tips.add("📚 Try 'apt show <package>' for package details")
            }
            command.startsWith("git") -> {
                tips.add("🔧 Pro tip: Use 'git status' to check your changes")
                tips.add("🌿 Remember to create branches: 'git checkout -b feature'")
            }
            command.contains("rm") -> {
                tips.add("⚠️ Always double-check before deleting files")
                tips.add("💡 Use 'ls' first to see what you're deleting")
            }
        }
        
        return tips
    }
    
    fun getNextLearningObjective(): LearningObjective? {
        return when (_currentLevel.value) {
            UserLevel.BEGINNER -> LearningObjective(
                title = "Master Basic Navigation",
                description = "Learn to navigate the file system using ls, cd, and pwd",
                commands = listOf("ls", "cd", "pwd"),
                xpReward = 50
            )
            UserLevel.HACKER -> LearningObjective(
                title = "File Operations",
                description = "Create, copy, move, and delete files",
                commands = listOf("touch", "cp", "mv", "rm"),
                xpReward = 100
            )
            UserLevel.DEVELOPER -> LearningObjective(
                title = "Version Control with Git",
                description = "Initialize repository and make your first commit",
                commands = listOf("git init", "git add", "git commit"),
                xpReward = 200
            )
            UserLevel.ENGINEER -> LearningObjective(
                title = "Package Management",
                description = "Install and manage software packages",
                commands = listOf("apt update", "apt install", "apt upgrade"),
                xpReward = 300
            )
            else -> null
        }
    }
    
    private suspend fun checkCommandAchievements(command: String) {
        when {
            command == "sudo" -> {
                achievementManager.unlock("Power User", "Used sudo command")
            }
            command.contains("nano") || command.contains("vim") -> {
                achievementManager.unlock("Editor", "Used a text editor")
            }
            command.startsWith("ssh") -> {
                achievementManager.unlock("Remote Access", "Connected via SSH")
            }
            command.startsWith("python") -> {
                achievementManager.unlock("Developer", "Ran Python code")
            }
        }
    }
    
    private fun updateLearningPath(command: String) {
        val path = _learningPath.value ?: createLearningPath()
        
        // Mark command as completed in learning path
        path.modules.forEach { module ->
            module.lessons.forEach { lesson ->
                if (lesson.command == command) {
                    lesson.completed = true
                }
            }
        }
        
        _learningPath.value = path
    }
    
    private fun createLearningPath(): LearningPath {
        return LearningPath(
            id = "default",
            name = "Linux Mastery Path",
            modules = listOf(
                LearningModule(
                    id = "basics",
                    name = "Command Line Basics",
                    lessons = listOf(
                        Lesson("Navigation", "ls", "Learn to list files"),
                        Lesson("Changing Directories", "cd", "Navigate through folders"),
                        Lesson("Print Working Directory", "pwd", "See your current location")
                    )
                ),
                LearningModule(
                    id = "files",
                    name = "File Management",
                    lessons = listOf(
                        Lesson("Create Files", "touch", "Create empty files"),
                        Lesson("Copy Files", "cp", "Duplicate files and directories"),
                        Lesson("Move Files", "mv", "Move or rename files"),
                        Lesson("Remove Files", "rm", "Delete files (carefully!)")
                    )
                ),
                LearningModule(
                    id = "viewing",
                    name = "Viewing Files",
                    lessons = listOf(
                        Lesson("Display Content", "cat", "Show file contents"),
                        Lesson("Page Through", "less", "View long files page by page"),
                        Lesson("Head", "head", "Show first few lines"),
                        Lesson("Tail", "tail", "Show last few lines")
                    )
                )
            )
        )
    }
}

enum class UserLevel {
    BEGINNER,
    HACKER,
    DEVELOPER,
    ENGINEER,
    ARCHITECT,
    GRAND_MASTER,
    UNIVERSE_LORD
}

data class LearningPath(
    val id: String,
    val name: String,
    val modules: List<LearningModule>
)

data class LearningModule(
    val id: String,
    val name: String,
    val lessons: List<Lesson>
)

data class Lesson(
    val name: String,
    val command: String,
    val description: String,
    var completed: Boolean = false
)

data class LearningObjective(
    val title: String,
    val description: String,
    val commands: List<String>,
    val xpReward: Int
)

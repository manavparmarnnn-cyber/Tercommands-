package com.terminal.universe.presentation.terminal

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = hiltViewModel(),
    onNavigate: (String) -> Unit
) {
    val terminalState by viewModel.terminalState.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val aiResponse by viewModel.aiResponse.collectAsState()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    
    // Handle keyboard visibility
    val isKeyboardOpen = remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar
            TerminalTopBar(
                sessions = terminalState.sessions,
                currentSessionId = terminalState.currentSessionId,
                onSessionChange = { viewModel.switchSession(it) },
                onNewSession = { viewModel.createSession() },
                onSplitTerminal = { viewModel.splitTerminal() }
            )
            
            // Tab Bar
            if (terminalState.sessions.isNotEmpty()) {
                TerminalTabBar(
                    tabs = terminalState.sessions
                        .find { it.id == terminalState.currentSessionId }?.tabs ?: emptyList(),
                    onTabSelected = { viewModel.switchTab(it) },
                    onNewTab = { viewModel.createTab() }
                )
            }
            
            // Terminal Content
            Box(
                modifier = Modifier.weight(1f)
            ) {
                // Split screen handling
                if (terminalState.isSplitMode) {
                    Row(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Left/Top terminal
                        TerminalContent(
                            content = terminalState.output,
                            listState = listState,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Divider
                        VerticalDivider(
                            modifier = Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                        )
                        
                        // Right/Bottom terminal
                        TerminalContent(
                            content = terminalState.secondaryOutput,
                            listState = rememberLazyListState(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    TerminalContent(
                        content = terminalState.output,
                        listState = listState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                // AI Assistant Popup
                if (aiResponse != null) {
                    AIPopup(
                        response = aiResponse!!,
                        onDismiss = { viewModel.clearAIResponse() },
                        onApply = { suggestion ->
                            viewModel.applySuggestion(suggestion)
                        }
                    )
                }
                
                // Performance Overlay
                if (terminalState.showPerformanceOverlay) {
                    PerformanceOverlay(
                        stats = terminalState.stats,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    )
                }
            }
            
            // Suggestion Bar
            if (suggestions.isNotEmpty()) {
                SuggestionBar(
                    suggestions = suggestions,
                    onSuggestionClick = { viewModel.applySuggestion(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                )
            }
            
            // Custom Keyboard Row
            if (isKeyboardOpen.value) {
                TerminalKeyboardRow(
                    onKeyClick = { key ->
                        viewModel.insertText(key)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .animateContentSize()
                )
            }
            
            // Input Field
            TerminalInputField(
                value = terminalState.currentInput,
                onValueChange = { viewModel.updateInput(it) },
                onExecute = { 
                    viewModel.executeCommand()
                    scope.launch {
                        listState.animateScrollToItem(Int.MAX_VALUE)
                    }
                },
                onSwipeUp = { 
                    viewModel.previousCommand() 
                },
                onSwipeDown = { 
                    viewModel.nextCommand() 
                },
                onFocusChange = { focused ->
                    isKeyboardOpen.value = focused
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Floating Action Button
        FloatingActionButton(
            onClick = { viewModel.togglePerformanceOverlay() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = if (terminalState.showPerformanceOverlay) 
                    Icons.Default.VisibilityOff 
                else 
                    Icons.Default.Visibility,
                contentDescription = "Toggle Performance"
            )
        }
    }
}

@Composable
fun TerminalContent(
    content: List<TerminalLine>,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        items(
            items = content,
            key = { it.id }
        ) { line ->
            AnimatedTerminalLine(
                line = line,
                modifier = Modifier.animateItemPlacement()
            )
        }
    }
}

@Composable
fun AnimatedTerminalLine(
    line: TerminalLine,
    modifier: Modifier = Modifier
) {
    val alpha = remember { Animatable(0f) }
    
    LaunchedEffect(line.id) {
        alpha.animateTo(1f, animationSpec = tween(300))
    }
    
    Text(
        text = buildAnnotatedString {
            line.segments.forEach { segment ->
                withStyle(style = segment.style.toSpanStyle()) {
                    append(segment.text)
                }
            }
        },
        modifier = modifier.alpha(alpha.value),
        fontFamily = FontFamily.Monospace,
        fontSize = line.fontSize.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun TerminalKeyboardRow(
    onKeyClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("ESC", "CTRL", "TAB", "↑", "↓", "←", "→").forEach { key ->
                KeyboardKey(
                    key = key,
                    onClick = { onKeyClick(key) }
                )
            }
        }
    }
}

@Composable
fun KeyboardKey(
    key: String,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isPressed) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                        onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = key,
            color = if (isPressed) 
                MaterialTheme.colorScheme.onPrimary 
            else 
                MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AIPopup(
    response: AIResponse,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var offsetY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onDismiss() }
                )
            }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = with(density) { offsetY.toDp() })
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .shadow(16.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = "AI Assistant",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Content
                when (response) {
                    is AIResponse.TypoCorrection -> {
                        Text("Did you mean:")
                        Text(
                            text = response.correction,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Button(
                            onClick = { onApply(response.correction) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Apply Correction")
                        }
                    }
                    is AIResponse.Warning -> {
                        WarningCard(
                            message = response.message,
                            severity = response.severity
                        )
                    }
                    is AIResponse.Explanation -> {
                        Text(response.explanation)
                    }
                    else -> {
                        Text("AI Suggestion")
                    }
                }
            }
        }
    }
}

@Composable
fun PerformanceOverlay(
    stats: SystemStats,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "System Performance",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            PerformanceBar(
                label = "CPU",
                value = stats.cpuUsage,
                color = Color.Cyan
            )
            
            PerformanceBar(
                label = "RAM",
                value = stats.memoryUsagePercent,
                color = Color.Green
            )
            
            Text(
                text = "Uptime: ${formatUptime(stats.uptime)}",
                color = Color.White,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun PerformanceBar(
    label: String,
    value: Float,
    color: Color
) {
    Column {
        Text(
            text = "$label: ${(value * 100).toInt()}%",
            color = Color.White,
            fontSize = 10.sp
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color.Gray.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value)
                    .fillMaxHeight()
                    .background(color)
            )
        }
    }
}

// Extension function to convert TextStyle to SpanStyle
fun TextStyle.toSpanStyle(): SpanStyle {
    return SpanStyle(
        color = this.color,
        fontSize = this.fontSize,
        fontWeight = this.fontWeight,
        fontFamily = this.fontFamily,
        textDecoration = this.textDecoration
    )
}

fun formatUptime(millis: Long): String {
    val seconds = millis / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, secs)
}

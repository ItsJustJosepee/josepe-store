package store.josepe.dev.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import josepe_store.sharedui.generated.resources.Res
import josepe_store.sharedui.generated.resources.ic_logo
import org.jetbrains.compose.resources.painterResource

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun FrameWindowScope.CustomTitleBar(
    windowState: WindowState,
    onClose: () -> Unit,
    title: String = "Josepe Store",
    modifier: Modifier = Modifier
) {
    val isWin = remember { System.getProperty("os.name").lowercase().contains("win") }
    val density = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(density) {
        if (isWin) {
            try {
                val cls = Class.forName("store.josepe.dev.WindowsWindowProcSubclass")
                val field = cls.getField("titleBarHeightPx")
                val heightPx = with(density) { 40.dp.roundToPx() }
                field.set(null, heightPx)
            } catch (e: Exception) {}
        }
    }

    val awtWindow = this.window
    var dragStartMouseX by remember { mutableStateOf(0) }
    var dragStartMouseY by remember { mutableStateOf(0) }
    var dragStartWindowX by remember { mutableStateOf(0) }
    var dragStartWindowY by remember { mutableStateOf(0) }

    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }

    Box {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(MaterialTheme.colorScheme.surface)
                .then(
                    Modifier.pointerInput(awtWindow) {
                        detectDragGestures(
                            onDragStart = { _ ->
                                val mouseLoc = java.awt.MouseInfo.getPointerInfo().location
                                dragStartMouseX = mouseLoc.x
                                dragStartMouseY = mouseLoc.y
                                val winLoc = awtWindow.location
                                dragStartWindowX = winLoc.x
                                dragStartWindowY = winLoc.y
                            },
                            onDragEnd = {
                                // Edge snapping across multi-monitor setups (Linux/Windows/macOS)
                                val mouseLoc = java.awt.MouseInfo.getPointerInfo().location
                                val ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                                val currentDevice = ge.screenDevices.firstOrNull { device ->
                                    device.defaultConfiguration.bounds.contains(mouseLoc)
                                } ?: awtWindow.graphicsConfiguration.device
                                val gc = currentDevice.defaultConfiguration
                                val screenBounds = gc.bounds
                                val insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc)

                                val workX = screenBounds.x + insets.left
                                val workY = screenBounds.y + insets.top
                                val workW = screenBounds.width - insets.left - insets.right
                                val workH = screenBounds.height - insets.top - insets.bottom

                                val snap = 12

                                when {
                                    // Top edge -> maximize
                                    mouseLoc.y <= workY + snap -> {
                                        windowState.placement = WindowPlacement.Maximized
                                    }
                                    // Left edge -> snap left half
                                    mouseLoc.x <= workX + snap -> {
                                        windowState.placement = WindowPlacement.Floating
                                        awtWindow.setBounds(workX, workY, workW / 2, workH)
                                    }
                                    // Right edge -> snap right half
                                    mouseLoc.x >= workX + workW - snap -> {
                                        windowState.placement = WindowPlacement.Floating
                                        awtWindow.setBounds(workX + workW / 2, workY, workW / 2, workH)
                                    }
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val mouseLoc = java.awt.MouseInfo.getPointerInfo().location
                                if (windowState.placement == WindowPlacement.Maximized) {
                                    windowState.placement = WindowPlacement.Floating
                                    val restoredWidth = windowState.size.width.value * density.density
                                    val newX = mouseLoc.x - (restoredWidth / 2).toInt()
                                    awtWindow.setLocation(newX, mouseLoc.y - 20)
                                    dragStartMouseX = mouseLoc.x
                                    dragStartMouseY = mouseLoc.y
                                    dragStartWindowX = newX
                                    dragStartWindowY = mouseLoc.y - 20
                                } else {
                                    val dx = mouseLoc.x - dragStartMouseX
                                    val dy = mouseLoc.y - dragStartMouseY
                                    awtWindow.setLocation(dragStartWindowX + dx, dragStartWindowY + dy)
                                }
                            }
                        )
                    }.pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                                    WindowPlacement.Floating
                                } else {
                                    WindowPlacement.Maximized
                                }
                            }
                        )
                    }
                )
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) {
                                val change = event.changes.firstOrNull()
                                if (change != null && event.button == PointerButton.Secondary) {
                                    menuOffset = DpOffset(change.position.x.toDp(), change.position.y.toDp())
                                    showMenu = true
                                }
                            }
                        }
                    }
                }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            Icon(
                painter = painterResource(Res.drawable.ic_logo),
                contentDescription = "Josepe Store Logo",
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            TitleBarButton(
                onClick = { windowState.isMinimized = true },
                onHoverChanged = { hovered ->
                    if (isWin) {
                        try {
                            val cls = Class.forName("store.josepe.dev.WindowsWindowProcSubclass")
                            val field = cls.getField("isMouseOverMinimize")
                            field.set(null, hovered)
                        } catch (e: Exception) {}
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Minimize,
                    contentDescription = "Minimizar",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            TitleBarButton(
                onClick = {
                    windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                        WindowPlacement.Floating
                    } else {
                        WindowPlacement.Maximized
                    }
                },
                onHoverChanged = { hovered ->
                    if (isWin) {
                        try {
                            val cls = Class.forName("store.josepe.dev.WindowsWindowProcSubclass")
                            val field = cls.getField("isMouseOverMaximize")
                            field.set(null, hovered)
                        } catch (e: Exception) {}
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.CropSquare,
                    contentDescription = "Maximizar",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            TitleBarButton(
                onClick = onClose,
                hoverColor = Color.Red.copy(alpha = 0.85f),
                onHoverChanged = { hovered ->
                    if (isWin) {
                        try {
                            val cls = Class.forName("store.josepe.dev.WindowsWindowProcSubclass")
                            val field = cls.getField("isMouseOverClose")
                            field.set(null, hovered)
                        } catch (e: Exception) {}
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            offset = menuOffset
        ) {
            DropdownMenuItem(
                text = { Text(if (windowState.placement == WindowPlacement.Maximized) "Restaurar" else "Maximizar") },
                onClick = {
                    windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                        WindowPlacement.Floating
                    } else {
                        WindowPlacement.Maximized
                    }
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Minimizar") },
                onClick = {
                    windowState.isMinimized = true
                    showMenu = false
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Cerrar") },
                onClick = {
                    onClose()
                    showMenu = false
                }
            )
        }
    }
}

@Composable
fun TitleBarButton(
    onClick: () -> Unit,
    hoverColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    onHoverChanged: (Boolean) -> Unit = {},
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    LaunchedEffect(isHovered) {
        onHoverChanged(isHovered)
    }

    Box(
        modifier = Modifier
            .size(32.dp)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .background(if (isHovered) hoverColor else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

package com.example.indoornavigation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A modal component that slides up from the bottom of the screen
 * and can be dismissed by tapping outside or sliding down
 */
@Composable
fun Modal(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    // Create a MutableTransitionState to control the animation
    val visibleState = remember { MutableTransitionState(false) }
    
    // Start the entrance animation
    LaunchedEffect(Unit) {
        visibleState.targetState = true
    }
    
    // Full screen dialog with transparent background
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        // Animated visibility container
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = fadeOut(animationSpec = tween(durationMillis = 300))
        ) {
            // Semi-transparent scrim that fills the screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { 
                        visibleState.targetState = false
                        onDismiss()
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                // Modal content with separate click handler to prevent dismissal when clicking inside
                AnimatedVisibility(
                    visibleState = visibleState,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(durationMillis = 300)
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(durationMillis = 300)
                    )
                ) {
                    Card(
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = {}, enabled = false) // Consume clicks without action
                    ) {
                        Surface {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                content()
                            }
                        }
                    }
                }
            }
        }
    }
}
package com.baklavapp.reachlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.baklavapp.reachlauncher.core.designsystem.theme.ReachTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single Activity, registered as the HOME screen in the manifest.
 *
 * Every screen (Home, intent detail, search, All Apps, Appearance) is a *state* of one
 * Compose tree, not a separate Activity or route. M0 only shows a placeholder.
 *
 * Flutter analogy: `MainActivity` is the Android host that `FlutterActivity` normally
 * hides from you; `setContent { … }` is our `runApp(…)`.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the status and navigation bars; composables apply insets themselves.
        enableEdgeToEdge()
        setContent {
            ReachTheme {
                FoundationPlaceholder()
            }
        }
    }
}

/** Temporary content until the Home feature lands in M1. */
@Composable
private fun FoundationPlaceholder() {
    val colors = ReachTheme.colors
    val type = ReachTheme.typography
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.bg)
                .safeDrawingPadding()
                .padding(ReachTheme.dimens.gutter),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(text = stringResource(R.string.foundation_title), style = type.clock, color = colors.fg)
        Text(text = stringResource(R.string.foundation_subtitle), style = type.body, color = colors.muted)
    }
}

@Preview
@Composable
private fun FoundationPlaceholderPreview() {
    ReachTheme { FoundationPlaceholder() }
}

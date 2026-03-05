package org.akanework.gramophone.ui

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import org.akanework.gramophone.logic.enableEdgeToEdgeProperly
import org.akanework.gramophone.logic.getBooleanStrict

abstract class BaseComposeActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    val pureDarkFlow by lazy {
        callbackFlow {
            val cb = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == "pureDark") {
                    trySendBlocking(prefs.getBooleanStrict("pureDark", false))
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(cb)
            awaitClose {
                prefs.unregisterOnSharedPreferenceChangeListener(cb)
            }
        }.stateIn(
            lifecycleScope, WhileSubscribed(),
            prefs.getBooleanStrict("pureDark", false)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeProperly()
    }
}

@Composable
fun BaseComposeActivity.GramophoneTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val pureDark by pureDarkFlow.collectAsState()
    GramophoneTheme(useDarkTheme, pureDark, content)
}

@Composable
fun GramophoneTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    pureDark: Boolean,
    content: @Composable () -> Unit
) {
    // JAMZ!!! custom color scheme — dark purple/grey with bright green accents
    val jamzDarkColors = darkColorScheme(
        primary = Color(0xFF69FF5C),
        onPrimary = Color(0xFF003A00),
        primaryContainer = Color(0xFF005300),
        onPrimaryContainer = Color(0xFF85FF76),
        secondary = Color(0xFFC5B3E0),
        onSecondary = Color(0xFF2D1A42),
        secondaryContainer = Color(0xFF44305A),
        onSecondaryContainer = Color(0xFFE2D0F8),
        tertiary = Color(0xFFD4BBFF),
        onTertiary = Color(0xFF3A1D6E),
        tertiaryContainer = Color(0xFF523E87),
        onTertiaryContainer = Color(0xFFECDCFF),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF1A1A1E),
        onBackground = Color(0xFFE4E1E9),
        surface = Color(0xFF1A1A1E),
        onSurface = Color(0xFFE4E1E9),
        surfaceVariant = Color(0xFF48454E),
        onSurfaceVariant = Color(0xFFCAC4CF),
        outline = Color(0xFF948F99),
        outlineVariant = Color(0xFF48454E),
        inverseSurface = Color(0xFFE6E1E9),
        inverseOnSurface = Color(0xFF322F37),
        inversePrimary = Color(0xFF2E7D32),
        surfaceDim = Color(0xFF1A1A1E),
        surfaceBright = Color(0xFF3B383F),
        surfaceContainerLowest = Color(0xFF0F0D14),
        surfaceContainerLow = Color(0xFF1D1B22),
        surfaceContainer = Color(0xFF211F26),
        surfaceContainerHigh = Color(0xFF2C2930),
        surfaceContainerHighest = Color(0xFF37343B),
    )
    val jamzLightColors = lightColorScheme(
        primary = Color(0xFF2E7D32),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFA8F5A0),
        onPrimaryContainer = Color(0xFF002200),
        secondary = Color(0xFF6750A4),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE8DEF8),
        onSecondaryContainer = Color(0xFF1D192B),
        tertiary = Color(0xFF7B5EA7),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFECDCFF),
        onTertiaryContainer = Color(0xFF250954),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFDF7FF),
        onBackground = Color(0xFF1D1B20),
        surface = Color(0xFFFDF7FF),
        onSurface = Color(0xFF1D1B20),
        surfaceVariant = Color(0xFFE7E0EB),
        onSurfaceVariant = Color(0xFF49454F),
        outline = Color(0xFF7A757F),
        outlineVariant = Color(0xFFCAC4CF),
        inverseSurface = Color(0xFF322F35),
        inverseOnSurface = Color(0xFFF5EFF7),
        inversePrimary = Color(0xFF69FF5C),
        surfaceDim = Color(0xFFDED8E0),
        surfaceBright = Color(0xFFFDF7FF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF7F2FA),
        surfaceContainer = Color(0xFFF2ECF4),
        surfaceContainerHigh = Color(0xFFECE6EE),
        surfaceContainerHighest = Color(0xFFE6E0E9),
    )

    MaterialTheme(
        colorScheme = (if (useDarkTheme) {
            jamzDarkColors.let {
                if (pureDark) {
                    it.copy(
                        background = Color.Black,
                        surface = Color.Black,
                        surfaceVariant = Color.Black,
                        surfaceContainerLowest = Color.Black,
                        surfaceContainerLow = Color.Black,
                        surfaceContainer = Color.Black,
                        surfaceContainerHigh = Color.Black,
                        surfaceContainerHighest = Color.Black,
                    )
                } else it
            }
        } else {
            jamzLightColors
        }), content = content
    )
}

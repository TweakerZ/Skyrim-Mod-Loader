package com.shonkware.droidmodloader.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skyrimsuite.ui.theme.SkyrimSuiteTheme

/**
 * Fixed palettes are no longer used to draw the UI: the app is coloured from
 * the user's wallpaper through [com.skyrimsuite.ui.theme.SkyrimSuiteTheme].
 *
 * [DmlPalettes.CarbonRed] survives because the native launch window and a few
 * instrumented tests still reference the old splash colour, and because
 * [DmlColors] has to resolve to *something* if it is ever read outside a theme.
 */
enum class DmlThemeId {
    CarbonRed,
    PipBoyGreen,
    SkyrimNordic,
    OblivionGold,
    FnvAmber
}

data class DmlPalette(
    val background: Color,
    val backgroundRaised: Color,

    val surface: Color,
    val surfaceRaised: Color,
    val surfacePanel: Color,
    val buttonSurface: Color,

    val borderDim: Color,
    val borderHot: Color,
    val glow: Color,

    val primary: Color,
    val primarySoft: Color,
    val primaryDark: Color,
    val primaryButton: Color,

    val amber: Color,
    val green: Color,

    val text: Color,
    val textMuted: Color,
    val textDim: Color
)

object DmlPalettes {
    val CarbonRed = DmlPalette(
        background = Color(0xFF050608),
        backgroundRaised = Color(0xFF0A0C10),

        surface = Color(0xFF101319),
        surfaceRaised = Color(0xFF171B23),
        surfacePanel = Color(0xFF202530),
        buttonSurface = Color(0xFF232934),

        borderDim = Color(0xFF343B49),
        borderHot = Color(0xFFD43A35),
        glow = Color(0xFFFF312A),

        primary = Color(0xFFE0413A),
        primarySoft = Color(0xFFB73531),
        primaryDark = Color(0xFF691819),
        primaryButton = Color(0xFF8F2020),

        amber = Color(0xFFE0A13A),
        green = Color(0xFF6EBB75),

        text = Color(0xFFF0E7D8),
        textMuted = Color(0xFFB8B0A2),
        textDim = Color(0xFF827B70)
    )

    val PipBoyGreen = DmlPalette(
        background = Color(0xFF020703),
        backgroundRaised = Color(0xFF071008),

        surface = Color(0xFF0B150D),
        surfaceRaised = Color(0xFF102014),
        surfacePanel = Color(0xFF162A1A),
        buttonSurface = Color(0xFF1B3420),

        borderDim = Color(0xFF304238),
        borderHot = Color(0xFF48D66B),
        glow = Color(0xFF61FF7D),

        primary = Color(0xFF65F07C),
        primarySoft = Color(0xFF45B95A),
        primaryDark = Color(0xFF174821),
        primaryButton = Color(0xFF216B31),

        amber = Color(0xFFD8C96B),
        green = Color(0xFF65F07C),

        text = Color(0xFFD9FFD7),
        textMuted = Color(0xFFA9C9A6),
        textDim = Color(0xFF6F846E)
    )

    val SkyrimNordic = DmlPalette(
        background = Color(0xFF05070A),
        backgroundRaised = Color(0xFF0B1015),

        surface = Color(0xFF111820),
        surfaceRaised = Color(0xFF18222D),
        surfacePanel = Color(0xFF22303C),
        buttonSurface = Color(0xFF263542),

        borderDim = Color(0xFF3A4856),
        borderHot = Color(0xFF8FB6D9),
        glow = Color(0xFFA7D8FF),

        primary = Color(0xFFAED2EC),
        primarySoft = Color(0xFF7EAACB),
        primaryDark = Color(0xFF1E3B52),
        primaryButton = Color(0xFF2B5878),

        amber = Color(0xFFD1B06A),
        green = Color(0xFF83C995),

        text = Color(0xFFE8EEF2),
        textMuted = Color(0xFFB4C0C8),
        textDim = Color(0xFF7D8A94)
    )

    val OblivionGold = DmlPalette(
        background = Color(0xFF080604),
        backgroundRaised = Color(0xFF120D08),

        surface = Color(0xFF17110A),
        surfaceRaised = Color(0xFF21180D),
        surfacePanel = Color(0xFF2B2113),
        buttonSurface = Color(0xFF352819),

        borderDim = Color(0xFF4B3B24),
        borderHot = Color(0xFFD9A943),
        glow = Color(0xFFFFC85A),

        primary = Color(0xFFE3B24E),
        primarySoft = Color(0xFFB88936),
        primaryDark = Color(0xFF604113),
        primaryButton = Color(0xFF8A5F1F),

        amber = Color(0xFFE3B24E),
        green = Color(0xFF90BD73),

        text = Color(0xFFF3E7D1),
        textMuted = Color(0xFFC7B99D),
        textDim = Color(0xFF8D806A)
    )

    val FnvAmber = DmlPalette(
        background = Color(0xFF070503),
        backgroundRaised = Color(0xFF100B05),

        surface = Color(0xFF15100A),
        surfaceRaised = Color(0xFF21170C),
        surfacePanel = Color(0xFF2B1F10),
        buttonSurface = Color(0xFF332612),

        borderDim = Color(0xFF4A3920),
        borderHot = Color(0xFFE08B2D),
        glow = Color(0xFFFF9C30),

        primary = Color(0xFFF1993A),
        primarySoft = Color(0xFFC77424),
        primaryDark = Color(0xFF5A2C0A),
        primaryButton = Color(0xFF8E4611),

        amber = Color(0xFFF1993A),
        green = Color(0xFF8CCB73),

        text = Color(0xFFF2DFC1),
        textMuted = Color(0xFFC2A984),
        textDim = Color(0xFF877459)
    )

    fun forTheme(themeId: DmlThemeId): DmlPalette = when (themeId) {
        DmlThemeId.CarbonRed -> CarbonRed
        DmlThemeId.PipBoyGreen -> PipBoyGreen
        DmlThemeId.SkyrimNordic -> SkyrimNordic
        DmlThemeId.OblivionGold -> OblivionGold
        DmlThemeId.FnvAmber -> FnvAmber
    }
}

/**
 * Translates the live Material You scheme into the old palette shape so the
 * existing screen code keeps compiling while looking like the rest of the suite.
 * Outlines are deliberately transparent: the suite uses tonal cards, not borders.
 */
private fun schemeToPalette(scheme: ColorScheme): DmlPalette = DmlPalette(
    background = scheme.background,
    backgroundRaised = scheme.surfaceContainerLowest,

    surface = scheme.surfaceContainerLow,
    surfaceRaised = scheme.surfaceContainerHigh,
    surfacePanel = scheme.surfaceContainerHighest,
    buttonSurface = scheme.secondaryContainer,

    borderDim = Color.Transparent,
    borderHot = scheme.primary,
    glow = scheme.primary,

    primary = scheme.primary,
    primarySoft = scheme.secondary,
    primaryDark = scheme.errorContainer,
    primaryButton = scheme.primary,

    amber = scheme.tertiary,
    green = scheme.secondary,

    text = scheme.onSurface,
    textMuted = scheme.onSurfaceVariant,
    textDim = scheme.outline
)

val LocalDmlPalette = staticCompositionLocalOf { DmlPalettes.CarbonRed }

object DmlColors {
    val Background: Color
        @Composable get() = LocalDmlPalette.current.background

    val BackgroundRaised: Color
        @Composable get() = LocalDmlPalette.current.backgroundRaised

    val Surface: Color
        @Composable get() = LocalDmlPalette.current.surface

    val SurfaceRaised: Color
        @Composable get() = LocalDmlPalette.current.surfaceRaised

    val SurfacePanel: Color
        @Composable get() = LocalDmlPalette.current.surfacePanel

    val ButtonSurface: Color
        @Composable get() = LocalDmlPalette.current.buttonSurface

    val BorderDim: Color
        @Composable get() = LocalDmlPalette.current.borderDim

    val BorderHot: Color
        @Composable get() = LocalDmlPalette.current.borderHot

    val Glow: Color
        @Composable get() = LocalDmlPalette.current.glow

    val Red: Color
        @Composable get() = LocalDmlPalette.current.primary

    val RedSoft: Color
        @Composable get() = LocalDmlPalette.current.primarySoft

    val RedDark: Color
        @Composable get() = LocalDmlPalette.current.primaryDark

    val RedButton: Color
        @Composable get() = LocalDmlPalette.current.primaryButton

    val Amber: Color
        @Composable get() = LocalDmlPalette.current.amber

    val Green: Color
        @Composable get() = LocalDmlPalette.current.green

    val Text: Color
        @Composable get() = LocalDmlPalette.current.text

    val TextMuted: Color
        @Composable get() = LocalDmlPalette.current.textMuted

    val TextDim: Color
        @Composable get() = LocalDmlPalette.current.textDim
}

/**
 * The app is themed from the shared suite theme, including wallpaper colour.
 * Signature kept stable for existing call sites and instrumented tests.
 */
@Composable
fun DmlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    SkyrimSuiteTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        CompositionLocalProvider(LocalDmlPalette provides schemeToPalette(MaterialTheme.colorScheme)) {
            content()
        }
    }
}

/** Flat tonal background; the old radial glow fought with dynamic colour. */
@Composable
fun DmlMatteBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        content()
    }
}

object DmlDefaults {
    @Composable
    fun panelCardColors() = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    )

    @Composable
    fun raisedCardColors() = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun DmlGlowPanel(
    modifier: Modifier = Modifier,
    hot: Boolean = false,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (hot) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = if (hot) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

object DmlButtons {
    @Composable
    fun Primary(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 56.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 16.dp)
        ) {
            Text(text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        }
    }

    @Composable
    fun Secondary(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 52.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        }
    }

    @Composable
    fun Danger(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 56.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 16.dp)
        ) {
            Text(text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        }
    }
}

package com.padel.scorer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*

// ─────────────────────────────────────────────
//  Paleta de colores
// ─────────────────────────────────────────────

private val ColorBackground  = Color(0xFF0A0A1A)
private val ColorTeamA       = Color(0xFF1565C0)   // azul
private val ColorTeamB       = Color(0xFFC62828)   // rojo
private val ColorAccent      = Color(0xFFFFD600)   // amarillo
private val ColorSurface     = Color(0xFF1C1C2E)
private val ColorText        = Color(0xFFF5F5F5)
private val ColorMuted       = Color(0xFF9E9E9E)

// ─────────────────────────────────────────────
//  Pantalla principal
// ─────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PadelScorerScreen(uiState: UiState) {
    val state = uiState.matchState

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground),
        contentAlignment = Alignment.Center
    ) {
        when (uiState.setupPhase) {
            SetupPhase.NOT_STARTED,
            SetupPhase.WAITING_TEAM_A,
            SetupPhase.WAITING_TEAM_B -> SetupScreen(uiState)

            SetupPhase.READY,
            SetupPhase.IN_PROGRESS    -> ScoreboardScreen(uiState)
        }

        // Overlay de escucha activa
        if (uiState.isListening) {
            ListeningOverlay()
        }

        // Overlay ganador
        if (state.winner != null) {
            WinnerOverlay(
                winnerName = if (state.winner == Team.A) state.teamA else state.teamB
            )
        }
    }
}

// ─────────────────────────────────────────────
//  Pantalla de configuración
// ─────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SetupScreen(uiState: UiState) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎾 MARCADOR PÁDEL",
            color = ColorAccent,
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp
        )

        Spacer(Modifier.height(48.dp))

        Text(
            text = uiState.voicePrompt.ifEmpty { "Presioná OK o el botón para comenzar" },
            color = ColorText,
            fontSize = 28.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        if (uiState.matchState.teamA != "Pareja A") {
            TeamNameBadge("Pareja A", uiState.matchState.teamA, ColorTeamA)
            Spacer(Modifier.height(16.dp))
        }

        if (uiState.setupPhase == SetupPhase.WAITING_TEAM_B) {
            Text(
                text = "▼ Ahora la pareja B",
                color = ColorMuted,
                fontSize = 22.sp
            )
        }
    }
}

@Composable
fun TeamNameBadge(label: String, name: String, color: Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .border(2.dp, color, RoundedCornerShape(12.dp))
            .padding(horizontal = 32.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "$label: ", color = ColorMuted, fontSize = 22.sp)
        Text(text = name, color = color, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────
//  Tablero de puntuación
// ─────────────────────────────────────────────

@Composable
fun ScoreboardScreen(uiState: UiState) {
    val state = uiState.matchState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Historial de sets ──────────────────
        if (state.sets.isNotEmpty()) {
            SetsHistory(state)
            Spacer(Modifier.height(16.dp))
        }

        // ── Título del set actual ──────────────
        Text(
            text = if (state.isTiebreak) "TIE-BREAK" else "SET ${state.currentSet}",
            color = ColorAccent,
            fontSize = 20.sp,
            letterSpacing = 3.sp
        )

        Spacer(Modifier.height(16.dp))

        // ── Panel principal (dos columnas) ─────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TeamPanel(
                name = state.teamA,
                pointDisplay = state.pointDisplay(Team.A),
                games = state.gamesA,
                color = ColorTeamA,
                isServing = state.servingTeam == Team.A,
                hasAdvantage = state.advantageTeam == Team.A
            )

            // Separador central
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(0.6f)
                    .background(ColorMuted.copy(alpha = 0.3f))
            )

            TeamPanel(
                name = state.teamB,
                pointDisplay = state.pointDisplay(Team.B),
                games = state.gamesB,
                color = ColorTeamB,
                isServing = state.servingTeam == Team.B,
                hasAdvantage = state.advantageTeam == Team.B
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Estado especial (Deuce) ────────────
        if (state.isDeuce && state.advantageTeam == null) {
            StatusBadge("DEUCE", ColorAccent)
        }

        Spacer(Modifier.height(16.dp))

        // ── Leyenda de controles ───────────────
        ControlsLegend()
    }
}

// ─────────────────────────────────────────────
//  Panel de equipo
// ─────────────────────────────────────────────

@Composable
fun TeamPanel(
    name: String,
    pointDisplay: String,
    games: Int,
    color: Color,
    isServing: Boolean,
    hasAdvantage: Boolean
) {
    val bgColor by animateColorAsState(
        targetValue = if (hasAdvantage) color.copy(alpha = 0.35f) else ColorSurface,
        animationSpec = tween(300)
    )

    Column(
        modifier = Modifier
            .width(320.dp)
            .background(bgColor, RoundedCornerShape(20.dp))
            .border(3.dp, if (hasAdvantage) color else Color.Transparent, RoundedCornerShape(20.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Nombre + indicador de saque
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isServing) {
                Text("🎾 ", fontSize = 20.sp)
            }
            Text(
                text = name.uppercase(),
                color = color,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(20.dp))

        // Punto actual (grande)
        Text(
            text = pointDisplay,
            color = ColorText,
            fontSize = 96.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 96.sp
        )

        Spacer(Modifier.height(12.dp))

        // Games del set
        Text(
            text = "Games: $games",
            color = ColorMuted,
            fontSize = 22.sp
        )
    }
}

// ─────────────────────────────────────────────
//  Historial de sets
// ─────────────────────────────────────────────

@Composable
fun SetsHistory(state: MatchState) {
    val setsA = state.sets.count { it.gamesA > it.gamesB }
    val setsB = state.sets.count { it.gamesB > it.gamesA }

    Row(
        modifier = Modifier
            .background(ColorSurface, RoundedCornerShape(12.dp))
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Sets: ", color = ColorMuted, fontSize = 18.sp)

        Text(
            text = "$setsA",
            color = ColorTeamA,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        state.sets.forEach { set ->
            Text(
                text = "${set.gamesA}-${set.gamesB}",
                color = ColorMuted,
                fontSize = 16.sp
            )
        }

        Text(
            text = "$setsB",
            color = ColorTeamB,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─────────────────────────────────────────────
//  Overlays
// ─────────────────────────────────────────────

@Composable
fun ListeningOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎙️", fontSize = 72.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Escuchando...",
                color = ColorAccent,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun WinnerOverlay(winnerName: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🏆", fontSize = 96.sp)
            Spacer(Modifier.height(24.dp))
            Text(
                text = "¡PARTIDO!",
                color = ColorAccent,
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = winnerName.uppercase(),
                color = ColorText,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─────────────────────────────────────────────
//  Badge de estado
// ─────────────────────────────────────────────

@Composable
fun StatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .border(2.dp, color, RoundedCornerShape(8.dp))
            .padding(horizontal = 32.dp, vertical = 8.dp)
    ) {
        Text(text, color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
    }
}

// ─────────────────────────────────────────────
//  Leyenda de controles
// ─────────────────────────────────────────────

@Composable
fun ControlsLegend() {
    Row(
        modifier = Modifier
            .background(ColorSurface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        LegendItem("VOL+", "Punto A", ColorTeamA)
        LegendItem("VOL-", "Punto B", ColorTeamB)
        LegendItem("●", "Deshacer", ColorMuted)
    }
}

@Composable
fun LegendItem(key: String, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = key,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
        Text(text = label, color = ColorMuted, fontSize = 14.sp)
    }
}

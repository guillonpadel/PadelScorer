package com.padel.scorer

// ─────────────────────────────────────────────
//  Modelos de datos
// ─────────────────────────────────────────────

enum class Team { A, B }

data class SetScore(val gamesA: Int = 0, val gamesB: Int = 0) {
    override fun toString() = "$gamesA-$gamesB"
}

data class MatchState(
    val teamA: String = "Pareja A",
    val teamB: String = "Pareja B",
    val pointsA: Int = 0,          // 0,1,2,3 → "0","15","30","40"
    val pointsB: Int = 0,
    val gamesA: Int = 0,
    val gamesB: Int = 0,
    val sets: List<SetScore> = emptyList(),
    val isTiebreak: Boolean = false,
    val isDeuce: Boolean = false,
    val advantageTeam: Team? = null,
    val goldenPoint: Boolean = false,  // si true, no hay deuce: punto directo
    val winner: Team? = null,
    val servingTeam: Team = Team.A
) {
    val currentSet get() = sets.size + 1

    fun pointDisplay(team: Team): String {
        if (isTiebreak) {
            return if (team == Team.A) pointsA.toString() else pointsB.toString()
        }
        return when {
            isDeuce -> if (advantageTeam == team) "ADV" else "40"
            else -> when (if (team == Team.A) pointsA else pointsB) {
                0 -> "0"
                1 -> "15"
                2 -> "30"
                3 -> "40"
                else -> "?"
            }
        }
    }

    /** Texto para TTS tras el último punto */
    fun ttsAnnouncement(): String {
        if (winner != null) {
            val winnerName = if (winner == Team.A) teamA else teamB
            return "Partido, $winnerName. ¡Felicitaciones!"
        }
        // Game ganado
        // (se anuncia desde el engine tras transición)
        return scoreVoiceText()
    }

    private fun scoreVoiceText(): String {
        return when {
            isDeuce -> "Iguales, deuce"
            advantageTeam == Team.A -> "Ventaja $teamA"
            advantageTeam == Team.B -> "Ventaja $teamB"
            isTiebreak -> "Tie-break, ${pointsA}, ${pointsB}"
            else -> "${pointDisplay(Team.A)} ${pointDisplay(Team.B)}"
        }
    }
}

// ─────────────────────────────────────────────
//  Motor del partido
// ─────────────────────────────────────────────

data class MatchEvent(
    val newState: MatchState,
    val ttsText: String        // texto listo para TextToSpeech
)

class PadelMatchEngine(
    private val goldenPoint: Boolean = false,
    private val setsToWin: Int = 2   // al mejor de 3 → setsToWin=2
) {

    // Historial para deshacer
    private val history = ArrayDeque<MatchState>()
    private var state = MatchState(goldenPoint = goldenPoint)

    // ── Configuración inicial ──────────────────

    fun setTeamNames(nameA: String, nameB: String): MatchEvent {
        state = state.copy(teamA = nameA.trim(), teamB = nameB.trim())
        return MatchEvent(state, "Partido entre ${state.teamA} y ${state.teamB}. ¡Que empiece el juego!")
    }

    // ── Acciones públicas ──────────────────────

    fun addPoint(team: Team): MatchEvent {
        if (state.winner != null) return MatchEvent(state, "El partido ya terminó.")
        history.addLast(state)
        state = computePoint(state, team)
        return MatchEvent(state, buildTts(state, team))
    }

    fun undoLastPoint(): MatchEvent {
        if (history.isEmpty()) return MatchEvent(state, "No hay puntos para deshacer.")
        state = history.removeLast()
        return MatchEvent(state, "Punto deshecho. ${state.ttsAnnouncement()}")
    }

    fun currentState() = state

    // ── Lógica interna ─────────────────────────

    private fun computePoint(s: MatchState, team: Team): MatchState {
        return if (s.isTiebreak) handleTiebreakPoint(s, team)
        else handleNormalPoint(s, team)
    }

    private fun handleNormalPoint(s: MatchState, team: Team): MatchState {
        val pA = s.pointsA
        val pB = s.pointsB

        // Golden point: con 40-40 el siguiente punto gana el game
        if (s.goldenPoint && pA == 3 && pB == 3) {
            return awardGame(s, team)
        }

        // Deuce / ventaja
        if (s.isDeuce || (pA == 3 && pB == 3)) {
            return when {
                s.advantageTeam == null -> s.copy(isDeuce = true, advantageTeam = team)
                s.advantageTeam == team -> awardGame(s, team)
                else -> s.copy(advantageTeam = null) // vuelve a deuce
            }
        }

        // Punto normal
        val newA = if (team == Team.A) pA + 1 else pA
        val newB = if (team == Team.B) pB + 1 else pB

        // ¿Alguien llegó a 4 (más de 40 sin deuce previo)?
        return if (newA >= 4 && newB < 3) awardGame(s.copy(pointsA = newA, pointsB = newB), Team.A)
        else if (newB >= 4 && newA < 3) awardGame(s.copy(pointsA = newA, pointsB = newB), Team.B)
        else if (newA == 3 && newB == 3) s.copy(pointsA = newA, pointsB = newB, isDeuce = true, advantageTeam = null)
        else s.copy(pointsA = newA, pointsB = newB)
    }

    private fun handleTiebreakPoint(s: MatchState, team: Team): MatchState {
        val newA = if (team == Team.A) s.pointsA + 1 else s.pointsA
        val newB = if (team == Team.B) s.pointsB + 1 else s.pointsB

        val minPoints = 7
        val winner = when {
            newA >= minPoints && newA - newB >= 2 -> Team.A
            newB >= minPoints && newB - newA >= 2 -> Team.B
            else -> null
        }

        return if (winner != null) awardSet(s.copy(pointsA = newA, pointsB = newB), winner)
        else {
            // Cambio de saque cada 2 puntos en tiebreak
            val total = newA + newB
            val serving = if (total % 2 == 0) s.servingTeam else opposite(s.servingTeam)
            s.copy(pointsA = newA, pointsB = newB, servingTeam = serving)
        }
    }

    private fun awardGame(s: MatchState, team: Team): MatchState {
        val newGamesA = if (team == Team.A) s.gamesA + 1 else s.gamesA
        val newGamesB = if (team == Team.B) s.gamesB + 1 else s.gamesB

        // ¿Gana el set?
        val setWinner = checkSetWinner(newGamesA, newGamesB)

        val base = s.copy(
            pointsA = 0, pointsB = 0,
            gamesA = newGamesA, gamesB = newGamesB,
            isDeuce = false, advantageTeam = null,
            isTiebreak = false,
            servingTeam = opposite(s.servingTeam) // cambia saque con cada game
        )

        return if (setWinner != null) awardSet(base, setWinner)
        else {
            // ¿Tiebreak? (6-6)
            if (newGamesA == 6 && newGamesB == 6) base.copy(isTiebreak = true, pointsA = 0, pointsB = 0)
            else base
        }
    }

    private fun checkSetWinner(gA: Int, gB: Int): Team? {
        return when {
            gA >= 6 && gA - gB >= 2 -> Team.A
            gB >= 6 && gB - gA >= 2 -> Team.B
            else -> null
        }
    }

    private fun awardSet(s: MatchState, team: Team): MatchState {
        val newSets = s.sets + SetScore(s.gamesA, s.gamesB)
        val setsA = newSets.count { it.gamesA > it.gamesB }
        val setsB = newSets.count { it.gamesB > it.gamesA }

        val matchWinner: Team? = when {
            setsA >= setsToWin -> Team.A
            setsB >= setsToWin -> Team.B
            else -> null
        }

        return s.copy(
            sets = newSets,
            gamesA = 0, gamesB = 0,
            pointsA = 0, pointsB = 0,
            isDeuce = false, advantageTeam = null,
            isTiebreak = false,
            winner = matchWinner,
            servingTeam = opposite(s.servingTeam)
        )
    }

    private fun opposite(t: Team) = if (t == Team.A) Team.B else Team.A

    // ── Construcción de TTS ────────────────────

    private fun buildTts(s: MatchState, lastTeam: Team): String {
        val scorer = if (lastTeam == Team.A) s.teamA else s.teamB

        if (s.winner != null) {
            val winnerName = if (s.winner == Team.A) s.teamA else s.teamB
            val sets = s.sets.joinToString(", ") { "${it.gamesA} a ${it.gamesB}" }
            return "¡Partido para $winnerName! Resultado de sets: $sets."
        }

        // ¿Acaba de ganarse un set? (lo detectamos comparando sets)
        // El set recién cerrado es el último en la lista
        val lastSet = s.sets.lastOrNull()
        val justWonSet = lastSet != null && (s.gamesA == 0 && s.gamesB == 0)

        if (justWonSet) {
            val setsA = s.sets.count { it.gamesA > it.gamesB }
            val setsB = s.sets.count { it.gamesB > it.gamesA }
            return "Set para $scorer, ${lastSet!!.gamesA} a ${lastSet.gamesB}. Sets: $setsA a $setsB."
        }

        // ¿Acaba de ganarse un game?
        val justWonGame = s.pointsA == 0 && s.pointsB == 0 && !s.isTiebreak
                && history.isNotEmpty()
                && (history.last().pointsA != 0 || history.last().pointsB != 0)

        if (justWonGame) {
            return "Game $scorer. ${s.gamesA} a ${s.gamesB}."
        }

        // Punto normal
        return s.ttsAnnouncement()
    }
}

package io.paritytech.polkadotapp.gameState

// import io.paritytech.polkadotapp.common.domain.model.AccountId
// import io.paritytech.polkadotapp.common.domain.model.Timestamp
// import io.paritytech.polkadotapp.common.domain.model.intoAccountId
// import io.paritytech.polkadotapp.feature_videogame.data.VideoGameStageDuration
// import io.paritytech.polkadotapp.feature_videogame.data.models.VideoGameInfo
// import io.paritytech.polkadotapp.feature_videogame.data.models.VideoGameRound
// import io.paritytech.polkadotapp.feature_videogame.data.models.VideoGameState
// import io.paritytech.polkadotapp.feature_videogame.data.models.webrtc.scale.RTCIceCandidatesTypeSchema
// import io.paritytech.polkadotapp.feature_videogame.domain.VideoGameLogicStateCalculator
// import io.paritytech.polkadotapp.feature_videogame.domain.models.HostingState
// import io.paritytech.polkadotapp.feature_videogame.domain.models.VideoGameProcessState
// import org.junit.Assert.assertEquals
// import org.junit.Assert.assertTrue
// import org.junit.Test
// import kotlin.time.Duration
// import kotlin.time.Duration.Companion.hours
// import kotlin.time.Duration.Companion.milliseconds
// import kotlin.time.Duration.Companion.seconds
//
// class VideoGameLogicStateCalculatorTest {
//
//    private val calculator = VideoGameLogicStateCalculator()
//
//    private val player1: AccountId = "player1".toByteArray().intoAccountId()
//    private val player2: AccountId = "player2".toByteArray().intoAccountId()
//    private val player3: AccountId = "player3".toByteArray().intoAccountId()
//    private val player4: AccountId = "player4".toByteArray().intoAccountId()
//
//    private fun createGameInfo(
//        gameStart: Timestamp,
//        reportEnd: Timestamp,
//        numRounds: Int,
//        roundsDetails: List<VideoGameRound>,
//        maxGroupSize: Int
//    ): VideoGameInfo {
//        return VideoGameInfo(
//            registrationEnd = gameStart - 10000L,
//            gameStart = gameStart,
//            reportEnd = reportEnd,
//            rounds = numRounds,
//            maxGroupSize = maxGroupSize,
//            state = VideoGameState.InProgress(rounds = roundsDetails, isReportSent = false)
//        )
//    }
//
//    // single round scenario
//    private val singleRoundGameStartTime = 100000L
//    private val singleRoundMaxGroupSize = 4
//    private val singleRoundDetails = listOf(
//        VideoGameRound(roundIndex = 0, players = listOf(player1, player2))
//    )
//    private val singleRoundNumRounds = singleRoundDetails.size
//
//    private val singleRoundDuration = VideoGameStageDuration.HOST_FULL_CYCLE * singleRoundMaxGroupSize
//
//    private val singlePlayerSlotDuration = singleRoundDuration / singleRoundDetails.first().players.size
//
//    private val actualHostingDurationSingleRound = singlePlayerSlotDuration -
//            VideoGameStageDuration.HOST_TRANSITION -
//            VideoGameStageDuration.HOST_INTRODUCTION -
//            VideoGameStageDuration.HOST_ENDING
//
//    private val singleRoundReportingStartsAt = (VideoGameStageDuration.ROTATION * singleRoundNumRounds) + (singleRoundDuration * singleRoundNumRounds)
//
//    private val singleRoundReportEndTime = singleRoundGameStartTime + 24.hours.inWholeMilliseconds
//
//    private val gameInfoSingleRound by lazy {
//        createGameInfo(
//            gameStart = singleRoundGameStartTime,
//            reportEnd = singleRoundReportEndTime,
//            numRounds = singleRoundNumRounds,
//            roundsDetails = singleRoundDetails,
//            maxGroupSize = singleRoundMaxGroupSize
//        )
//    }
//
//    // few rounds scenario
//    private val multiRoundGameStartTime = 200000L
//    private val multiRoundMaxGroupSize = 4
//    private val multiRoundDetails = listOf(
//        VideoGameRound(roundIndex = 0, players = listOf(player1, player2)),
//        VideoGameRound(roundIndex = 1, players = listOf(player3, player4))
//    )
//    private val multiRoundNumRounds = multiRoundDetails.size
//
//    private val multiRoundDurationPerRound = VideoGameStageDuration.HOST_FULL_CYCLE * multiRoundMaxGroupSize
//
//    private val gameInfoMultiRound by lazy {
//        createGameInfo(
//            gameStart = multiRoundGameStartTime,
//            reportEnd = multiRoundGameStartTime + 24.hours.inWholeMilliseconds,
//            numRounds = multiRoundNumRounds,
//            roundsDetails = multiRoundDetails,
//            maxGroupSize = multiRoundMaxGroupSize
//        )
//    }
//
//    @Test
//    fun `calculate state when gameTime is before gameStart should be WaitingRoom`() {
//        val state = calculator.calculateProcessState((-10).seconds, gameInfoSingleRound)
//        assertTrue(state is VideoGameProcessState.WaitingRoom)
//        assertEquals(Duration.ZERO, (state as VideoGameProcessState.WaitingRoom).endsAt)
//    }
//
//    @Test
//    fun `calculate state during Starting phase`() {
//        val gameTime = 10.seconds
//        val state = calculator.calculateProcessState(gameTime, gameInfoSingleRound)
//
//        assertTrue(state is VideoGameProcessState.Starting)
//        val startingState = state as VideoGameProcessState.Starting
//        assertEquals(VideoGameStageDuration.ROTATION, startingState.endsAt)
//        assertEquals(singleRoundDetails.first().players, startingState.players)
//    }
//
//    @Test
//    fun `calculate state during Round 0, Host 0 (player1), Transition phase`() {
//        val gameTime = VideoGameStageDuration.ROTATION + 2.seconds
//        val state = calculator.calculateProcessState(gameTime, gameInfoSingleRound)
//
//        assertTrue(state is VideoGameProcessState.Round)
//        val roundState = state as VideoGameProcessState.Round
//        assertEquals(player1, roundState.currentHost)
//        assertTrue(roundState.hostingState is HostingState.Transition)
//        assertEquals(
//            VideoGameStageDuration.ROTATION + VideoGameStageDuration.HOST_TRANSITION,
//            roundState.hostingState.endsAt
//        )
//    }
//
//    @Test
//    fun `calculate state during Round 0, Host 0 (player1), Hosting phase`() {
//        val gameTime = VideoGameStageDuration.ROTATION +
//                VideoGameStageDuration.HOST_TRANSITION +
//                VideoGameStageDuration.HOST_INTRODUCTION + 5.seconds
//        val state = calculator.calculateProcessState(gameTime, gameInfoSingleRound)
//
//        assertTrue(state is VideoGameProcessState.Round)
//        val roundState = state as VideoGameProcessState.Round
//        assertEquals(player1, roundState.currentHost)
//        assertTrue(roundState.hostingState is HostingState.Hosting)
//
//        val hostingState = roundState.hostingState as HostingState.Hosting
//        assertEquals(actualHostingDurationSingleRound, hostingState.duration)
//    }
//
//    @Test
//    fun `calculate state during Round 0, Host 1 (player2), Transition phase`() {
//        val gameTime = VideoGameStageDuration.ROTATION + singlePlayerSlotDuration + 2.seconds
//        val state = calculator.calculateProcessState(gameTime, gameInfoSingleRound)
//
//        assertTrue(state is VideoGameProcessState.Round)
//        val roundState = state as VideoGameProcessState.Round
//        assertEquals(player2, roundState.currentHost)
//        assertTrue(roundState.hostingState is HostingState.Transition)
//    }
//
//    @Test
//    fun `calculate state during Reporting phase`() {
//        val gameTime = singleRoundReportingStartsAt + 10.seconds
//        val state = calculator.calculateProcessState(gameTime, gameInfoSingleRound)
//        val reportingEndsAt = (singleRoundReportEndTime - singleRoundGameStartTime).milliseconds
//
//        assertTrue(state is VideoGameProcessState.Reporting)
//        assertEquals(reportingEndsAt, (state as VideoGameProcessState.Reporting).endsAt)
//    }
//
//    @Test
//    fun `calculate state when gameTime is after reportingEndsAt should be Finished`() {
//        val reportingEndsAt = (singleRoundReportEndTime - singleRoundGameStartTime).milliseconds
//        val gameTime = reportingEndsAt + 10.seconds
//        val state = calculator.calculateProcessState(gameTime, gameInfoSingleRound)
//        assertEquals(VideoGameProcessState.Finished, state)
//    }
//
//    @Test
//    fun `calculate state during Rotating phase in multi-round game`() {
//        val timeAfterFirstRound = VideoGameStageDuration.ROTATION + multiRoundDurationPerRound
//        val gameTime = timeAfterFirstRound + 5.seconds
//        val state = calculator.calculateProcessState(gameTime, gameInfoMultiRound)
//
//        assertTrue(state is VideoGameProcessState.Rotating)
//        val rotatingState = state as VideoGameProcessState.Rotating
//        assertEquals(multiRoundDetails[1].players, rotatingState.players)
//        assertEquals(timeAfterFirstRound + VideoGameStageDuration.ROTATION, rotatingState.endsAt)
//    }
//
//    @Test
//    fun `calculate state during Round 1, Host 0 (player3), Transition phase in multi-round game`() {
//        val timeAfterFirstRoundAndRotation = VideoGameStageDuration.ROTATION +
//                multiRoundDurationPerRound +
//                VideoGameStageDuration.ROTATION
//
//        val gameTime = timeAfterFirstRoundAndRotation + 2.seconds
//        val state = calculator.calculateProcessState(gameTime, gameInfoMultiRound)
//
//        assertTrue(state is VideoGameProcessState.Round)
//        val roundState = state as VideoGameProcessState.Round
//        assertEquals(1, roundState.roundIndex)
//        assertEquals(player3, roundState.currentHost)
//        assertTrue(roundState.hostingState is HostingState.Transition)
//    }
//
//    @Test
//    fun asdasasdd() {
//        val candidates = RTCIceCandidatesTypeSchema
//    }
// }

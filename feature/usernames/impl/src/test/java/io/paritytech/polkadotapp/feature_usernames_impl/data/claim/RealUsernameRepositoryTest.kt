package io.paritytech.polkadotapp.feature_usernames_impl.data.claim

import com.google.gson.Gson
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_usernames_impl.data.claim.network.api.UsernameApi
import io.paritytech.polkadotapp.feature_usernames_impl.data.claim.network.api.model.RegistrationOutcome
import io.paritytech.polkadotapp.feature_usernames_impl.data.claim.network.api.model.UsernameClaimRequest
import io.paritytech.polkadotapp.feature_usernames_impl.data.claim.network.api.model.UsernameClaimResponse
import io.paritytech.polkadotapp.feature_usernames_impl.domain.UsernamesChainProvider
import io.paritytech.polkadotapp.feature_usernames_impl.domain.model.ClaimUsernameParams
import io.paritytech.polkadotapp.feature_usernames_impl.domain.model.UsernameClaimResult
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.whenever
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import retrofit2.HttpException
import retrofit2.Response
import io.paritytech.polkadotapp.tools_integrity_api.claim.ClaimDeviceEvidence
import io.paritytech.polkadotapp.tools_integrity_api.claim.ClaimDeviceEvidenceProvider

class RealUsernameRepositoryTest {
    private val api: UsernameApi = mock(UsernameApi::class.java)
    private val chainProvider: UsernamesChainProvider = mock(UsernamesChainProvider::class.java)
    private val evidenceProvider: ClaimDeviceEvidenceProvider = mock(ClaimDeviceEvidenceProvider::class.java)

    private val repository = RealUsernameRepository(api, chainProvider, evidenceProvider, Gson())

    private val params = ClaimUsernameParams(
        username = "alice.07",
        preferredDigits = "07",
        candidateAddress = "5FakeAddress",
        candidateSignature = ByteArray(64),
        consumerSignature = ByteArray(64),
        membershipSignature = ByteArray(64),
        ringVrfKey = ByteArray(32),
        identifierKey = ByteArray(65).toDataByteArray(),
        dotNsSignature = ByteArray(64),
        dotNsSignedAt = 0L,
        dotNsReservedUsername = "alice"
    )

    private val evidence = ClaimDeviceEvidence(
        attestationChain = listOf("leaf", "root"),
        deviceChallenge = "challenge",
        deviceId = "device-id"
    )

    private val requests = mutableListOf<UsernameClaimRequest>()

    @Before
    fun setUp() = runTest {
        whenever(evidenceProvider.collectEvidence()).thenReturn(Result.success(evidence))
    }

    @Test
    fun `claim attaches all three evidence fields`() = runTest {
        stubClaimResponses({ registeredResponse() })

        val result = repository.claimUsername(params)

        assertEquals(UsernameClaimResult.Registered::class, result.getOrNull()!!::class)
        val request = requests.single()
        assertEquals(listOf("leaf", "root"), request.attestationChain)
        assertEquals("challenge", request.deviceChallenge)
        assertEquals("device-id", request.deviceId)
    }

    @Test
    fun `claim without applicable evidence omits all three fields`() = runTest {
        whenever(evidenceProvider.collectEvidence()).thenReturn(Result.success(null))
        stubClaimResponses({ registeredResponse() })

        val result = repository.claimUsername(params)

        assertTrue(result.isSuccess)
        val request = requests.single()
        assertNull(request.attestationChain)
        assertNull(request.deviceChallenge)
        assertNull(request.deviceId)
    }

    @Test
    fun `evidence collection failure aborts the claim without a request`() = runTest {
        whenever(evidenceProvider.collectEvidence()).thenReturn(Result.failure(IllegalStateException("drm busy")))

        val result = repository.claimUsername(params)

        assertTrue(result.isFailure)
        verify(api, never()).claimUsername(any())
    }

    @Test
    fun `invalid device evidence is retried once with fresh evidence`() = runTest {
        stubClaimResponses(
            { throw deviceEvidenceInvalid() },
            { registeredResponse() }
        )

        val result = repository.claimUsername(params)

        assertTrue(result.isSuccess)
        assertEquals(2, requests.size)
        assertEquals("device-id", requests[1].deviceId)
        verify(evidenceProvider, times(2)).collectEvidence()
    }

    @Test
    fun `second invalid evidence rejection falls back to an evidence-less claim`() = runTest {
        stubClaimResponses(
            { throw deviceEvidenceInvalid() },
            { throw deviceEvidenceInvalid() },
            { paymentRequiredResponse() }
        )

        val result = repository.claimUsername(params)

        assertEquals(UsernameClaimResult.PaymentRequired, result.getOrNull())
        assertEquals(3, requests.size)
        assertNull(requests[2].attestationChain)
        assertNull(requests[2].deviceChallenge)
        assertNull(requests[2].deviceId)
        verify(evidenceProvider, times(2)).collectEvidence()
    }

    @Test
    fun `unrelated errors are not retried`() = runTest {
        stubClaimResponses({ throw httpException(code = 403, body = """{"error":"SOMETHING_ELSE"}""") })

        val result = repository.claimUsername(params)

        assertTrue(result.isFailure)
        assertEquals(1, requests.size)
    }

    private suspend fun stubClaimResponses(vararg responses: () -> UsernameClaimResponse) {
        var call = 0
        whenever(api.claimUsername(any())).thenAnswer { invocation ->
            requests += invocation.getArgument<UsernameClaimRequest>(0)
            responses[call++]()
        }
    }

    private fun registeredResponse() = UsernameClaimResponse(
        baseUsername = "alice",
        digits = "07",
        username = "alice.07",
        registrationOutcome = null
    )

    private fun paymentRequiredResponse() = UsernameClaimResponse(
        baseUsername = null,
        digits = null,
        username = null,
        registrationOutcome = RegistrationOutcome.PAYMENT_REQUIRED
    )

    private fun deviceEvidenceInvalid() = httpException(
        code = 403,
        body = """{"error":"DEVICE_EVIDENCE_INVALID","message":"envelope expired"}"""
    )

    private fun httpException(code: Int, body: String): HttpException {
        val errorBody = body.toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<UsernameClaimResponse>(code, errorBody))
    }
}

package io.paritytech.polkadotapp.feature_products_impl.domain.truapi

import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.DerivationIndex32
import io.paritytech.polkadotapp.feature_account_api.domain.derivation.asDisplayString
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocatableResource
import io.paritytech.polkadotapp.feature_products_api.model.ProductAccountId
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.model.signing.RawPayloadContent
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningRequestBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import uniffi.truapi.AllocatableResource
import uniffi.truapi.DerivationIndex
import uniffi.truapi.HostAccountSignVrfRequest
import uniffi.truapi.HostSignPayloadData
import uniffi.truapi.HostSignPayloadRequest
import uniffi.truapi.HostSignPayloadWithLegacyAccountRequest
import uniffi.truapi.HostSignRawRequest
import uniffi.truapi.HostSignRawWithLegacyAccountRequest
import uniffi.truapi.LegacyAccountTxPayload
import uniffi.truapi.ProductAccountTxPayload
import uniffi.truapi.ProductProofContext
import uniffi.truapi.RawPayload
import uniffi.truapi.RingLocation
import uniffi.truapi.RingLocationJunction
import uniffi.truapi.TxPayloadExtension
import uniffi.truapi.VrfTranscriptItem
import uniffi.truapi_platform.AccountAccessReview
import uniffi.truapi_platform.AccountAliasReview
import uniffi.truapi_platform.CreateProofReview
import uniffi.truapi_platform.CreateTransactionReview
import uniffi.truapi_platform.IdentityDisclosureReview
import uniffi.truapi_platform.PreimageSubmitReview
import uniffi.truapi_platform.ResourceAllocationReview
import uniffi.truapi_platform.SignPayloadReview
import uniffi.truapi_platform.SignRawReview
import uniffi.truapi_platform.SignVrfReview
import uniffi.truapi_platform.StatementStoreProductSignReview
import uniffi.truapi_platform.UserConfirmationReview
import uniffi.truapi.ProductAccountId as NativeProductAccountId

private const val ALICE_SS58 = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"

class ConfirmationReviewMappingTest {
    private val caller = ProductId.fromStoredValue("caller-product.dot")

    @OptIn(ExperimentalStdlibApi::class)
    private fun aliceAccountId() =
        "d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d".hexToByteArray()

    @Test
    fun `sign payload product maps to transaction`() {
        val review = UserConfirmationReview.SignPayload(
            SignPayloadReview.Product(
                HostSignPayloadRequest(account = nativeAccount(), payload = signPayloadData()),
            ),
        )

        val body = review.signingRequest() as SigningRequestBody.Transaction

        assertEquals(ProductAccountId("demo-product.dot", DerivationIndex32.fromUInt(7u)), body.payload.account)
        assertArrayEquals(byteArrayOf(0xde.toByte(), 0xad.toByte()), body.payload.method)
        assertEquals(listOf("CheckNonce", "CheckWeight"), body.payload.signedExtensions)
        assertEquals(4, body.payload.version)
        assertEquals(1, body.payload.mode)
        assertEquals(true, body.payload.withSignedTransaction)
    }

    /** The core exposes this method, so refusing the variant denied it silently. */
    @Test
    fun `sign payload legacy hex signer maps to transaction legacy`() {
        val body = legacySignPayload(signer = "0x0102") as SigningRequestBody.TransactionLegacy

        assertArrayEquals(byteArrayOf(1, 2), body.payload.account.value)
        assertArrayEquals(byteArrayOf(0xde.toByte(), 0xad.toByte()), body.payload.method)
        assertEquals(listOf("CheckNonce", "CheckWeight"), body.payload.signedExtensions)
        assertEquals(4, body.payload.version)
    }

    /** The wire carries "SS58 or hex"; UTF-8 bytes of an SS58 string match nothing. */
    @Test
    fun `sign payload legacy ss58 signer decodes to an account id`() {
        val body = legacySignPayload(signer = ALICE_SS58) as SigningRequestBody.TransactionLegacy

        assertArrayEquals(aliceAccountId(), body.payload.account.value)
    }

    @Test
    fun `a legacy signer that is neither hex nor ss58 is unsupported`() {
        assertThrows(UnsupportedReviewException::class.java) { legacySignPayload(signer = "not-an-address") }
    }

    private fun legacySignPayload(signer: String): SigningRequestBody =
        UserConfirmationReview.SignPayload(
            SignPayloadReview.LegacyAccount(
                HostSignPayloadWithLegacyAccountRequest(signer = signer, payload = signPayloadData()),
            ),
        ).signingRequest()

    @Test
    fun `sign raw product bytes maps to raw`() {
        val review = UserConfirmationReview.SignRaw(
            SignRawReview.Product(
                HostSignRawRequest(
                    account = nativeAccount(),
                    payload = RawPayload.Bytes(byteArrayOf(0xca.toByte(), 0xfe.toByte())),
                ),
            ),
        )

        val body = review.signingRequest() as SigningRequestBody.Raw

        assertEquals(ProductAccountId("demo-product.dot", DerivationIndex32.fromUInt(7u)), body.payload.account)
        val content = body.payload.type as RawPayloadContent.Bytes
        assertArrayEquals(byteArrayOf(0xca.toByte(), 0xfe.toByte()), content.data)
    }

    @Test
    fun `sign raw legacy hex signer maps to raw legacy`() {
        val review = UserConfirmationReview.SignRaw(
            SignRawReview.LegacyAccount(
                HostSignRawWithLegacyAccountRequest(
                    signer = "0x0102",
                    payload = RawPayload.Payload("hello"),
                ),
            ),
        )

        val body = review.signingRequest() as SigningRequestBody.RawLegacy

        assertArrayEquals(byteArrayOf(1, 2), body.payload.account.value)
        assertEquals("hello", (body.payload.type as RawPayloadContent.Payload).data)
    }

    @Test
    fun `create transaction product maps extensions`() {
        val review = UserConfirmationReview.CreateTransaction(
            CreateTransactionReview.Product(
                ProductAccountTxPayload(
                    signer = nativeAccount(),
                    genesisHash = ByteArray(32) { 3 },
                    callData = byteArrayOf(9),
                    extensions = listOf(
                        TxPayloadExtension(
                            id = "CheckNonce",
                            extra = byteArrayOf(1),
                            additionalSigned = byteArrayOf(2),
                        ),
                    ),
                    txExtVersion = 0u,
                ),
            ),
        )

        val body = review.signingRequest() as SigningRequestBody.CreateTransaction

        assertEquals(ProductAccountId("demo-product.dot", DerivationIndex32.fromUInt(7u)), body.payload.signer)
        val extension = body.payload.extensions.single()
        assertEquals("CheckNonce", extension.id)
        assertArrayEquals(byteArrayOf(1), extension.explicit.value)
        assertArrayEquals(byteArrayOf(2), extension.implicit.value)
    }

    @Test
    fun `create transaction legacy maps raw signer`() {
        val review = UserConfirmationReview.CreateTransaction(
            CreateTransactionReview.LegacyAccount(
                LegacyAccountTxPayload(
                    signer = ByteArray(32) { 5 },
                    genesisHash = ByteArray(32) { 3 },
                    callData = byteArrayOf(9),
                    extensions = emptyList(),
                    txExtVersion = 0u,
                ),
            ),
        )

        val body = review.signingRequest() as SigningRequestBody.CreateTransactionLegacy

        assertArrayEquals(ByteArray(32) { 5 }, body.payload.signer.value)
    }

    @Test
    fun `sign vrf maps transcript`() {
        val review = UserConfirmationReview.SignVrf(
            SignVrfReview(
                callingProductId = "caller-product.dot",
                request = HostAccountSignVrfRequest(
                    account = nativeAccount(),
                    transcriptLabel = "pop:airdrop".toByteArray(),
                    items = listOf(
                        VrfTranscriptItem(label = "round".toByteArray(), value = byteArrayOf(1)),
                    ),
                ),
            ),
        )

        val body = review.signingRequest() as SigningRequestBody.SignVrf

        assertEquals(ProductAccountId("demo-product.dot", DerivationIndex32.fromUInt(7u)), body.account)
        assertArrayEquals("pop:airdrop".toByteArray(), body.transcriptLabel)
        val item = body.items.single()
        assertArrayEquals("round".toByteArray(), item.label.value)
        assertArrayEquals(byteArrayOf(1), item.value.value)
    }

    @Test
    fun `raw 32-byte derivation index maps to a raw selector`() {
        val review = signVrfReview(derivationIndex = DerivationIndex.Raw(ByteArray(32) { 9 }))

        val body = review.signingRequest() as SigningRequestBody.SignVrf

        val expected = DerivationIndex32.fromBytes(ByteArray(32) { 9 }.toDataByteArray()).getOrThrow()
        assertEquals(expected, body.account.index)
    }

    @Test
    fun `raw derivation index of the wrong length is unsupported`() {
        val review = signVrfReview(derivationIndex = DerivationIndex.Raw(ByteArray(31)))

        assertThrows(UnsupportedReviewException::class.java) { review.toConfirmation(caller) }
    }

    private fun signVrfReview(derivationIndex: DerivationIndex) = UserConfirmationReview.SignVrf(
        SignVrfReview(
            callingProductId = "caller-product.dot",
            request = HostAccountSignVrfRequest(
                account = NativeProductAccountId(
                    dotNsIdentifier = "demo-product.dot",
                    derivationIndex = derivationIndex,
                ),
                transcriptLabel = ByteArray(0),
                items = emptyList(),
            ),
        ),
    )

    @Test
    fun `statement store sign requests access to the signing account's product`() {
        val review = UserConfirmationReview.StatementStoreProductSign(
            StatementStoreProductSignReview(account = nativeAccount(), payload = byteArrayOf(1, 2, 3)),
        )

        val confirmation = review.toConfirmation(caller) as TrUAPIConfirmation.StatementSign

        assertEquals(caller, confirmation.callingProductId)
        assertEquals(ProductId.fromStoredValue("demo-product.dot"), confirmation.accountOwner)
    }

    @Test
    fun `account alias requests access to the context's product`() {
        val review = UserConfirmationReview.AccountAlias(
            AccountAliasReview(
                callingProductId = "caller.dot",
                context = ProductProofContext(productId = "owner.dot", suffix = DerivationIndex.Index(7u)),
                ringLocation = ringLocation(),
            ),
        )

        val confirmation = review.toConfirmation(caller) as TrUAPIConfirmation.AccountAlias

        assertEquals(ProductId.fromStoredValue("caller.dot"), confirmation.callingProductId)
        assertEquals(ProductId.fromStoredValue("owner.dot"), confirmation.contextOwner)
    }

    @Test
    fun `create proof carries the cross-product prompt's inputs`() {
        val review = UserConfirmationReview.CreateProof(
            CreateProofReview(
                callingProductId = "caller.dot",
                context = ProductProofContext(productId = "owner.dot", suffix = DerivationIndex.Index(7u)),
                ringLocation = ringLocation(),
                message = byteArrayOf(9, 8),
            ),
        )

        val confirmation = review.toConfirmation(caller) as TrUAPIConfirmation.CreateProof

        assertEquals(ProductId.fromStoredValue("caller.dot"), confirmation.callingProductId)
        assertEquals(ProductId.fromStoredValue("owner.dot"), confirmation.contextOwner)
        assertEquals(DerivationIndex32.fromUInt(7u).asDisplayString(), confirmation.suffix.asDisplayString())
        assertArrayEquals(byteArrayOf(9, 8), confirmation.message.value)
    }

    @Test
    fun `create proof with a malformed context suffix is unsupported`() {
        val review = UserConfirmationReview.CreateProof(
            CreateProofReview(
                callingProductId = "caller.dot",
                context = ProductProofContext(productId = "owner.dot", suffix = DerivationIndex.Raw(ByteArray(31))),
                ringLocation = ringLocation(),
                message = byteArrayOf(9, 8),
            ),
        )

        assertThrows(UnsupportedReviewException::class.java) { review.toConfirmation(caller) }
    }

    @Test
    fun `account access maps both products`() {
        val review = UserConfirmationReview.AccountAccess(
            AccountAccessReview(requestingProductId = "caller.dot", targetProductId = "target.dot"),
        )

        val confirmation = review.toConfirmation(caller) as TrUAPIConfirmation.AccountAccess

        assertEquals(ProductId.fromStoredValue("caller.dot"), confirmation.requestingProductId)
        assertEquals(ProductId.fromStoredValue("target.dot"), confirmation.targetProductId)
    }

    @Test
    fun `identity disclosure maps the product`() {
        val review = UserConfirmationReview.IdentityDisclosure(
            IdentityDisclosureReview(productId = "discloser.dot"),
        )

        val confirmation = review.toConfirmation(caller) as TrUAPIConfirmation.IdentityDisclosure

        assertEquals(ProductId.fromStoredValue("discloser.dot"), confirmation.productId)
    }

    @Test
    fun `preimage submit carries the calling product`() {
        val review = UserConfirmationReview.PreimageSubmit(PreimageSubmitReview(size = 4096uL))

        val confirmation = review.toConfirmation(caller) as TrUAPIConfirmation.PreimageSubmit

        assertEquals(caller, confirmation.callingProductId)
    }

    @Test
    fun `resource allocation maps every resource to its domain form`() {
        val review = UserConfirmationReview.ResourceAllocation(
            ResourceAllocationReview(
                callingProductId = "caller.dot",
                resources = listOf(
                    AllocatableResource.StatementStoreAllowance,
                    AllocatableResource.AutoSigning,
                ),
            ),
        )

        val confirmation = review.toConfirmation(caller) as TrUAPIConfirmation.ResourceAllocation

        assertEquals(ProductId.fromStoredValue("caller.dot"), confirmation.callingProductId)
        assertEquals(
            listOf(ApAllocatableResource.StatementStoreAllowance, ApAllocatableResource.AutoSigning),
            confirmation.resources,
        )
    }

    @Test
    fun `smart-contract allowance keeps its destination index`() {
        val review = UserConfirmationReview.ResourceAllocation(
            ResourceAllocationReview(
                callingProductId = "caller.dot",
                resources = listOf(AllocatableResource.SmartContractAllowance(DerivationIndex.Index(3u))),
            ),
        )

        val confirmation = review.toConfirmation(caller) as TrUAPIConfirmation.ResourceAllocation

        val resource = confirmation.resources.single() as ApAllocatableResource.SmartContractAllowance
        assertEquals(DerivationIndex32.fromUInt(3u).asDisplayString(), resource.dest.asDisplayString())
    }

    private fun UserConfirmationReview.signingRequest(): SigningRequestBody =
        (toConfirmation(caller) as TrUAPIConfirmation.Signing).request

    private fun ringLocation() = RingLocation(
        chainId = ByteArray(32) { 1 },
        junctions = listOf(RingLocationJunction.PalletInstance(9u)),
    )

    private fun nativeAccount() = NativeProductAccountId(
        dotNsIdentifier = "demo-product.dot",
        derivationIndex = DerivationIndex.Index(7u),
    )

    private fun signPayloadData() = HostSignPayloadData(
        blockHash = ByteArray(32) { 1 },
        blockNumber = byteArrayOf(0x2a),
        era = byteArrayOf(0),
        genesisHash = ByteArray(32) { 2 },
        method = byteArrayOf(0xde.toByte(), 0xad.toByte()),
        nonce = byteArrayOf(1),
        specVersion = byteArrayOf(4),
        tip = byteArrayOf(0),
        transactionVersion = byteArrayOf(2),
        signedExtensions = listOf("CheckNonce", "CheckWeight"),
        version = 4u,
        assetId = null,
        metadataHash = null,
        mode = 1u,
        withSignedTransaction = true,
    )
}

package io.paritytech.polkadotapp.feature_sso_impl.data.model.scale.session

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.BinaryScale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.decodeFromByteArray
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.encodeToByteArray
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.types.BSResult
import io.paritytech.polkadotapp.bandersnatch_crypto.ContextualAlias
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocatableResource
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocatedResource
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocationOutcome
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.OnExistingAllowancePolicy
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.SlotAccountKey
import io.paritytech.polkadotapp.feature_products_api.model.ProductAccountId
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_api.model.scale.toDomain
import io.paritytech.polkadotapp.feature_products_api.model.scale.toScale
import io.paritytech.polkadotapp.feature_products_api.model.signing.RawPayloadContent
import io.paritytech.polkadotapp.feature_products_api.model.signing.SignedTransaction
import io.paritytech.polkadotapp.feature_products_api.model.signing.SignerPayloadJson
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningRawPayload
import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningRequestBody
import io.paritytech.polkadotapp.feature_products_api.model.signing.createTransaction.TxPayload
import io.paritytech.polkadotapp.feature_sso_impl.domain.session.model.SsoSessionId
import io.paritytech.polkadotapp.feature_sso_impl.domain.session.model.SsoSessionRequest
import io.paritytech.polkadotapp.feature_sso_impl.domain.session.model.SsoSessionRequestId
import io.paritytech.polkadotapp.feature_sso_impl.domain.session.model.SsoSessionResponse
import io.paritytech.polkadotapp.feature_statement_store_api.domain.models.EncodedMessage
import io.paritytech.polkadotapp.feature_transactions.api.data.extensions.EncodedTransactionExtensionValue

// ==================== Encoding (Domain -> Scale -> ByteArray) ====================

fun SsoSessionRequest.toSessionMessage(): SsoSessionMessage {
    val statementContent = content.toMessageContent()
    val message = SsoSessionMessageV1(statementContent)
    val versioned = VersionedSsoSessionMessage.V1(message)
    return SsoSessionMessage(requestId, versioned)
}

fun SsoSessionRequest.toEncodedMessage(): EncodedMessage {
    val sessionMessage = toSessionMessage()
    return BinaryScale.encodeToByteArray(sessionMessage)
}

fun SsoSessionResponse.toSessionMessage(): SsoSessionMessage {
    val messageContent = content.toMessageContent(respondingTo)
    val message = SsoSessionMessageV1(messageContent)
    val versioned = VersionedSsoSessionMessage.V1(message)
    return SsoSessionMessage(ownRequestId, versioned)
}

fun SsoSessionResponse.toEncodedMessage(): EncodedMessage {
    val sessionMessage = toSessionMessage()
    return BinaryScale.encodeToByteArray(sessionMessage)
}

// ==================== Decoding (ByteArray -> Scale -> Domain) ====================

fun EncodedMessage.decodeSsoSessionMessage(): Result<SsoSessionMessage> {
    return runCatching { BinaryScale.decodeFromByteArray<SsoSessionMessage>(this) }
}

fun EncodedMessage.decodeAlwaysDecodableSsoMessagePart(): Result<AlwaysDecodableSsoMessagePart> {
    return runCatching { BinaryScale.decodeFromByteArray<AlwaysDecodableSsoMessagePart>(this) }
}

fun EncodedMessage.toSsoSessionRequest(sessionId: SsoSessionId): Result<SsoSessionRequest> {
    return decodeSsoSessionMessage().mapCatching { message ->
        message.toSsoSessionRequest(sessionId)
    }
}

// ==================== Scale -> Domain mappers ====================

private fun SsoSessionMessage.toSsoSessionRequest(sessionId: SsoSessionId): SsoSessionRequest {
    return when (versioned) {
        is VersionedSsoSessionMessage.V1 -> versioned.message.toSsoSessionRequest(id, sessionId)
    }
}

private fun SsoSessionMessageV1.toSsoSessionRequest(
    messageId: String,
    sessionId: SsoSessionId
): SsoSessionRequest {
    val requestContent = content.toRequestContent()
    return SsoSessionRequest(
        sessionId = sessionId,
        requestId = messageId,
        content = requestContent
    )
}

private fun SsoMessageContent.toRequestContent(): SsoSessionRequest.Content {
    return when (this) {
        SsoMessageContent.Disconnected -> SsoSessionRequest.Content.Disconnected
        is SsoMessageContent.SigningRequest -> SsoSessionRequest.Content.SigningRequest(request.toDomain())
        is SsoMessageContent.SigningResponse -> error("SigningResponse is a response-only message type")
        is SsoMessageContent.RingVrfAliasRequest -> SsoSessionRequest.Content.AliasRequest(
            productAccountId = requestedAccount.toDomain(),
            productDotNsIdentifier = callingProductId,
        )
        is SsoMessageContent.RingVrfAliasResponse -> error("RingVrfAliasResponse is a response-only message type")
        is SsoMessageContent.ResourceAllocationRequest -> SsoSessionRequest.Content.ResourceAllocationRequest(
            callingProduct = ProductId.fromStoredValue(request.callingProductId),
            resources = request.resources.map { it.toDomain() },
            onExisting = request.onExisting.toDomain(),
        )
        is SsoMessageContent.ResourceAllocationResponse -> error("ResourceAllocationResponse is a response-only message type")
        is SsoMessageContent.CreateTransactionRequest -> SsoSessionRequest.Content.CreateTransactionRequest(
            SigningRequestBody.CreateTransaction(request.toDomain())
        )
        is SsoMessageContent.CreateTransactionResponse -> error("CreateTransactionResponse is a response-only message type")
    }
}

// ==================== Domain -> Scale mappers ====================

private fun SsoSessionRequest.Content.toMessageContent(): SsoMessageContent {
    return when (this) {
        SsoSessionRequest.Content.Disconnected -> SsoMessageContent.Disconnected
        is SsoSessionRequest.Content.SigningRequest -> SsoMessageContent.SigningRequest(request.toScale())
        is SsoSessionRequest.Content.CreateTransactionRequest -> SsoMessageContent.CreateTransactionRequest(request.payload.toCreateTransactionRequestScale())
        is SsoSessionRequest.Content.AliasRequest -> SsoMessageContent.RingVrfAliasRequest(
            requestedAccount = productAccountId.toScale(),
            callingProductId = productDotNsIdentifier,
        )
        is SsoSessionRequest.Content.ResourceAllocationRequest -> SsoMessageContent.ResourceAllocationRequest(
            request = SsoResourceAllocationRequestScale(
                callingProductId = callingProduct.value,
                resources = resources.map { it.toScale() },
                onExisting = onExisting.toScale(),
            )
        )
    }
}

private fun SsoSessionResponse.Content.toMessageContent(respondingTo: SsoSessionRequestId): SsoMessageContent {
    return when (this) {
        is SsoSessionResponse.Content.SignedPayload -> SsoMessageContent.SigningResponse(respondingTo, BSResult.Ok(signed.toScale()))
        is SsoSessionResponse.Content.FailedToSignTransaction -> SsoMessageContent.SigningResponse(respondingTo, BSResult.Err(error))
        is SsoSessionResponse.Content.SignedGeneralTransaction -> SsoMessageContent.CreateTransactionResponse(respondingTo, BSResult.Ok(signedTx))
        is SsoSessionResponse.Content.FailedToCreateTransaction -> SsoMessageContent.CreateTransactionResponse(respondingTo, BSResult.Err(error))
        is SsoSessionResponse.Content.AliasResult -> SsoMessageContent.RingVrfAliasResponse(respondingTo, BSResult.Ok(alias.toScale()))
        is SsoSessionResponse.Content.FailedToGetAlias -> SsoMessageContent.RingVrfAliasResponse(respondingTo, BSResult.Err(error))
        is SsoSessionResponse.Content.ResourceAllocationResult -> SsoMessageContent.ResourceAllocationResponse(
            respondingTo = respondingTo,
            payload = BSResult.Ok(outcomes.map { it.toScale() }),
        )
        is SsoSessionResponse.Content.FailedToAllocateResources -> SsoMessageContent.ResourceAllocationResponse(
            respondingTo = respondingTo,
            payload = BSResult.Err(error),
        )
    }
}

private fun ApAllocatableResource.toScale(): SsoApAllocatableResourceScale = when (this) {
    ApAllocatableResource.StatementStoreAllowance -> SsoApAllocatableResourceScale.StatementStoreAllowance
    ApAllocatableResource.BulletInAllowance -> SsoApAllocatableResourceScale.BulletInAllowance
    is ApAllocatableResource.SmartContractAllowance -> SsoApAllocatableResourceScale.SmartContractAllowance(dest)
    ApAllocatableResource.AutoSigning -> SsoApAllocatableResourceScale.AutoSigning
}

private fun SsoApAllocatableResourceScale.toDomain(): ApAllocatableResource = when (this) {
    SsoApAllocatableResourceScale.StatementStoreAllowance -> ApAllocatableResource.StatementStoreAllowance
    SsoApAllocatableResourceScale.BulletInAllowance -> ApAllocatableResource.BulletInAllowance
    is SsoApAllocatableResourceScale.SmartContractAllowance -> ApAllocatableResource.SmartContractAllowance(dest)
    SsoApAllocatableResourceScale.AutoSigning -> ApAllocatableResource.AutoSigning
}

private fun OnExistingAllowancePolicy.toScale(): SsoOnExistingAllowancePolicyScale = when (this) {
    OnExistingAllowancePolicy.IGNORE -> SsoOnExistingAllowancePolicyScale.IGNORE
    OnExistingAllowancePolicy.INCREASE -> SsoOnExistingAllowancePolicyScale.INCREASE
}

private fun SsoOnExistingAllowancePolicyScale.toDomain(): OnExistingAllowancePolicy = when (this) {
    SsoOnExistingAllowancePolicyScale.IGNORE -> OnExistingAllowancePolicy.IGNORE
    SsoOnExistingAllowancePolicyScale.INCREASE -> OnExistingAllowancePolicy.INCREASE
}

private fun ApAllocationOutcome.toScale(): SsoApAllocationOutcomeScale = when (this) {
    is ApAllocationOutcome.Allocated -> SsoApAllocationOutcomeScale.Allocated(resource.toScale())
    ApAllocationOutcome.Rejected -> SsoApAllocationOutcomeScale.Rejected
    ApAllocationOutcome.NotAvailable -> SsoApAllocationOutcomeScale.NotAvailable
}

private fun ApAllocatedResource.toScale(): SsoApAllocatedResourceScale = when (this) {
    is ApAllocatedResource.StatementStoreAllowance ->
        SsoApAllocatedResourceScale.StatementStoreAllowance(slotAccountKey.bytes.value)
    is ApAllocatedResource.BulletInAllowance ->
        SsoApAllocatedResourceScale.BulletInAllowance(slotAccountKey.bytes.value)
    ApAllocatedResource.SmartContractAllowance -> SsoApAllocatedResourceScale.SmartContractAllowance
}

@Suppress("unused")
private fun SsoApAllocationOutcomeScale.toDomain(): ApAllocationOutcome = when (this) {
    is SsoApAllocationOutcomeScale.Allocated -> ApAllocationOutcome.Allocated(resource.toDomain())
    SsoApAllocationOutcomeScale.Rejected -> ApAllocationOutcome.Rejected
    SsoApAllocationOutcomeScale.NotAvailable -> ApAllocationOutcome.NotAvailable
}

@Suppress("unused")
private fun SsoApAllocatedResourceScale.toDomain(): ApAllocatedResource = when (this) {
    is SsoApAllocatedResourceScale.StatementStoreAllowance ->
        ApAllocatedResource.StatementStoreAllowance(SlotAccountKey(DataByteArray(slotAccountKey)))
    is SsoApAllocatedResourceScale.BulletInAllowance ->
        ApAllocatedResource.BulletInAllowance(SlotAccountKey(DataByteArray(slotAccountKey)))
    SsoApAllocatedResourceScale.SmartContractAllowance -> ApAllocatedResource.SmartContractAllowance
}

// ==================== SigningRequest mappers ====================

private fun SsoSigningRequestScale.toDomain(): SigningRequestBody.ResultHasSignature {
    return when (this) {
        is SsoSigningRequestScale.Transaction -> SigningRequestBody.Transaction(payload.toDomain())
        is SsoSigningRequestScale.RawPayload -> SigningRequestBody.Raw(payload.toDomain())
    }
}

private fun SigningRequestBody.ResultHasSignature.toScale(): SsoSigningRequestScale {
    return when (this) {
        is SigningRequestBody.Transaction -> SsoSigningRequestScale.Transaction(payload.toScale())
        is SigningRequestBody.Raw -> SsoSigningRequestScale.RawPayload(payload.toScale())
    }
}

// ==================== SigningRawPayload mappers ====================

private fun SsoSigningRawPayloadScale.toDomain(): SigningRawPayload {
    return SigningRawPayload(
        account = account.toDomain(),
        type = type.toDomain()
    )
}

private fun SigningRawPayload.toScale(): SsoSigningRawPayloadScale {
    return SsoSigningRawPayloadScale(
        account = account.toScale(),
        type = type.toScale()
    )
}

private fun SsoPayloadTypeScale.toDomain(): RawPayloadContent {
    return when (this) {
        is SsoPayloadTypeScale.Bytes -> RawPayloadContent.Bytes(data)
        is SsoPayloadTypeScale.Payload -> RawPayloadContent.Payload(data)
    }
}

private fun RawPayloadContent.toScale(): SsoPayloadTypeScale {
    return when (this) {
        is RawPayloadContent.Bytes -> SsoPayloadTypeScale.Bytes(data)
        is RawPayloadContent.Payload -> SsoPayloadTypeScale.Payload(data)
    }
}

// ==================== SignerPayloadJson mappers ====================

private fun SsoSignerPayloadJsonScale.toDomain(): SignerPayloadJson {
    return SignerPayloadJson(
        account = account.toDomain(),
        blockHash = blockHash,
        blockNumber = blockNumber,
        era = era,
        genesisHash = genesisHash,
        method = method,
        nonce = nonce,
        specVersion = specVersion,
        tip = tip,
        transactionVersion = transactionVersion,
        signedExtensions = signedExtensions,
        version = version,
        assetId = assetId,
        metadataHash = metadataHash,
        mode = mode,
        withSignedTransaction = withSignedTransaction,
    )
}

private fun SignerPayloadJson.toScale(): SsoSignerPayloadJsonScale {
    return SsoSignerPayloadJsonScale(
        account = account.toScale(),
        blockHash = blockHash,
        blockNumber = blockNumber,
        era = era,
        genesisHash = genesisHash,
        method = method,
        nonce = nonce,
        specVersion = specVersion,
        tip = tip,
        transactionVersion = transactionVersion,
        signedExtensions = signedExtensions,
        version = version,
        assetId = assetId,
        metadataHash = metadataHash,
        mode = mode,
        withSignedTransaction = withSignedTransaction,
    )
}

// ==================== SignedTransaction mappers ====================

private fun SignedTransaction.WithDedicatedSignature.toScale(): SsoSignedPayloadJsonScale {
    return when (this) {
        is SignedTransaction.PayloadJson -> SsoSignedPayloadJsonScale(
            signature = signature.value,
            signedTx = signedTx.value,
        )
        is SignedTransaction.Raw -> SsoSignedPayloadJsonScale(
            signature = signature.value,
            signedTx = null,
        )
    }
}

// ==================== ContextualAlias mappers ====================

private fun ContextualAlias.toScale(): SsoContextualAliasScale {
    return SsoContextualAliasScale(
        context = context.value,
        alias = alias.value,
    )
}

// ==================== CreateTransaction mappers ====================

private fun SsoCreateTransactionRequestScale.toDomain(): TxPayload<ProductAccountId> {
    return when (val versioned = payload) {
        is SsoVersionedTxPayloadScale.V1 -> versioned.payload.toDomain()
    }
}

private fun SsoTxPayloadScale.toDomain(): TxPayload<ProductAccountId> {
    return TxPayload(
        signer = signer.toDomain(),
        genesisHash = genesisHash.toDataByteArray(),
        callData = callData.toDataByteArray(),
        extensions = extensions.map { it.toDomain() },
        txExtVersion = txExtVersion,
    )
}

private fun SsoEncodedTransactionExtensionValueScale.toDomain(): EncodedTransactionExtensionValue {
    return EncodedTransactionExtensionValue(id = id, implicit = implicit.toDataByteArray(), explicit = explicit.toDataByteArray())
}

private fun TxPayload<ProductAccountId>.toCreateTransactionRequestScale(): SsoCreateTransactionRequestScale {
    val payload = SsoTxPayloadScale(
        signer = signer.toScale(),
        genesisHash = genesisHash.value,
        callData = callData.value,
        extensions = extensions.map { it.toScale() },
        txExtVersion = txExtVersion,
    )
    return SsoCreateTransactionRequestScale(payload = SsoVersionedTxPayloadScale.V1(payload))
}

private fun EncodedTransactionExtensionValue.toScale(): SsoEncodedTransactionExtensionValueScale {
    return SsoEncodedTransactionExtensionValueScale(id = id, implicit = implicit.value, explicit = explicit.value)
}

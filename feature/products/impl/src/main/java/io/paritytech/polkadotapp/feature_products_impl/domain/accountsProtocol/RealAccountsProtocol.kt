package io.paritytech.polkadotapp.feature_products_impl.domain.accountsProtocol

import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.common.utils.mapAsync
import io.paritytech.polkadotapp.feature_pgas_api.domain.PgasClaimer
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.AccountsProtocol
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocatableResource
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocatedResource
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.ApAllocationOutcome
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.OnExistingAllowancePolicy
import io.paritytech.polkadotapp.feature_products_api.domain.accountsProtocol.deriveAccountId
import io.paritytech.polkadotapp.feature_products_api.model.ProductAccountId
import io.paritytech.polkadotapp.feature_products_api.model.ProductId
import io.paritytech.polkadotapp.feature_products_impl.domain.ProductAccountDerivationUseCase
import io.paritytech.polkadotapp.feature_products_impl.domain.resourceAllocationRequest.ResourceAllocationRequestContext
import io.paritytech.polkadotapp.feature_products_impl.domain.resourceAllocationRequest.ResourceAllocationRequestContextHolder
import io.paritytech.polkadotapp.feature_products_impl.presentation.productBotManagement.ProductsRouter
import io.paritytech.polkadotapp.feature_statement_store_api.domain.slotAllocator.SlotPriority
import io.paritytech.polkadotapp.feature_statement_store_api.domain.slotAllocator.StatementStoreSlotAllocator
import io.paritytech.polkadotapp.feature_transaction_storage_api.domain.slotAllocator.TransactionStorageSlotAllocator
import javax.inject.Inject
import io.paritytech.polkadotapp.feature_pgas_api.domain.OnExistingAllocationStrategy as PgasOnExistingAllocationStrategy
import io.paritytech.polkadotapp.feature_statement_store_api.domain.slotAllocator.OnExistingAllocationStrategy as StatementStoreOnExistingAllocationStrategy
import io.paritytech.polkadotapp.feature_transaction_storage_api.domain.slotAllocator.OnExistingAllocationStrategy as TransactionStorageOnExistingAllocationStrategy

class RealAccountsProtocol @Inject constructor(
    private val allowanceAccountDerivation: AllowanceAccountDerivation,
    private val productAccountDerivationUseCase: ProductAccountDerivationUseCase,
    private val transactionStorageSlotAllocator: TransactionStorageSlotAllocator,
    private val statementStoreSlotAllocator: StatementStoreSlotAllocator,
    private val pgasClaimer: PgasClaimer,
    private val contextHolder: ResourceAllocationRequestContextHolder,
    private val productsRouter: ProductsRouter,
) : AccountsProtocol {
    override suspend fun requestResourceAllocation(
        callingProduct: ProductId,
        resources: List<ApAllocatableResource>,
        onExisting: OnExistingAllowancePolicy,
    ): List<ApAllocationOutcome> {
        if (resources.isEmpty()) return emptyList()

        if (!awaitUserApproval(callingProduct, resources)) {
            return List(resources.size) { ApAllocationOutcome.Rejected }
        }

        return resources.mapAsync { resource ->
            allocate(callingProduct, resource, onExisting)
                .logFailure("Failed to allocate $resource for $callingProduct")
                .getOrElse { ApAllocationOutcome.NotAvailable }
        }
    }

    private suspend fun awaitUserApproval(
        callingProduct: ProductId,
        resources: List<ApAllocatableResource>,
    ): Boolean {
        val context = ResourceAllocationRequestContext(
            productId = callingProduct,
            resources = resources,
        )
        contextHolder.set(context)
        productsRouter.openResourceAllocationRequestPrompt()
        return context.awaitDecision() is ResourceAllocationRequestContext.Decision.Approved
    }

    private suspend fun allocate(
        callingProduct: ProductId,
        resource: ApAllocatableResource,
        onExisting: OnExistingAllowancePolicy,
    ): Result<ApAllocationOutcome> = when (resource) {
        ApAllocatableResource.BulletInAllowance ->
            allowanceAccountDerivation.deriveSlotKey(AllowanceSystem.BULLETIN, callingProduct)
                .flatMap { key ->
                    transactionStorageSlotAllocator.allocate(key.deriveAccountId(), onExisting.toTransactionStorageStrategy())
                        .map { ApAllocationOutcome.Allocated(ApAllocatedResource.BulletInAllowance(key)) }
                }

        ApAllocatableResource.StatementStoreAllowance ->
            allowanceAccountDerivation.deriveSlotKey(AllowanceSystem.STATEMENT_STORE, callingProduct)
                .flatMap { key ->
                    statementStoreSlotAllocator.allocate(key.deriveAccountId(), onExisting.toStatementStoreStrategy(), SlotPriority.Normal)
                        .map { ApAllocationOutcome.Allocated(ApAllocatedResource.StatementStoreAllowance(key)) }
                }

        is ApAllocatableResource.SmartContractAllowance -> {
            val productAccountId = ProductAccountId(callingProduct.value, resource.dest)
            productAccountDerivationUseCase.deriveAccountId(productAccountId)
                .flatMap { pgasClaimer.claim(it, onExisting.toPgasStrategy()) }
                .map { ApAllocationOutcome.Allocated(ApAllocatedResource.SmartContractAllowance) }
        }

        ApAllocatableResource.AutoSigning -> Result.success(ApAllocationOutcome.NotAvailable)
    }
}

private fun OnExistingAllowancePolicy.toTransactionStorageStrategy(): TransactionStorageOnExistingAllocationStrategy = when (this) {
    OnExistingAllowancePolicy.IGNORE -> TransactionStorageOnExistingAllocationStrategy.IGNORE
    OnExistingAllowancePolicy.INCREASE -> TransactionStorageOnExistingAllocationStrategy.INCREASE
}

private fun OnExistingAllowancePolicy.toStatementStoreStrategy(): StatementStoreOnExistingAllocationStrategy = when (this) {
    OnExistingAllowancePolicy.IGNORE -> StatementStoreOnExistingAllocationStrategy.IGNORE
    OnExistingAllowancePolicy.INCREASE -> StatementStoreOnExistingAllocationStrategy.INCREASE
}

private fun OnExistingAllowancePolicy.toPgasStrategy(): PgasOnExistingAllocationStrategy = when (this) {
    OnExistingAllowancePolicy.IGNORE -> PgasOnExistingAllocationStrategy.IGNORE
    OnExistingAllowancePolicy.INCREASE -> PgasOnExistingAllocationStrategy.INCREASE
}

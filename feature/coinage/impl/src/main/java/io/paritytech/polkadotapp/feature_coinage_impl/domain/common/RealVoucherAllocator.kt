package io.paritytech.polkadotapp.feature_coinage_impl.domain.common

import io.paritytech.polkadotapp.feature_coinage_api.domain.common.VoucherAllocator
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerFungibility
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent
import io.paritytech.polkadotapp.feature_coinage_impl.data.derivation.VoucherRingDerivation
import io.paritytech.polkadotapp.feature_coinage_impl.data.derivation.getDerivedMemberKey
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.ExponentBoundsRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.validateValueExponent
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.validateValueExponents
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class RealVoucherAllocator @Inject constructor(
    private val voucherRepository: VoucherRepository,
    private val voucherRingDerivation: VoucherRingDerivation,
    private val boundsRepository: ExponentBoundsRepository,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider
) : VoucherAllocator {
    private val allocationMutex = Mutex()

    override suspend fun allocate(valueExponent: ValueExponent): Result<RecyclerVoucher> =
        allocationMutex.withLock {
            boundsRepository.validateValueExponent(chainAssetProvider.chainId(), valueExponent)
                .map { validExponent ->
                    val derivationIndex = voucherRepository.getNextDerivationIndex()
                    val voucher = createVoucherForIndex(derivationIndex, validExponent)
                    voucher.apply { voucherRepository.save(this) }
                }
        }

    override suspend fun allocateAll(valueExponents: List<ValueExponent>): Result<List<RecyclerVoucher>> = allocationMutex.withLock {
        boundsRepository.validateValueExponents(chainAssetProvider.chainId(), valueExponents)
            .map { validExponents ->
                val nexDerivationIndex = voucherRepository.getNextDerivationIndex()

                val vouchers = validExponents.mapIndexed { index, value ->
                    createVoucherForIndex(nexDerivationIndex + index, value)
                }

                voucherRepository.saveAll(vouchers)

                vouchers
            }
    }

    private suspend fun createVoucherForIndex(derivationIndex: Int, valueExponent: ValueExponent): RecyclerVoucher {
        val publicKey = voucherRingDerivation.getDerivedMemberKey(derivationIndex)

        return RecyclerVoucher(
            ringVrfKeyIndex = derivationIndex,
            ringVrfPublicKey = publicKey,
            location = RecyclerVoucher.Location.Unknown,
            recyclerValue = valueExponent,
            // A voucher outside a ring hides in nothing, so zero is the truth rather than a stand-in. The
            // max is a different matter: it needs a ring index the chain has not assigned yet, so it stays
            // unfrozen until the location service sees the voucher land.
            recyclerFungibility = RecyclerFungibility.NONE,
            maxRecyclerFungibility = null,
        )
    }
}

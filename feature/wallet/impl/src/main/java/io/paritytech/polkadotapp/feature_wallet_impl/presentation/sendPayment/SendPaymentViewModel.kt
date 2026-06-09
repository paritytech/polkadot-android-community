package io.paritytech.polkadotapp.feature_wallet_impl.presentation.sendPayment

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.paritytech.polkadotapp.common.presentation.clipboard.ClipboardService
import io.paritytech.polkadotapp.common.presentation.loading.mapLoading
import io.paritytech.polkadotapp.common.presentation.screens.BaseViewModel
import io.paritytech.polkadotapp.common.utils.flowOf
import io.paritytech.polkadotapp.common.utils.mapList
import io.paritytech.polkadotapp.common.utils.mapValuesNotNull
import io.paritytech.polkadotapp.design.components.avatar.AvatarUiModel
import io.paritytech.polkadotapp.design.configs.colors.AvatarColorScheme
import io.paritytech.polkadotapp.feature_account_api.presentation.address.converter.ParseAddressConverterFactory
import io.paritytech.polkadotapp.feature_account_api.presentation.address.mixin.AddressInputMixin
import io.paritytech.polkadotapp.feature_account_api.presentation.address.model.ExtractedAddress
import io.paritytech.polkadotapp.feature_account_api.presentation.address.model.toParcel
import io.paritytech.polkadotapp.feature_chats_api.domain.usecase.GetContactsUseCase
import io.paritytech.polkadotapp.feature_transfers_api.presentation.PreviousPaymentsAddressConverterFactory
import io.paritytech.polkadotapp.feature_usernames_api.presentation.address.ParseAddressUsernameConverterFactory
import io.paritytech.polkadotapp.feature_usernames_api.presentation.address.UsernameAddressConverterFactory
import io.paritytech.polkadotapp.feature_usernames_api.presentation.filterAvailableUsernameSymbols
import io.paritytech.polkadotapp.feature_wallet_api.presentation.enterAmount.SendEnterAmountPayload
import io.paritytech.polkadotapp.feature_wallet_api.presentation.enterAmount.TransferMethodPayload
import io.paritytech.polkadotapp.feature_wallet_impl.PocketRouter
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.scanAddressQr.ScanAddressQrResultPayload
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.sendPayment.domain.SendPaymentInteractor
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class SendPaymentViewModel @Inject constructor(
    private val walletRouter: PocketRouter,
    private val clipboardService: ClipboardService,
    private val getContactsUseCase: GetContactsUseCase,
    addressInputMixinFactory: AddressInputMixin.Factory,
    usernameAddressConverterFactory: UsernameAddressConverterFactory,
    parseAddressConverterFactory: ParseAddressConverterFactory,
    parserAddressUsernameConverterFactory: ParseAddressUsernameConverterFactory,
    previousPaymentsAddressConverterFactory: PreviousPaymentsAddressConverterFactory,
    interactor: SendPaymentInteractor,
) : BaseViewModel(), SendPaymentContract {
    private val contacts = flowOf { getContactsUseCase() }
        .mapList { it.accountId }
        .stateIn(this, SharingStarted.Eagerly, listOf())

    private val addressInputMixin = addressInputMixinFactory.create(
        coroutineScope = viewModelScope,
        converters = listOf(
            parserAddressUsernameConverterFactory.create(
                parseAddressConverterFactory.create(interactor.chainId())
            ),
            usernameAddressConverterFactory.create(),
            previousPaymentsAddressConverterFactory.create(interactor.chainId())
        )
    )

    private val input = MutableStateFlow("")

    private val addressCandidates = addressInputMixin.addressCandidates
        .mapLoading { result ->
            val contacts = contacts.value

            buildList {
                result.forEach {
                    addAll(it.value)
                }
            }
                .filter { contacts.contains(it.accountId) }
                .groupBy { it.accountId }
                .mapValuesNotNull { (_, group) ->
                    group
                        .find {
                            it.type == ExtractedAddress.DisplayType.USERNAME
                        }
                        ?: group
                            .find {
                                it.type == ExtractedAddress.DisplayType.ADDRESS
                            }
                }
                .values
                .map { extractedAddress ->
                    PaymentSearchResultUiModel(
                        extractedAddress = extractedAddress,
                        avatarModel = AvatarUiModel.Name(
                            name = extractedAddress.display,
                            colorScheme = AvatarColorScheme.from(extractedAddress.accountId.value)
                        ),
                    )
                }
                .toImmutableList()
        }

    override val state: StateFlow<SendPaymentUiState> = combine(
        input,
        addressCandidates
    ) { inputValue, loadingState ->
        SendPaymentUiState(
            input = inputValue,
            loadingState = loadingState,
        )
    }.stateIn(
        scope = this,
        started = SharingStarted.Eagerly,
        initialValue = SendPaymentUiState()
    )

    init {
        input
            .debounce(300.milliseconds)
            .mapLatest {
                addressInputMixin.input.value = it
            }
            .launchIn(this)
    }

    fun onQrResult(payload: ScanAddressQrResultPayload) {
        onInputChange(payload.address)
    }

    override fun onInputChange(value: String) {
        input.update { value.filterAvailableUsernameSymbols() }
    }

    override fun onRecipientSelect(recipient: PaymentSearchResultUiModel) {
        walletRouter.openSendEnterAmount(
            SendEnterAmountPayload(
                showTransactionResult = true,
                transferMethod = TransferMethodPayload.CoinsViaChat(recipient.extractedAddress.toParcel()),
                amountPreset = null,
            )
        )
    }

    override fun onPasteClick() {
        clipboardService.getPrimaryClip()?.let { onInputChange(it) }
    }

    override fun onScannerClick() {
        walletRouter.openScanAddressQr()
    }

    override fun onBackClick() {
        walletRouter.back()
    }
}

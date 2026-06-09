# State Management (ViewModels and Flows)

The bedrock rule: **derive, don't push.** Each `StateFlow` exposed by a ViewModel is built by composing upstream flows (`combine`, `map`, `flatMapLatest`, `stateIn`). The only mutable sources at the top of the chain are user inputs and one-shot UI events.

This single discipline kills the majority of "go-to code" — chasing where a value gets emitted across files and coroutines.

---

## The canonical ViewModel

`feature/sso/impl/.../presentation/pairRequest/PairRequestViewModel.kt` is the reference shape. Mentally template every new VM on this.

```kotlin
@HiltViewModel
class PairRequestViewModel @Inject constructor(
    private val interactor: PairRequestInteractor,
    @Assisted private val payload: PairRequestPayload,
) : BaseViewModel(), PairRequestContract {

    // ── Mutable internal state (user-driven only) ──────────────
    private val approving = MutableStateFlow(false)

    // ── Source flows ────────────────────────────────────────────
    private val hostMetadataFlow = flowOf { interactor.fetchHostMetadata(payload.url) }   // Flow<Result<HostMetadata>>

    // ── Derived UI state ────────────────────────────────────────
    override val state: StateFlow<LoadingState<PairRequestUiState>> =
        combine(hostMetadataFlow, approving) { metadataResult, isApproving ->
            metadataResult.map { metadata ->
                PairRequestUiState(
                    hostName = metadata.name,
                    hostIconUrl = metadata.icon,
                    approving = isApproving,
                )
            }
        }
            .withLoading("PairRequest")
            .stateIn(this, SharingStarted.Eagerly, LoadingState.Loading)

    // ── Action handlers ─────────────────────────────────────────
    override fun onApproveClicked() = launchUnit {
        approving.value = true
        interactor.approveHandshake(payload.toDomain(), getHostMetadata())
            .onSuccess { router.back() }
            .onFailure { approving.value = false; showError(it) }
    }
}
```

Layout principle: **mutable inputs → source flows → derived flows → exposed state**, top to bottom. Side-effect handlers (action methods) at the bottom.

---

## Top-to-bottom flow style

This is the doctrine:

```
// Mutable state / components section
val pin = MutableStateFlow("")
val amountInput = amountInputFactory.create(viewModelScope, config)

// Derived flows section
val isValid = pin.map { it.length == 6 }
val total   = combine(amountInput.state, feeMixin.fee) { amt, fee -> amt + fee }

// Connecting coroutines (init block)
init {
    feeMixin.connectWith(amountInput.state) { amt -> loadFee(amt) }
}
```

The reader can pick the file up cold and trace from inputs → outputs without jumping across files or coroutines.

### Anti-pattern: MutableStateFlow + coroutine emitting into it

```kotlin
// ✗ Forces the reader to find where this is updated
override val username = MutableStateFlow<LoadingState<String?>>(LoadingState.Loading)

private fun loadUsername() = launchUnit {
    interactor.getUsername(accountId)
        .onSuccess { username.value = LoadingState.Loaded(it) }
        .onFailure { username.value = LoadingState.Loaded(null) }
}
```

Replace with:
```kotlin
// ✓ Derived at declaration
override val username: StateFlow<LoadingState<String?>> = flow {
    val result = interactor.getUsername(accountId)
    emit(LoadingState.Loaded(result.getOrNull()))
}
    .onStart { emit(LoadingState.Loading) }
    .stateIn(this, SharingStarted.Eagerly, LoadingState.Loading)
```

### When `MutableStateFlow` *is* the right tool

Use a `MutableStateFlow` (or shared state from a Mixin) for:
- **Text field values** typed by the user.
- **Selected tab / radio / picker**.
- **Action-in-progress flags** like `approving`, `submitting`.
- **One-shot UI events** when no `SharedFlow` is appropriate.

When the value can be derived from existing flows, derive it. Agent uses judgment — but the bar is "could I `combine` to produce this?".

---

## Single state, generally

Default to a single `StateFlow<LoadingState<XxxUiState>>` for a screen. Construct it **once** via `combine`, not by patching with `.copy(...)`.

```kotlin
// ✓
override val state = combine(metaFlow, approving) { meta, isApproving ->
    meta.map { PairRequestUiState(it.name, approving = isApproving) }
}.withLoading("Tag").stateIn(this, SharingStarted.Eagerly, LoadingState.Loading)

// ✗ — copy-patching loses the "construct once" invariant
override val state = MutableStateFlow(PairRequestUiState())
private fun loadMeta() = launchUnit { ... state.value = state.value.copy(hostName = it.name) ... }
private fun setApproving(b: Boolean) { state.value = state.value.copy(approving = b) }
```

**Granular states** (multiple exposed `StateFlow`s) are allowed when:
- A widget genuinely validates independently (e.g. username field pinging a server while the rest of the form sits idle).
- Two unrelated chunks of UI live on the same screen and recombining them doesn't add clarity.

Reviewer flags **only when granular states cause inconsistency** — one updates, the others stay stale, the UI shows a contradictory mix. If you can produce a consistent UI by composing the granular states at render time, fine.

### When `combine` arity gets ugly

If you find yourself combining 6+ flows in one shot, **combine earlier**. Group related upstream flows into intermediate combined flows first. PR #503 lesson: "cramming everything into a single combine makes the code harder to read and leads to 'array by index' reads".

---

## Shadow the mutable: avoid `_state` backing fields

In Kotlin, `MutableStateFlow` is a `StateFlow`. Override the Contract's `StateFlow` property directly with a `MutableStateFlow`:

```kotlin
// ✓
override val state = MutableStateFlow(InitialState)

// ✗ Boilerplate
private val _state = MutableStateFlow(InitialState)
override val state: StateFlow<State> = _state.asStateFlow()
```

(See UI_GUIDELINES section absorbed here. CLAUDE.md memory `feedback_state_machine_shape` reiterates.)

---

## Mixins — reusable VM components

When the same state machine recurs across screens (amount input, fee, recipient, address book picker), encapsulate it in a **Mixin**.

### Interface in `api/`

```kotlin
// feature/transfers/api/.../presentation/AmountInputMixin.kt
interface AmountInputMixin {

    data class Configuration(
        val asset: Asset,
        val initialAmount: Balance?,
    )

    data class State(
        val raw: String,
        val parsed: Balance?,
        val error: AmountInputError?,
    )

    val state: StateFlow<State>

    fun onValueChanged(raw: String)

    interface Factory {
        fun create(scope: CoroutineScope, config: Configuration): AmountInputMixin
    }
}
```

### Impl in `impl/`

```kotlin
// feature/transfers/impl/.../presentation/mixin/RealAmountInputMixin.kt
internal class RealAmountInputMixin(
    private val scope: CoroutineScope,
    private val config: Configuration,
    // collaborators injected via @AssistedInject Factory:
    private val priceFormatter: PriceFormatter,
) : AmountInputMixin {

    private val raw = MutableStateFlow(config.initialAmount?.toRaw().orEmpty())

    override val state = raw.map { rawString ->
        val parsed = rawString.parseAmount(config.asset)
        State(rawString, parsed, parsed.validate())
    }.stateIn(scope, SharingStarted.Eagerly, State("", null, null))

    override fun onValueChanged(raw: String) { this.raw.value = raw }
}
```

### Factory binding

```kotlin
// @AssistedInject with @AssistedFactory bound by Hilt
class RealAmountInputMixinFactory @Inject constructor(...) : AmountInputMixin.Factory {
    override fun create(scope: CoroutineScope, config: Configuration) = RealAmountInputMixin(scope, config, ...)
}
```

### Use in a ViewModel

```kotlin
class SendViewModel @Inject constructor(
    amountInputFactory: AmountInputMixin.Factory,
    feeFactory: FeeMixin.Factory,
) : BaseViewModel() {
    val amountInput = amountInputFactory.create(viewModelScope, Configuration(asset, null))
    val fee = feeFactory.create(viewModelScope)

    init {
        fee.connectWith(amountInput.state) { state -> state.parsed?.let(::loadFee) }
    }
}
```

### Mixin rules

- **Interface (+ State + Configuration + Factory) lives in `api/presentation/mixin/`.**
- **Impl + Factory binding live in `impl/presentation/mixin/`.** Bound by Hilt.
- The Factory takes a `CoroutineScope` so the Mixin is **scope-tied** — when the VM dies, the Mixin's flows go silent.
- Mixins compose via simple `connectWith` facade functions where one Mixin needs another's output. Don't make Mixins know about each other's types — pass states explicitly.
- Generics are OK: `interface FeeMixin<F>` for the fee value type.

### When NOT to introduce a Mixin

- One-off state used in a single VM — keep it in the VM.
- A reusable Mixin would just wrap one repository call — inline it.

---

## Loading state — reuse, don't reinvent

`LoadingState<T>` lives in `common/.../presentation/loading/`. Use `.withLoading("Tag")` on `Flow<Result<T>>` to wrap it:

```kotlin
flow.withLoading("ScreenTag").stateIn(this, SharingStarted.Eagerly, LoadingState.Loading)
```

Do **not** invent bespoke `sealed class Loading | Loaded | Failed` per feature (memory `feedback_loading_state`). The exception: when a screen's loading model is truly distinct (e.g. multi-stage progress with named stages), define a custom enum — but only after you've ruled out `LoadingState<MyMultiStageData>`.

---

## Coroutine scopes

| Scope | Use |
|---|---|
| `viewModelScope` | VM-tied work. `launch`, `launchUnit`. |
| `ComputationalScope` | App-process-wide flows that outlive a screen. Wraps `ProcessLifecycleOwner`. Limited use — most things should be VM-scoped. |
| `AppInitializerPipeline` (called from `App.onCreate`) | One-shot startup work registered via `Set<AppInitializer>` (PR #499). |
| `context(CoroutineScope)` | Flow extension functions like `shareInBackground()`. |

### `launchUnit`

`launchUnit { ... }` is the project's terse `Job`-less launch for fire-and-forget VM coroutines. Use in click handlers and one-shot actions. (Multiple PR comments enforce this.)

### Don't use `replayCache`

If you find yourself reaching for `replayCache[0]` to peek a `SharedFlow`, either:
- Convert to a `StateFlow` (always has a current value), or
- Use `flow.first()` to pull the latest synchronously inside a suspending call.

`replayCache` access is a workaround signal (PR #494).

---

## AppInitializer — for app-startup side effects

See `code/di-and-lifecycle.md § AppInitializer — the only allowed startup-side-effect pattern`. Use `AppInitializer @IntoSet` for app-start subscriptions, registry priming, stale-notification cleanup. Don't inject classes into `App.onCreate` just to trigger their `init {}` blocks (PR #499).

---

## One-shot UI events

For "show snackbar / open dialog / navigate once / play a one-shot animation" — events that should fire exactly once and not survive recomposition:

- Prefer a `MutableSharedFlow<Event>()` (default constructor — `replay = 0`, no extra buffer) exposed via the Contract as `SharedFlow<Event>`. Emit from a `viewModelScope.launch { _events.emit(...) }`. Project examples: `ScanQrViewModel.invalidCodeEvent`, `RecoverMnemonicViewModel.invalidMnemonicEvents`, `MobRuleBotFooterViewModel.votingFailedEvents`, `ScanAddressQrViewModel.invalidAddressEvent`.
- For "must-not-drop" events (e.g. awaitable actions), expose a `Channel<Event>.receiveAsFlow()` instead. Project example: `SsoSessionsListViewModel.deleteConfirmation`.
- **`major`** — The screen consumes one-shot events with `Flow<T>.collectAsEffect { context, event -> ... }` from `design/utils/Flows.kt`, **not** a hand-rolled `LaunchedEffect(Unit) { flow.collect { … } }`. The extension wraps `repeatOnLifecycle(STARTED)` so the collector pauses when the screen is backgrounded; hand-rolled versions leak events to a backgrounded screen. (`StateFlow` state is a separate concern — that still uses `collectAsStateWithLifecycle`.)

Don't reach into `Activity` from the VM (PR #460); always go through a channel/flow + screen handler.

---

## Flow operators — most-used

The project has a rich Flow library in `common/.../utils/Flows.kt`. **The full catalog is in `code/flow-operators-reference.md`** — load it only when you need an unusual operator. The ones you'll reach for daily:

| Operator | Use |
|---|---|
| `Flow<T>.inBackground()` | `flowOn(Dispatchers.Default)` — wrap heavy producers. |
| `Flow<T>.stateInBackgroundWithLoading(failureLog?)` (`context(CoroutineScope)`) | Most common VM terminal for `Flow<Result<T>>`. |
| `Flow<Result<T>>.withLoading("Tag")` | `Flow<Result<T>> → Flow<LoadingState<T>>` with `Loading` on subscribe. |
| `Flow<Result<T>>.mapResult { ... }` | Inline-map success; failures untouched. |
| `flowOf { suspend producer() }` | Terse single-emission flow. |
| `OneShotEventChannel<T>()` | `Channel<T>(Channel.CONFLATED)` for VM → screen one-shot events. |
| `combineToPair(a, b)` / `combineToTriple(a, b, c)` | `combine` packaged as `Pair`/`Triple`. |
| `Flow<List<T : Identifiable>>.transformLatestDiffed { ... }` | Per-element `transformLatest` driven by a diff. |

### Rules

- **`major`** — Use the catalog before writing a new combinator. If you reach for the same new operator 3+ times, **add it to `Flows.kt`** and mention it in `flow-operators-reference.md`.
- **`major`** — `inBackground()` over hand-written `flowOn(Dispatchers.Default)`.
- **`major`** — `.withLoading("Tag")` over `.onStart { emit(Loading) }.map { Loaded(it) }`.
- **`minor`** — `flowOf { suspend }` over `flow { emit(suspend()) }`.

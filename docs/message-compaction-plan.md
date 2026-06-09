# Message Compaction — Android Implementation Plan

## Problem

Chat messages sent via the Statement Store are bounded by a per-statement payload limit (`MAX_STATEMENT_SIZE = 2 KB`, effective `maxRequestSize ≈ 1880 bytes` after the 168-byte statement frame). Today, Android handles oversize in two distinct, mostly invisible ways:

1. **Single message exceeds the limit.** The state machine (`Active.kt:26-29`) emits `NotifyMessageTooLarge` and silently drops the message. The chat session (`RealContactChatSession.handleMessageTooLarge`) reacts by **deleting the row** from the database — destructive and user-visible only as a "lost" message.
2. **Backed-up queue.** When a new message can't fit into the in-flight statement-store request, the state machine just appends it to `pendingMessages` (`Active.kt:39-43` and `tryAppendMessageToOutgoingRequest:169-171`). On each `ResponseReceived` it pulls the next batch via `splitMessagesToFitRequest`. No event is emitted; the queue can grow unbounded if the producer outpaces the drain.

We need a mechanism to bundle multiple messages into a single SCALE-encoded blob, upload that blob to HOP, and replace the originals with a small reference message that fits the statement budget — and we need it triggered for both scenarios above. On the receiving side, compacted messages must be transparently downloaded and expanded back into the individual messages, with the original UI / notification / status semantics preserved.

This document is the implementation plan for porting the iOS `feature/compaction` design to Android.

## Architectural mapping (iOS → Android)

| iOS concept | Android equivalent / target location |
|---|---|
| `HandoffFileLoading.uploadBlob` / `downloadBlob` | New `HopBlobUploader` / `HopBlobDownloader` (sibling of `HopFileUploader`) |
| `FileClaimer` | Reuse `HopEncryption` + `HopTicketKeyDerivation` + `HopMultiSigner` |
| `MessageCompacting` protocol + `AnyMessageCompactor` | Plain Kotlin interface `ChatMessageCompacting`; no type-erasure wrapper needed |
| `OutgoingChannelCompactionProxy` | Logic merged into `RealContactChatSession` via a new `CompactionGate` collaborator |
| `OutgoingMessageChannelDelegate.didCompactMessages` | Not needed as a delegate — session writes to DB directly; UI subscribes via Room Flow |
| `PeerSessionFactory.compactorFactory` wiring | Constructor injection into `RealContactChatSessionManager` / `RealContactChatSession` |
| `ChatMessageCompactor` | `ChatMessageCompactor` (impl module) |
| `CompactedMessagesBlob` (SCALE versioned) | Same name, `@Serializable sealed interface` with `@EnumIndex(0) V1(...)` |
| `CompactedMessageExpansionService` | Same name, Singleton, application-scoped |
| `CompactedMessageExpansionContext` (actor) | `Mutex`-guarded class with `active`/`pending` maps |
| `ChatMessageClaimer` | Same name |
| `CompactedExpansionMessageMapper` | Inline DB transaction inside the expansion service |
| `CompactionCommitMapper` | `ChatMessageRepository.commitCompaction(...)` method |
| `MessagesLocalStorageService.fetchExpandedMessages` | New `ChatMessageDao` query + repository wrapper |
| `OutboxMessageTracker.insertAsInFlight` | Inserted via `ChatMessageRepository.commitCompaction` (NEW status); no separate tracker |
| `CDChatMessage.compactionId` / `contentExpanded` | New columns on `ChatMessageLocal` |
| `ChatMessageStatusUpdateMapper.propagateOutgoingStatusToCompactedChildren` | New DAO query + call from the status-update path |

## Key Android-specific differences

1. **The state machine already owns size accounting.** `Active.kt` runs `splitMessagesToFitRequest` and decides per-message whether to ship, append, or pend. The chat session does not (and should not) replicate this. The right place to trigger compaction is *inside* the state machine via a new event when it has to push messages into `pendingMessages` — the chat session subscribes and compacts in reaction.

2. **`EncodedMessage = ByteArray` is a leaky abstraction.** It carries no identity, so events about "these messages couldn't ship" can't be correlated back to `ChatMessage` ids without re-encoding. We type it (`data class EncodedMessage(val id: String, val data: ByteArray)`) as part of this work — see Phase 5.

3. **No delegate fan-out is needed.** Android UIs observe message state via Room `Flow`s. Persisting the compacted message + setting `compactionId` on originals is enough to drive the UI.

4. **No `OutboxMessageTracker` class today.** Outgoing-message lifecycle lives in `RealContactChatSession`'s subscription over `ChatMessage.Status`. We reuse that; the compacted message is just another NEW message it picks up and submits.

5. **`handleMessageTooLarge` only handles the single-too-big case** (not accumulated overflow — that path is silent today). After this work it routes the offending message through compaction (which wraps it into a small HOP reference) instead of deleting it.

## Phases

The phases are designed to be merged in order; each phase compiles and leaves the app in a working state.

---

### Phase 1 — Domain & wire models

**Goal:** add the new content variant end-to-end (domain → local SCALE → statement-store SCALE) without wiring any behavior.

Files to modify:
- `feature/chats/api/src/main/kotlin/io/paritytech/polkadotapp/feature_chats_api/domain/model/ChatMessage.kt`
  - Add `data class CompactedMessages(val claimIdentifier: ByteArray, val claimTicket: ByteArray, val node: String) : Content`.
- `feature/chats/impl/src/main/java/io/paritytech/polkadotapp/feature_chats_impl/domain/models/scale/ChatMessageContentLocal.kt`
  - Add `@EnumIndex(18) class CompactedMessages(val claimIdentifier: ByteArray, val claimTicket: ByteArray, val node: String)`. (Confirm next free `@EnumIndex` — current max is 17 `CoinagePayment`.)
- `feature/chats/impl/src/main/java/io/paritytech/polkadotapp/feature_chats_impl/data/model/ChatMessageStatementContent.kt`
  - Add corresponding wire variant under `ChatMessageV1`.
- `feature/chats/impl/src/main/java/io/paritytech/polkadotapp/feature_chats_impl/domain/models/scale/LocalChatMessageContentMappers.kt`
  - Bidirectional mapping for the new variant.
- `feature/chats/impl/src/main/java/io/paritytech/polkadotapp/feature_chats_impl/data/model/ChatMessageStatementContentMappers.kt`
  - Bidirectional mapping for the new variant.

New files:
- `feature/chats/impl/src/main/java/io/paritytech/polkadotapp/feature_chats_impl/domain/compaction/CompactedMessagesBlob.kt`
  ```kotlin
  @Serializable
  sealed interface CompactedMessagesBlob {
      @Serializable @EnumIndex(0)
      class V1(val messages: List<ChatMessageStatement>) : CompactedMessagesBlob
  }
  ```
  Wire format = `List<ChatMessageStatement>` (the same shape an individual chat message uses on the wire). Decoding produces messages that can be persisted via the existing `ChatMessageStatement → ChatMessage` path.

---

### Phase 2 — Database schema

**Goal:** add columns for parent/child linking and expansion tracking.

Files to modify:
- `database/src/main/java/io/paritytech/polkadotapp/database/model/ChatMessageLocal.kt`
  - Add `val compactionId: String? = null` (indexed) and `val contentExpanded: Boolean = false`.
- `database/src/main/java/io/paritytech/polkadotapp/database/dao/ChatMessageDao.kt`
  - `flowIncomingCompactedUnexpanded(): Flow<List<ChatMessageLocal>>` — `WHERE contentExpanded = 0 AND type = :compactedTypeOrdinal AND status IN (:incomingStatuses)`.
  - `selectByCompactionIds(ids: Set<String>): List<ChatMessageLocal>`.
  - `updateStatusForCompactionId(parentId: String, status: Int)` — single bulk UPDATE.
  - `selectIdAndType(id: String): Pair<String, Int>?` (or similar) — used to decide whether to propagate.
- `database/src/main/java/io/paritytech/polkadotapp/database/AppDatabase.kt`
  - Bump version 38 → 39, register migration.

New files:
- `database/src/main/java/io/paritytech/polkadotapp/database/migrations/Migration38To39.kt`
  - `ALTER TABLE chatMessages ADD COLUMN compactionId TEXT`
  - `ALTER TABLE chatMessages ADD COLUMN contentExpanded INTEGER NOT NULL DEFAULT 0`
  - `CREATE INDEX index_chatMessages_compactionId ON chatMessages(compactionId)`

---

### Phase 3 — HOP blob upload / download

**Goal:** add single-shot small-blob upload/download alongside the existing chunked `HopFileUploader`. The compaction blob is small (≤ tens of KB), so chunking + metadata-file overhead is unwarranted.

New files:
- `feature/chats/impl/src/main/java/io/paritytech/polkadotapp/feature_chats_impl/data/hop/blob/HopBlobUploader.kt`
  ```kotlin
  class HopBlobUploader(
      private val hopService: HopService,
      private val encryption: HopEncryption,
      private val keyDerivation: HopTicketKeyDerivation,
      private val signerFactory: HopSignerFactory,
  ) {
      suspend fun upload(
          data: ByteArray,
          ticket: ByteArray,
          recipients: List<HopMultiSigner>
      ): HopBlobReference // (identifier=hash, ticket, node)
  }
  ```
  Algorithm: derive encryption key from ticket → encrypt → `hopService.submit(encrypted, recipients, proof)` → return hash + ticket + node URL used.

- `feature/chats/impl/src/main/java/io/paritytech/polkadotapp/feature_chats_impl/data/hop/blob/HopBlobDownloader.kt`
  ```kotlin
  class HopBlobDownloader(...) {
      suspend fun download(
          identifier: ByteArray,
          ticket: ByteArray,
          node: String,
          onConfirm: suspend (ByteArray) -> Unit
      )
  }
  ```
  Algorithm: validate node is trusted → `hopService.claim(...)` → decrypt → invoke `onConfirm(plain)`; only ack after `onConfirm` returns.

Files to verify / possibly modify:
- `feature/chats/impl/src/main/java/io/paritytech/polkadotapp/feature_chats_impl/data/hop/HopNodeUrlProvider.kt`
  - Confirm there is (or add) a `fun isAllowed(node: String): Boolean`. iOS rejects untrusted nodes in `ChatMessageClaimer`; we need the same gate.

---

### Phase 4 — Outgoing compactor

**Goal:** given `List<ChatMessage>`, produce one compacted `ChatMessage`.

New files:
- `feature/chats/impl/src/main/java/io/paritytech/polkadotapp/feature_chats_impl/domain/compaction/ChatMessageCompactor.kt`
  ```kotlin
  interface ChatMessageCompactor {
      suspend fun compact(messages: List<ChatMessage>): Result<ChatMessage>
  }

  class RealChatMessageCompactor(
      private val blobUploader: HopBlobUploader,
      private val nodeProvider: HopNodeUrlProvider,
      private val signKeyId: String,
      private val signerFactory: HopSignerFactory,
  ) : ChatMessageCompactor { ... }
  ```
  - Maps each `ChatMessage` → `ChatMessageStatement` via existing mappers.
  - Wraps as `CompactedMessagesBlob.V1`, SCALE-encodes via `BinaryScale`.
  - Generates a fresh HOP ticket.
  - Calls `HopBlobUploader.upload(...)`.
  - Builds a new `ChatMessage` with `Content.CompactedMessages(identifier, ticket, node)`, fresh `id`, current timestamp, status `NEW`, origin = self.
  - Retry policy: **3 attempts, 2 s fixed delay**.
  - Does **not** touch the DB — that's the session's job (matches iOS).

- `feature/chats/impl/.../domain/compaction/ChatMessageCompactorFactory.kt`
  - `fun create(signKeyId: String): ChatMessageCompactor` — derives the proof wallet from the chat's signing key (mirrors iOS `DynamicDerivedWallet(derivationPath: signKeyId)`).

---

### Phase 5 — Event-driven compaction trigger

**Goal:** the statement-store state machine emits a new event when it can't ship a message immediately; the chat session reacts to that event by compacting. Size accounting stays entirely inside the state machine.

#### 5a. Type `EncodedMessage`

Convert the typealias to a data class so events can correlate back to chat-message ids.

Files to modify:
- `feature/statement-store/api/.../domain/models/CommunicationModels.kt`
  ```kotlin
  // before: typealias EncodedMessage = ByteArray
  data class EncodedMessage(val id: String, val data: ByteArray) {
      override fun equals(other: Any?): Boolean { /* id-based */ }
      override fun hashCode(): Int = id.hashCode()
  }
  ```
- All call sites — most importantly:
  - `feature/chats/impl/.../data/model/ChatMessageStatementContentMappers.kt` — `toEncodedMessage()` now produces `EncodedMessage(id = chatMessage.id, data = scaleBytes)`.
  - `feature/statement-store/impl/.../domain/sessions/stateMachine/states/CommunicationState.kt`:
    - `checkAlreadyPendingMessage` — change from `contentEquals` to id-based lookup (faster, more correct).
    - `splitMessagesToFitRequest` — measure `message.data.size` instead of `message.size`.
    - `checkSizeLimitExceeded` — measure `message.data.size`.
  - `feature/statement-store/impl/.../domain/sessions/RealCommunicationSession.kt` — wherever `EncodedMessage` byte ops happen.

#### 5b. New side-effect + event

Files to modify:
- `feature/statement-store/impl/.../domain/sessions/stateMachine/CommunicationSideEffect.kt`
  ```kotlin
  class NotifyStatementSizeLimitReached(
      val pendingMessages: List<EncodedMessage>,
      val maxAllowedSize: InformationSize
  ) : CommunicationSideEffect
  ```
  (Keep `NotifyMessageTooLarge` for now — see Open Q #11 on whether to unify.)
- `feature/statement-store/api/.../domain/models/CommunicationSessionEvent.kt`
  ```kotlin
  class StatementSizeLimitReached(
      val pendingMessages: List<EncodedMessage>,
      val maxAllowedSize: InformationSize
  ) : CommunicationSessionEvent
  ```
- `feature/statement-store/impl/.../domain/sessions/RealCommunicationSession.kt`
  - Add handler `handleStatementSizeLimitReached(...)` that translates the side-effect → event on `eventsFlow`, mirroring the existing `handleTooLargeMessage` pattern (`RealCommunicationSession.kt:251-253`).

#### 5c. Fire the event from the state machine

Files to modify:
- `feature/statement-store/impl/.../domain/sessions/stateMachine/states/Active.kt`
  - At three sites where a message becomes "stuck" in `pendingMessages`:
    1. `SubmitMessage` else-branch (`line 39-43`) — pending already non-empty, new message appended.
    2. `tryAppendMessageToOutgoingRequest` else-branch (`line 169-171`) — couldn't append to in-flight request.
    3. `ResponseReceived` split-with-remaining (`line 97-110`) — even after draining, some still don't fit.
  - In each, after the `emitState(copy(pendingMessages = ...))`, emit `NotifyStatementSizeLimitReached(pendingMessages = <new pending snapshot>, maxAllowedSize = maxRequestSize)`.
  - Also: the existing `NotifyMessageTooLarge` path (`line 26-29`) should additionally not just emit and `return` — current behavior drops the message. After this work, leave the side-effect emission (so the chat layer can log / metric) but also feed the offending message into the compaction trigger via a synthesized "pending of one" event. Or unify into a single event (Open Q #11).

#### 5d. Chat session reacts

Files to modify:
- `feature/chats/impl/.../domain/sessions/RealContactChatSession.kt`
  - Inject `ChatMessageCompactor` (created via factory keyed by `signKeyId`).
  - In the `communicationSession.subscribeEvents()` collector, handle `StatementSizeLimitReached`:
    ```kotlin
    is CommunicationSessionEvent.StatementSizeLimitReached ->
        compactionGate.onLimitReached(event.pendingMessages)
    ```
  - New collaborator `CompactionGate` (private class in the session, or sibling file under `domain/sessions/compaction/`):
    - State guarded by `Mutex`: `activeCompactionJob: Job?`, `compactionMapping: MutableMap<CompactedId, List<OriginalId>>`.
    - `onLimitReached(pendingIds: List<EncodedMessage>)`:
      1. If a compaction job is already running, ignore (single-flight).
      2. Map `pendingIds.map { it.id }` to `ChatMessage`s by looking them up in the chat's NEW-status set (already maintained via `chatEngine.subscribeOutgoingMessagesByStatus(chatId, NEW)`).
      3. Launch a job that calls `compactor.compact(snapshot)`:
         - Success → `chatMessageRepository.commitCompaction(compacted, originalIds)`; record `compactionMapping[compacted.id] = originalIds`. The existing NEW-status subscription will pick up the new compacted message and submit it.
         - Failure (3 retries) → leave originals in NEW; log. The queue keeps draining slowly via the existing batching. See Open Q #7 for UX.
  - Replace `handleMessageTooLarge`:
    - Remove `chatMessageRepository.removeMessage(...)`.
    - New behavior: feed the single oversize message into `compactionGate.onLimitReached(listOf(encodedMessage))` (or whatever the unified event carries — Open Q #11).
  - In `MessagesSentSuccessfully` / status-update handlers: when an acknowledged id is in `compactionMapping`, fan out the status update over the original ids (delegating to the DAO query in Phase 7).

---

### Phase 6 — Repository: commit compaction

**Goal:** atomic DB write that inserts the compacted message and links originals via `compactionId`.

Files to modify:
- `feature/chats/impl/src/main/java/io/paritytech/polkadotapp/feature_chats_impl/data/repository/ChatMessageRepository.kt`
  - Add `suspend fun commitCompaction(compacted: ChatMessage, originalIds: List<String>)`.
- `feature/chats/impl/.../data/repository/RealChatMessageRepository.kt`
  - Implement via a single Room `@Transaction` DAO method:
    1. Insert compacted row (status = `NEW` to flow through the existing send pipeline naturally — see Open Q #5).
    2. `UPDATE chatMessages SET compactionId = :id WHERE id IN (:originalIds)`.
- `database/.../dao/ChatMessageDao.kt`
  - Expose the transactional DAO method.

---

### Phase 7 — Outgoing status propagation

**Goal:** when a compacted message transitions to `IS_SENT` / `IS_READ`, propagate to children.

Files to modify:
- `feature/chats/impl/.../data/repository/RealChatMessageRepository.kt`
  - In `updateMessageStatus(...)`: after the update, if the message's content type is `CompactedMessages`, call `dao.updateStatusForCompactionId(messageId, status)`.
  - Support recursion (nested compactions): the DAO update is one-level, but call site recurses across newly affected rows. (Open Q #6 — punt nested support or not.)

---

### Phase 8 — Expansion service (incoming)

**Goal:** transparently download + expand compacted incoming messages.

New files:
- `feature/chats/impl/src/main/java/io/paritytech/polkadotapp/feature_chats_impl/domain/compaction/CompactedMessageExpansionService.kt`
  ```kotlin
  interface CompactedMessageExpansionService {
      fun start()
      fun stop()
  }
  ```
  - Singleton, started by app lifecycle (along with other always-on chat services).
  - Subscribes to `chatMessageDao.flowIncomingCompactedUnexpanded()`.
  - For each message, hands off to `CompactedMessageExpansionContext.processExpansion(...)`.

- `feature/chats/impl/.../domain/compaction/CompactedMessageExpansionContext.kt`
  ```kotlin
  class CompactedMessageExpansionContext(
      private val claimer: ChatMessageClaimer,
      private val repository: ChatMessageRepository,
      private val scope: CoroutineScope,
      private val maxConcurrentExpansions: Int = 100,
  )
  ```
  - State guarded by `Mutex`:
    - `active: MutableMap<MessageId, Job>`
    - `pending: ArrayDeque<MessageId>`
  - `processExpansion(id, runner)`:
    - Dedup if already active or pending.
    - If `active.size < maxConcurrentExpansions` → start, store job.
    - Else → enqueue in `pending`.
  - On task completion → remove from `active`, pop next pending into `active` if any.
  - Persistence on success: open Room transaction → upsert each expanded `ChatMessageLocal` with status `NEW` → set `contentExpanded = true` on parent. The upsert (not insert) handles the case where an expanded message id already exists.
  - On failure / cancellation → keep `contentExpanded = false` so retry happens on next subscription tick (TBD — see Open Q #8).

- `feature/chats/impl/.../domain/compaction/ChatMessageClaimer.kt`
  ```kotlin
  class ChatMessageClaimer(
      private val nodeProvider: HopNodeUrlProvider,
      private val downloader: HopBlobDownloader,
  ) {
      suspend fun claim(
          message: ChatMessage,                       // must be CompactedMessages
          onExpansion: suspend (List<ChatMessage>) -> Unit
      )
  }
  ```
  - Validates `node` is trusted; throws `UntrustedNodeException` otherwise.
  - Calls `downloader.download(...)` with an `onConfirm` that:
    - SCALE-decodes the blob as `CompactedMessagesBlob`.
    - Maps each `ChatMessageStatement` → `ChatMessage` via existing mappers.
    - Calls `onExpansion(messages)`.

DI:
- `feature/chats/impl/.../di/ChatsFeatureApiModule.kt`
  - `@Provides @Singleton CompactedMessageExpansionService`, `ChatMessageClaimer`, `HopBlobUploader`, `HopBlobDownloader`, `ChatMessageCompactorFactory`.
  - Wire `CompactedMessageExpansionService.start()` into the existing app-startup hook for chat services (locate the current call site — likely a Hilt `@IntoSet` of an `ApplicationStartup` interface or similar).

---

### Phase 9 — Push notifications

**Goal:** push notifications for incoming compacted messages must surface the contents of each expanded message, not the opaque "compacted" wrapper.

Files to modify:
- `feature/chats/impl/.../data/notifications/ChatPushNotificationHandler.kt` and/or `RealIncomingChatPushDecoder.kt`
  - If the decoded incoming message is `Content.CompactedMessages`: skip the normal notification path; the expansion service will trigger notifications for the expanded children once it persists them. (Verify: is there a notification flow downstream of message persistence today, or only at push-decode time? If only push-decode, we need the expansion service to drive the notifications too.)
- `feature/chats/impl/.../domain/notifications/ChatPushNotificationsSender.kt`
  - If applicable on the outgoing path (iOS expands on outgoing too "until push v2"), consider whether we need parity.

Open Q #9 covers this — needs a closer look at the Android notification trigger model.

---

### Phase 10 — Edge cases & polish

- **Nested compactions:** if a `CompactedMessagesBlob` contains another `CompactedMessages` content variant, the expansion service should re-trigger on the inserted child. Naturally falls out of the Flow subscription if we set `contentExpanded = false` on inserted compacted children.
- **Concurrent expansion deduplication:** handled by `CompactedMessageExpansionContext.active` map.
- **Retry on transient failures:** rely on the Flow re-emitting unexpanded messages on the next tick. Add bounded backoff to avoid hot-loop on hard failures (TBD).
- **Send-fallback flow:** if compaction fails 3× and individual messages still don't fit, surface user-visible error (no current path for this — needs UX decision).
- **Test plan:**
  - Manual: stress-send messages above 1880 B aggregate, verify all reach `IS_SENT` and remote side displays them individually.
  - Unit: `ChatMessageCompactor.compact` retry logic; `CompactedMessageExpansionContext` concurrency capping & deduplication; `ChatMessageStatementContentMappers` round-trip for `CompactedMessages` variant; `Migration38To39` schema correctness.
  - Integration: end-to-end with a stubbed `HopService` — verify status propagation, nested compactions, untrusted-node rejection.
  - Regression: messages below the size threshold must skip compaction entirely.

---

## Open questions (to resolve before / during implementation)

1. **Trusted HOP nodes.** Does `HopNodeUrlProvider` enforce an allow-list today, or only return *the* configured node URL? If the latter, we need to add an `isAllowed(node)` check — claiming from an attacker-controlled node is the main security gate during expansion.
2. **Blob inner format.** Use `List<ChatMessageStatement>` (recommended — full wire shape, symmetric with single-message sends) or just `List<ChatMessageV1>` (smaller, matches iOS more literally)? Choice affects whether decoded compacted messages get new ids/timestamps or carry the originals.
3. ~~**Proactive vs reactive compaction trigger.**~~ **Resolved: event-driven.** Statement-store state machine emits `StatementSizeLimitReached`; chat session reacts and compacts. See Phase 5.
4. ~~**`maxRequestSize` exposure.**~~ **Resolved: not exposed.** Size accounting stays inside the state machine; the chat session never computes encoded sizes itself.
5. **Compacted message initial status.** Insert with status `NEW` (and let normal send path handle it) or directly `IS_SENT` (since the HOP upload already completed)? iOS uses `.outgoing(.new)`. Recommend `NEW` for symmetry with iOS and to let the existing send pipeline batch + push the compacted message normally. **But** this re-introduces a size check on the compacted message itself — which should always pass, since the compacted message has fixed-size content. Verify the encoded `Content.CompactedMessages` fits comfortably in `maxRequestSize`.
6. **Nested compaction support.** iOS supports recursive `propagateOutgoingStatusToCompactedChildren` and `expandedMessagesWrapper`. Punt to follow-up, or include from day one? Recommend include — cost is low if we design recursion in.
7. **Send-fallback UX.** If compaction retries are exhausted, what does the user see? Today: nothing (message silently deleted in the single-too-big case, or silently queued forever in the overflow case). After this work: ? (Error banner? Per-message error state? New `Status.FAILED`?)
8. **Expansion retry policy.** On transient HOP claim failure: rely on the next Flow tick to retry, or add explicit backoff? Risk: hot loop if persistent failure.
9. **Notification trigger model.** Does Android send chat notifications from the push-decode path only, or also from a DB-change observer? If push-only, the expansion service must drive notifications for newly-inserted expanded messages. (iOS does this via `expandMessagesToNotify`.)
10. **DI lifecycle of expansion service.** Where is the existing app-startup hook for always-on chat services? `ChatsFeatureApiModule.kt` shows `@IntoSet ChatMessageSaveProcessor` and `@IntoSet PushNotificationHandler`, but no obvious "OnAppStarted" service set. We may need to start the service from `ChatEngine.init` or add a new startup set.
11. **Unify `NotifyMessageTooLarge` and `StatementSizeLimitReached`?** Today `NotifyMessageTooLarge` carries a single `EncodedMessage`; the new event carries a list. Two options:
    - Keep them distinct (preserves the "this *specific* message will never fit" signal — useful for diagnostics).
    - Collapse into one event (chat session has uniform handling: compact whatever is in scope).
    Recommend: keep distinct events but route both to the same `CompactionGate.onLimitReached` handler in the chat session. The "single too big" event still surfaces in logs/metrics.
12. **Event debouncing.** The new event can fire many times in a row (once per `SubmitMessage` while pending is non-empty, once per `ResponseReceived` with remaining). The `CompactionGate` single-flights via `activeCompactionJob`, so duplicate events are no-ops. Confirm this is the only debounce we need (no rate-limit on emission side).
13. **`EncodedMessage` typing refactor scope.** The change is small in principle (one typealias → data class) but touches every statement-store call site. Confirm we want it in the same PR as compaction, vs landing it standalone first.

---

## File-creation summary

**New files (12):**
- `feature/chats/impl/.../domain/compaction/CompactedMessagesBlob.kt`
- `feature/chats/impl/.../domain/compaction/ChatMessageCompactor.kt`
- `feature/chats/impl/.../domain/compaction/ChatMessageCompactorFactory.kt`
- `feature/chats/impl/.../domain/compaction/ChatMessageClaimer.kt`
- `feature/chats/impl/.../domain/compaction/CompactedMessageExpansionService.kt`
- `feature/chats/impl/.../domain/compaction/CompactedMessageExpansionContext.kt`
- `feature/chats/impl/.../data/hop/blob/HopBlobUploader.kt`
- `feature/chats/impl/.../data/hop/blob/HopBlobDownloader.kt`
- `feature/chats/impl/.../data/hop/blob/HopBlobReference.kt` (small data class)
- `database/.../migrations/Migration38To39.kt`
- Optional: `feature/statement-store/api/.../domain/StatementSize.kt` (shared constants — depends on Open Q #4)
- Tests under each module

**Modified files (~16):**
- `feature/chats/api/.../domain/model/ChatMessage.kt`
- `feature/chats/impl/.../domain/models/scale/ChatMessageContentLocal.kt`
- `feature/chats/impl/.../domain/models/scale/LocalChatMessageContentMappers.kt`
- `feature/chats/impl/.../data/model/ChatMessageStatementContent.kt`
- `feature/chats/impl/.../data/model/ChatMessageStatementContentMappers.kt`
- `feature/chats/impl/.../domain/sessions/RealContactChatSession.kt`
- `feature/chats/impl/.../domain/sessions/RealContactChatSessionManager.kt`
- `feature/chats/impl/.../data/repository/ChatMessageRepository.kt` + `RealChatMessageRepository.kt`
- `feature/chats/impl/.../data/hop/HopNodeUrlProvider.kt` (if `isAllowed` missing)
- `feature/chats/impl/.../data/notifications/...` (push expansion — depends on Open Q #9)
- `feature/chats/impl/.../di/ChatsFeatureApiModule.kt`
- `database/.../model/ChatMessageLocal.kt` + `dao/ChatMessageDao.kt` + `AppDatabase.kt`
- `feature/statement-store/api/.../domain/models/CommunicationModels.kt` — `EncodedMessage` typealias → data class
- `feature/statement-store/api/.../domain/models/CommunicationSessionEvent.kt` — new `StatementSizeLimitReached` event
- `feature/statement-store/impl/.../domain/sessions/stateMachine/CommunicationSideEffect.kt` — new side-effect
- `feature/statement-store/impl/.../domain/sessions/stateMachine/states/Active.kt` — fire the new side-effect from three sites
- `feature/statement-store/impl/.../domain/sessions/stateMachine/states/CommunicationState.kt` — `EncodedMessage` typing fallout in `splitMessagesToFitRequest`, `checkAlreadyPendingMessage`, `checkSizeLimitExceeded`
- `feature/statement-store/impl/.../domain/sessions/RealCommunicationSession.kt` — handler for the new side-effect

---

## Risks

- **Compaction race with already-shipped messages.** When the state machine fires `StatementSizeLimitReached`, some of the chat session's NEW messages may already be in the in-flight `outgoingPendingRequest` (the state machine accepted them, just hasn't gotten the response yet). If the chat session compacts those same messages, the originals will still ship from the state machine (which has them by reference). Mitigation: the `CompactionGate` filters the chat-session's NEW-status set down to those whose ids appear in `event.pendingMessages` — only compact what the state machine confirmed is *pending*, not in-flight.
- **`EncodedMessage` refactor blast radius.** The typing refactor touches every site that handles statement-store payloads. Risk of merge conflicts and unit-test fallout. Mitigation: land the refactor as a prerequisite commit before compaction logic; verify all existing `ChatP2PTests`-equivalent suites still pass.
- **Migration safety.** Adding two nullable columns is low-risk, but the indexed `compactionId` must not collide with any existing query plan.
- **Encryption interplay.** The blob is encrypted before HOP submission; ticket reuse semantics need to match iOS (`HopTicketKeyDerivation`) to ensure recipients can decrypt with the same ticket bytes.
- **Race on expansion.** If a compacted message is received and the expansion service is not yet started, the Flow subscription will deliver it on start. If it's received during shutdown, it persists with `contentExpanded = false` and gets picked up next launch. Both are safe.
- **Status backfill.** Existing in-flight messages at migration time are not compacted; the migration only adds nullable columns and shouldn't affect them.

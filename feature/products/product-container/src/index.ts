import { createContainer } from '@novasamatech/host-container';
import type { Provider, Subscription } from '@novasamatech/host-api';
import { RequestCredentialsErr, ChatMessagePostingErr, NavigateToErr, StorageErr, SigningErr, PreimageSubmitErr, StatementProofErr, GenericError, DeriveEntropyErr, PaymentRequestErr, PaymentTopUpErr, ResourceAllocationErr, CreateTransactionErr, CustomRendererNode, PushNotificationError, GetUserIdErr, toHex, fromHex } from '@novasamatech/host-api';
import { createWsJsonRpcProvider } from '@novasamatech/host-substrate-chain-connection';

type PausableJsonRpcProvider = ReturnType<typeof createWsJsonRpcProvider>;
import { createNativeTransport } from './native-transport';

// =============================================================================
// Isolation: Capture private refs BEFORE locking down globals.
// The container IIFE closure keeps these inaccessible to product scripts.
// =============================================================================

// Captured before sandbox lockdown freezes window.WebSocket below.
const CapturedWebSocket = globalThis.WebSocket;

// =============================================================================
// Isolation: Lock down globals so product scripts cannot access platform APIs.
// =============================================================================

function freezeAndDelete(obj: any, prop: string) {
  try {
    Object.defineProperty(obj, prop, {
      value: undefined,
      writable: false,
      configurable: false,
    });
  } catch {
    // Property may already be non-configurable; try delete as fallback
    try { delete obj[prop]; } catch { /* best effort */ }
  }
}

function freezeValue(obj: any, prop: string, value: any) {
  try {
    Object.defineProperty(obj, prop, {
      value,
      writable: false,
      configurable: false,
    });
  } catch { /* best effort */ }
}

// --- Network: intercept with error (future: permission-gated) ---

freezeValue(window, 'XMLHttpRequest', function XMLHttpRequest() {
  throw new TypeError('Network access is not allowed');
});

freezeValue(window, 'WebSocket', function WebSocket() {
  throw new TypeError('Network access is not allowed');
});

// --- Network: delete (no future permission path) ---
freezeAndDelete(window, 'RTCPeerConnection');
freezeAndDelete(window, 'EventSource');

freezeValue(navigator, 'sendBeacon', () => false);

// --- Storage ---
freezeAndDelete(window, 'indexedDB');
freezeAndDelete(window, 'caches');

// document.cookie — redefine as no-op getter/setter
try {
  Object.defineProperty(document, 'cookie', {
    get: () => '',
    set: () => {},
    configurable: false,
  });
} catch { /* best effort */ }

// --- Workers ---
freezeAndDelete(window, 'SharedWorker');

if (navigator.serviceWorker) {
  try {
    Object.defineProperty(navigator, 'serviceWorker', {
      value: Object.freeze({ register: () => { throw new Error('ServiceWorker is not available'); } }),
      writable: false,
      configurable: false,
    });
  } catch { /* best effort */ }
}

// --- DOM: block iframe creation ---
const _createElement = document.createElement.bind(document);
freezeValue(document, 'createElement', (tagName: string, options?: ElementCreationOptions) => {
  if (tagName.toLowerCase() === 'iframe') {
    throw new Error('iframe creation is not allowed');
  }
  return _createElement(tagName, options);
});

(window as any).__HOST_WEBVIEW_MARK__ = true;

const { callNative, subscribeNative } = createNativeTransport((message) => {
  const json = JSON.stringify(message);
  (window as any).Android.call('__container__', json);
});

const { port1, port2 } = new MessageChannel();

(window as any).__HOST_API_PORT__ = port1;

const subscribers = new Set<(message: Uint8Array) => void>();

port2.onmessage = (event: MessageEvent) => {
  for (const subscriber of subscribers) {
    subscriber(event.data);
  }
};

const containerProvider: Provider = {
  logger: console,
  isCorrectEnvironment() {
    return true;
  },
  postMessage(message: Uint8Array) {
    port2.postMessage(message, [message.buffer]);
  },
  subscribe(callback: (message: Uint8Array) => void) {
    subscribers.add(callback);
    return () => { subscribers.delete(callback); };
  },
  dispose() {
    subscribers.clear();
  },
};

const container = createContainer(containerProvider);

container.handleAccountGet((params, { ok, err }) => {
  const [productId, derivationIndex] = params;
  const promise: Promise<any> = callNative('accountGet', { productId, derivationIndex }).then(
    (result) => ok({
      publicKey: fromHex(result.publicKey),
      name: result.name ?? undefined,
    }),
    (e) => err(new RequestCredentialsErr.Unknown({ reason: String(e) })),
  );
  return promise;
});

container.handleAccountGetAlias((params, { ok, err }) => {
  const [productId, derivationIndex] = params;
  return callNative('accountGetAlias', { productId, derivationIndex }).then(
    (result) => ok({
      context: fromHex(result.context),
      alias: fromHex(result.alias),
    }),
    (e) => err(new RequestCredentialsErr.Unknown({ reason: String(e) })),
  );
});

// --- Get User ID ---

container.handleGetUserId(async (_params, { ok, err }) => {
  try {
    const result = await callNative('getUserId', {});
    return ok({ primaryUsername: result.primaryUsername });
  } catch (e) {
    const reason = String(e instanceof Error ? e.message : e);
    if (reason.includes('NotConnected')) {
      return err(new GetUserIdErr.NotConnected());
    }
    if (reason.includes('Permission denied')) {
      return err(new GetUserIdErr.PermissionDenied());
    }
    return err(new GetUserIdErr.Unknown({ reason }));
  }
});

container.handleFeatureSupported((params, { ok }) => {
  switch (params.tag) {
    case 'Chain':
      return callNative('chainSupported', { genesisHash: params.value })
        .then((supported: boolean) => ok(supported))
        .catch(() => ok(false));
    default:
      return ok(false);
  }
});

const activeProviders = new Set<PausableJsonRpcProvider>();

container.handleChainConnection((genesisHash) => {
  return (onMessage) => {
    let inner: { send(msg: string): void; disconnect(): void } | null = null;
    let provider: PausableJsonRpcProvider | null = null;
    const buffer: string[] = [];

    callNative('chainNodes', { genesisHash }).then((urls: string[]) => {
      provider = createWsJsonRpcProvider({
        endpoints: urls,
        websocketClass: CapturedWebSocket
      });
      activeProviders.add(provider);
      inner = provider(onMessage) as unknown as { send(msg: string): void; disconnect(): void };
      for (const msg of buffer) inner.send(msg);
      buffer.length = 0;
    });

    return {
      send(message: string) {
        if (inner) inner.send(message);
        else buffer.push(message);
      },
      disconnect() {
        inner?.disconnect();
        if (provider) {
          activeProviders.delete(provider);
          provider = null;
        }
      },
    };
  };
});

(window as any).__pauseConnections__ = () => {
  for (const provider of activeProviders) {
    provider.pause();
  }
};

(window as any).__resumeConnections__ = () => {
  for (const provider of activeProviders) {
    provider.resume();
  }
};

// --- Entropy Derivation ---

container.handleDeriveEntropy(async (key, { ok, err }) => {
  try {
    const result = await callNative('deriveEntropy', { key: toHex(key) });
    return ok(fromHex(result.entropy));
  } catch (e) {
    return err(new DeriveEntropyErr.Unknown({ reason: String(e) }));
  }
});

// --- Signing ---

container.handleSignPayload(async ({ account, payload }, { ok, err }) => {
  try {
    const result = await callNative('signPayload', { account, ...payload });
    return ok({ signature: result.signature, signedTransaction: result.signedTx ?? undefined });
  } catch (e) {
    return err(new SigningErr.Rejected());
  }
});

container.handleSignRaw(async ({ account, payload }, { ok, err }) => {
  try {
    const nativeData = payload.tag === 'Bytes'
      ? { data: toHex(payload.value) }
      : { payload: payload.value };

    const result = await callNative('signRaw', { account, ...nativeData });
    return ok({ signature: result.signature, signedTransaction: result.signedTx ?? undefined });
  } catch (e) {
    return err(new SigningErr.Rejected());
  }
});

// --- Create Transaction (RFC-0020) ---

container.handleCreateTransaction(async (payload, { ok, err }) => {
  try {
    const result = await callNative('createTransaction', {
      signer: payload.signer,
      genesisHash: toHex(payload.genesisHash),
      callData: toHex(payload.callData),
      extensions: payload.extensions.map((e) => ({
        id: e.id,
        implicit: toHex(e.additionalSigned),
        explicit: toHex(e.extra),
      })),
      txExtVersion: payload.txExtVersion,
    });
    return ok(fromHex(result.signedTx));
  } catch (e) {
    return err(new CreateTransactionErr.Unknown({ reason: String(e) }));
  }
});

// --- Account Connection Status ---

container.handleAccountConnectionStatusSubscribe((_params, send, _interrupt) => {
  send('connected');
  return () => {};
});

// --- Theme ---

container.handleThemeSubscribe((_params, send, interrupt) => {
  return subscribeNative(
    'themeSubscribe',
    {},
    (payload: { name: { tag: 'Custom' | 'Default'; value?: string }; variant: 'Light' | 'Dark' }) => {
      const name =
        payload.name.tag === 'Custom'
          ? { tag: 'Custom' as const, value: payload.name.value ?? '' }
          : { tag: 'Default' as const, value: undefined };
      send({ name, variant: payload.variant });
    },
    () => interrupt(),
  );
});

// --- Request Login ---

container.handleRequestLogin((_reason, { ok }) => ok('alreadyConnected'));

// --- Device Permission ---

container.handleDevicePermission(async (capability, { ok, err }) => {
  console.log(`handleDevicePermission called with: ${capability}`);
  try {
    const result = await callNative('devicePermission', { capability });
    console.log(`handleDevicePermission native result: ${result} (type: ${typeof result})`);
    return ok(result);
  } catch (e) {
    console.log(`handleDevicePermission error: ${e}`);
    return err(new GenericError({ reason: String(e) }));
  }
});

// --- Remote Permission ---

container.handlePermission(async (requests, { ok, err }) => {
  try {
    const payload = (Array.isArray(requests) ? requests : [requests]).map((r: any) => ({
      tag: r.tag,
      value: r.tag === 'Remote' ? r.value : undefined,
    }));
    const result = await callNative('remotePermission', payload);
    return ok(result);
  } catch (e) {
    return err(new GenericError({ reason: String(e) }));
  }
});

// --- Push Notification ---

container.handlePushNotification(async (params, { ok, err }) => {
  try {
    const scheduledAt = params.scheduledAt !== undefined ? Number(params.scheduledAt) : undefined;
    const id: number = await callNative('pushNotification', {
      text: params.text,
      deeplink: params.deeplink,
      scheduledAtMs: scheduledAt,
    });
    return ok(id);
  } catch (e) {
    const reason = String(e);
    if (reason.includes('Schedule limit reached')) {
      return err(new PushNotificationError.ScheduleLimitReached());
    }
    return err(new PushNotificationError.Unknown({ reason }));
  }
});

container.handlePushNotificationCancel(async (identifier, { ok, err }) => {
  try {
    await callNative('cancelPushNotification', identifier);
    return ok(undefined);
  } catch (e) {
    return err(new GenericError({ reason: String(e) }));
  }
});

// --- Non-Product Accounts ---

container.handleGetLegacyAccounts((_params, { ok, err }) => {
  return callNative('getLegacyAccounts', {}).then(
    (result: { publicKey: string; name?: string }[]) => ok(result.map((account) => ({
      publicKey: fromHex(account.publicKey),
      name: account.name ?? undefined,
    }))),
    (e) => err(new RequestCredentialsErr.Unknown({ reason: String(e) })),
  );
});

// --- Preimage Lookup ---

container.handlePreimageLookupSubscribe((hashHex, send, _interrupt) => {
  callNative('preimageLookup', { hash: hashHex }).then(
    (result) => send(result.data ? fromHex(result.data) : null),
    () => send(null),
  );
  return () => {};
});

// --- Preimage Submit ---

container.handlePreimageSubmit(async (data, { ok, err }) => {
  try {
    const result = await callNative('preimageSubmit', { data: toHex(data) });
    return ok(result.hash);
  } catch (e) {
    return err(new PreimageSubmitErr.Unknown({ reason: String(e) }));
  }
});

// --- Statement Store Create Proof ---

// TODO: Currently signs with wallet account, ignoring requested product account id.
//  Switch to product-derived account once the chain supports granting allowances (e.g. zk vouchers).
container.handleStatementStoreCreateProof(async ([_account, statement], { ok, err }) => {
  try {
    const result = await callNative('createStatementProof', {
      channel: statement.channel ? toHex(statement.channel) : undefined,
      expiry: statement.expiry?.toString() ?? undefined,
      topics: statement.topics.map((t) => toHex(t)),
      data: statement.data ? toHex(statement.data) : undefined,
    });
    return ok({
      tag: result.tag as 'Sr25519',
      value: { signature: fromHex(result.signature), signer: fromHex(result.signer) },
    });
  } catch (e) {
    return err(new StatementProofErr.UnableToSign());
  }
});

// --- Statement Store Create Proof Authorized (RFC-0010) ---

container.handleStatementStoreCreateProofAuthorized(async (statement, { ok, err }) => {
  try {
    const result = await callNative('createStatementProofAuthorized', {
      channel: statement.channel ? toHex(statement.channel) : undefined,
      expiry: statement.expiry?.toString() ?? undefined,
      topics: statement.topics.map((t) => toHex(t)),
      data: statement.data ? toHex(statement.data) : undefined,
    });
    return ok({
      tag: result.tag as 'Sr25519',
      value: { signature: fromHex(result.signature), signer: fromHex(result.signer) },
    });
  } catch (e) {
    return err(new StatementProofErr.UnableToSign());
  }
});

// --- Resource Allocation (RFC-0010) ---

container.handleRequestResourceAllocation(async (resources, { ok, err }) => {
  try {
    const dtos = resources.map((r) => {
      switch (r.tag) {
        case 'SmartContractAllowance':
          return { kind: r.tag, dest: r.value };
        case 'StatementStoreAllowance':
        case 'BulletinAllowance':
        case 'AutoSigning':
          return { kind: r.tag };
      }
    });
    const result = await callNative('hostRequestResourceAllocation', { resources: dtos });
    const outcomes = (result.outcomes as { kind: string }[]).map((o) => ({
      tag: o.kind as 'Allocated' | 'Rejected' | 'NotAvailable',
      value: undefined,
    }));
    return ok(outcomes);
  } catch (e) {
    return err(new ResourceAllocationErr.Unknown({ reason: String(e) }));
  }
});

// --- Statement Store Submit ---

container.handleStatementStoreSubmit(async (statement, { ok, err }) => {
  try {
    await callNative('statementStoreSubmit', {
      proof: {
        tag: statement.proof.tag,
        signature: toHex(statement.proof.value.signature),
        signer: toHex(statement.proof.value.signer),
      },
      channel: statement.channel ? toHex(statement.channel) : undefined,
      expiry: statement.expiry?.toString() ?? undefined,
      topics: statement.topics.map((t) => toHex(t)),
      data: statement.data ? toHex(statement.data) : undefined,
    });
    return ok(undefined);
  } catch (e) {
    return err(new GenericError({ reason: String(e) }));
  }
});

// --- Statement Store Subscribe ---

container.handleStatementStoreSubscribe((filter, send, _interrupt) => {
  const hexTopics = filter.value.map((t) => toHex(t));
  const nativeFilter = filter.tag === 'MatchAll' ? { matchAll: hexTopics } : { matchAny: hexTopics };
  const unsub = subscribeNative('statementStoreSubscribe', { filter: nativeFilter }, (page: { statements: { proof: { tag: string; signature: string; signer: string }; channel?: string; expiry: string; topics: string[]; data?: string }[]; isComplete: boolean }) => {
    send({
      statements: page.statements.map((s) => ({
        proof: { tag: s.proof.tag as 'Sr25519', value: { signature: fromHex(s.proof.signature), signer: fromHex(s.proof.signer) } },
        decryptionKey: undefined,
        expiry: s.expiry ? BigInt(s.expiry) : undefined,
        channel: s.channel ? fromHex(s.channel) : undefined,
        topics: s.topics.map((t: string) => fromHex(t)),
        data: s.data ? fromHex(s.data) : undefined,
      })),
      isComplete: page.isComplete,
    });
  });
  return unsub;
});

// --- Chat: Create Room ---

container.handleChatCreateRoom(async (params, { ok, err }) => {
  try {
    const result = await callNative('chatCreateRoom', params);
    return ok({ status: result.status });
  } catch (e) {
    return err({ reason: String(e) });
  }
});

// --- Chat: List Subscribe ---

container.handleChatListSubscribe((_params, send, _interrupt) => {
  const unsub = subscribeNative('chatListSubscribe', {}, (rooms) => {
    send(rooms);
  });
  return unsub;
});

// --- Chat: Post Message ---

container.handleChatPostMessage(async (params, { ok, err }) => {
  const { payload } = params;

  try {
    switch (payload.tag) {
      case 'Text': {
        const result = await callNative('chatSendTextMessage', { text: payload.value, chatId: params.roomId });
        return ok({ messageId: result.messageId });
      }
      case 'Custom': {
        const result = await callNative('chatSendCustomMessage', {
          messageType: payload.value.messageType,
          payloadHex: toHex(payload.value.payload),
          chatId: params.roomId
        });
        return ok({ messageId: result.messageId });
      }
      default:
        return err(new ChatMessagePostingErr.Unknown({
          reason: `Unsupported message type: ${(payload as any).tag}`,
        }));
    }
  } catch (e) {
    return err(new ChatMessagePostingErr.Unknown({ reason: String(e) }));
  }
});

// --- Chat: Custom Message Rendering ---

const renderSubscriptions = new Map<string, Subscription>();

(window as any).renderMessage = (messageType: string, payloadHex: string, messageId: string) => {
  renderSubscriptions.get(messageId)?.unsubscribe();

  const payload = fromHex(payloadHex);

  const subscription = container.renderChatCustomMessage({ messageId, messageType, payload }, (node) => {
    const scaleHex = toHex(CustomRendererNode.enc(node));
    callNative('chatRenderWidget', { messageId, scaleHex });
  });

  renderSubscriptions.set(messageId, subscription);
};

// --- Chat: Action Subscribe ---

type ChatActionSend = (action: {
  roomId: string;
  peer: string;
  payload:
    | { tag: 'MessagePosted'; value: { tag: 'Text'; value: string } }
    | { tag: 'ActionTriggered'; value: { messageId: string; actionId: string; payload?: Uint8Array } };
}) => void;

let chatActionSend: ChatActionSend | null = null;

container.handleChatActionSubscribe((_params, send, _interrupt) => {
    console.log("handleChatActionSubscribe called")
  chatActionSend = send;
  return () => { chatActionSend = null; };
});

(window as any).dispatchChatAction = (
  roomId: string,
  messageId: string,
  actionId: string,
  payloadHex?: string,
) => {
  console.log(`Received dispatchChatAction: roomId=${roomId}, messageId=${messageId}, actionId=${actionId}, payload=${payloadHex}`)

  chatActionSend?.({
    roomId,
    peer: 'native',
    payload: {
      tag: 'ActionTriggered',
      value: {
        messageId,
        actionId,
        payload: payloadHex ? fromHex(payloadHex) : undefined,
      },
    },
  });
};

(window as any).dispatchUserMessage = (roomId: string, text: string) => {
    console.log(`Received dispatchUserMessage: roomId=${roomId}, text=${text}`)

  chatActionSend?.({
    roomId,
    peer: 'native',
    payload: {
      tag: 'MessagePosted',
      value: {
        tag: 'Text',
        value: text,
      },
    },
  });
};

// --- Payment Balance ---

container.handlePaymentBalanceSubscribe((_params, send, interrupt) => {
  return subscribeNative(
    'paymentBalanceSubscribe',
    {},
    (payload: { available: string }) => {
      send({ available: BigInt(payload.available) });
    },
    () => interrupt(),
  );
});

// --- Payment Request (RFC-0006) ---

container.handlePaymentRequest(async (params, { ok, err }) => {
  try {
    const result = await callNative('paymentRequest', {
      amount: params.amount.toString(),
      destinationHex: toHex(params.destination),
    });
    return ok({ id: result.id });
  } catch (e) {
    const msg = String(e instanceof Error ? e.message : e);
    if (msg.includes('payment rejected')) {
      return err(new PaymentRequestErr.Rejected());
    }
    if (msg.includes('insufficient balance')) {
      return err(new PaymentRequestErr.InsufficientBalance());
    }
    return err(new PaymentRequestErr.Unknown({ reason: msg }));
  }
});


container.handlePaymentTopUp(async (params, { ok, err }) => {
  try {
    const nativeParams: Record<string, unknown> = {
      amount: params.amount.toString(),
      sourceTag: params.source.tag,
    };
    if (params.source.tag === 'ProductAccount') {
      nativeParams.sourceDerivationIndex = params.source.value;
    } else if (params.source.tag === 'Coins') {
      nativeParams.sourceKeyListHex = params.source.value.map((key) => toHex(key));
    } else {
      nativeParams.sourceKeyHex = toHex(params.source.value);
    }
    await callNative('paymentTopUp', nativeParams);
    return ok();
  } catch (e) {
    const msg = String(e instanceof Error ? e.message : e);
    return err(new PaymentTopUpErr.Unknown({ reason: msg }));
  }
});

container.handlePaymentStatusSubscribe((paymentId, send, interrupt) => {
  return subscribeNative(
    'paymentStatusSubscribe',
    { paymentId },
    (payload: { tag: 'Processing' | 'Completed' | 'Failed'; value: string | null }) => {
      if (payload.tag === 'Processing') {
        send({ tag: 'Processing', value: undefined });
      } else if (payload.tag === 'Completed') {
        send({ tag: 'Completed', value: undefined });
      } else {
        send({ tag: 'Failed', value: payload.value ?? '' });
      }
    },
    () => interrupt(),
  );
});

// --- Local Storage (native-bridged) ---

container.handleLocalStorageRead((key, { ok, err }) => {
  return callNative('localStorageRead', { key }).then(
    (result) => ok(result.value != null ? fromHex(result.value) : undefined),
    (e) => err(new StorageErr.Unknown({ reason: String(e) })),
  );
});

container.handleLocalStorageWrite((params, { ok, err }) => {
  const [key, value] = params;
  return callNative('localStorageWrite', { key, value: toHex(value) }).then(
    () => ok(undefined),
    (e) => err(new StorageErr.Unknown({ reason: String(e) })),
  );
});

container.handleLocalStorageClear((key, { ok, err }) => {
  return callNative('localStorageClear', { key }).then(
    () => ok(undefined),
    (e) => err(new StorageErr.Unknown({ reason: String(e) })),
  );
});

// --- Navigation (native-bridged) ---

container.handleNavigateTo((destination, { ok, err }) => {
    return callNative('navigateTo', { destination }).then(
      () => ok(undefined),
      (e) => err(new NavigateToErr.Unknown({ reason: String(e) })),
    );
});

console.log('Host container initialized');

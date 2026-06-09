/**
 * Products Demo - Counter Example (React)
 *
 * Demonstrates reactive state with React's useState via the custom reconciler.
 * Uses the product SDK directly for messaging and action handling.
 */

import { useState, useEffect } from 'react';
import { Column, Row, Spacer, Text, Button, TextField, registerChatMessageRenderer } from '@novasamatech/product-react-renderer';
import { createClient, type PolkadotSigner, type TxEvent } from 'polkadot-api';
import { pop, assetHub, MultiAddress, XcmV5Junctions, XcmV5Junction } from '@polkadot-api/descriptors';
import { createAccountsProvider, createPapiProvider, createProductChatManager, createStatementStore, deriveEntropy, hostApi, notificationManager, paymentManager, preimageManager, requestDevicePermission, type SignedStatement, type TopUpSource } from '@novasamatech/host-api-wrapper';
import { enumValue } from '@novasamatech/host-api';
import { fromBufferToBase58 } from '@polkadot-api/substrate-bindings';
import { Keyring } from '@polkadot/keyring';
import { randomAsU8a } from '@polkadot/util-crypto';

// ============================================================================
// Message Data Types
// ============================================================================

interface CounterData {
    type: 'counter';
    initialCount: number;
}

interface BalanceData {
    type: 'balance';
}

interface TransferData {
    type: 'transfer';
}

interface SignRawData {
    type: 'signRaw';
}

interface AliasData {
    type: 'alias';
}

interface CreateRoomFormData {
    type: 'createRoomForm';
}

interface NotificationTestData {
    type: 'notificationTest';
}

interface NetworkPermissionTestData {
    type: 'networkPermissionTest';
}

interface DeriveEntropyData {
    type: 'deriveEntropy';
}

interface BatchedPermissionTestData {
    type: 'batchedPermissionTest';
}

interface WildcardPermissionTestData {
    type: 'wildcardPermissionTest';
}

interface ChainSubmitPermissionTestData {
    type: 'chainSubmitPermissionTest';
}

interface PaymentBalanceData {
    type: 'paymentBalance';
}

interface PaymentRequestData {
    type: 'paymentRequest';
}

interface PaymentTrackingData {
    type: 'paymentTracking';
    paymentId: string;
}

interface PaymentTopUpData {
    type: 'paymentTopUp';
}

interface RfcAllowanceData {
    type: 'rfcAllowance';
}

interface StatementSubscribeData {
    type: 'statementSubscribe';
}

interface UserIdentityData {
    type: 'userIdentity';
}

type MessageData = CounterData | BalanceData | TransferData | SignRawData | AliasData | CreateRoomFormData | NotificationTestData | NetworkPermissionTestData | DeriveEntropyData | BatchedPermissionTestData | WildcardPermissionTestData | ChainSubmitPermissionTestData | PaymentBalanceData | PaymentRequestData | PaymentTrackingData | PaymentTopUpData | RfcAllowanceData | StatementSubscribeData | UserIdentityData;

// ============================================================================
// Host API & Chain Client
// ============================================================================

const POP_GENESIS_HASH = '0x3389bc9179d3be32568c67278bd080d05631ac71982d28a3fe545421147b311e';
const ASSET_HUB_GENESIS_HASH = '0x29f7b15e6227f86b90bf5199b5c872c28649a30e5f15fae6dd8fa9d5d48d6fbb';
const ROOM_ID = 'default';

// Shared 32-byte topic so the statement subscription only matches statements this demo submits.
const DEMO_STATEMENT_TOPIC: Uint8Array = (() => {
    const topic = new Uint8Array(32);
    topic.set(new TextEncoder().encode('polkadotapp-demo-topic').slice(0, 32));
    return topic;
})();

const BALANCE_ASSET_ID = {
    parents: 1,
    interior: XcmV5Junctions.X3([
        XcmV5Junction.Parachain(1500),
        XcmV5Junction.PalletInstance(50),
        XcmV5Junction.GeneralIndex(50000413),
    ]),
};

const accountsProvider = createAccountsProvider();
const chat = createProductChatManager();

let popClient: ReturnType<typeof createClient> | null = null;
let popApi: ReturnType<ReturnType<typeof createClient>['getTypedApi']> | null = null;
let assetHubClient: ReturnType<typeof createClient> | null = null;
let assetHubApi: ReturnType<ReturnType<typeof createClient>['getTypedApi']> | null = null;
let chainProperties: { ss58Prefix: number; tokenDecimals: number; tokenSymbol: string } | null = null;
let assetMetadata: { decimals: number; symbol: string } | null = { decimals: 6, symbol: "CASH" };

// ============================================================================
// Bot Initialization (runs at module load — replaces onBotStarted global)
// ============================================================================

console.log('Products Demo: Bot started!');

const provider = createPapiProvider(POP_GENESIS_HASH);
popClient = createClient(provider);
popApi = popClient.getTypedApi(pop);

const assetHubProvider = createPapiProvider(ASSET_HUB_GENESIS_HASH);
assetHubClient = createClient(assetHubProvider);
assetHubApi = assetHubClient.getTypedApi(assetHub);

console.log('Products Demo: PAPI clients initialized (pop + assetHub)');

loadChainProperties().catch(e => console.log(`Products Demo: Failed to load chain properties: ${e}`));

chat.registerRoom({ roomId: ROOM_ID, name: 'Products Demo', icon: null }).then(status => {
    console.log(`Products Demo: Room registration status: ${status}`);
    if (status === 'New') {
        sendTextMessageViaChat("Hello! Send me a number to start a counter, or use /balance, /address, /transfer, /signraw, /alias, /rooms, /notify, /network, /entropy, /batched, /wildcard, /chain, /paybalance, /payrequest, /paytopup, /rfc10, or /userid");
    }
}).catch(e => console.log(`Products Demo: Failed to register room: ${e}`));

// ============================================================================
// Action Handling (replaces onUserMessage global)
// ============================================================================

chat.subscribeAction((action) => {
    console.log(`Bot received ${JSON.stringify(action)}`)

    switch (action.payload.tag) {
        case 'MessagePosted': {
            const msg = action.payload.value;
            if (msg.tag === 'Text') {
                onUserMessage(msg.value);
            }
            break;
        }
    }
});

// ============================================================================
// Bot Logic
// ============================================================================

async function loadChainProperties(): Promise<void> {
    const [chainSpec, ss58Prefix] = await Promise.all([
        popClient!.getChainSpecData(),
        popApi!.constants.System.SS58Prefix(),
    ]);

    const props = chainSpec.properties;
    const tokenDecimals = Array.isArray(props.tokenDecimals) ? props.tokenDecimals[0] : props.tokenDecimals;
    const tokenSymbol = Array.isArray(props.tokenSymbol) ? props.tokenSymbol[0] : props.tokenSymbol;

    chainProperties = { ss58Prefix, tokenDecimals, tokenSymbol };
    console.log(`Products Demo: Chain properties - ss58: ${ss58Prefix}, decimals: ${tokenDecimals}, symbol: ${tokenSymbol}`);
}

function onUserMessage(text: string): void {
    console.log(`Products Demo: User said "${text}"`);

    if (text.trim() === '/balance') {
        sendCustomMessage<BalanceData>({ type: 'balance' });
        return;
    }

    if (text.trim() === '/address') {
        resolveAddress().then(address => {
            sendTextMessageViaChat(address);
        }).catch(e => {
            console.log(`Products Demo: Failed to resolve address: ${e}`);
            sendTextMessageViaChat(`Error: ${e}`);
        });
        return;
    }

    if (text.trim() === '/transfer') {
        sendCustomMessage<TransferData>({ type: 'transfer' });
        return;
    }

    if (text.trim() === '/signraw') {
        sendCustomMessage<SignRawData>({ type: 'signRaw' });
        return;
    }

    if (text.trim() === '/alias') {
        sendCustomMessage<AliasData>({ type: 'alias' });
        return;
    }

    if (text.trim() === '/rooms') {
        sendCustomMessage<CreateRoomFormData>({ type: 'createRoomForm' });
        return;
    }

    if (text.trim() === '/notify') {
        sendCustomMessage<NotificationTestData>({ type: 'notificationTest' });
        return;
    }

    if (text.trim() === '/network') {
        sendCustomMessage<NetworkPermissionTestData>({ type: 'networkPermissionTest' });
        return;
    }

    if (text.trim() === '/entropy') {
        sendCustomMessage<DeriveEntropyData>({ type: 'deriveEntropy' });
        return;
    }

    if (text.trim() === '/batched') {
        sendCustomMessage<BatchedPermissionTestData>({ type: 'batchedPermissionTest' });
        return;
    }

    if (text.trim() === '/wildcard') {
        sendCustomMessage<WildcardPermissionTestData>({ type: 'wildcardPermissionTest' });
        return;
    }

    if (text.trim() === '/chain') {
        sendCustomMessage<ChainSubmitPermissionTestData>({ type: 'chainSubmitPermissionTest' });
        return;
    }

    if (text.trim() === '/paybalance') {
        sendCustomMessage<PaymentBalanceData>({ type: 'paymentBalance' });
        return;
    }

    if (text.trim() === '/payrequest') {
        sendCustomMessage<PaymentRequestData>({ type: 'paymentRequest' });
        return;
    }

    if (text.trim() === '/paytopup') {
        sendCustomMessage<PaymentTopUpData>({ type: 'paymentTopUp' });
        return;
    }

    if (text.trim() === '/rfc10') {
        sendCustomMessage<RfcAllowanceData>({ type: 'rfcAllowance' });
        return;
    }

    if (text.trim() === '/statements') {
        sendCustomMessage<StatementSubscribeData>({ type: 'statementSubscribe' });
        return;
    }

    if (text.trim() === '/userid') {
        sendCustomMessage<UserIdentityData>({ type: 'userIdentity' });
        return;
    }

    // Parse number from text or default to 0
    const num = parseInt(text, 10) || 0;

    // Send a counter starting at that number
    const data: CounterData = { type: 'counter', initialCount: num };
    sendCustomMessage(data);
}

// ============================================================================
// SDK-based Message Sending
// ============================================================================

function sendTextMessageViaChat(text: string): void {
    chat.sendMessage(ROOM_ID, enumValue('Text', text));
}

function sendCustomMessage<T>(data: T): void {
    const payload = new TextEncoder().encode(JSON.stringify(data));
    chat.sendMessage(ROOM_ID, enumValue('Custom', {
        messageType: 'json',
        payload,
    }));
}

// ============================================================================
// React-based Renderer
// ============================================================================

chat.onCustomMessageRenderingRequest(
    registerChatMessageRenderer(
        (raw) => JSON.parse(new TextDecoder().decode(raw)) as MessageData,
        ({ messageType, payload: data }) => {
            console.log(`Products Demo: Rendering ${data.type} message`);

            switch (data.type) {
                case 'balance':
                    return <BalanceCard />;
                case 'transfer':
                    return <TransferCard />;
                case 'signRaw':
                    return <SignRawCard />;
                case 'alias':
                    return <AliasCard />;
                case 'createRoomForm':
                    return <CreateRoomFormCard />;
                case 'notificationTest':
                    return <NotificationTestCard />;
                case 'networkPermissionTest':
                    return <NetworkPermissionTestCard />;
                case 'deriveEntropy':
                    return <DeriveEntropyCard />;
                case 'batchedPermissionTest':
                    return <BatchedPermissionTestCard />;
                case 'wildcardPermissionTest':
                    return <WildcardPermissionTestCard />;
                case 'chainSubmitPermissionTest':
                    return <ChainSubmitPermissionTestCard />;
                case 'paymentBalance':
                    return <PaymentBalanceCard />;
                case 'paymentRequest':
                    return <PaymentRequestCard />;
                case 'paymentTopUp':
                    return <PaymentTopUpCard />;
                case 'paymentTracking':
                    return <PaymentTrackingCard paymentId={data.paymentId} />;
                case 'rfcAllowance':
                    return <RfcAllowanceCard />;
                case 'statementSubscribe':
                    return <StatementSubscribeCard />;
                case 'userIdentity':
                    return <UserIdentityCard />;
                case 'counter':
                default:
                    const initialCount = data.initialCount ?? 0;
                    return <CounterCard initialCount={initialCount} />;
            }
        }
    )
);

// ============================================================================
// UI Components
// ============================================================================

function CounterCard({ initialCount }: { initialCount: number }) {
    const [count, setCount] = useState(initialCount);
    const [accountName, setAccountName] = useState<string | undefined>();

    useEffect(() => {
        Promise.resolve(accountsProvider.getProductAccount('product-sample.dot', 0)).then(result => {
            if (result.isOk()) {
                setAccountName(result.value.name);
                console.log(`accountGet success - name: ${result.value.name}`);
            } else {
                setAccountName(`Error: ${JSON.stringify(result.error)}`);
                console.log(`accountGet error: ${JSON.stringify(result.error)}`);
            }
        }).catch((e: unknown) => {
            console.log(`accountGet exception: ${e}`);
        });
    }, []);

    return (
        <Column
            padding={20}
            background={{ color: 'bg.surface.nested', shape: { tag: 'Rounded', value: 16 } }}
            horizontalAlignment="center"
        >
            <Text style="body.small.regular" color="fg.secondary">
                {accountName != null ? `Account: ${accountName}` : 'Loading account...'}
            </Text>

            <Spacer height={12} />

            <Text style="headline.large" color="fg.primary">
                {`${count}`}
            </Text>

            <Spacer height={16} />

            <Row horizontalArrangement="center">
                <Button
                    text="-"
                    variant="secondary"
                    onClick={() => setCount(c => c - 1)}
                />

                <Spacer width={12} />

                <Button
                    text="+"
                    variant="primary"
                    onClick={() => setCount(c => c + 1)}
                />
            </Row>

            <Spacer height={12} />

            <Button
                text="Reset"
                variant="text"
                onClick={() => setCount(0)}
            />
        </Column>
    );
}

function BalanceCard() {
    const [balance, setBalance] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!popApi) {
            setError('Client not initialized');
            return;
        }

        let subscription: { unsubscribe(): void } | null = null;

        resolveAddress().then(address => {
            subscription = popApi!.query.Assets.Account.watchValue(BALANCE_ASSET_ID, address, { at: 'best' })
                .subscribe({
                    next: (emission: any) => {
                        const value = emission?.value;
                        setBalance(value != null ? formatBalance(value.balance) : '0');
                    },
                    error: (e: unknown) => {
                        setError(String(e));
                    },
                });
        }).catch((e: unknown) => setError(String(e)));

        return () => { subscription?.unsubscribe(); };
    }, []);

    return (
        <Column
            padding={20}
            background={{ color: 'bg.surface.nested', shape: { tag: 'Rounded', value: 16 } }}
            horizontalAlignment="center"
        >
            <Text style="body.small.regular" color="fg.secondary">Product account balance (on-chain)</Text>
            <Spacer height={4} />
            <Text style="body.small.regular" color="fg.tertiary">Queried directly from Assets pallet</Text>
            <Spacer height={12} />
            {error != null
                ? <Text style="body.large.regular" color="fg.primary">{`Error: ${error}`}</Text>
                : balance != null
                    ? <Text style="headline.large" color="fg.primary">{balance}</Text>
                    : <Text style="body.large.regular" color="fg.secondary">Loading...</Text>
            }
        </Column>
    );
}

function TransferCard() {
    const [status, setStatus] = useState<string>('Ready to transfer 1 UNIT to Alice');
    const [transferring, setTransferring] = useState(false);

    const handleTransfer = () => {
        if (!popApi || transferring) return;

        setTransferring(true);
        setStatus('Preparing transfer...');

        resolveProductAccountWithSigner().then(async ({ address, signer }) => {
            const decimals = chainProperties?.tokenDecimals ?? 10;
            const oneToken = 10n ** BigInt(decimals);

            const aliceAddress = getAliceAddress();

            setStatus('Awaiting signature...');

            const tx = popApi!.tx.Balances.transfer_keep_alive({
                dest: MultiAddress.Id(aliceAddress),
                value: oneToken,
            });

            tx.signSubmitAndWatch(signer).subscribe({
                next: (event: TxEvent) => {
                    console.log(`Products Demo: Transfer tx event: ${event.type}`);
                    if (event.type === 'txBestBlocksState') {
                        if (event.found) {
                            setStatus('Transfer successful!');
                        } else {
                            setStatus('Transaction pending...');
                        }
                        setTransferring(false);
                    }
                },
                error: (e: unknown) => {
                    console.log(`Products Demo: Transfer tx error: ${e}`);
                    setStatus(`Error: ${e}`);
                    setTransferring(false);
                },
            });
        }).catch((e: unknown) => {
            console.log(`Products Demo: Transfer failed: ${e}`);
            setStatus(`Error: ${e}`);
            setTransferring(false);
        });
    };

    return (
        <Column
            padding={20}
            background={{ color: 'bg.surface.nested', shape: { tag: 'Rounded', value: 16 } }}
            horizontalAlignment="center"
        >
            <Text style="body.small.regular" color="fg.secondary">Transfer via Host API</Text>
            <Spacer height={8} />
            <Text style="body.large.regular" color="fg.primary">{status}</Text>
            <Spacer height={16} />
            <Button
                text="Send 1 UNIT to Alice"
                variant="primary"
                loading={transferring}
                onClick={handleTransfer}
            />
        </Column>
    );
}

function SignRawCard() {
    const [status, setStatus] = useState<string>('Ready');
    const [signature, setSignature] = useState<string | null>(null);
    const [signing, setSigning] = useState(false);

    const handleSignRaw = () => {
        if (signing) return;

        setSigning(true);
        setStatus('Awaiting signature...');

        resolveProductAccountWithSigner().then(async ({ signer }) => {
            const message = new TextEncoder().encode('Hello from Products Demo!');
            const result = await signer.signBytes(message);
            const hex = Array.from(new Uint8Array(result))
                .map(b => b.toString(16).padStart(2, '0'))
                .join('');
            setSignature(`0x${hex}`);
            setStatus('Signed successfully!');
            setSigning(false);
        }).catch((e: unknown) => {
            console.log(`Products Demo: Sign raw failed: ${e}`);
            setStatus(`Error: ${e}`);
            setSigning(false);
        });
    };

    return (
        <Column
            padding={20}
            background={{ color: 'bg.surface.nested', shape: { tag: 'Rounded', value: 16 } }}
            horizontalAlignment="center"
        >
            <Text style="body.small.regular" color="fg.secondary">Sign Raw via Host API</Text>
            <Spacer height={8} />
            <Text style="body.large.regular" color="fg.primary">{status}</Text>
            {signature != null && (
                <>
                    <Spacer height={8} />
                    <Text style="body.small.regular" color="fg.secondary">
                        {`${signature.slice(0, 24)}...${signature.slice(-8)}`}
                    </Text>
                </>
            )}
            <Spacer height={16} />
            <Button
                text="Sign Message"
                variant="primary"
                loading={signing}
                onClick={handleSignRaw}
            />
        </Column>
    );
}

function toHex(bytes: Uint8Array): string {
    return '0x' + Array.from(bytes).map(b => b.toString(16).padStart(2, '0')).join('');
}

function AliasCard() {
    const [context, setContext] = useState<string | null>(null);
    const [alias, setAlias] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);

    const handleGetAlias = () => {
        if (loading) return;

        setLoading(true);
        setError(null);

        Promise.resolve(accountsProvider.getProductAccountAlias('product-sample.dot', 0)).then(result => {
            if (result.isOk()) {
                setContext(toHex(result.value.context));
                setAlias(toHex(result.value.alias));
                console.log(`Products Demo: getAlias success`);
            } else {
                setError(`Error: ${JSON.stringify(result.error)}`);
                console.log(`Products Demo: getAlias error: ${JSON.stringify(result.error)}`);
            }
            setLoading(false);
        }).catch((e: unknown) => {
            console.log(`Products Demo: getAlias exception: ${e}`);
            setError(`Error: ${e}`);
            setLoading(false);
        });
    };

    return (
        <Column
            padding={20}
            background={{ color: 'bg.surface.nested', shape: { tag: 'Rounded', value: 16 } }}
            horizontalAlignment="center"
        >
            <Text style="body.small.regular" color="fg.secondary">Get Alias via Host API</Text>
            <Spacer height={8} />
            {error != null
                ? <Text style="body.large.regular" color="fg.primary">{error}</Text>
                : alias != null && context != null
                    ? (
                        <>
                            <Text style="body.small.regular" color="fg.secondary">Context</Text>
                            <Spacer height={4} />
                            <Text style="body.large.regular" color="fg.primary">
                                {`${context.slice(0, 24)}...${context.slice(-8)}`}
                            </Text>
                            <Spacer height={8} />
                            <Text style="body.small.regular" color="fg.secondary">Alias</Text>
                            <Spacer height={4} />
                            <Text style="body.large.regular" color="fg.primary">
                                {`${alias.slice(0, 24)}...${alias.slice(-8)}`}
                            </Text>
                        </>
                    )
                    : <Text style="body.large.regular" color="fg.secondary">Press button to fetch alias</Text>
            }
            <Spacer height={16} />
            <Button
                text="Get Alias"
                variant="primary"
                loading={loading}
                onClick={handleGetAlias}
            />
        </Column>
    );
}

function CreateRoomFormCard() {
    const [roomName, setRoomName] = useState('');
    const [status, setStatus] = useState<string | null>(null);
    const [creating, setCreating] = useState(false);
    const [rooms, setRooms] = useState<{ roomId: string; participatingAs: string }[]>([]);

    useEffect(() => {
        const subscription = chat.subscribeChatList((updatedRooms) => {
            setRooms(updatedRooms);
        });

        return () => { subscription.unsubscribe(); };
    }, []);

    const handleCreateRoom = () => {
        const name = roomName.trim();
        if (!name || creating) return;

        setCreating(true);
        setStatus(null);

        const roomId = `user-room-${name.toLowerCase().replace(/\s+/g, '-')}`;

        chat.registerRoom({ roomId, name, icon: '' }).then(result => {
            setStatus(result === 'New' ? `Room "${name}" created!` : `Room "${name}" already exists`);

            if (result === 'New') {
                chat.sendMessage(roomId, enumValue('Text', `Welcome to ${name}!`));
            }

            setCreating(false);
        }).catch(e => {
            setStatus(`Error: ${e}`);
            setCreating(false);
        });
    };

    return (
        <Column
            padding={20}
            background={{ color: 'bg.surface.nested', shape: { tag: 'Rounded', value: 16 } }}
            horizontalAlignment="center"
        >
            <Text style="body.small.regular" color="fg.secondary">Rooms</Text>
            <Spacer height={12} />

            {rooms.length === 0
                ? <Text style="body.large.regular" color="fg.secondary">No rooms yet</Text>
                : rooms.map((room) => (
                    <Column key={room.roomId}>
                        <Text style="body.large.regular" color="fg.primary">
                            {`${room.roomId} (${room.participatingAs})`}
                        </Text>
                        <Spacer height={4} />
                    </Column>
                ))
            }

            <Spacer height={16} />

            <TextField
                placeholder="Room name"
                value={roomName}
                onValueChange={setRoomName}
            />

            <Spacer height={12} />

            {status != null && (
                <>
                    <Text style="body.large.regular" color="fg.primary">{status}</Text>
                    <Spacer height={8} />
                </>
            )}

            <Button
                text="Create Room"
                variant="primary"
                loading={creating}
                onClick={handleCreateRoom}
            />
        </Column>
    );
}

async function resolveProductAccountWithSigner(): Promise<{ address: string; signer: PolkadotSigner }> {
    const accountResult = await accountsProvider.getProductAccount('product-sample.dot', 0);
    if (!accountResult.isOk()) throw new Error('Failed to get account');

    const account = {
        ...accountResult.value,
        dotNsIdentifier: 'product-sample.dot',
        derivationIndex: 0,
    };
    const ss58Prefix = chainProperties?.ss58Prefix ?? 42;
    const address = fromBufferToBase58(ss58Prefix)(account.publicKey);
    const signer = accountsProvider.getProductAccountSigner(account, 'createTransaction');

    return { address, signer };
}

function getAliceAddress(): string {
    const keyring = new Keyring({ type: 'sr25519' });
    const alicePair = keyring.addFromUri('//Alice');
    const ss58Prefix = chainProperties?.ss58Prefix ?? 42;
    return fromBufferToBase58(ss58Prefix)(alicePair.publicKey);
}

async function resolveAddress(): Promise<string> {
    const accountResult = await accountsProvider.getProductAccount('product-sample.dot', 0);
    if (!accountResult.isOk()) throw new Error('Failed to get account');

    const ss58Prefix = chainProperties?.ss58Prefix ?? 42;
    return fromBufferToBase58(ss58Prefix)(accountResult.value.publicKey);
}

function formatBalance(free: bigint): string {
    if (!assetMetadata) throw new Error('Asset metadata not loaded');
    const decimals = assetMetadata.decimals;
    const symbol = assetMetadata.symbol;
    const divisor = 10n ** BigInt(decimals);
    const whole = free / divisor;
    const fraction = (free % divisor).toString().padStart(decimals, '0').slice(0, 4);
    return `${whole}.${fraction} ${symbol}`;
}

// ============================================================================
// Notification Test Card
// ============================================================================

function NotificationTestCard() {
    const [seconds, setSeconds] = useState<string>('10');
    const [status, setStatus] = useState<string>('Not scheduled');
    const [scheduling, setScheduling] = useState(false);
    const [cancelling, setCancelling] = useState(false);
    const [lastScheduledId, setLastScheduledId] = useState<number | null>(null);

    async function handleSchedule() {
        const delaySeconds = parseInt(seconds, 10);
        if (Number.isNaN(delaySeconds) || delaySeconds < 0) {
            setStatus('Enter a non-negative number of seconds');
            return;
        }

        setScheduling(true);
        try {
            const permission = await requestDevicePermission('Notifications');
            if (permission.isErr() || !permission.value) {
                setStatus('Notification permission denied');
                return;
            }

            const scheduledAt = Date.now() + delaySeconds * 1000;
            const id = await notificationManager.push({
                text: `Scheduled reminder fired after ${delaySeconds}s`,
                scheduledAt,
            });
            setLastScheduledId(id);
            setStatus(`Scheduled (id ${id}) to fire in ${delaySeconds}s`);
        } catch (e: any) {
            setStatus(`Error: ${e?.message ?? e?.reason ?? String(e)}`);
        } finally {
            setScheduling(false);
        }
    }

    async function handleCancelLast() {
        if (lastScheduledId == null) return;

        setCancelling(true);
        try {
            await notificationManager.cancel(lastScheduledId);
            setStatus(`Cancelled notification id ${lastScheduledId}`);
            setLastScheduledId(null);
        } catch (e: any) {
            setStatus(`Error: ${e?.message ?? e?.reason ?? String(e)}`);
        } finally {
            setCancelling(false);
        }
    }

    return (
        <Column
            padding={20}
            background={{ color: 'bg.surface.nested', shape: { tag: 'Rounded', value: 16 } }}
            horizontalAlignment="center"
        >
            <Text style="body.small.regular" color="fg.secondary">Schedule Notification</Text>
            <Spacer height={12} />
            <TextField
                placeholder="Number of seconds to schedule notification in the future"
                value={seconds}
                onValueChange={setSeconds}
            />
            <Spacer height={12} />
            <Text style="body.large.regular" color="fg.primary">{status}</Text>
            <Spacer height={16} />
            <Button
                text="Schedule Notification"
                variant="primary"
                loading={scheduling}
                onClick={handleSchedule}
            />
            {lastScheduledId != null && (
                <>
                    <Spacer height={8} />
                    <Button
                        text="Cancel last"
                        variant="secondary"
                        loading={cancelling}
                        onClick={handleCancelLast}
                    />
                </>
            )}
        </Column>
    );
}

// ============================================================================
// Network Permission Test Card
// ============================================================================

const TEST_URL = 'https://jsonplaceholder.typicode.com/todos/1';

function NetworkPermissionTestCard() {
    const [status, setStatus] = useState<string>('Not requested');
    const [requesting, setRequesting] = useState(false);
    const [fetching, setFetching] = useState(false);

    async function handleRequestPermission() {
        setRequesting(true);
        try {
            const result = await hostApi.permission({
                tag: 'v1',
                value: [{ tag: 'Remote', value: ['jsonplaceholder.typicode.com'] }],
            });
            if (result.isOk()) {
                setStatus(result.value.value ? 'Permission granted' : 'Permission denied');
            } else {
                const err = result.error as any;
                setStatus(`Error: ${err?.value?.reason ?? JSON.stringify(err)}`);
            }
        } catch (e: any) {
            setStatus(`Error: ${e?.message ?? String(e)}`);
        } finally {
            setRequesting(false);
        }
    }

    async function handleFetch() {
        setFetching(true);
        try {
            const response = await fetch(TEST_URL);
            const data = await response.json();
            setStatus(`Fetched: ${JSON.stringify(data).slice(0, 80)}`);
        } catch (e: any) {
            setStatus(`Fetch error: ${e?.message ?? String(e)}`);
        } finally {
            setFetching(false);
        }
    }

    return (
        <Column
            padding={20}
            background={{ color: 'bg.surface.nested', shape: { tag: 'Rounded', value: 16 } }}
            horizontalAlignment="center"
        >
            <Text style="body.small.regular" color="fg.secondary">Network Permission Test</Text>
            <Spacer height={8} />
            <Text style="body.large.regular" color="fg.primary">{status}</Text>
            <Spacer height={16} />
            <Row>
                <Button
                    text="Request Permission"
                    variant="primary"
                    loading={requesting}
                    onClick={handleRequestPermission}
                />
                <Spacer width={8} />
                <Button
                    text="Fetch URL"
                    variant="secondary"
                    loading={fetching}
                    onClick={handleFetch}
                />
            </Row>
        </Column>
    );
}

// ============================================================================
// Derive Entropy Card
// ============================================================================

function DeriveEntropyCard() {
    const [key, setKey] = useState('my-app-key');
    const [entropy, setEntropy] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [deriving, setDeriving] = useState(false);

    const handleDerive = () => {
        if (deriving) return;

        setDeriving(true);
        setEntropy(null);
        setError(null);

        const keyBytes = new TextEncoder().encode(key);

        deriveEntropy(keyBytes).match(
            (value) => {
                setEntropy(toHex(value));
                setDeriving(false);
            },
            (err) => {
                setError(err.payload.reason);
                setDeriving(false);
            },
        );
    };

    return (
        <Column
            padding={20}
            background={{ color: 'bg.surface.nested', shape: { tag: 'Rounded', value: 16 } }}
            horizontalAlignment="center"
        >
            <Text style="body.small.regular" color="fg.secondary">Derive Entropy via Host API</Text>
            <Spacer height={12} />

            <TextField
                placeholder="Derivation key"
                value={key}
                onValueChange={setKey}
            />

            <Spacer height={12} />

            {error != null
                ? <Text style="body.large.regular" color="fg.primary">{`Error: ${error}`}</Text>
                : entropy != null
                    ? (
                        <>
                            <Text style="body.small.regular" color="fg.secondary">Entropy (32 bytes)</Text>
                            <Spacer height={4} />
                            <Text style="body.large.regular" color="fg.primary">
                                {`${entropy.slice(0, 24)}...${entropy.slice(-8)}`}
                            </Text>
                        </>
                    )
                    : <Text style="body.large.regular" color="fg.secondary">Press button to derive</Text>
            }

            <Spacer height={16} />

            <Button
                text="Derive Entropy"
                variant="primary"
                loading={deriving}
                onClick={handleDerive}
            />
        </Column>
    );
}

// ============================================================================
// PANS-2391 Permission Model Test Cards
// ============================================================================

function BatchedPermissionTestCard() {
    const [status, setStatus] = useState<string>('Not requested');
    const [requesting, setRequesting] = useState(false);

    async function handleRequest() {
        setRequesting(true);
        try {
            const result = await hostApi.permission({
                tag: 'v1',
                value: [
                    { tag: 'Remote', value: ['api.example.com', 'cdn.example.com'] },
                    { tag: 'WebRtc', value: undefined },
                    { tag: 'ChainSubmit', value: undefined },
                    { tag: 'StatementSubmit', value: undefined },
                ],
            });
            if (result.isOk()) {
                setStatus(result.value.value ? 'Batch granted (single decision)' : 'Batch denied');
            } else {
                const err = result.error as any;
                setStatus(`Error: ${err?.value?.reason ?? JSON.stringify(err)}`);
            }
        } catch (e: any) {
            setStatus(`Error: ${e?.message ?? String(e)}`);
        } finally {
            setRequesting(false);
        }
    }

    return (
        <Column
            padding={20}
            background={{ color: 'bg.surface.nested', shape: { tag: 'Rounded', value: 16 } }}
            horizontalAlignment="center"
        >
            <Text style="body.small.regular" color="fg.secondary">Batched Permission Test</Text>
            <Spacer height={4} />
            <Text style="body.large.regular" color="fg.secondary">Remote (2) + WebRTC + ChainSubmit + StatementSubmit</Text>
            <Spacer height={12} />
            <Text style="body.large.regular" color="fg.primary">{status}</Text>
            <Spacer height={16} />
            <Button
                text="Request all 4 variants"
                variant="primary"
                loading={requesting}
                onClick={handleRequest}
            />
        </Column>
    );
}

function WildcardPermissionTestCard() {
    const [status, setStatus] = useState<string>('Not requested');
    const [grantingParent, setGrantingParent] = useState(false);
    const [checkingChild, setCheckingChild] = useState(false);

    async function handleGrantParent() {
        setGrantingParent(true);
        try {
            const result = await hostApi.permission({
                tag: 'v1',
                value: [{ tag: 'Remote', value: ['*.example.com'] }],
            });
            if (result.isOk()) {
                setStatus(result.value.value ? 'Granted *.example.com' : 'Denied *.example.com');
            } else {
                const err = result.error as any;
                setStatus(`Error: ${err?.value?.reason ?? JSON.stringify(err)}`);
            }
        } finally {
            setGrantingParent(false);
        }
    }

    async function handleCheckChild() {
        setCheckingChild(true);
        try {
            const result = await hostApi.permission({
                tag: 'v1',
                value: [{ tag: 'Remote', value: ['a.example.com'] }],
            });
            if (result.isOk()) {
                setStatus(result.value.value ? 'a.example.com: true (no prompt expected)' : 'a.example.com: false');
            } else {
                const err = result.error as any;
                setStatus(`Error: ${err?.value?.reason ?? JSON.stringify(err)}`);
            }
        } finally {
            setCheckingChild(false);
        }
    }

    return (
        <Column
            padding={20}
            background={{ color: 'bg.surface.nested', shape: { tag: 'Rounded', value: 16 } }}
            horizontalAlignment="center"
        >
            <Text style="body.small.regular" color="fg.secondary">Wildcard Domain Test</Text>
            <Spacer height={4} />
            <Text style="body.large.regular" color="fg.secondary">1. Grant *.example.com  2. Check a.example.com (should skip prompt)</Text>
            <Spacer height={12} />
            <Text style="body.large.regular" color="fg.primary">{status}</Text>
            <Spacer height={16} />
            <Button
                text="Grant *.example.com"
                variant="primary"
                loading={grantingParent}
                onClick={handleGrantParent}
            />
            <Spacer height={8} />
            <Button
                text="Check a.example.com"
                variant="secondary"
                loading={checkingChild}
                onClick={handleCheckChild}
            />
        </Column>
    );
}

function ChainSubmitPermissionTestCard() {
    const [status, setStatus] = useState<string>('Not requested');
    const [requesting, setRequesting] = useState(false);

    async function handleRequest() {
        setRequesting(true);
        try {
            const result = await hostApi.permission({
                tag: 'v1',
                value: [{ tag: 'ChainSubmit', value: undefined }],
            });
            if (result.isOk()) {
                setStatus(result.value.value ? 'ChainSubmit granted' : 'ChainSubmit denied');
            } else {
                const err = result.error as any;
                setStatus(`Error: ${err?.value?.reason ?? JSON.stringify(err)}`);
            }
        } catch (e: any) {
            setStatus(`Error: ${e?.message ?? String(e)}`);
        } finally {
            setRequesting(false);
        }
    }

    return (
        <Column
            padding={20}
            background={{ color: 'bg.surface.nested', shape: { tag: 'Rounded', value: 16 } }}
            horizontalAlignment="center"
        >
            <Text style="body.small.regular" color="fg.secondary">ChainSubmit Permission Test</Text>
            <Spacer height={4} />
            <Text style="body.large.regular" color="fg.secondary">Standalone ChainSubmit via remote_permission</Text>
            <Spacer height={12} />
            <Text style="body.large.regular" color="fg.primary">{status}</Text>
            <Spacer height={16} />
            <Button
                text="Request ChainSubmit"
                variant="primary"
                loading={requesting}
                onClick={handleRequest}
            />
        </Column>
    );
}

// ============================================================================
// Payment Balance Card (RFC-0006 host_payment_balance_subscribe)
// ============================================================================

function PaymentBalanceCard() {
    const [available, setAvailable] = useState<bigint | null>(null);
    const [status, setStatus] = useState<string>('Requesting permission...');

    useEffect(() => {
        const subscription = paymentManager.subscribeBalance((balance) => {
            setAvailable(balance.available);
            setStatus('Live');
        });

        subscription.onInterrupt(() => {
            setStatus('Subscription interrupted (permission denied or host error)');
        });

        return () => { subscription.unsubscribe(); };
    }, []);

    return (
        <Column
            padding={20}
            background={{ color: 'bg.surface.nested', shape: { tag: 'Rounded', value: 16 } }}
            horizontalAlignment="center"
        >
            <Text style="body.small.regular" color="fg.secondary">User's spendable balance (via host)</Text>
            <Spacer height={4} />
            <Text style="body.small.regular" color="fg.tertiary">Reported by host_payment_balance_subscribe</Text>
            <Spacer height={8} />
            {available != null
                ? <Text style="headline.large" color="fg.primary">{formatBalance(available)}</Text>
                : <Text style="body.large.regular" color="fg.secondary">{status}</Text>
            }
            {available != null && (
                <>
                    <Spacer height={4} />
                    <Text style="body.small.regular" color="fg.secondary">{status}</Text>
                </>
            )}
        </Column>
    );
}

// ============================================================================
// Statement-Store Subscription Card
// ============================================================================

function StatementSubscribeCard() {
    const [count, setCount] = useState<number>(0);
    const [latest, setLatest] = useState<SignedStatement | null>(null);
    const [status, setStatus] = useState<string>('Subscribing…');

    useEffect(() => {
        const store = createStatementStore();
        const subscription = store.subscribe({ matchAll: [DEMO_STATEMENT_TOPIC] }, (page) => {
            setCount(page.statements.length);
            setLatest(page.statements[page.statements.length - 1] ?? null);
            setStatus(`Live — ${page.statements.length} statement(s), complete: ${page.isComplete}`);
        });

        subscription.onInterrupt(() => {
            setStatus('Subscription interrupted (permission denied or host error)');
        });

        return () => { subscription.unsubscribe(); };
    }, []);

    return (
        <Column
            padding={20}
            background={{ color: 'bg.surface.nested', shape: { tag: 'Rounded', value: 16 } }}
            horizontalAlignment="center"
        >
            <Text style="body.small.regular" color="fg.secondary">Statement-store subscription (demo topic)</Text>
            <Spacer height={4} />
            <Text style="body.small.regular" color="fg.tertiary">Reported by statementStoreSubscribe</Text>
            <Spacer height={8} />
            <Text style="headline.large" color="fg.primary">{count}</Text>
            <Spacer height={4} />
            <Text style="body.small.regular" color="fg.secondary">{status}</Text>
            {latest?.data && (
                <>
                    <Spacer height={4} />
                    <Text style="body.small.regular" color="fg.tertiary">latest data: {toHex(latest.data).slice(0, 34)}…</Text>
                </>
            )}
        </Column>
    );
}

// ============================================================================
// Payment Request Card (RFC-0006 host_payment_request)
// ============================================================================

function decimalToPlanks(decimal: string, precision: number): bigint {
    const trimmed = decimal.trim();
    if (!/^\d+(\.\d+)?$/.test(trimmed)) {
        throw new Error(`Invalid decimal amount: "${decimal}"`);
    }
    const [whole, fraction = ''] = trimmed.split('.');
    const padded = (fraction + '0'.repeat(precision)).slice(0, precision);
    return BigInt(whole + padded);
}

function PaymentRequestCard() {
    const [amountText, setAmountText] = useState('0.1');
    const [status, setStatus] = useState<string>('Ready');
    const [requesting, setRequesting] = useState(false);

    const handleRequest = () => {
        if (requesting) return;

        const precision = assetMetadata?.decimals;
        if (precision == null) {
            setStatus('Asset metadata not loaded yet');
            return;
        }

        let planks: bigint;
        try {
            planks = decimalToPlanks(amountText, precision);
        } catch (e) {
            setStatus(e instanceof Error ? e.message : String(e));
            return;
        }

        setRequesting(true);
        setStatus('Awaiting user approval...');

        accountsProvider.getProductAccount('product-sample.dot', 0).then(async (result) => {
            if (!result.isOk()) throw new Error(`Failed to get product account: ${JSON.stringify(result.error)}`);
            const destination = result.value.publicKey;

            return paymentManager.requestPayment(planks, destination);
        }).then(({ id }) => {
            sendCustomMessage<PaymentTrackingData>({ type: 'paymentTracking', paymentId: id });
            setStatus(`Submitted (id=${id.slice(0, 8)}…) — tracking message posted`);
        }).catch((e: unknown) => {
            const msg = e instanceof Error ? e.message : String(e);
            setStatus(`Error: ${msg}`);
        }).finally(() => setRequesting(false));
    };

    return (
        <Column
            padding={20}
            background={{ color: 'bg.surface.nested', shape: { tag: 'Rounded', value: 16 } }}
            horizontalAlignment="center"
        >
            <Text style="body.small.regular" color="fg.secondary">Payment Request (host)</Text>
            <Spacer height={12} />
            <TextField
                placeholder="Amount"
                value={amountText}
                onValueChange={setAmountText}
            />
            <Spacer height={8} />
            <Text style="body.large.regular" color="fg.primary">{status}</Text>
            <Spacer height={16} />
            <Button
                text="Request to product account"
                variant="primary"
                loading={requesting}
                onClick={handleRequest}
            />
        </Column>
    );
}

// ============================================================================
// Payment Top Up Card (RFC-0006 host_payment_top_up)
// ============================================================================

// Produces a 64-byte sr25519 secret key whose first 32 bytes form a canonical scalar
// (< 2^252 < ℓ), so the host always accepts it as valid — only the masking matters for a fake key.
function fakeSr25519SecretKey(): Uint8Array {
    const sk = randomAsU8a(64);
    sk[31] &= 0x0f;
    return sk;
}

function PaymentTopUpCard() {
    const [amountText, setAmountText] = useState('0.1');
    const [status, setStatus] = useState<string>('Ready');
    const [sending, setSending] = useState(false);

    const handleSend = (source: TopUpSource) => {
        if (sending) return;

        const precision = assetMetadata?.decimals;
        if (precision == null) {
            setStatus('Asset metadata not loaded yet');
            return;
        }

        let planks: bigint;

        try {
            planks = decimalToPlanks(amountText, precision);
        } catch (e) {
            setStatus(e instanceof Error ? e.message : String(e));
            return;
        }

        setSending(true);
        setStatus('Awaiting user to claim…');

        paymentManager.topUp(planks, source).then(() => {
            setStatus('Claimed and onboarded ✅');
        }).catch((e: unknown) => {
            const msg = e instanceof Error ? e.message : String(e);
            setStatus(`Error: ${msg}`);
        }).finally(() => setSending(false));
    };

    return (
        <Column
            padding={20}
            background={{ color: 'bg.surface.nested', shape: { tag: 'Rounded', value: 16 } }}
            horizontalAlignment="center"
        >
            <Text style="body.small.regular" color="fg.secondary">Payment Top Up (host)</Text>
            <Spacer height={12} />
            <TextField
                placeholder="Amount"
                value={amountText}
                onValueChange={setAmountText}
            />
            <Spacer height={8} />
            <Text style="body.large.regular" color="fg.primary">{status}</Text>
            <Spacer height={16} />
            <Button
                text="Claim from product account"
                variant="primary"
                loading={sending}
                onClick={() => handleSend({ type: 'productAccount', derivationIndex: 0 })}
            />
            <Spacer height={8} />
            <Button
                text="Claim a fake coin"
                variant="secondary"
                loading={sending}
                onClick={() => handleSend({ type: 'coins', keys: [fakeSr25519SecretKey()] })}
            />
        </Column>
    );
}

// ============================================================================
// Payment Tracking Card — one instance per initiated payment
// ============================================================================

function PaymentTrackingCard({ paymentId }: { paymentId: string }) {
    const [status, setStatus] = useState<string>('Subscribing...');

    useEffect(() => {
        const subscription = paymentManager.subscribePaymentStatus(paymentId, (s) => {
            if (s.type === 'processing') setStatus('Processing on chain');
            else if (s.type === 'completed') setStatus('Completed ✅');
            else setStatus(`Failed: ${s.reason}`);
        });
        subscription.onInterrupt(() => setStatus('Status subscription interrupted'));

        return () => { subscription.unsubscribe(); };
    }, [paymentId]);

    return (
        <Column
            padding={20}
            background={{ color: 'bg.surface.nested', shape: { tag: 'Rounded', value: 16 } }}
            horizontalAlignment="center"
        >
            <Text style="body.small.regular" color="fg.secondary">Payment</Text>
            <Spacer height={4} />
            <Text style="body.small.regular" color="fg.tertiary">{`id=${paymentId.slice(0, 12)}…`}</Text>
            <Spacer height={12} />
            <Text style="body.large.regular" color="fg.primary">{status}</Text>
        </Column>
    );
}

// ============================================================================
// RFC-0010: Allowance Management
// ============================================================================

function RfcAllowanceCard() {
    const [status, setStatus] = useState<string>('Idle');
    const [busy, setBusy] = useState(false);

    async function allocate(label: string, resources: { tag: string; value: any }[]) {
        if (busy) return;
        setBusy(true);
        setStatus(`Allocating ${label}…`);
        try {
            const result = await hostApi.requestResourceAllocation(enumValue('v1', resources as any));
            if (result.isOk() && result.value.tag === 'v1') {
                setStatus(`${label}: ${result.value.value.map(o => o.tag).join(', ')}`);
            } else {
                const errVal = result.isErr() ? (result.error as any) : 'unsupported version';
                setStatus(`${label} failed: ${errVal?.value?.reason ?? JSON.stringify(errVal)}`);
            }
        } catch (e) {
            setStatus(`${label} error: ${e instanceof Error ? e.message : String(e)}`);
        } finally {
            setBusy(false);
        }
    }

    const allocateBulletin = () => allocate('Bulletin', [enumValue('BulletinAllowance', undefined)]);
    const allocateStatementStore = () => allocate('StatementStore', [enumValue('StatementStoreAllowance', undefined)]);
    const allocateSmartContract = () => allocate('SmartContract(0)', [enumValue('SmartContractAllowance', 0)]);
    const allocateAll = () => allocate('All resources', [
        enumValue('BulletinAllowance', undefined),
        enumValue('StatementStoreAllowance', undefined),
        enumValue('SmartContractAllowance', 0),
    ]);

    async function submitToBulletin() {
        if (busy) return;
        setBusy(true);
        setStatus('Submitting preimage to Bulletin…');
        try {
            const blob = new TextEncoder().encode(`hello-bulletin-${Date.now()}`);
            const hash = await preimageManager.submit(blob);
            setStatus(`Bulletin hash: ${hash.slice(0, 18)}…`);
        } catch (e) {
            setStatus(`Bulletin error: ${e instanceof Error ? e.message : String(e)}`);
        } finally {
            setBusy(false);
        }
    }

    async function authorizeAndSubmitStatement() {
        if (busy) return;
        setBusy(true);
        setStatus('Creating Statement-Store proof…');
        try {
            const data = new TextEncoder().encode(`hello-sss-${Date.now()}`);
            const statementBody = {
                proof: undefined,
                decryptionKey: undefined,
                expiry: undefined,
                channel: undefined,
                topics: [DEMO_STATEMENT_TOPIC],
                data,
            };
            const proofResult = await hostApi.statementStoreCreateProofAuthorized(enumValue('v1', statementBody));
            if (!proofResult.isOk() || proofResult.value.tag !== 'v1') {
                const errVal = proofResult.isErr() ? (proofResult.error as any) : 'unsupported version';
                setStatus(`SSS proof failed: ${errVal?.value?.reason ?? JSON.stringify(errVal)}`);
                return;
            }
            const proof = proofResult.value.value;
            const signer = proof.tag === 'OnChain' ? proof.value.who : proof.value.signer;
            setStatus(`Proof ${proof.tag} signer ${toHex(signer).slice(0, 18)}…; submitting on-chain…`);

            const submitResult = await hostApi.statementStoreSubmit(enumValue('v1', { ...statementBody, proof }));
            if (submitResult.isOk() && submitResult.value.tag === 'v1') {
                setStatus(`Authorize + Submit ✅ (${proof.tag} ${toHex(signer).slice(0, 18)}…)`);
            } else {
                const errVal = submitResult.isErr() ? (submitResult.error as any) : 'unsupported version';
                setStatus(`Submit failed: ${errVal?.value?.reason ?? JSON.stringify(errVal)}`);
            }
        } catch (e) {
            setStatus(`SSS error: ${e instanceof Error ? e.message : String(e)}`);
        } finally {
            setBusy(false);
        }
    }

    async function testPgasReviveCall() {
        if (busy) return;
        if (!assetHubApi) {
            setStatus('Asset Hub client not initialized');
            return;
        }
        setBusy(true);
        setStatus('Building Revive call on Asset Hub…');
        try {
            const { signer } = await resolveProductAccountWithSigner();

            // Arbitrary contract call — does not need to succeed. Triggers
            // SponsorReviveCallsWithPgas on the host (chainId == AH, module == Revive).
            const tx = assetHubApi.tx.Revive.call({
                dest: ('0x' + '00'.repeat(20)) as `0x${string}`,
                value: 0n,
                weight_limit: { ref_time: 1n, proof_size: 1n },
                storage_deposit_limit: 1n,
                data: new Uint8Array(0),
            });

            setStatus('Awaiting signature (PGAS sponsor will run)…');
            const customSignedExtensions = {
                AsPgas: { value: undefined },
                AsRingAlias: { value: undefined },
            };
            tx.signSubmitAndWatch(signer, { customSignedExtensions }).subscribe({
                next: (event: TxEvent) => {
                    console.log(`Products Demo: Revive tx event: ${event.type}`);
                    if (event.type === 'broadcasted') {
                        setStatus('Revive tx broadcasted ✅ (sponsor passed)');
                        setBusy(false);
                    } else if (event.type === 'txBestBlocksState' && event.found) {
                        setStatus('Revive tx in block (sponsor passed)');
                        setBusy(false);
                    }
                },
                error: (e: unknown) => {
                    // Failure is expected — we only care that signing reached the sponsor.
                    setStatus(`Revive tx error (likely on-chain): ${e instanceof Error ? e.message : String(e)}`);
                    setBusy(false);
                },
            });
        } catch (e) {
            setStatus(`PGAS test error: ${e instanceof Error ? e.message : String(e)}`);
            setBusy(false);
        }
    }

    return (
        <Column
            padding={20}
            background={{ color: 'bg.surface.nested', shape: { tag: 'Rounded', value: 16 } }}
            horizontalAlignment="center"
        >
            <Text style="body.small.regular" color="fg.secondary">RFC-0010 Allowance Management</Text>
            <Spacer height={12} />
            <Text style="body.large.regular" color="fg.primary">{status}</Text>
            <Spacer height={16} />
            <Button text="Allocate Bulletin" variant="secondary" loading={busy} onClick={allocateBulletin} />
            <Spacer height={8} />
            <Button text="Allocate StatementStore" variant="secondary" loading={busy} onClick={allocateStatementStore} />
            <Spacer height={8} />
            <Button text="Allocate SmartContract(0)" variant="secondary" loading={busy} onClick={allocateSmartContract} />
            <Spacer height={8} />
            <Button text="Allocate All" variant="primary" loading={busy} onClick={allocateAll} />
            <Spacer height={16} />
            <Button text="Submit to Bulletin" variant="secondary" loading={busy} onClick={submitToBulletin} />
            <Spacer height={8} />
            <Button text="Authorize + Submit Statement" variant="secondary" loading={busy} onClick={authorizeAndSubmitStatement} />
            <Spacer height={8} />
            <Button text="Test PGAS (Revive on AH)" variant="secondary" loading={busy} onClick={testPgasReviveCall} />
        </Column>
    );
}

// ============================================================================
// User Identity Card (RFC-0014 host_get_user_id)
// ============================================================================

function UserIdentityCard() {
    const [username, setUsername] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);

    const handleGetUserId = () => {
        if (loading) return;

        setLoading(true);
        setError(null);
        setUsername(null);

        accountsProvider.getUserId().match(
            ({ primaryUsername }) => {
                setUsername(primaryUsername);
                console.log(`Products Demo: getUserId success - username: ${primaryUsername}`);
                setLoading(false);
            },
            (err) => {
                setError(`Error: ${err.name}`);
                console.log(`Products Demo: getUserId error: ${err.name}`);
                setLoading(false);
            },
        );
    };

    return (
        <Column
            padding={20}
            background={{ color: 'bg.surface.nested', shape: { tag: 'Rounded', value: 16 } }}
            horizontalAlignment="center"
        >
            <Text style="body.small.regular" color="fg.secondary">Get User ID (RFC-0014)</Text>
            <Spacer height={8} />
            {error != null
                ? <Text style="body.large.regular" color="fg.primary">{error}</Text>
                : username != null
                    ? (
                        <>
                            <Text style="body.small.regular" color="fg.secondary">Primary username</Text>
                            <Spacer height={4} />
                            <Text style="body.large.regular" color="fg.primary">{username}</Text>
                        </>
                    )
                    : <Text style="body.small.regular" color="fg.tertiary">Press button to fetch user ID</Text>
            }
            <Spacer height={16} />
            <Button
                text="Get User ID"
                variant="primary"
                loading={loading}
                onClick={handleGetUserId}
            />
        </Column>
    );
}

console.log('products_demo.tsx loaded');

# Building a Pocket card without deploying

Two loops for working on a Pocket card against a debug build of the app. Neither writes to a chain,
and neither needs a published manifest.

Both need a **debug build** (`assembleGpDebug` or `assembleVanillaDebug`, or the Firebase debug
distribution). They are off entirely on release builds.

## Where this works

Anywhere `adb` can reach the app: an emulator, or a physical device over USB or Wi-Fi debugging.
Nothing below is emulator-specific.

What it does **not** cover is a phone that only has the app from Firebase App Distribution, with no
`adb` connection. The tunnel below is what makes your dev server reachable, and there is no way to
set it up without `adb`. Until a browser-based preview exists, the fast loop needs a cable.

> **On the emulator, do not use `10.0.2.2`.** It is the usual alias for the host machine, but the
> app permits cleartext to loopback only, so a face served over `http://10.0.2.2:5173` is refused
> before it is fetched. Use `adb reverse` and `127.0.0.1` on the emulator exactly as on a phone.

## Before either loop

Serve your files from your machine and make them reachable from the device:

```sh
# anything that serves static files will do
npx serve -l 5173 .

# point the device's own loopback at your machine
adb reverse tcp:5173 tcp:5173
```

`127.0.0.1` is already permitted for cleartext in the app's network security config, so plain HTTP
over this tunnel works with no further setup. Any other host needs HTTPS.

A face file is a renderer tree in the generated TypeScript shape — `{ tag, value }` per variant,
PascalCase enum names. Two encoding rules catch most first attempts:

- **A `Padding` or `Margin` needs `top` and `end`.** They are a shorthand: `bottom` defaults to `top`
  and `start` defaults to `end`, so `{ "top": 16, "end": 16 }` is 16 all round. Omitting `end` is
  rejected as a missing field.
- **Sizes are non-negative whole numbers.** `16.5` is refused, not rounded.

The smallest face that draws:

```json
{
  "tag": "Column",
  "value": {
    "modifiers": [{ "tag": "Padding", "value": { "top": 16, "end": 16 } }],
    "props": {},
    "children": [
      {
        "tag": "Text",
        "value": {
          "modifiers": [],
          "props": { "style": "TitleMediumRegular", "color": "FgPrimary" },
          "children": [{ "tag": "String", "value": { "text": "Loyalty" } }]
        }
      }
    ]
  }
}
```

## Loop A — how does the face look

Answers the visual question, with no worker, no manifest and no product.

1. Open the app's debug menu and choose **Pocket face preview**.
2. Enter your face's URL, for example `http://127.0.0.1:5173/faces/loyalty.json`, and press **Draw**.
3. Edit the file on your machine, press **Draw** again.

What it proves: the face decodes, the tokens resolve, the text fits, and the layout holds at the real
card frame in both themes. It draws through the same decoder and the same renderer the Pocket tab
uses, so it cannot agree with a face the app would not draw.

Two things it does not show. The frame is the one the **approval sheet** gives a card — the Pocket
tab additionally lays the card on its own ground with a grain texture. And images do not resolve
here: an `Image` node names a Bulletin CID or a path inside a worker archive, and a loose file on a
dev server has neither, so it draws as empty space.

If the face is rejected, the message under the frame is the decoder's own, naming what it refused.

## Loop B — the whole card, live

Runs your real worker and streams a live face into a real card, with actions coming back.

1. Serve your worker bundle and your face from the same directory.
2. Debug menu → **Product bots**, then add or edit a product. Fill in:
   - the dotNS name, for example `humanity.paseo`
   - **Script URL** — `http://127.0.0.1:5173/worker.js`
   - **Pocket card id** — for example `loyalty`
   - **Pocket card title** — what the approval sheet calls it
   - **Pocket card face URL** — `http://127.0.0.1:5173/faces/loyalty.json`
3. Confirm. The card fields are optional; leaving the id or the face URL blank gives a worker with no
   card, exactly as before.
4. Follow the add link: `polkadotapp://<your product>.<tld>/-/pocket/add?card=loyalty`. `adb` can
   send it for you:

   ```sh
   adb shell am start -a android.intent.action.VIEW \
     -d "polkadotapp://humanity.paseo/-/pocket/add?card=loyalty"
   ```

5. The approval sheet shows the face from your URL. Approve it, and the card joins the Pocket tab.
6. From then on the face comes from your worker's `renderer.onRender` on the `PocketCard` context,
   not from the URL — the URL is only what the approval sheet draws.

What it proves: the manifest shape, the add flow, the live render stream, redraws in place, and
button presses arriving back in the worker.

### Things worth knowing

- **A published worker always wins.** If the product's dotNS record has a worker, that is what runs;
  the debug URL only fills in when there is none. Use a name with no published worker while
  iterating.
- **Changing the card means re-confirming the form.** The card rides on the resolved worker, and
  confirming is what invalidates that resolution.
- **A rejected card id reads as no card.** Ids are screened with the rules the core applies, so a bad
  one leaves the worker with no card rather than failing later. Check logcat for `pocket:` if a card
  you named does not appear.
- **Faces are capped at 256 KiB**, wherever they are served from.

## When you are ready to publish

Declare the card in the worker's manifest instead, and the debug fields stop being involved:

```json
{
  "$v": 1,
  "kind": "worker",
  "appVersion": [1, 0, 0],
  "entrypoint": "worker.js",
  "includes": { "chat": false, "pocket": true },
  "pocket": {
    "cards": [{ "id": "loyalty", "title": "Loyalty", "preview": "faces/loyalty.json" }]
  }
}
```

`preview` is now a path **inside the worker archive**, not a URL — a published manifest cannot name a
URL, by design. Publish with `bulletin-deploy`, which writes this as the `executable` record on
`worker.<product>.<tld>`.

Note that `bulletin-deploy` does not yet validate `pocket.cards`. A malformed card publishes cleanly
and is then dropped by the app with only a log line, so check the card appears after publishing.

### The pinned Humanity card

It is bound to `peopl.<tld>` and draws a face bundled in the APK until that product publishes a
worker card with the id `humanity`. Publish that record and the host boots the worker and streams its
face in place of the bundled one. Get the id wrong and the card silently keeps the bundled face.

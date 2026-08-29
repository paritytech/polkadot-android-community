package io.paritytech.polkadotapp.tools_integrity_impl.data.claim

import java.io.ByteArrayOutputStream

// Canonical CBOR (RFC 8949 §4.2.1) encoding of the `dub/poud/android/v1`
// device envelope. The backend rejects any other byte form, and the envelope
// signature covers exactly these bytes.
internal object DeviceEnvelopeEncoder {
    const val DOMAIN = "dub/poud/android/v1"

    private const val VERSION = 1
    private const val CHALLENGE_BYTES = 32
    private const val CANDIDATE_BYTES = 32
    private const val MIN_WIDEVINE_ID_BYTES = 1
    private const val MAX_WIDEVINE_ID_BYTES = 64
    private const val MAX_ENVELOPE_BYTES = 512

    private const val MAJOR_UINT = 0
    private const val MAJOR_BYTES = 2
    private const val MAJOR_TEXT = 3
    private const val MAJOR_MAP = 5

    fun encode(
        challenge: ByteArray,
        candidate: ByteArray,
        widevineId: ByteArray,
        level: Int
    ): ByteArray {
        require(challenge.size == CHALLENGE_BYTES) {
            "envelope challenge must be $CHALLENGE_BYTES bytes, got ${challenge.size}"
        }
        require(candidate.size == CANDIDATE_BYTES) {
            "envelope candidate must be $CANDIDATE_BYTES bytes, got ${candidate.size}"
        }
        require(widevineId.size in MIN_WIDEVINE_ID_BYTES..MAX_WIDEVINE_ID_BYTES) {
            "widevine id must be $MIN_WIDEVINE_ID_BYTES..$MAX_WIDEVINE_ID_BYTES bytes, got ${widevineId.size}"
        }
        require(level == WIDEVINE_LEVEL_L1 || level == WIDEVINE_LEVEL_L3) {
            "widevine level must be $WIDEVINE_LEVEL_L1 or $WIDEVINE_LEVEL_L3, got $level"
        }

        val out = ByteArrayOutputStream()
        out.head(MAJOR_MAP, 6)
        out.head(MAJOR_UINT, 0)
        out.text(DOMAIN)
        out.head(MAJOR_UINT, 1)
        out.head(MAJOR_UINT, VERSION)
        out.head(MAJOR_UINT, 2)
        out.bytes(challenge)
        out.head(MAJOR_UINT, 3)
        out.bytes(candidate)
        out.head(MAJOR_UINT, 4)
        out.bytes(widevineId)
        out.head(MAJOR_UINT, 5)
        out.head(MAJOR_UINT, level)

        val encoded = out.toByteArray()
        check(encoded.size <= MAX_ENVELOPE_BYTES) {
            "device envelope is ${encoded.size} bytes, expected at most $MAX_ENVELOPE_BYTES"
        }
        return encoded
    }

    private fun ByteArrayOutputStream.bytes(content: ByteArray) {
        head(MAJOR_BYTES, content.size)
        write(content)
    }

    private fun ByteArrayOutputStream.text(content: String) {
        val encoded = content.encodeToByteArray()
        head(MAJOR_TEXT, encoded.size)
        write(encoded)
    }

    // Shortest-form head; no envelope field needs an argument above one byte.
    private fun ByteArrayOutputStream.head(major: Int, value: Int) {
        require(value in 0..0xFF) { "CBOR head argument out of range: $value" }
        val majorBits = major shl 5
        if (value < 24) {
            write(majorBits or value)
        } else {
            write(majorBits or 24)
            write(value)
        }
    }
}

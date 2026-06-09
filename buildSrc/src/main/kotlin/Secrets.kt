import java.util.Properties

fun Properties.readStringSecret(key: String) = "\"${readSecret(key)}\""

fun Properties.readSecret(secretName: String): String {
    val secret = readSecretOrNull(secretName)
    return if (secret.isNullOrEmpty()) secretNotFound(secretName) else secret
}

fun Properties.readSecretOrNull(secretName: String): String? {
    val secret = getProperty(secretName) ?: System.getenv(secretName)
    return secret?.takeIf { it.isNotEmpty() }
}

private fun secretNotFound(secretName: String): Nothing {
    throw NoSuchElementException("$secretName secret is not found in local.properties or environment variables")
}

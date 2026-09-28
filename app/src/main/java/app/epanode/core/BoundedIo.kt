package app.epanode.core

import java.io.InputStream
import java.io.ByteArrayOutputStream

/** API 29 compatible, bounded read for user-provided text and JSON. */
fun InputStream.readBounded(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
    val buffer = ByteArray(8192)
    var remaining = maxBytes
    while (remaining > 0) {
        val n = read(buffer, 0, minOf(remaining, buffer.size))
        if (n < 0) break
        output.write(buffer, 0, n); remaining -= n
    }
    return output.toByteArray()
}

package io.github.janetyulin.grounded.data.local

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * DataStore [Serializer] that stores one value as JSON. Unknown keys are ignored (see the injected
 * [Json]) so older app versions can read newer files. DataStore already runs reads and writes off
 * the main thread, so no dispatcher switch is needed here.
 */
class JsonSerializer<T>(private val json: Json, private val serializer: KSerializer<T>, override val defaultValue: T) :
    Serializer<T> {

    override suspend fun readFrom(input: InputStream): T = try {
        json.decodeFromString(serializer, input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unable to read ${serializer.descriptor.serialName}", e)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(json.encodeToString(serializer, t).encodeToByteArray())
        }
    }
}

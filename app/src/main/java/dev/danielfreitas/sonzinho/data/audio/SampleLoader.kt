package dev.danielfreitas.sonzinho.data.audio

import android.content.res.AssetManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.ByteOrder
import kotlin.math.abs

@Serializable
internal data class SoundManifest(
    val sampleRate: Int,
    val instruments: List<ManifestInstrument>,
)

@Serializable
internal data class ManifestInstrument(
    val id: String,
    val zones: List<ManifestZone>,
)

@Serializable
internal data class ManifestZone(
    val file: String,
    val root: Float,
)

internal class DecodedZone(
    val root: Float,
    val sampleRate: Int,
    val channels: Int,
    val pcm: ShortArray,
)

/** Como cada pasta de amostras vira um instrumento do motor (slot, ganho e liberação). */
internal enum class SampledSpec(
    val folder: String,
    val slot: Int,
    val gain: Float,
    val releaseSeconds: Float,
) {
    PIANO("piano", 0, 1.0f, 0.6f),
    MARIMBA("marimba", 1, 0.95f, 0.3f),
    GLOCKENSPIEL("glockenspiel", 2, 1.0f, 0.8f),
    XYLOPHONE("xylophone", 3, 1.1f, 0.25f),
    VIBRAPHONE("vibraphone", 4, 1.3f, 0.9f),
    KALIMBA("kalimba", 5, 1.2f, 0.5f),
    HARP("harp", 6, 1.2f, 0.7f),
    CHIMES("chimes", 7, 1.5f, 1.0f),
    KALIMBA_SOFT("kalimba_soft", 8, 1.2f, 0.5f),
    MUSIC_BOX("musicbox", 9, 1.1f, 0.6f),
    RECORDER("recorder", 10, 1.0f, 0.12f),
}

/**
 * Decodifica as amostras Opus/WebM embarcadas para PCM 16 bits em memória, uma única vez,
 * para que tocar uma nota seja só ler memória (latência mínima, nada de E/S no toque).
 */
internal class SampleLoader(
    private val assets: AssetManager,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun readManifest(): SoundManifest =
        assets.open("$ROOT/manifest.json").use { stream ->
            json.decodeFromString(SoundManifest.serializer(), stream.readBytes().decodeToString())
        }

    fun decode(zone: ManifestZone): DecodedZone {
        val fd = assets.openFd("$ROOT/${zone.file}")
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            extractor.selectTrack(0)
            val format = extractor.getTrackFormat(0)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Sem MIME em ${zone.file}")
            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                return decodeAll(extractor, codec, format, zone)
            } finally {
                codec.stop()
                codec.release()
            }
        } finally {
            extractor.release()
            fd.close()
        }
    }

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    private fun decodeAll(
        extractor: MediaExtractor,
        codec: MediaCodec,
        inputFormat: MediaFormat,
        zone: ManifestZone,
    ): DecodedZone {
        var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val out = ShortArrayBuilder()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    val buffer = codec.getInputBuffer(index)!!
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = codec.outputFormat
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }

                outIndex >= 0 -> {
                    if (info.size > 0) {
                        val buffer = codec.getOutputBuffer(outIndex)!!
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        out.append(buffer.order(ByteOrder.nativeOrder()).asShortBuffer())
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
        val pcm = trimLeadingSilence(out.toArray(), channels)
        return DecodedZone(zone.root, sampleRate, channels, pcm)
    }

    companion object {
        const val ROOT = "sounds"
        private const val TIMEOUT_US = 10_000L
        private const val SILENCE = 6
        private const val MAX_TRIM_FRAMES = 960 // 20 ms a 48 kHz

        /** Remove o silêncio residual do pré-skip do Opus: o ataque começa já no primeiro quadro. */
        fun trimLeadingSilence(
            pcm: ShortArray,
            channels: Int,
        ): ShortArray {
            var frame = 0
            val frames = pcm.size / channels
            while (frame < frames && frame < MAX_TRIM_FRAMES) {
                var loud = false
                for (c in 0 until channels) if (abs(pcm[frame * channels + c].toInt()) > SILENCE) loud = true
                if (loud) break
                frame++
            }
            return if (frame == 0) pcm else pcm.copyOfRange(frame * channels, pcm.size)
        }
    }
}

private class ShortArrayBuilder {
    private var data = ShortArray(48_000 * 2)
    private var size = 0

    fun append(buffer: java.nio.ShortBuffer) {
        val n = buffer.remaining()
        if (size + n > data.size) data = data.copyOf(maxOf(data.size * 2, size + n))
        buffer.get(data, size, n)
        size += n
    }

    fun toArray(): ShortArray = data.copyOf(size)
}

package com.sonicsight.app.video

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavWriter {
    fun writeMono16(file: File, samples: FloatArray, sampleRate: Int) {
        val dataBytes = samples.size * 2
        FileOutputStream(file).use { out ->
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray(Charsets.US_ASCII))
            h.putInt(36 + dataBytes)
            h.put("WAVE".toByteArray(Charsets.US_ASCII))
            h.put("fmt ".toByteArray(Charsets.US_ASCII))
            h.putInt(16)
            h.putShort(1)
            h.putShort(1)
            h.putInt(sampleRate)
            h.putInt(sampleRate * 2)
            h.putShort(2)
            h.putShort(16)
            h.put("data".toByteArray(Charsets.US_ASCII))
            h.putInt(dataBytes)
            out.write(h.array())
            val pcm = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) {
                if (pcm.remaining() < 2) {
                    out.write(pcm.array(), 0, pcm.position())
                    pcm.clear()
                }
                pcm.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            }
            if (pcm.position() > 0) out.write(pcm.array(), 0, pcm.position())
        }
    }
}

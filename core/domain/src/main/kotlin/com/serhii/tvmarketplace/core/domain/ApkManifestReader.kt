package com.serhii.tvmarketplace.core.domain

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.DataFormatException
import java.util.zip.Inflater

// reads an APK's package name from three slices of the file, so an app installed before it was tracked can be
// recognized without downloading its APK: the zip's end, its central directory, and the manifest entry
object ApkManifestReader {

  // the end of central directory record is 22 bytes, plus a comment of up to 64 KiB
  const val TAIL_SIZE = 22 + 0xFFFF

  private const val EOCD_SIGNATURE = 0x06054b50
  private const val CENTRAL_SIGNATURE = 0x02014b50
  private const val LOCAL_SIGNATURE = 0x04034b50
  private const val LOCAL_HEADER_SIZE = 30
  private const val STORED = 0
  private const val DEFLATED = 8
  private const val MANIFEST = "AndroidManifest.xml"

  // a binary manifest stays far below this; anything bigger isn't one
  private const val MAX_MANIFEST_SIZE = 4 * 1024 * 1024

  data class Slice(
    val offset: Long,
    val size: Long,
  )

  data class Entry(
    val localHeaderOffset: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val method: Int,
    val nameLength: Int,
  ) {
    // the local header's own extra field isn't known before it's read: some room for it
    val fetchSize: Long get() = LOCAL_HEADER_SIZE + nameLength + EXTRA_ALLOWANCE + compressedSize

    companion object {
      const val EXTRA_ALLOWANCE = 1024
    }
  }

  // tail: the last bytes of the file, at most TAIL_SIZE; where the central directory is
  fun centralDirectory(
    tail: ByteArray,
    fileSize: Long,
  ): Slice? {
    val buffer = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN)
    for (i in tail.size - 22 downTo 0) {
      if (buffer.getInt(i) != EOCD_SIGNATURE) continue
      val size = buffer.getInt(i + 12).toLong() and 0xFFFFFFFFL
      val offset = buffer.getInt(i + 16).toLong() and 0xFFFFFFFFL
      // zip64 marks these as 0xFFFFFFFF; APKs from GitHub releases stay far below 4 GiB
      if (offset + size > fileSize) return null
      return Slice(offset, size)
    }
    return null
  }

  fun manifestEntry(centralDirectory: ByteArray): Entry? {
    val buffer = ByteBuffer.wrap(centralDirectory).order(ByteOrder.LITTLE_ENDIAN)
    var i = 0
    while (i + 46 <= centralDirectory.size && buffer.getInt(i) == CENTRAL_SIGNATURE) {
      val method = buffer.getShort(i + 10).toInt() and 0xFFFF
      val compressed = buffer.getInt(i + 20).toLong() and 0xFFFFFFFFL
      val uncompressed = buffer.getInt(i + 24).toLong() and 0xFFFFFFFFL
      val nameLength = buffer.getShort(i + 28).toInt() and 0xFFFF
      val extraLength = buffer.getShort(i + 30).toInt() and 0xFFFF
      val commentLength = buffer.getShort(i + 32).toInt() and 0xFFFF
      val localOffset = buffer.getInt(i + 42).toLong() and 0xFFFFFFFFL
      if (i + 46 + nameLength > centralDirectory.size) return null
      val name = String(centralDirectory, i + 46, nameLength, Charsets.UTF_8)
      if (name == MANIFEST) return Entry(localOffset, compressed, uncompressed, method, nameLength)
      i += 46 + nameLength + extraLength + commentLength
    }
    return null
  }

  // localEntry: the file from the entry's local header on, at least its fetchSize or to the end of the file
  fun manifest(
    localEntry: ByteArray,
    entry: Entry,
  ): ByteArray? {
    if (entry.uncompressedSize > MAX_MANIFEST_SIZE) return null
    val buffer = ByteBuffer.wrap(localEntry).order(ByteOrder.LITTLE_ENDIAN)
    if (localEntry.size < LOCAL_HEADER_SIZE || buffer.getInt(0) != LOCAL_SIGNATURE) return null
    val nameLength = buffer.getShort(26).toInt() and 0xFFFF
    val extraLength = buffer.getShort(28).toInt() and 0xFFFF
    val start = LOCAL_HEADER_SIZE + nameLength + extraLength
    val end = start + entry.compressedSize
    if (end > localEntry.size) return null
    val data = localEntry.copyOfRange(start, end.toInt())
    return when (entry.method) {
      STORED -> data
      DEFLATED -> inflate(data, entry.uncompressedSize.toInt())
      else -> null
    }
  }

  private fun inflate(
    data: ByteArray,
    size: Int,
  ): ByteArray? {
    val inflater = Inflater(true)
    return try {
      inflater.setInput(data)
      val out = ByteArray(size)
      var read = 0
      while (read < size && !inflater.finished()) {
        val n = inflater.inflate(out, read, size - read)
        if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
        read += n
      }
      if (read == size) out else null
    } catch (_: DataFormatException) {
      null
    } finally {
      inflater.end()
    }
  }

  // the package attribute of the manifest's root element, from Android's binary XML
  fun packageName(binaryXml: ByteArray): String? = try {
    BinaryXml(binaryXml).rootAttribute("package")
  } catch (_: IndexOutOfBoundsException) {
    null
  } catch (_: IllegalArgumentException) {
    null
  }

  private class BinaryXml(bytes: ByteArray) {
    private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private var strings: List<String> = emptyList()

    fun rootAttribute(attribute: String): String? {
      require(buffer.getShort(0).toInt() == RES_XML_TYPE)
      var chunk = buffer.getShort(2).toInt() and 0xFFFF
      while (chunk + 8 <= buffer.limit()) {
        val type = buffer.getShort(chunk).toInt() and 0xFFFF
        val size = buffer.getInt(chunk + 4)
        require(size > 0)
        when (type) {
          RES_STRING_POOL_TYPE -> strings = stringPool(chunk)
          RES_XML_START_ELEMENT_TYPE -> return attribute(chunk, attribute)
        }
        chunk += size
      }
      return null
    }

    private fun attribute(
      chunk: Int,
      wanted: String,
    ): String? {
      val body = chunk + (buffer.getShort(chunk + 2).toInt() and 0xFFFF)
      val attributeStart = buffer.getShort(body + 8).toInt() and 0xFFFF
      val attributeSize = buffer.getShort(body + 10).toInt() and 0xFFFF
      val count = buffer.getShort(body + 12).toInt() and 0xFFFF
      for (i in 0 until count) {
        val at = body + attributeStart + i * attributeSize
        val name = strings.getOrNull(buffer.getInt(at + 4)) ?: continue
        if (name != wanted) continue
        val raw = buffer.getInt(at + 8)
        val dataType = buffer.get(at + 15).toInt() and 0xFF
        val data = buffer.getInt(at + 16)
        return strings.getOrNull(
          if (raw >= 0) {
            raw
          } else if (dataType == TYPE_STRING) {
            data
          } else {
            -1
          },
        )
      }
      return null
    }

    private fun stringPool(chunk: Int): List<String> {
      val headerSize = buffer.getShort(chunk + 2).toInt() and 0xFFFF
      val count = buffer.getInt(chunk + 8)
      val utf8 = buffer.getInt(chunk + 16) and UTF8_FLAG != 0
      val stringsStart = chunk + buffer.getInt(chunk + 20)
      return List(count) { index ->
        val at = stringsStart + buffer.getInt(chunk + headerSize + index * 4)
        if (utf8) utf8String(at) else utf16String(at)
      }
    }

    private fun utf8String(at: Int): String {
      var i = at
      // the UTF-16 length first, then the byte length: each one or two bytes
      i += if (buffer.get(i).toInt() and 0x80 != 0) 2 else 1
      var length = buffer.get(i).toInt() and 0xFF
      if (length and 0x80 != 0) {
        length = ((length and 0x7F) shl 8) or (buffer.get(i + 1).toInt() and 0xFF)
        i += 2
      } else {
        i += 1
      }
      return String(buffer.array(), i, length, Charsets.UTF_8)
    }

    private fun utf16String(at: Int): String {
      var length = buffer.getShort(at).toInt() and 0xFFFF
      var i = at + 2
      if (length and 0x8000 != 0) {
        length = ((length and 0x7FFF) shl 16) or (buffer.getShort(at + 2).toInt() and 0xFFFF)
        i += 2
      }
      return String(buffer.array(), i, length * 2, Charsets.UTF_16LE)
    }
  }

  private const val RES_XML_TYPE = 0x0003
  private const val RES_STRING_POOL_TYPE = 0x0001
  private const val RES_XML_START_ELEMENT_TYPE = 0x0102
  private const val TYPE_STRING = 0x03
  private const val UTF8_FLAG = 1 shl 8
}

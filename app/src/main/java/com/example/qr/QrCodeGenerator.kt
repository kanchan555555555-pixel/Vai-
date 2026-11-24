package com.example.qr

/**
 * Pure-Kotlin ISO/IEC 18004 QR Code generator (Versions 1..6, Byte mode, ECC Level M).
 * Allows generating crisp scannable QR codes on Compose Canvas with zero internet or external libs.
 */
object QrCodeGenerator {

    data class QrMatrix(val size: Int, val modules: BooleanArray) {
        operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]
    }

    // Version specs for ECC Level M (index 1..6): totalCodewords, dataCodewords, eccPerBlock, blocks
    private data class VersionSpec(
        val version: Int,
        val size: Int,
        val dataBytes: Int,
        val eccPerBlock: Int,
        val numBlocks: Int,
        val alignCenter: Int?
    )

    private val specs = listOf(
        VersionSpec(1, 21, 16, 10, 1, null),
        VersionSpec(2, 25, 28, 16, 1, 18),
        VersionSpec(3, 29, 44, 26, 1, 22),
        VersionSpec(4, 33, 64, 18, 2, 26),
        VersionSpec(5, 37, 86, 24, 2, 30),
        VersionSpec(6, 41, 108, 16, 4, 34)
    )

    private val gfExp = IntArray(512)
    private val gfLog = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            gfExp[i] = x
            gfLog[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) {
            gfExp[i] = gfExp[i - 255]
        }
    }

    fun encode(text: String): QrMatrix {
        val bytes = text.toByteArray(Charsets.UTF_8)
        // Byte mode header is 4 bits + 8 bits length = 12 bits (1.5 bytes) -> needs <= dataBytes - 2
        val spec = specs.firstOrNull { bytes.size + 2 <= it.dataBytes }
            ?: specs.last()
        val payload = if (bytes.size + 2 > spec.dataBytes) {
            bytes.copyOf(spec.dataBytes - 2)
        } else {
            bytes
        }

        val dataCodewords = encodeDataBits(payload, spec.dataBytes)
        val finalCodewords = addErrorCorrection(dataCodewords, spec)

        val size = spec.size
        val matrix = BooleanArray(size * size)
        val isFunction = BooleanArray(size * size)

        drawFunctionPatterns(size, spec.alignCenter, matrix, isFunction)
        placeDataBits(size, matrix, isFunction, finalCodewords)

        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        for (mask in 0..7) {
            val candidate = matrix.copyOf()
            applyMask(size, candidate, isFunction, mask)
            drawFormatBits(size, candidate, mask)
            val penalty = computePenalty(size, candidate)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
            }
        }

        applyMask(size, matrix, isFunction, bestMask)
        drawFormatBits(size, matrix, bestMask)
        return QrMatrix(size, matrix)
    }

    private fun encodeDataBits(bytes: ByteArray, capacityBytes: Int): IntArray {
        val bits = ArrayList<Int>(capacityBytes * 8)
        fun appendBits(value: Int, length: Int) {
            for (i in length - 1 downTo 0) {
                bits.add((value ushr i) and 1)
            }
        }

        // Mode indicator: Byte = 0100
        appendBits(0b0100, 4)
        // Char count indicator (8 bits for versions 1..9)
        appendBits(bytes.size, 8)
        for (b in bytes) {
            appendBits(b.toInt() and 0xFF, 8)
        }

        val totalBits = capacityBytes * 8
        val terminatorLen = minOf(4, totalBits - bits.size)
        repeat(terminatorLen) { bits.add(0) }
        while (bits.size % 8 != 0) bits.add(0)

        val padBytes = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (bits.size < totalBits) {
            appendBits(padBytes[padIdx % 2], 8)
            padIdx++
        }

        return IntArray(capacityBytes) { i ->
            var v = 0
            for (b in 0 until 8) {
                v = (v shl 1) or bits[i * 8 + b]
            }
            v
        }
    }

    private fun addErrorCorrection(data: IntArray, spec: VersionSpec): IntArray {
        val blocks = spec.numBlocks
        val blockLen = data.size / blocks
        val gen = rsGeneratorPoly(spec.eccPerBlock)

        val dataBlocks = Array(blocks) { b ->
            data.copyOfRange(b * blockLen, (b + 1) * blockLen)
        }
        val eccBlocks = Array(blocks) { b ->
            rsComputeRemainder(dataBlocks[b], gen, spec.eccPerBlock)
        }

        val out = IntArray(data.size + blocks * spec.eccPerBlock)
        var idx = 0
        for (i in 0 until blockLen) {
            for (b in 0 until blocks) out[idx++] = dataBlocks[b][i]
        }
        for (i in 0 until spec.eccPerBlock) {
            for (b in 0 until blocks) out[idx++] = eccBlocks[b][i]
        }
        return out
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return gfExp[gfLog[a] + gfLog[b]]
    }

    private fun rsGeneratorPoly(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            val root = gfExp[i]
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor gfMul(poly[j], root)
            }
            poly = next
        }
        return poly
    }

    private fun rsComputeRemainder(data: IntArray, gen: IntArray, eccLen: Int): IntArray {
        val rem = IntArray(eccLen)
        for (b in data) {
            val factor = b xor rem[0]
            System.arraycopy(rem, 1, rem, 0, eccLen - 1)
            rem[eccLen - 1] = 0
            for (i in 0 until eccLen) {
                rem[i] = rem[i] xor gfMul(gen[i + 1], factor)
            }
        }
        return rem
    }

    private fun setModule(size: Int, matrix: BooleanArray, isFunc: BooleanArray, x: Int, y: Int, dark: Boolean) {
        if (x in 0 until size && y in 0 until size) {
            val idx = y * size + x
            matrix[idx] = dark
            isFunc[idx] = true
        }
    }

    private fun drawFinderPattern(size: Int, matrix: BooleanArray, isFunc: BooleanArray, cx: Int, cy: Int) {
        for (dy in -4..4) {
            for (dx in -4..4) {
                val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                val x = cx + dx
                val y = cy + dy
                if (x in 0 until size && y in 0 until size) {
                    val dark = dist != 2 && dist != 4
                    setModule(size, matrix, isFunc, x, y, dark)
                }
            }
        }
    }

    private fun drawAlignmentPattern(size: Int, matrix: BooleanArray, isFunc: BooleanArray, cx: Int, cy: Int) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                setModule(size, matrix, isFunc, cx + dx, cy + dy, dist != 1)
            }
        }
    }

    private fun drawFunctionPatterns(
        size: Int,
        alignCenter: Int?,
        matrix: BooleanArray,
        isFunc: BooleanArray
    ) {
        // Timing patterns
        for (i in 0 until size) {
            setModule(size, matrix, isFunc, 6, i, i % 2 == 0)
            setModule(size, matrix, isFunc, i, 6, i % 2 == 0)
        }
        // Finder patterns
        drawFinderPattern(size, matrix, isFunc, 3, 3)
        drawFinderPattern(size, matrix, isFunc, size - 4, 3)
        drawFinderPattern(size, matrix, isFunc, 3, size - 4)

        // Alignment pattern (versions 2..6 have single center coordinate)
        if (alignCenter != null) {
            drawAlignmentPattern(size, matrix, isFunc, alignCenter, alignCenter)
        }

        // Format info reservation
        for (i in 0..8) {
            if (i != 6) {
                setModule(size, matrix, isFunc, 8, i, false)
                setModule(size, matrix, isFunc, i, 8, false)
            }
        }
        for (i in size - 8 until size) {
            setModule(size, matrix, isFunc, 8, i, false)
            setModule(size, matrix, isFunc, i, 8, false)
        }
        // Dark module
        setModule(size, matrix, isFunc, 8, size - 8, true)
    }

    private fun placeDataBits(size: Int, matrix: BooleanArray, isFunc: BooleanArray, codewords: IntArray) {
        var bitIdx = 0
        val totalBits = codewords.size * 8
        var up = true
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right--
            for (vert in 0 until size) {
                val y = if (up) size - 1 - vert else vert
                for (j in 0..1) {
                    val x = right - j
                    val idx = y * size + x
                    if (!isFunc[idx]) {
                        val dark = if (bitIdx < totalBits) {
                            val cw = codewords[bitIdx ushr 3]
                            ((cw ushr (7 - (bitIdx and 7))) and 1) != 0
                        } else {
                            false
                        }
                        matrix[idx] = dark
                        bitIdx++
                    }
                }
            }
            up = !up
            right -= 2
        }
    }

    private fun applyMask(size: Int, matrix: BooleanArray, isFunc: BooleanArray, mask: Int) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                val idx = y * size + x
                if (isFunc[idx]) continue
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> ((y / 2) + (x / 3)) % 2 == 0
                    5 -> ((x * y) % 2) + ((x * y) % 3) == 0
                    6 -> (((x * y) % 2) + ((x * y) % 3)) % 2 == 0
                    7 -> (((x + y) % 2) + ((x * y) % 3)) % 2 == 0
                    else -> false
                }
                if (invert) matrix[idx] = !matrix[idx]
            }
        }
    }

    private fun drawFormatBits(size: Int, matrix: BooleanArray, mask: Int) {
        // ECC Level M = 00
        val data = (0b00 shl 3) or mask
        var rem = data
        for (i in 0 until 10) {
            rem = (rem shl 1) xor (((rem ushr 9) and 1) * 0x537)
        }
        val bits = ((data shl 10) or rem) xor 0x5412

        fun bit(i: Int) = ((bits ushr i) and 1) != 0

        for (i in 0..5) matrix[i * size + 8] = bit(i)
        matrix[7 * size + 8] = bit(6)
        matrix[8 * size + 8] = bit(7)
        matrix[8 * size + 7] = bit(8)
        for (i in 9..14) matrix[8 * size + (14 - i)] = bit(i)

        for (i in 0..7) matrix[8 * size + (size - 1 - i)] = bit(i)
        for (i in 8..14) matrix[(size - 15 + i) * size + 8] = bit(i)
        matrix[(size - 8) * size + 8] = true
    }

    private fun computePenalty(size: Int, matrix: BooleanArray): Int {
        var penalty = 0
        // Rule 1: adjacent modules in row/col
        for (y in 0 until size) {
            var runColor = false
            var runLen = 0
            for (x in 0 until size) {
                val c = matrix[y * size + x]
                if (x == 0 || c != runColor) {
                    runColor = c
                    runLen = 1
                } else {
                    runLen++
                    if (runLen == 5) penalty += 3 else if (runLen > 5) penalty++
                }
            }
        }
        for (x in 0 until size) {
            var runColor = false
            var runLen = 0
            for (y in 0 until size) {
                val c = matrix[y * size + x]
                if (y == 0 || c != runColor) {
                    runColor = c
                    runLen = 1
                } else {
                    runLen++
                    if (runLen == 5) penalty += 3 else if (runLen > 5) penalty++
                }
            }
        }
        // Rule 2: 2x2 blocks
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val c = matrix[y * size + x]
                if (c == matrix[y * size + x + 1] &&
                    c == matrix[(y + 1) * size + x] &&
                    c == matrix[(y + 1) * size + x + 1]
                ) {
                    penalty += 3
                }
            }
        }
        return penalty
    }
}

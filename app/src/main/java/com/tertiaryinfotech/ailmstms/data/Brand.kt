package com.tertiaryinfotech.ailmstms.data

import kotlin.math.pow

/** The three brand tones the theme is built from, as 0xRRGGBB values. */
data class BrandPalette(val primary: Int, val deep: Int, val onDark: Int)

object Brand {
    const val BLUE = 0x0A3D8F
    const val NAVY = 0x051C47
    const val LIGHT_BLUE = 0x8AB4F8
    private const val DARK_SURFACE = 0x0A1020
    val DEFAULT = BrandPalette(BLUE, NAVY, LIGHT_BLUE)

    fun parseHex(hex: String?): Int? =
        hex?.trim()?.takeIf { Regex("^#[0-9a-fA-F]{6}$").matches(it) }?.substring(1)?.toInt(16)

    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(rgb: Int): Double =
        0.2126 * channel(rgb shr 16 and 0xFF) + 0.7152 * channel(rgb shr 8 and 0xFF) + 0.0722 * channel(rgb and 0xFF)

    /** WCAG contrast ratio between two colours (1.0 to 21.0). */
    fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    fun blend(from: Int, to: Int, amount: Double): Int {
        fun mix(shift: Int): Int {
            val a = from shr shift and 0xFF
            val b = to shr shift and 0xFF
            return (a + (b - a) * amount).toInt().coerceIn(0, 255)
        }
        return (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /**
     * The academy's colour is used only when white text on it stays readable (WCAG AA, 4.5:1)
     * and it is not a near-grey; anything else keeps the fixed TIA blue. The dark-mode tone is
     * the same hue lightened until it reads clearly on the dark surface.
     */
    fun palette(hex: String?): BrandPalette {
        val color = parseHex(hex) ?: return DEFAULT
        val channels = listOf(color shr 16 and 0xFF, color shr 8 and 0xFF, color and 0xFF)
        if (channels.max() - channels.min() < 40) return DEFAULT
        if (contrast(color, 0xFFFFFF) < 4.5) return DEFAULT
        var light = color
        var step = 0.3
        while (contrast(light, DARK_SURFACE) < 7.0 && step <= 0.9) {
            light = blend(color, 0xFFFFFF, step)
            step += 0.1
        }
        if (contrast(light, DARK_SURFACE) < 7.0) return DEFAULT
        return BrandPalette(color, blend(color, 0x000000, 0.5), light)
    }
}

package com.tosin.docprocessor.domain.document.model

/**
 * Canonical unit helpers for both OOXML and ODF values.
 *
 * The domain model stores font sizes and geometric lengths as points (Double)
 * where a scalar is needed. These conversions are pure and unit-testable so that
 * mappers never embed magic divisors.
 */
object Units {
    const val POINTS_PER_INCH = 72.0
    const val TWIPS_PER_POINT = 20.0
    const val EMU_PER_INCH = 914_400.0

    fun twipsToPoints(twips: Int?): Double? = twips?.let { it / TWIPS_PER_POINT }

    fun halfPointsToPoints(halfPoints: Int?): Double? = halfPoints?.let { it / 2.0 }

    fun emuToPoints(emu: Long?): Double? = emu?.let { it / (EMU_PER_INCH / POINTS_PER_INCH) }

    fun hundredthsOfMmToPoints(value: Int?): Double? =
        value?.let { it / 100.0 / 25.4 * POINTS_PER_INCH }

    fun cmToPoints(cm: Double?): Double? = cm?.let { it / 2.54 * POINTS_PER_INCH }

    fun mmToPoints(mm: Double?): Double? = mm?.let { it / 25.4 * POINTS_PER_INCH }

    fun inchesToPoints(inches: Double?): Double? = inches?.let { it * POINTS_PER_INCH }

    fun picasToPoints(picas: Double?): Double? = picas?.let { it * 12.0 }

    /** ODF `fo:*` length strings such as `1.2cm`, `10pt`, `0.5in`, `2pc`. */
    fun parseOdfLength(raw: String?): Double? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val unit = value.takeLastWhile { it.isLetter() }.lowercase()
        val number = value.dropLast(unit.length).toDoubleOrNull() ?: return null
        return when (unit) {
            "pt" -> number
            "cm" -> cmToPoints(number)
            "mm" -> mmToPoints(number)
            "in" -> inchesToPoints(number)
            "pc" -> picasToPoints(number)
            "px" -> number * 0.75
            "", "twip" -> twipsToPoints(number.toInt())
            else -> null
        }
    }
}

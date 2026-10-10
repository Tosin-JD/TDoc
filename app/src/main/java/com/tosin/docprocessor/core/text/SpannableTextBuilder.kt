package com.tosin.docprocessor.core.text

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.StrikethroughSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.UnderlineSpan
import com.tosin.docprocessor.core.color.DocumentColorTokens
import com.tosin.docprocessor.core.color.DocumentTextColorResolver
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.domain.typography.FontFamilyResolver

/**
 * Builds a styled [CharSequence] from a paragraph's [TextSpan] list so that
 * `StaticLayout` paints every run with its own weight, slant, colour, size,
 * spacing and baseline shift.
 *
 * This replaces the previous behaviour where the whole paragraph inherited the
 * first run's `TextPaint`, which silently dropped bold lead-ins and mixed-size
 * runs. All geometry that the layout consumes (typeface, size, letter spacing)
 * is applied through metric-affecting spans.
 */
object SpannableTextBuilder {

    private const val TWIPS_PER_POINT = 20f

    fun build(
        spans: List<TextSpan>,
        baseSizePt: Float,
        fontResolver: FontFamilyResolver,
        tokens: DocumentColorTokens,
        isDarkMode: Boolean,
        sizeToPx: (Float) -> Int
    ): CharSequence {
        val builder = SpannableStringBuilder()
        spans.forEach { span ->
            val start = builder.length
            builder.append(span.text)
            val end = builder.length
            if (start == end) return@forEach

            val fontPt = span.fontSize?.toFloat()?.takeIf { it > 0f } ?: baseSizePt
            val typeface = AndroidTypefaceMapper.map(
                fontResolver.resolve(span.fontFamily),
                span.isBold,
                span.isItalic
            )
            val color = DocumentTextColorResolver.resolveTextColor(span.color, tokens, isDarkMode)
            val flag = SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE

            builder.setSpan(TypefaceSpanCompat(typeface), start, end, flag)
            builder.setSpan(ForegroundColorSpan(color), start, end, flag)
            builder.setSpan(AbsoluteSizeSpan(sizeToPx(fontPt), false), start, end, flag)

            span.characterSpacing
                ?.takeIf { it != 0 }
                ?.let { twips ->
                    val em = (twips / TWIPS_PER_POINT) / fontPt
                    builder.setSpan(LetterSpacingSpan(em), start, end, flag)
                }

            if (span.isUnderline) builder.setSpan(UnderlineSpan(), start, end, flag)
            if (span.isStrikethrough) builder.setSpan(StrikethroughSpan(), start, end, flag)
            if (span.isSuperscript) builder.setSpan(SuperscriptSpan(), start, end, flag)
            if (span.isSubscript) builder.setSpan(SubscriptSpan(), start, end, flag)

            DocumentTextColorResolver.parseColorString(span.highlightColor)?.let {
                builder.setSpan(BackgroundColorSpan(it), start, end, flag)
            }
        }
        return builder
    }
}

/** Applies a fully-resolved [Typeface] to a run (family + weight + slant). */
private class TypefaceSpanCompat(private val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) {
        tp.typeface = typeface
    }

    override fun updateMeasureState(p: TextPaint) {
        p.typeface = typeface
    }
}

/** Applies tracking in em units so it scales with the run's font size. */
private class LetterSpacingSpan(private val em: Float) : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) {
        tp.letterSpacing = em
    }

    override fun updateMeasureState(p: TextPaint) {
        p.letterSpacing = em
    }
}

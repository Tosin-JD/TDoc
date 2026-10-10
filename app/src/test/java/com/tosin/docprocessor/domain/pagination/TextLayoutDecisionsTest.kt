package com.tosin.docprocessor.domain.pagination

import android.text.Layout
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import org.junit.Assert.assertEquals
import org.junit.Test

class TextLayoutDecisionsTest {

    @Test
    fun `justified and distributed enable inter-word justification`() {
        assertEquals(Layout.JUSTIFICATION_MODE_INTER_WORD, justificationModeFor(ParagraphAlignment.JUSTIFIED))
        assertEquals(Layout.JUSTIFICATION_MODE_INTER_WORD, justificationModeFor(ParagraphAlignment.DISTRIBUTED))
    }

    @Test
    fun `other alignments do not enable justification`() {
        assertEquals(Layout.JUSTIFICATION_MODE_NONE, justificationModeFor(ParagraphAlignment.START))
        assertEquals(Layout.JUSTIFICATION_MODE_NONE, justificationModeFor(ParagraphAlignment.END))
        assertEquals(Layout.JUSTIFICATION_MODE_NONE, justificationModeFor(ParagraphAlignment.CENTER))
    }
}

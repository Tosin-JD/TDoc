package com.tosin.docprocessor.domain.document

import com.tosin.docprocessor.domain.document.model.InlineNode
import org.junit.Assert.assertEquals
import org.junit.Test

class FieldEvaluatorTest {

    @Test
    fun `evaluates deterministic fields and retains cached values otherwise`() {
        val context = FieldEvaluator.FieldContext(
            pageNumber = 3,
            pageCount = 12,
            sectionNumber = 2,
            sectionName = "Appendix",
            date = "2026-10-24",
            styleReferences = mapOf("Heading 1" to "Overview")
        )

        assertEquals("3", field("PAGE").evaluate(context))
        assertEquals("12", field("NUMPAGES").evaluate(context))
        assertEquals("2", field("SECTION").evaluate(context))
        assertEquals("Overview", field("STYLEREF", "STYLEREF \"Heading 1\"").evaluate(context))
        assertEquals("2026-10-24", field("DATE").evaluate(context))
        assertEquals("cached", field("REF", cached = "cached").evaluate(context))
    }

    @Test
    fun `extracts cached field result entries through inline containers`() {
        val field = InlineNode.Field(
            kind = "TOC",
            instruction = "TOC",
            result = listOf(
                InlineNode.Hyperlink(
                    children = listOf(
                        InlineNode.Text("Chapter one"),
                        InlineNode.Tab(),
                        InlineNode.Text("4")
                    )
                ),
                InlineNode.Break(com.tosin.docprocessor.domain.document.model.BreakKind.LINE),
                InlineNode.Text("Chapter two")
            )
        )
        assertEquals(listOf("Chapter one\t4", "Chapter two"), FieldEvaluator.cachedEntries(field))
    }

    private fun field(
        kind: String,
        instruction: String = kind,
        cached: String = "old"
    ): InlineNode.Field = InlineNode.Field(
        kind = kind,
        instruction = instruction,
        result = listOf(InlineNode.Text(cached))
    )

    private fun InlineNode.Field.evaluate(context: FieldEvaluator.FieldContext): String =
        (FieldEvaluator.evaluate(this, context).single() as InlineNode.Text).text
}

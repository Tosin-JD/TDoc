package com.tosin.docprocessor.data.parser

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.InputStream
import java.io.OutputStream

class DocxParser : DocumentParser {
    override fun parse(inputStream: InputStream): AnnotatedString {
        return try {
            val doc = XWPFDocument(inputStream)
            val builder = AnnotatedString.Builder()

            doc.paragraphs.forEach { paragraph ->
                paragraph.runs.forEach { run ->
                    val start = builder.length
                    builder.append(run.text() ?: "")
                    val end = builder.length

                    // Map POI styles to Compose SpanStyles
                    if (run.isBold) {
                        builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                    }
                    if (run.isItalic) {
                        builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                    }
                    if (run.fontSize > 0) {
                        builder.addStyle(SpanStyle(fontSize = run.fontSize.sp), start, end)
                    }
                }
                builder.append("\n") // New line after each paragraph
            }
            builder.toAnnotatedString()
        } catch (e: Exception) {
            AnnotatedString("Error parsing Word document: ${e.message}")
        }
    }

    override fun save(outputStream: OutputStream, content: AnnotatedString) {
        val doc = XWPFDocument()
        
        // This is a simplified version. A robust version would parse paragraphs.
        // For MVP-to-V2, we treat newlines as paragraph breaks and apply runs based on spans.
        
        val paragraphsTexts = content.text.split("\n")
        var currentOffset = 0
        
        for (paragraphText in paragraphsTexts) {
            val p = doc.createParagraph()
            
            // If the paragraph is empty, just create an empty run
            if (paragraphText.isEmpty()) {
                p.createRun()
                currentOffset += 1 // For the newline character
                continue
            }
            
            // Find all span styles that intersect with this paragraph
            val paragraphStart = currentOffset
            val paragraphEnd = currentOffset + paragraphText.length
            
            val spanStyles = content.spanStyles.filter { spanRange ->
                spanRange.start < paragraphEnd && spanRange.end > paragraphStart
            }
            
            // Simplified: if there are no span styles, create one run for the whole paragraph
            if (spanStyles.isEmpty()) {
                val run = p.createRun()
                run.setText(paragraphText)
            } else {
                // Advanced: Need to break paragraph into runs based on span boundaries
                // For a truly robust implementation, we would need an interval tree or similar.
                // Here, we'll iterate character by character or use boundaries.
                // Let's collect all boundaries within this paragraph
                val boundaries = mutableSetOf(paragraphStart, paragraphEnd)
                for (span in spanStyles) {
                    if (span.start > paragraphStart) boundaries.add(span.start)
                    if (span.end < paragraphEnd) boundaries.add(span.end)
                }
                
                val sortedBoundaries = boundaries.sorted()
                for (i in 0 until sortedBoundaries.size - 1) {
                    val start = sortedBoundaries[i]
                    val end = sortedBoundaries[i+1]
                    
                    if (start == end) continue
                    
                    val runText = content.text.substring(start, end)
                    val run = p.createRun()
                    run.setText(runText)
                    
                    // Apply styles active in this range
                    // Since a run represents text with identical formatting, we check the midpoint
                    val midpoint = start + (end - start) / 2
                    val activeSpans = spanStyles.filter { it.start <= midpoint && it.end > midpoint }
                    
                    for (span in activeSpans) {
                        if (span.item.fontWeight == FontWeight.Bold) run.isBold = true
                        if (span.item.fontStyle == FontStyle.Italic) run.isItalic = true
                        span.item.fontSize.takeIf { !it.isUnspecified }?.let {
                            if (it.isSp) {
                                run.fontSize = it.value.toInt()
                            }
                        }
                    }
                }
            }
            currentOffset += paragraphText.length + 1 // +1 for the newline
        }
        
        doc.write(outputStream)
    }
}

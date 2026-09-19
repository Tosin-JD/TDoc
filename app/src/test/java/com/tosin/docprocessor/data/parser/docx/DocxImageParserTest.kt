package com.tosin.docprocessor.data.parser.docx

import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.Document
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFPicture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openxmlformats.schemas.drawingml.x2006.picture.CTPicture
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class DocxImageParserTest {

    private val pngBytes = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01
    )

    private fun documentWithPicture(): Pair<XWPFDocument, XWPFPicture> {
        val document = XWPFDocument()
        val run = document.createParagraph().createRun()
        val picture = run.addPicture(
            ByteArrayInputStream(pngBytes),
            Document.PICTURE_TYPE_PNG,
            "pic.png",
            Units.toEMU(400.0),
            Units.toEMU(300.0)
        )
        return document to picture
    }

    @Test
    fun testExtractEmbeddedImage() {
        val (document, picture) = documentWithPicture()
        document.use {
            val cacheDir: File = Files.createTempDirectory("docx-img-test").toFile()
            val parsed = DocxImageParser(cacheDir).parse(picture)
                ?: throw AssertionError("expected Image element")

            assertTrue("source URI should point to a png cache file", parsed.sourceUri.endsWith(".png"))
            val cached = File(parsed.sourceUri)
            assertTrue("cached image file should exist", cached.isFile)
            assertTrue("extracted bytes should equal the source bytes", cached.readBytes().contentEquals(pngBytes))
        }
    }

    @Test
    fun testExtractImageUsesEmbeddedDescriptionAsAltText() {
        val (document, picture) = documentWithPicture()
        document.use {
            val cacheDir: File = Files.createTempDirectory("docx-img-alt").toFile()
            val parsed = DocxImageParser(cacheDir).parse(picture)
                ?: throw AssertionError("expected Image element")
            // XWPFRun.addPicture records the file name as the picture description.
            assertEquals("pic.png", parsed.altText)
        }
    }

    @Test
    fun testNullPictureDataReturnsNull() {
        val document = XWPFDocument()
        document.use {
            val run = document.createParagraph().createRun()
            val ctPicture = CTPicture.Factory.newInstance()
            ctPicture.addNewNvPicPr().addNewCNvPr().descr = ""
            val picture = XWPFPicture(ctPicture, run)
            val cacheDir: File = Files.createTempDirectory("docx-img-null").toFile()
            assertNull(DocxImageParser(cacheDir).parse(picture))
        }
    }
}
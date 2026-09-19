package com.tosin.docprocessor.data.parser.odt

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Hand-crafted, deterministic ODT fixtures for parser tests. Builds an ODT ZIP
 * in memory from explicit XML so tests never depend on external binary tools.
 */
object OdtTestFixtures {

    fun odtZip(
        contentXml: String,
        stylesXml: String? = null,
        metaXml: String? = null,
        images: Map<String, ByteArray> = emptyMap()
    ): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            val mimetype = "application/vnd.oasis.opendocument.text"
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write(mimetype.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("META-INF/manifest.xml"))
            zip.write(manifest(images).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("content.xml"))
            zip.write(contentXml.toByteArray())
            zip.closeEntry()
            if (stylesXml != null) {
                zip.putNextEntry(ZipEntry("styles.xml"))
                zip.write(stylesXml.toByteArray())
                zip.closeEntry()
            }
            if (metaXml != null) {
                zip.putNextEntry(ZipEntry("meta.xml"))
                zip.write(metaXml.toByteArray())
                zip.closeEntry()
            }
            images.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry("Pictures/$name"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    fun parseDocument(contentXml: String): org.w3c.dom.Element {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(contentXml.toByteArray())).documentElement
    }

    private fun manifest(images: Map<String, ByteArray>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2">""").append('\n')
        append(""" <manifest:file-entry manifest:full-path="/" manifest:media-type="application/vnd.oasis.opendocument.text" manifest:version="1.2"/>""").append('\n')
        append(""" <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>""").append('\n')
        if (images.isNotEmpty()) {
            append(""" <manifest:file-entry manifest:full-path="Pictures/""" + images.keys.first() + """" manifest:media-type="image/png"/>""").append('\n')
        }
        append("""</manifest:manifest>""")
    }

    fun contentXmlRoot(
        automaticStyles: String = "",
        bodyChildren: String
    ): String {
        val stylesTag = if (automaticStyles.isBlank()) {
            ""
        } else {
            "<office:automatic-styles>$automaticStyles</office:automatic-styles>"
        }
        return """<?xml version="1.0" encoding="UTF-8"?>
<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
 xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
 xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
 xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
 xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
 xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"
 xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
 xmlns:xlink="http://www.w3.org/1999/xlink"
 office:version="1.2">
$stylesTag
<office:body><office:text>
$bodyChildren
</office:text></office:body>
</office:document-content>"""
    }
}
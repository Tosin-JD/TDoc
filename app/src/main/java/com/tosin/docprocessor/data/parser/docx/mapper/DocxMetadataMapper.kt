package com.tosin.docprocessor.data.parser.docx.mapper

import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.descendantsNs
import com.tosin.docprocessor.data.parser.docx.opc.OpcPackage
import com.tosin.docprocessor.domain.document.model.DocumentMetadata
import org.w3c.dom.Document
import org.w3c.dom.Element

/** Reads `docProps/core.xml`, `docProps/app.xml` and custom properties. */
class DocxMetadataMapper(private val opc: OpcPackage) {

    fun parse(): DocumentMetadata {
        val core = documentFor("core-properties", "docProps/core.xml")
        val app = documentFor("extended-properties", "docProps/app.xml")
        val custom = documentFor("custom-properties", "docProps/custom.xml")

        val keywords = text(core, Ns.CP, "keywords")
            ?.split(',', ';')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        val appRaw = linkedMapOf<String, String>()
        app?.let { doc ->
            listOf("Application", "AppVersion", "Company", "Manager", "Template", "TotalTime", "Pages", "Words", "Characters", "Paragraphs", "Lines")
                .forEach { name -> text(doc, Ns.EP, name)?.let { appRaw[name] = it } }
        }

        val customProps = linkedMapOf<String, String>()
        custom?.documentElement?.let { root ->
            root.descendantsNs(Ns.CUST, "property").forEach { property ->
                val name = property.getAttribute("name").takeIf { it.isNotBlank() } ?: return@forEach
                property.textContent?.trim()?.takeIf { it.isNotEmpty() }?.let { customProps["custom.$name"] = it }
            }
        }

        return DocumentMetadata(
            title = text(core, Ns.DC, "title"),
            subject = text(core, Ns.DC, "subject"),
            creator = text(core, Ns.DC, "creator"),
            lastModifiedBy = text(core, Ns.CP, "lastModifiedBy"),
            description = text(core, Ns.DC, "description"),
            keywords = keywords,
            category = text(core, Ns.CP, "category"),
            contentStatus = text(core, Ns.CP, "contentStatus"),
            created = text(core, Ns.DCTERMS, "created"),
            modified = text(core, Ns.DCTERMS, "modified"),
            revision = text(core, Ns.CP, "revision"),
            application = text(app, Ns.EP, "Application"),
            company = text(app, Ns.EP, "Company"),
            template = text(app, Ns.EP, "Template"),
            words = text(app, Ns.EP, "Words")?.toIntOrNull(),
            pages = text(app, Ns.EP, "Pages")?.toIntOrNull(),
            characters = text(app, Ns.EP, "Characters")?.toIntOrNull(),
            paragraphs = text(app, Ns.EP, "Paragraphs")?.toIntOrNull(),
            language = text(core, Ns.DC, "language"),
            userDefined = customProps,
            raw = appRaw
        )
    }

    private fun documentFor(suffix: String, fallback: String): Document? {
        val path = opc.rootRelationships.values.firstOrNull {
            it.type?.substringAfterLast('/')?.equals(suffix, ignoreCase = true) == true
        }?.target ?: fallback
        return opc.document(path)
    }

    private fun text(document: Document?, namespace: String, local: String): String? {
        val root = document?.documentElement ?: return null
        return root.descendantsNs(namespace, local)
            .firstOrNull()
            ?.textContent
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}

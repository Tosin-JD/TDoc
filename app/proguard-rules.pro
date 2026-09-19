# TDoc release rules (R8).

# Apache POI + the generated OOXML types rely on heavy reflection, so keep them
# whole for MVP reliability. Size cost is acceptable.
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.** { *; }
-keep class org.apache.xmlbeans.** { *; }

# odfdom (ODT authoring) — kept whole for the same reflective reasons.
-keep class org.odftoolkit.** { *; }

# DocumentElement subtypes are referenced from exhaustive Compose `when` checks
# and must survive obfuscation under the same class names.
-keep class com.tosin.docprocessor.data.common.model.DocumentElement { *; }
-keep class com.tosin.docprocessor.data.common.model.DocumentElement$* { *; }

# Classes POI/XMLBeans reference that are not on the Android classpath and are
# only reached through code paths the app never exercises (slideshows, sheets,
# document signing, PDF rendering helpers, Saxon XPath, build tooling).
# Warn-and-skip keeps R8 from failing; nothing here is reachable at runtime.
-dontwarn com.github.javaparser.**
-dontwarn com.sun.org.apache.xml.internal.resolver.**
-dontwarn de.rototor.pdfbox.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.xml.crypto.**
-dontwarn javax.xml.stream.**
-dontwarn javax.swing.**
-dontwarn net.sf.saxon.**
-dontwarn org.apache.batik.**
-dontwarn org.apache.jcp.xml.**
-dontwarn org.apache.maven.**
-dontwarn org.apache.pdfbox.**
-dontwarn org.apache.tools.ant.**
-dontwarn org.apache.xml.security.**
-dontwarn org.bouncycastle.**
-dontwarn org.ietf.jgss.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn org.osgi.framework.**
-dontwarn org.w3c.dom.**
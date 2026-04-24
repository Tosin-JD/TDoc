package com.tosin.docprocessor.data.parser

import com.tosin.docprocessor.data.model.DocumentData
import java.io.File

interface DocumentParser {
    fun parse(file: File): DocumentData
    fun canParse(file: File): Boolean
}

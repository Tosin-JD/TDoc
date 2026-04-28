package com.tosin.docprocessor.ui.editor.engine

import com.tosin.docprocessor.data.common.model.DocumentData
import com.tosin.docprocessor.data.common.model.layout.PageDimensions
import com.tosin.docprocessor.data.common.model.layout.PageModel

interface Paginator {
    fun paginate(
        documentData: DocumentData,
        pageDimensions: PageDimensions
    ): List<PageModel>
}

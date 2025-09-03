package com.natinf.searchpro.util

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.natinf.searchpro.vm.UIInfraction
import java.io.File

object PdfExporter {
    fun exportInfraction(context: Context, item: UIInfraction): Uri {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = doc.startPage(pageInfo)
        val c = page.canvas
        val paint = android.graphics.Paint().apply { textSize = 14f }
        var y = 40f
        fun drawLine(text: String) { c.drawText(text, 40f, y, paint); y += 24f }
        drawLine("NATINF ${item.natinf}")
        drawLine(item.qualification)
        drawLine("Nature : ${item.nature}")
        if (item.articlesDef.isNotBlank()) drawLine("Définition : ${item.articlesDef}")
        if (item.articlesPeine.isNotBlank()) drawLine("Peines : ${item.articlesPeine}")
        doc.finishPage(page)

        val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val outFile = File(dir, "natinf_${item.natinf}.pdf")
        outFile.outputStream().use { doc.writeTo(it) }
        doc.close()
        return FileProvider.getUriForFile(context, "com.natinf.searchpro.fileprovider", outFile)
    }
}

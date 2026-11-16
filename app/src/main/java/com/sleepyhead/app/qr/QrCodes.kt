package com.sleepyhead.app.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.sleepyhead.app.data.Step
import java.io.FileOutputStream

object QrCodes {

    fun bitmap(text: String, size: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    /** Opens the Android print dialog with one page per step. "Save as PDF" also works there. */
    fun print(context: Context, steps: List<Step>) {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val attrs = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.NA_LETTER)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .build()
        pm.print("Sleepyhead QR codes", StepsAdapter(steps), attrs)
    }

    private class StepsAdapter(private val steps: List<Step>) : PrintDocumentAdapter() {
        private var attrs: PrintAttributes? = null

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            if (cancellationSignal.isCanceled) {
                callback.onLayoutCancelled(); return
            }
            attrs = newAttributes
            val info = PrintDocumentInfo.Builder("sleepyhead-qr-codes.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(steps.size)
                .build()
            callback.onLayoutFinished(info, newAttributes != oldAttributes)
        }

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal,
            callback: WriteResultCallback,
        ) {
            val media = attrs?.mediaSize ?: PrintAttributes.MediaSize.NA_LETTER
            // Page size in PostScript points (1/72 inch). Media size is in mils (1/1000 inch).
            val w = (media.widthMils / 1000f * 72f).toInt()
            val h = (media.heightMils / 1000f * 72f).toInt()
            val doc = PdfDocument()
            try {
                steps.forEachIndexed { i, step ->
                    if (cancellationSignal.isCanceled) {
                        callback.onWriteCancelled(); return
                    }
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, i).create())
                    drawPage(page.canvas, w.toFloat(), h.toFloat(), step, i + 1, steps.size)
                    doc.finishPage(page)
                }
                FileOutputStream(destination.fileDescriptor).use { doc.writeTo(it) }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            } finally {
                doc.close()
            }
        }

        private fun drawPage(c: Canvas, w: Float, h: Float, step: Step, n: Int, total: Int) {
            val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK; textSize = 34f; typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY; textSize = 20f; textAlign = Paint.Align.CENTER
            }
            c.drawText("Step $n of $total: ${step.title}", w / 2, 90f, title)
            c.drawText("Put this code in: ${step.place}", w / 2, 125f, sub)
            val qrSize = (w * 0.7f)
            val left = (w - qrSize) / 2
            val top = 160f
            c.drawBitmap(bitmap(step.code, 1024), null, RectF(left, top, left + qrSize, top + qrSize), null)
            c.drawText("Scan with Sleepyhead to finish this step.", w / 2, top + qrSize + 40f, sub)
            c.drawText("Sleepyhead", w / 2, h - 50f, sub)
        }
    }
}

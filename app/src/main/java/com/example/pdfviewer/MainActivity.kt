package com.example.pdfviewer

import android.app.ActionBar.LayoutParams
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// PDF sample code from
// https://medium.com/@chahat.jain0/rendering-a-pdf-document-in-android-activity-fragment-using-pdfrenderer-442462cb8f9a
// Issues about cache etc. are not at all obvious from documentation, so we should expect people to need this.
// We may wish to provide this code.
class MainActivity : AppCompatActivity() {
    val LOGNAME = "pdf_viewer"
    val FILENAME = "shannon1948.pdf"
    val FILERESID = R.raw.shannon1948

    // manage the pages of the PDF, see below
    lateinit var pdfRenderer: PdfRenderer
    lateinit var parcelFileDescriptor: ParcelFileDescriptor
    var currentPage: PdfRenderer.Page? = null

    // custom ImageView class that captures strokes and draws them over the image
    lateinit var pageImage: PDFimage
    lateinit var topic: TextView
    lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val layout = findViewById<LinearLayout>(R.id.pdfLayout)
        layout.isEnabled = true

        topic = findViewById<TextView>(R.id.topicText)
        topic.text = FILENAME

        pageImage = PDFimage(this)
        layout.addView(pageImage)
        pageImage.minimumWidth = 1000
        pageImage.minimumHeight = 2000

        // open page 0 of the PDF
        // it will be displayed as an image in the pageImage (above)
        try {
            openRenderer(this)
            if(savedInstanceState != null) {
                with(savedInstanceState) {
                    pageImage.pageNum = getInt("pageNum")
                    // pageImage.paths = getSerializable("paths") as ArrayList<ArrayList<Pair<Path?, Int>>>
                }
            }
            showPage(pageImage.pageNum - 1)
            status = findViewById<TextView>(R.id.statusText)
            status.text = "Page ".plus(pageImage.pageNum).plus("/").plus(pdfRenderer.pageCount)
        } catch (exception: IOException) {
            Log.d(LOGNAME, "Error opening PDF")
        }

        val buttonLeft = findViewById<Button>(R.id.button_left)
        buttonLeft.setOnClickListener {
            if(pageImage.pageNum > 1) {
                pageImage.pageNum--
                showPage(pageImage.pageNum - 1)
                status.text = "Page ".plus(pageImage.pageNum).plus("/").plus(pdfRenderer.pageCount)
            }
        }
        val buttonRight = findViewById<Button>(R.id.button_right)
        buttonRight.setOnClickListener {
            if(pageImage.pageNum < pdfRenderer.pageCount) {
                pageImage.pageNum++
                showPage(pageImage.pageNum - 1)
                status.text = "Page ".plus(pageImage.pageNum).plus("/").plus(pdfRenderer.pageCount)
            }
        }

        val buttonDraw = findViewById<ToggleButton>(R.id.button_draw)
        buttonDraw.setOnClickListener {
            pageImage.draw = true
            pageImage.yellow = false
            pageImage.eraser = false
            pageImage.mouse = false
        }
        val buttonYellow = findViewById<ToggleButton>(R.id.button_yellow)
        buttonYellow.setOnClickListener {
            pageImage.draw = false
            pageImage.yellow = true
            pageImage.eraser = false
            pageImage.mouse = false
        }
        val buttonEraser = findViewById<ToggleButton>(R.id.button_eraser)
        buttonEraser.setOnClickListener {
            pageImage.draw = false
            pageImage.yellow = false
            pageImage.eraser = true
            pageImage.mouse = false
        }
        val buttonMouse = findViewById<ToggleButton>(R.id.button_mouse)
        buttonMouse.setOnClickListener {
            pageImage.draw = false
            pageImage.yellow = false
            pageImage.eraser = false
            pageImage.mouse = true
        }

        val buttonUndo = findViewById<Button>(R.id.button_undo)
        buttonUndo.setOnClickListener {
            pageImage.undo()
        }

        val buttonRedo = findViewById<Button>(R.id.button_redo)
        buttonRedo.setOnClickListener {
            pageImage.redo()
        }
    }

    override fun onStart() {
        super.onStart()
        println("onStart")
    }

    override fun onResume() {
        super.onResume()
        println("onResume")
    }

    override fun onPause() {
        super.onPause()
        println("onPause")
    }

    override fun onStop() {
        super.onStop()
        println("onStop")
//        try {
//            closeRenderer()
//        } catch (ex: IOException) {
//            Log.d(LOGNAME, "Unable to close PDF renderer")
//        }
    }

    override fun onDestroy() {
        super.onDestroy()
        println("onDestroy")
    }

    @Throws(IOException::class)
    private fun openRenderer(context: Context) {
        // In this sample, we read a PDF from the assets directory.
        val file = File(context.cacheDir, FILENAME)
        if (!file.exists()) {
            // pdfRenderer cannot handle the resource directly,
            // so extract it into the local cache directory.
            val asset = this.resources.openRawResource(FILERESID)
            val output = FileOutputStream(file)
            val buffer = ByteArray(1024)
            var size: Int
            while (asset.read(buffer).also { size = it } != -1) {
                output.write(buffer, 0, size)
            }
            asset.close()
            output.close()
        }
        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

        // capture PDF data
        // all this just to get a handle to the actual PDF representation
        pdfRenderer = PdfRenderer(parcelFileDescriptor)
        for(i in 1..pdfRenderer.pageCount) {
            val paths = ArrayList<Pair<Path?,Int>>()
            pageImage.paths.add(paths)
        }
    }

    // do this before you quit!
    @Throws(IOException::class)
    private fun closeRenderer() {
        currentPage?.close()
        pdfRenderer.close()
        parcelFileDescriptor.close()
    }

    private fun showPage(index: Int) {
        if (pdfRenderer.pageCount <= index) {
            return
        }
        // Close the current page before opening another one.
        currentPage?.close()

        // Use `openPage` to open a specific page in PDF.
        currentPage = pdfRenderer.openPage(index)

        if (currentPage != null) {
            // Important: the destination bitmap must be ARGB (not RGB).
            val bitmap = Bitmap.createBitmap(currentPage!!.getWidth(), currentPage!!.getHeight(), Bitmap.Config.ARGB_8888)

            // Here, we render the page onto the Bitmap.
            // To render a portion of the page, use the second and third parameter. Pass nulls to get the default result.
            // Pass either RENDER_MODE_FOR_DISPLAY or RENDER_MODE_FOR_PRINT for the last parameter.
            currentPage!!.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            // Display the page
            pageImage.setImage(bitmap)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {

        // put the unique key value with the data
        // to be restored after configuration changes
        with(outState) {
            putInt("pageNum", pageImage.pageNum)
            putSerializable("paths", pageImage.paths)
            // get matrix values
            val matrixValues = FloatArray(9)
            pageImage.currentMatrix.getValues(matrixValues)
            putFloatArray("matrix", matrixValues)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(inState: Bundle) {
        // get the stored data from the bundle using the unique key
        super.onRestoreInstanceState(inState)
        with(inState) {
            pageImage.pageNum = getInt("pageNum")
            pageImage.paths = getSerializable("paths") as ArrayList<ArrayList<Pair<Path?, Int>>>
            // set matrix values
            pageImage.currentMatrix.setValues(getFloatArray("matrix"))
        }
        showPage(pageImage.pageNum - 1)
        status = findViewById<TextView>(R.id.statusText)
        status.text = "Page ".plus(pageImage.pageNum).plus("/").plus(pdfRenderer.pageCount)
    }

}
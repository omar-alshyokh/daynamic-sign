package com.example.dynamic_sign

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.github.barteksc.pdfviewer.PDFView
import com.github.gcacace.signaturepad.views.SignaturePad
import com.google.android.material.bottomsheet.BottomSheetDialog

class MainActivity : AppCompatActivity() {

    private lateinit var buttonSelectPdf: Button
    private lateinit var buttonShowBottomSheet: Button
    private lateinit var buttonSavePosition: Button
    private lateinit var pdfView: PDFView
    private lateinit var imageViewSignature: ImageView
    private var selectedPdfUri: Uri? = null
    private var signatureBitmap: Bitmap? = null
    private val pdfViewBounds = Rect()

    private var currentPage: Int = 0

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var scaleFactor = 1.0f


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        buttonSelectPdf = findViewById(R.id.button_select_pdf)
        buttonShowBottomSheet = findViewById(R.id.button_show_bottom_sheet)
        buttonSavePosition = findViewById(R.id.button_save_position)
        pdfView = findViewById(R.id.pdfView)
        imageViewSignature = findViewById(R.id.imageViewSignature)

        buttonSelectPdf.setOnClickListener { selectPdf() }

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.5f, 3.0f) // Limit scale between 0.5x and 3x

                imageViewSignature.scaleX = scaleFactor
                imageViewSignature.scaleY = scaleFactor
                return true
            }
        })


        buttonShowBottomSheet.setOnClickListener {
            if (selectedPdfUri != null) {
                showSignaturePad()
            } else {
                Toast.makeText(this, "Please select a PDF first.", Toast.LENGTH_SHORT).show()
            }
        }

        buttonSavePosition.setOnClickListener {
            // Get the current offset in the PDFView
            val positionOffset = pdfView.positionOffset
            val pageHeight = pdfView.height / pdfView.zoom
            val visiblePageTop = positionOffset * pdfView.pageCount * pageHeight

            // Calculate relative x and y within the current page
            val relativeX = (imageViewSignature.x - pdfViewBounds.left) / pdfViewBounds.width()
            val relativeY = ((imageViewSignature.y - pdfViewBounds.top) + visiblePageTop) / (pdfViewBounds.height())

            Log.d("Signature", "Page: $currentPage, Relative X: $relativeX, Relative Y: $relativeY")

            Toast.makeText(
                this,
                "Signature saved on page: $currentPage, X: $relativeX, Y: $relativeY",
                Toast.LENGTH_LONG
            ).show()
        }

        pdfView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            pdfView.getGlobalVisibleRect(pdfViewBounds) // Update PDFView bounds
            positionSignatureOnPdf()
        }

//        imageViewSignature.setOnTouchListener(signatureTouchListener)
    }
    private fun positionSignatureOnPdf() {
        // Set the initial position for the signature ImageView
        val initialPadding = 10
        val defaultWidth = 100
        val defaultHeight = 100

        val params = imageViewSignature.layoutParams
        params.width = defaultWidth
        params.height = defaultHeight
        imageViewSignature.layoutParams = params

        imageViewSignature.x = pdfViewBounds.left.toFloat()
        imageViewSignature.y = (pdfViewBounds.top + initialPadding).toFloat()
        imageViewSignature.visibility = View.VISIBLE
    }

    private fun selectPdf() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        pdfPickerLauncher.launch(intent)
    }

    private val pdfPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedPdfUri = uri
                buttonShowBottomSheet.visibility = View.VISIBLE
                buttonSavePosition.visibility = View.VISIBLE
                displayPdf()
            }
        }
    }

    private fun displayPdf() {
        selectedPdfUri?.let { uri ->
            try {
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    pdfView.fromStream(inputStream)
                        .defaultPage(0)
                        .enableSwipe(true)
                        .swipeHorizontal(false)
                        .enableDoubletap(true)
                        .fitEachPage(true)
                        .onPageChange { page, _ ->
                            currentPage = page
                            pdfView.getGlobalVisibleRect(pdfViewBounds) }
                        .onError { t ->
                            Toast.makeText(this, "Error loading PDF: ${t.message}", Toast.LENGTH_SHORT).show()
                            Log.e("PDFView", "Error loading PDF: ${t.message}")
                        }
                        .onLoad { totalPages ->
                            Toast.makeText(this, "PDF loaded with $totalPages pages.", Toast.LENGTH_SHORT).show()
                        }
                        .load()
                } else {
                    Toast.makeText(this, "Unable to open PDF file.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error loading PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("PDFView", "Exception: ${e.message}", e)
            }
        }
    }

    private fun showSignaturePad() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.signature_bottom_sheet, null)
        val signaturePad = view.findViewById<SignaturePad>(R.id.signature_pad)
        val buttonClear = view.findViewById<Button>(R.id.button_clear)
        val buttonDone = view.findViewById<Button>(R.id.button_done)

        buttonClear.setOnClickListener { signaturePad.clear() }

        buttonDone.setOnClickListener {
            signatureBitmap = signaturePad.signatureBitmap
            if (signatureBitmap != null) {
                val scaledSignature = Bitmap.createScaledBitmap(signatureBitmap!!, 150, 150, true)
                imageViewSignature.setBackgroundColor(0x00FFFFFF)
                imageViewSignature.setImageBitmap(scaledSignature)
                val params = imageViewSignature.layoutParams
                params.width = 150 // Set width
                params.height = 150 // Set height
                imageViewSignature.layoutParams = params

                imageViewSignature.visibility = View.VISIBLE
                imageViewSignature.setOnTouchListener(signatureTouchListener)

                bottomSheetDialog.dismiss()
            } else {
                Toast.makeText(this, "Please provide a signature.", Toast.LENGTH_SHORT).show()
            }
        }

        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    private val signatureTouchListener = View.OnTouchListener { view, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                view.tag = Pair(event.rawX - view.x, event.rawY - view.y)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val (dx, dy) = view.tag as Pair<Float, Float>
                var newX = event.rawX - dx
                var newY = event.rawY - dy
                val signatureWidth = view.width
                val signatureHeight = view.height
                view.x = newX.coerceIn(pdfViewBounds.left.toFloat(), (pdfViewBounds.right - signatureWidth).toFloat())
                view.y = newY.coerceIn(pdfViewBounds.top.toFloat(), (pdfViewBounds.bottom - signatureHeight).toFloat())
                true
            }
            else -> false
        }
    }
}

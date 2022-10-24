package com.example.pdfviewer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.MotionEvent
import android.widget.ImageView
import java.util.Stack

@SuppressLint("AppCompatCustomView")
class PDFimage  // constructor
    (context: Context?) : ImageView(context) {
    val LOGNAME = "pdf_image"

    // drawing path
    var path: Path? = null
    var paths = ArrayList<ArrayList<Pair<Path?, Int>>>()

    // undo redo stack
    // path, paint, erase or add, page num
    var undoManager = Stack<Pair<Pair<Pair<Path?, Int>, Int>, Int>>()
    var redoManager = Stack<Pair<Pair<Pair<Path?, Int>, Int>, Int>>()

    // image to display
    var bitmap: Bitmap? = null
    var paint = Paint(Color.BLUE)
    var pageNum = 1

    // tool buttons
    var draw = false
    var yellow = false
    var eraser = false
    var mouse = true

    // we save a lot of points because they need to be processed
    // during touch events e.g. ACTION_MOVE
    var x1 = 0f
    var x2 = 0f
    var y1 = 0f
    var y2 = 0f
    var old_x1 = 0f
    var old_y1 = 0f
    var old_x2 = 0f
    var old_y2 = 0f
    var mid_x = -1f
    var mid_y = -1f
    var old_mid_x = -1f
    var old_mid_y = -1f
    var p1_id = 0
    var p1_index = 0
    var p2_id = 0
    var p2_index = 0

    // store cumulative transformations
    // the inverse matrix is used to align points with the transformations - see below
    var currentMatrix = Matrix()
    var inverse = Matrix()

    // capture touch events (down/move/up) to create a path
    // and use that to create a stroke that we can draw
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        var inverted = floatArrayOf()
        when(event.pointerCount) {
            1 -> {
                if(mouse) {
                    p1_id = event.getPointerId(0)
                    p1_index = event.findPointerIndex(p1_id)

                    // invert using the current matrix to account for pan/scale
                    // inverts in-place and returns boolean
                    inverse = Matrix()
                    currentMatrix.invert(inverse)

                    // mapPoints returns values in-place
                    inverted = floatArrayOf(event.getX(p1_index), event.getY(p1_index))
                    inverse.mapPoints(inverted)
                    if (old_x1 < 0 || old_y1 < 0) {
                        x1 = inverted.get(0)
                        old_x1 = x1
                        y1 = inverted.get(1)
                        old_y1 = y1
                    } else {
                        old_x1 = x1
                        old_y1 = y1
                        x1 = inverted.get(0)
                        y1 = inverted.get(1)
                    }
                    when (event.action) {
                        MotionEvent.ACTION_MOVE -> {
                            val dx = x1 - old_x1
                            val dy = y1 - old_y1
                            currentMatrix.preTranslate(dx, dy)
                            Log.d(LOGNAME, "translate: $dx,$dy")
                        }
                        MotionEvent.ACTION_UP -> {
                            old_x1 = -1f
                            old_y1 = -1f
                            old_x2 = -1f
                            old_y2 = -1f
                            old_mid_x = -1f
                            old_mid_y = -1f
                        }
                    }
                }
                else if(draw || yellow) {
                    p1_id = event.getPointerId(0)
                    p1_index = event.findPointerIndex(p1_id)

                    // invert using the current matrix to account for pan/scale
                    // inverts in-place and returns boolean
                    inverse = Matrix()
                    currentMatrix.invert(inverse)

                    // mapPoints returns values in-place
                    inverted = floatArrayOf(event.getX(p1_index), event.getY(p1_index))
                    inverse.mapPoints(inverted)
                    x1 = inverted[0]
                    y1 = inverted[1]
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            Log.d(LOGNAME, "Action down")
                            path = Path()
                            if(draw) {
                                paths[pageNum - 1].add(Pair(path, 0))
                                undoManager.push(Pair(Pair(Pair(path, 0), 0), pageNum))
                                redoManager.clear()
                            }
                            else {
                                paths[pageNum - 1].add(Pair(path, 1))
                                undoManager.push(Pair(Pair(Pair(path, 1), 0), pageNum))
                                redoManager.clear()
                            }
                            path!!.moveTo(x1, y1)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            Log.d(LOGNAME, "Action move")
                            path!!.lineTo(x1, y1)
                        }
                        MotionEvent.ACTION_UP -> Log.d(LOGNAME, "Action up")
                    }
                }
                else if(eraser) {
                    p1_id = event.getPointerId(0)
                    p1_index = event.findPointerIndex(p1_id)

                    // invert using the current matrix to account for pan/scale
                    // inverts in-place and returns boolean
                    inverse = Matrix()
                    currentMatrix.invert(inverse)

                    // mapPoints returns values in-place
                    inverted = floatArrayOf(event.getX(p1_index), event.getY(p1_index))
                    inverse.mapPoints(inverted)
                    x1 = inverted[0]
                    y1 = inverted[1]
                    when (event.action) {
                        MotionEvent.ACTION_MOVE -> {
                            Log.d(LOGNAME, "Eraser move")
                            var erase: Pair<Path?, Int>? = null
                            for (path in paths[pageNum - 1]) {
                                val rectF = RectF()
                                path.first!!.computeBounds(rectF, true)
                                if(rectF.contains(x1, y1)) {
                                    erase = path
                                }
                            }
                            if(erase != null) {
                                undoManager.push(Pair(Pair(erase, 1), pageNum))
                                redoManager.clear()
                                paths[pageNum - 1].remove(erase)
                            }
                        }
                        MotionEvent.ACTION_UP -> Log.d(LOGNAME, "Eraser up")
                    }
                }
            }
            2 -> {
                // point 1
                p1_id = event.getPointerId(0)
                p1_index = event.findPointerIndex(p1_id)

                // mapPoints returns values in-place
                inverted = floatArrayOf(event.getX(p1_index), event.getY(p1_index))
                inverse.mapPoints(inverted)

                // first pass, initialize the old == current value
                if (old_x1 < 0 || old_y1 < 0) {
                    x1 = inverted.get(0)
                    old_x1 = x1
                    y1 = inverted.get(1)
                    old_y1 = y1
                } else {
                    old_x1 = x1
                    old_y1 = y1
                    x1 = inverted.get(0)
                    y1 = inverted.get(1)
                }

                // point 2
                p2_id = event.getPointerId(1)
                p2_index = event.findPointerIndex(p2_id)

                // mapPoints returns values in-place
                inverted = floatArrayOf(event.getX(p2_index), event.getY(p2_index))
                inverse.mapPoints(inverted)

                // first pass, initialize the old == current value
                if (old_x2 < 0 || old_y2 < 0) {
                    x2 = inverted.get(0)
                    old_x2 = x2
                    y2 = inverted.get(1)
                    old_y2 = y2
                } else {
                    old_x2 = x2
                    old_y2 = y2
                    x2 = inverted.get(0)
                    y2 = inverted.get(1)
                }

                // midpoint
                mid_x = (x1 + x2) / 2
                mid_y = (y1 + y2) / 2
                old_mid_x = (old_x1 + old_x2) / 2
                old_mid_y = (old_y1 + old_y2) / 2

                // distance
                val d_old =
                    Math.sqrt(Math.pow((old_x1 - old_x2).toDouble(), 2.0) + Math.pow((old_y1 - old_y2).toDouble(), 2.0))
                        .toFloat()
                val d = Math.sqrt(Math.pow((x1 - x2).toDouble(), 2.0) + Math.pow((y1 - y2).toDouble(), 2.0))
                    .toFloat()

                // pan and zoom during MOVE event
                if (event.action == MotionEvent.ACTION_MOVE) {
                    Log.d(LOGNAME, "Multitouch move")
                    // pan == translate of midpoint
                    val dx = mid_x - old_mid_x
                    val dy = mid_y - old_mid_y
                    currentMatrix.preTranslate(dx, dy)
                    Log.d(LOGNAME, "translate: $dx,$dy")

                    // zoom == change of spread between p1 and p2
                    var scale = d / d_old
                    scale = Math.max(0f, scale)
                    currentMatrix.preScale(scale, scale, mid_x, mid_y)
                    Log.d(LOGNAME, "scale: $scale")

                    // reset on up
                } else if (event.action == MotionEvent.ACTION_UP) {
                    old_x1 = -1f
                    old_y1 = -1f
                    old_x2 = -1f
                    old_y2 = -1f
                    old_mid_x = -1f
                    old_mid_y = -1f
                }
            }

        }
        return true
    }

    // set image as background
    fun setImage(bitmap: Bitmap?) {
        this.bitmap = bitmap
    }

    override fun onDraw(canvas: Canvas) {
        // draw background
        if (bitmap != null) {
            setImageBitmap(bitmap)
        }

        canvas.concat(currentMatrix)
        // draw lines over it
        for (path in paths[pageNum - 1]) {
            if(path.second == 0) {
                paint.style = Paint.Style.STROKE
                paint.color = Color.BLACK
                paint.strokeWidth = 5f
            }
            else if(path.second == 1) {
                paint.style = Paint.Style.STROKE
                paint.color = Color.argb(0.5f,0f,255f,255f)
                paint.strokeWidth = 10f
            }
            canvas.drawPath(path.first!!, paint)
        }
        super.onDraw(canvas)
    }

    init {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
    }

    fun undo() {
        if(!undoManager.empty()) {
            val path = undoManager.pop()
            redoManager.push(path)
            if(path.first.second == 1) {
                paths[path.second - 1].add(path.first.first)
            }
            else if(path.first.second == 0) {
                paths[path.second - 1].remove(path.first.first)
            }
            Log.d(LOGNAME, "Undo")
        }
    }

    fun redo() {
        if(!redoManager.empty()) {
            val path = redoManager.pop()
            undoManager.push(path)
            if(path.first.second == 0) {
                paths[path.second - 1].add(path.first.first)
            }
            else if(path.first.second == 1) {
                paths[path.second - 1].remove(path.first.first)
            }
            Log.d(LOGNAME, "Redo")
        }
    }
}
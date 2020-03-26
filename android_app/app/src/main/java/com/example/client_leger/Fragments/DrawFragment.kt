package com.example.client_leger.Fragments

import android.content.Context
import android.graphics.*
import android.graphics.Bitmap.createBitmap
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.AttributeSet
import android.view.*
import android.widget.Button
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import com.example.client_leger.Communication.Communication
import com.example.client_leger.R
import com.example.client_leger.SocketIO
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.android.synthetic.main.fragment_draw.view.*
import org.json.JSONObject
import yuku.ambilwarna.AmbilWarnaDialog
import kotlin.math.pow
import kotlin.math.sqrt


class DrawFragment: Fragment() {

    private val canvasViewChildPosition = 4

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): ViewGroup {
        val v = inflater.inflate(R.layout.fragment_draw, container, false) as ViewGroup

        v.button_change_color.setOnClickListener {
            openColorPicker(v)
        }

        v.button_strokeErase.setOnClickListener {
            switchStrokeEraser(v)
        }

        v.button_normalErase.setOnClickListener {
            switchNormalEraser(v)
        }

        v.button_changeWidth.setOnClickListener {
            openWidthSelector(v)
        }

        v.addView(DrawCanvas(activity!!.applicationContext, null, this.activity!!.intent.getStringExtra("username")))

        return v
    }

    private fun switchStrokeEraser(v: ViewGroup) {
        val drawCanvasView = v.getChildAt(canvasViewChildPosition) as DrawCanvas
        drawCanvasView.isStrokeErasing = !drawCanvasView.isStrokeErasing
        if (drawCanvasView.isNormalErasing) {
            drawCanvasView.isNormalErasing = false
            v.button_normalErase.toggle()
        }
    }

    private fun switchNormalEraser(v: ViewGroup) {
        val drawCanvasView = v.getChildAt(canvasViewChildPosition) as DrawCanvas
        drawCanvasView.isNormalErasing = !drawCanvasView.isNormalErasing
        if (drawCanvasView.isStrokeErasing) {
            drawCanvasView.isStrokeErasing = false
            v.button_strokeErase.toggle()
        }
    }

    private fun openColorPicker(v: ViewGroup) {
        val drawCanvasView = v.getChildAt(canvasViewChildPosition) as DrawCanvas
        val colorPicker = AmbilWarnaDialog(this.context, drawCanvasView.paintLine.color, object : AmbilWarnaDialog.OnAmbilWarnaListener {
                override fun onCancel(dialog: AmbilWarnaDialog?) {}
                override fun onOk(dialog: AmbilWarnaDialog?, color: Int) {
                    drawCanvasView.paintLine.color = color
                }
            })
        colorPicker.show()
    }

    private fun openWidthSelector(v: ViewGroup) {
        val drawCanvasView = v.getChildAt(canvasViewChildPosition) as DrawCanvas
        val view = layoutInflater.inflate(R.layout.popup_change_width, null)
        val popupWindow = PopupWindow(
            view,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            true)

        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0)

        val seekBar = view.findViewById<SeekBar>(R.id.seekBar_changeWidth)
        val okButton = view.findViewById<Button>(R.id.button_changeWidthOk)

        updateSeekBarThumbSize(seekBar)
        seekBar.progress = drawCanvasView.paintLine.strokeWidth.toInt()

        okButton.setOnClickListener {
            drawCanvasView.paintLine.strokeWidth = seekBar.progress.toFloat()
            popupWindow.dismiss()
        }

        seekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) { }

            override fun onStartTrackingTouch(seekBar: SeekBar) { }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateSeekBarThumbSize(seekBar)
            }
        })

    }

    private fun updateSeekBarThumbSize(seekBar: SeekBar) {
        val th = ShapeDrawable(OvalShape())
        val minThumbWidth = 5
        th.intrinsicWidth = seekBar.progress + minThumbWidth
        th.intrinsicHeight = seekBar.progress + minThumbWidth
        seekBar.thumb = th
    }
}

class Segment(var path: Path, var paint: Paint, var previousSegment: Segment?, var nextSegment: Segment?)

class DrawCanvas(ctx: Context, attr: AttributeSet?, private var username: String) : View(ctx, attr) {
    var paintLine: Paint = Paint()
    var isStrokeErasing = false
    var isNormalErasing = false
    private var paintScreen = Paint()
    private var currentStroke = Path()
    private var currentStartX = 0f
    private var currentStartY = 0f
    private val segments = ArrayList<Segment>()
    private var strokeJustEnded = false
    private var drawListener: Disposable
    private lateinit var bitmap: Bitmap
    private lateinit var bitmapCanvas: Canvas

    init {
        paintLine.isAntiAlias = true
        paintLine.color = Color.BLACK
        paintLine.style = Paint.Style.STROKE
        paintLine.strokeWidth = 16.0F
        paintLine.strokeCap = Paint.Cap.ROUND

        drawListener = Communication.getDrawListener().subscribe{ obj ->
            strokeReceived(obj)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        drawListener.dispose()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmapCanvas = Canvas(bitmap)
        bitmap.eraseColor(Color.WHITE)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(bitmap, 0.0F, 0.0F, paintScreen)
        canvas.drawPath(currentStroke, paintLine)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked

        if (isStrokeErasing || isNormalErasing) {
            if (isStrokeErasing)
                sendEraseStroke(event.x, event.y)   //TODO: send only if is not white and stuff
            else
                sendErasePoint(event.x, event.y)
            checkForStrokesToErase(event.x, event.y)
        } else if (isStrokeErasing) {
            sendEraseStroke(event.x, event.y)
            checkForStrokesToErase(event.x, event.y)
        } else if (action == MotionEvent.ACTION_DOWN) {
            currentStroke.moveTo(event.x, event.y)
            currentStartX = event.x
            currentStartY = event.y
        } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP){
            touchMoved(
                currentStartX,
                currentStartY,
                event.x,
                event.y,
                false
            )
            if (action == MotionEvent.ACTION_UP) {
                sendStroke(currentStartX, event.x, currentStartY, event.y, true)
                strokeJustEnded = true
                bitmapCanvas.drawPath(Path(currentStroke), Paint(paintLine))
                currentStroke.reset()
            }
        }

        return true
    }

    private fun redrawPathsToBitmap() {
        bitmap.eraseColor(Color.WHITE)
        for (segment in segments) {
            bitmapCanvas.drawPath(segment.path, segment.paint)
        }
    }

    private fun batchErase(segment: Segment) {
        // recursive breadth search
        if (segment.nextSegment != null) {
            if (segment.nextSegment!!.paint.color != Color.TRANSPARENT) {
                segment.nextSegment!!.paint.color = Color.TRANSPARENT
                batchErase(segment.nextSegment!!)
            }
        }

        if (segment.previousSegment != null) {
            if (segment.previousSegment!!.paint.color != Color.TRANSPARENT) {
                segment.previousSegment!!.paint.color = Color.TRANSPARENT
                batchErase(segment.previousSegment!!)
            }
        }
    }

    private fun checkForStrokesToErase(pointX: Float, pointY: Float) {
        if (pointX > bitmap.width ||
            pointX < 0 ||
            pointY > bitmap.height ||
            pointY < 0) {

            return
        }

        if (bitmap.getPixel(pointX.toInt(), pointY.toInt()) == Color.WHITE) {
            return
        }

        var strokeFound = false

        for (segment in segments) {
            if (segment.paint.color == Color.TRANSPARENT) {
                continue
            }

            val pm = PathMeasure(segment.path, false)
            val coordinates = FloatArray(2)

            pm.getPosTan(pm.length / 2.0f, coordinates, null)
            val eraserHalfSize = segment.paint.strokeWidth / 2.0f
            val xOnLine = coordinates[0]
            val yOnLine = coordinates[1]

            if (xOnLine <= pointX.toInt() + eraserHalfSize && xOnLine >= pointX.toInt() - eraserHalfSize) {
                if (yOnLine <= pointY.toInt() + eraserHalfSize && yOnLine >= pointY.toInt() - eraserHalfSize) {
                    segment.paint.color = Color.TRANSPARENT
                    if (isStrokeErasing) {
                        batchErase(segment)
                    }

                    strokeFound = true
                }
            }
        }

        if (strokeFound) {
            redrawPathsToBitmap()
            invalidate()
        }
    }

    private fun touchMoved(startX: Float, startY: Float, destX: Float, destY: Float, isEnd: Boolean) {
        val deltaX = (destX - currentStartX)
        val deltaY = (destY - currentStartY)
        val distance = sqrt(deltaX.pow(2.0F) + deltaY.pow(2.0F))

        if (distance == 0.0F) {
            return
        }

        val directionX = (destX - currentStartX) / distance
        val directionY = (destY - currentStartY) / distance

        for (i in 1..(distance / paintLine.strokeWidth).toInt()) {
            val newX = startX + directionX * paintLine.strokeWidth * i
            val newY = startY + directionY * paintLine.strokeWidth * i

            sendStroke(currentStartX, newX, currentStartY, newY, isEnd)

            addSegment(newX, newY)
            currentStartX = newX
            currentStartY = newY
        }

        currentStroke.lineTo(currentStartX, currentStartY)

        invalidate()
    }

    private fun addSegment(destX: Float, destY: Float) {
        val newSegment = Path()
        newSegment.moveTo(currentStartX, currentStartY)
        newSegment.lineTo(destX, destY)
        segments.add(Segment(newSegment, Paint(paintLine), null, null))
        if (segments.size - 2 >= 0 && !strokeJustEnded) {
            // segments.size - 1 is the index of the segment we just added,
            // segments.size - 2 is the index of the segment just before it.
            segments[segments.size - 1].previousSegment = segments[segments.size - 2]
            segments[segments.size - 2].nextSegment = segments[segments.size - 1]
        }

        strokeJustEnded = false
    }

    private var firstStrokeReceived = true

    private fun strokeReceived(obj: JSONObject) {
        when {
            obj.getString("type") == "ink" -> {
                if (obj.getBoolean("isEnd")) {
                    strokeJustEnded = true
                    firstStrokeReceived = true
                    redrawPathsToBitmap()
                    return
                }

                paintLine.isAntiAlias = true
                paintLine.color = obj.getInt("color")
                paintLine.style = Paint.Style.STROKE
                paintLine.strokeWidth = obj.getInt("width").toFloat()
                paintLine.strokeCap = Paint.Cap.ROUND

                currentStartX = obj.getInt("startPosX").toFloat()
                currentStartY = obj.getInt("startPosY").toFloat()

                addSegment(
                    obj.getInt("endPosX").toFloat(),
                    obj.getInt("endPosY").toFloat()
                )

                if (firstStrokeReceived) {
                    firstStrokeReceived = false
                    currentStroke = Path()
                    currentStroke.moveTo(currentStartX, currentStartY)
                    currentStroke.lineTo(obj.getInt("endPosX").toFloat(), obj.getInt("endPosY").toFloat())
                } else {
                    currentStroke.lineTo(obj.getInt("endPosX").toFloat(), obj.getInt("endPosY").toFloat())
                }

                invalidate()
            }
            obj.getString("type") == "eraser" -> {
                currentStroke.reset()
                if (obj.getString("eraser") == "stroke") {
                    isStrokeErasing = true
                    checkForStrokesToErase(obj.getInt("x").toFloat(), obj.getInt("y").toFloat())
                    isStrokeErasing = false
                } else {
                    isNormalErasing = true
                    checkForStrokesToErase(obj.getInt("x").toFloat(), obj.getInt("y").toFloat())
                    isNormalErasing = false
                }
            }
        }
    }

    private fun sendStroke(startPointX: Float, finishPointX: Float, startPointY: Float, finishPointY: Float, isEnd: Boolean) {
        val obj = JSONObject()
        obj.put("type", "ink")
        obj.put("username", username)
        obj.put("startPosX", startPointX)
        obj.put("startPosY", startPointY)
        obj.put("endPosX", finishPointX)
        obj.put("endPosY", finishPointY)
        obj.put("color", paintLine.color)
        obj.put("width", paintLine.strokeWidth)
        obj.put("isEnd", isEnd)

        SocketIO.sendMessage("gameplay", obj)
    }

    private fun sendErasePoint(x: Float, y: Float) {
        val obj = JSONObject()
        obj.put("type", "eraser")
        obj.put("username", username)
        obj.put("x", x)
        obj.put("y", y)
        obj.put("eraser", "point")

        SocketIO.sendMessage("gameplay", obj)
    }

    private fun sendEraseStroke(x: Float, y: Float) {
        val obj = JSONObject()
        obj.put("type", "eraser")
        obj.put("username", username)
        obj.put("x", x)
        obj.put("y", y)
        obj.put("eraser", "stroke")

        SocketIO.sendMessage("gameplay", obj)
    }
}



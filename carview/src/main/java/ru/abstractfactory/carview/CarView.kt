package ru.abstractfactory.carview

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.graphics.Bitmap
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.graphics.Paint.Style.STROKE
import android.animation.*
import android.support.v7.content.res.AppCompatResources
import android.view.MotionEvent
import android.widget.Toast


class CarView : View {
    companion object {
        private const val PROPERTY_ANGLE = "PROPERTY_ANGLE"
        private val pathMeasure = PathMeasure()
    }

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(attrs)
    }

    private lateinit var bitmapCar: Bitmap
    private var wayPointColor: Int = 0
    private var wayPointPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)


    private var currentAngle = 0f
    private var currentX = -1f
    private var currentY = -1f
    private var currentPath = Path()

    private var wayPoint = WayPoint(PointF(0f, 0f), PointF(0f, 0f))

    private lateinit var carRect: Rect
    private var animation: AnimatorSet? = null
    private var touchX = 0f
    private var touchY = 0f

    private fun init(set: AttributeSet?) {

        carRect = Rect()
        val matrix = Matrix().apply { postRotate(90f) and postScale(0.25f, 0.25f) }
        val ta = context.obtainStyledAttributes(set, R.styleable.CarView)
        if (set == null) {
            val scaledBitmap = getBitmap(R.drawable.ic_car_default)
            scaledBitmap?.let {
                bitmapCar = Bitmap.createBitmap(it, 0, 0, it.width, it.height, matrix, true)
            }
            wayPointPaint.color = Color.parseColor("0xFFDB556F")
        } else {
            val scaledBitmap = getBitmap(ta.getResourceId(R.styleable.CarView_drawableCar, R.drawable.ic_car_default))
            scaledBitmap?.let {
                bitmapCar = Bitmap.createBitmap(it, 0, 0, it.width, it.height, matrix, true)
            }
            wayPointColor = ta.getColor(R.styleable.CarView_wayPointColor, 0)
            wayPointPaint.color = wayPointColor

            currentX = ta.getInt(R.styleable.CarView_positionX, -1).toFloat()
            currentY = ta.getInt(R.styleable.CarView_positionY, -1).toFloat()
        }
        wayPointPaint.style = STROKE

        ta.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (currentX < 0 || currentY < 0)
            updateCarPosition(measuredWidth / 2f, measuredHeight / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = currentX - bitmapCar.width / 2
        val centerY = currentY - bitmapCar.height / 2

        canvas.apply {
            drawPath(wayPoint.path, wayPointPaint)
            rotate(currentAngle, currentX, currentY)
            drawBitmap(
                bitmapCar,
                centerX,
                centerY, null
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            moveToNewPoint(event.x, event.y)
        }
        return performClick()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun moveToNewPoint(x: Float, y: Float) {
        touchX = x
        touchY = y
        if (currentX != x && currentY != y) {
            wayPoint = WayPoint(PointF(currentX, currentY), PointF(x, y))
            animation?.cancel()
            animation = createAnimator(getAngle(PointF(x, y)))
            animation?.start()
        } else {
            Toast.makeText(context, resources.getString(R.string.finishMessage), Toast.LENGTH_SHORT).show()
        }
    }

    private fun createAnimator(angle: Float): AnimatorSet {
        val propertyAngle = PropertyValuesHolder.ofFloat(PROPERTY_ANGLE, currentAngle, angle)

        val rotateAnimator =
            if (angle > currentAngle) ValueAnimator.ofFloat(currentAngle, angle) else ValueAnimator.ofFloat(
                angle,
                currentAngle
            )
        rotateAnimator.interpolator = AccelerateDecelerateInterpolator()
        rotateAnimator.setValues(propertyAngle)
        rotateAnimator.duration = 1000
        rotateAnimator.addUpdateListener {
            onAnimationRotate(it)
        }

        val moveAnimator = ValueAnimator.ofFloat(0f, wayPoint.length)
        moveAnimator.interpolator = AccelerateDecelerateInterpolator()
        moveAnimator.addUpdateListener {
            onAnimationPosition(it)
        }

        val set = AnimatorSet()
        set.play(rotateAnimator).before(moveAnimator)

        return set
    }

    private fun onAnimationRotate(valueAnimator: ValueAnimator?) {
        if (valueAnimator == null) {
            return
        }

        currentAngle = valueAnimator.getAnimatedValue(PROPERTY_ANGLE) as Float

        invalidate()
    }

    private fun onAnimationPosition(valueAnimator: ValueAnimator?) {
        if (valueAnimator == null) {
            return
        }

        val v = valueAnimator.animatedValue as Float
        val pos = FloatArray(2)
        pathMeasure.getPosTan(v, pos, null)
        updateCarPosition(PointF(pos[0], pos[1]))
        wayPoint = WayPoint(PointF(pos[0], pos[1]), PointF(touchX, touchY))
        invalidate()
    }

    private fun getBitmap(drawableId: Int, desireWidth: Int? = null, desireHeight: Int? = null): Bitmap? {
        val drawable = AppCompatResources.getDrawable(context, drawableId) ?: return null
        val bitmap = Bitmap.createBitmap(
            desireWidth ?: drawable.intrinsicWidth,
            desireHeight ?: drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun getAngle(target: PointF): Float {
        //Mathf.Atan2(point2Y - point1Y, point2X - point1X) * 180 / Math.PI))
        val angle =
            (Math.atan2(
                target.y - currentY.toDouble(),
                target.x - currentX.toDouble()
            ) * 180 / Math.PI).toFloat()

        val reverseAngle = if (angle < 0) {
            angle + 360f
        } else {
            angle - 360f
        }
        val angleFirst = Math.abs(angle - currentAngle)
        val angleTwo = Math.abs(reverseAngle - currentAngle)
        if (angleFirst > angleTwo) {
            return reverseAngle
        }

        return angle
    }

    private fun updateCarPosition(point: PointF) {
        currentX = point.x
        currentY = point.y
    }

    private fun updateCarPosition(x: Float, y: Float) {
        currentX = x
        currentY = y
    }

    private class WayPoint(startPoint: PointF, endPoint: PointF) {
        val path = createPath(startPoint, endPoint)

        val length by lazy(LazyThreadSafetyMode.NONE) {
            pathMeasure.setPath(path, false)
            pathMeasure.length
        }


        private fun createPath(startPoint: PointF, endPoint: PointF): Path {
            val path = Path()
            path.moveTo(
                startPoint.x,
                startPoint.y
            )
            path.lineTo(
                endPoint.x,
                endPoint.y
            )

            path.fillType = Path.FillType.EVEN_ODD

            return path
        }
    }

    fun resetPosition() {
        updateCarPosition(PointF(measuredWidth / 2f, measuredHeight / 2f))
        currentAngle = 0f
        currentPath = Path()

        requestLayout()
        invalidate()
    }

    fun setCarBitmap(bitmap: Bitmap) {
        bitmapCar = bitmap
        requestLayout()
    }

    fun setCarPosition(x: Int, y: Int) {
        updateCarPosition(PointF(x.toFloat(), y.toFloat()))
        invalidate()
    }
}
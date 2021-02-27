package gl

import com.soywiz.korma.geom.IPoint
import com.soywiz.korma.geom.vector.VectorPath
import kotlin.math.*

class EarClipper(private val path: VectorPath) {
    private val origPoints = path.getPoints()
    private val points = origPoints.toMutableList()

    init {
        origPoints.forEachIndexed { index, iPoint -> points[index] = iPoint }
    }

    fun triangulate(): List<IPoint> {
        require(points.size > 2)

        val triPoints = mutableListOf<IPoint>()

        if (points.size == 3) {
            return origPoints
        }else {
            var i = 1
            var sizeLastRun = 0
            while (points.size > 3) {
                val mid = IPoint((points[i-1].x + points[i+1].x) / 2, (points[i-1].y + points[i+1].y) / 2)
                if (path.containsPoint(mid.x, mid.y)) {
                    triPoints.addAll(listOf(points[i-1], points[i], points[i+1]))
                    points.removeAt(i)
                    i = 1
                }else {
                    i++
                }

                if (i >= points.size-1) {
                    if (sizeLastRun == points.size) {
                        //println("WARN cant fully triangulate")
                        break
                    }
                    sizeLastRun = points.size
                    i = 1
                }
            }
            triPoints.addAll(listOf(points[0], points[1], points[2]))
        }
        return triPoints
    }
}
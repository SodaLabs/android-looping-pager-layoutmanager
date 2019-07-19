@file:Suppress("unused")

package co.sodalabs.pager

import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

@Suppress("MemberVisibilityCanBePrivate")
open class PagerSnapHelper(
    displayDensity: Float,
    velocityToSnap: Int = 500
) : LinearSnapHelper() {

    protected val velocityToSnapNextPosition by lazy { (velocityToSnap * displayDensity).roundToInt() }

    override fun findTargetSnapPosition(
        layoutManager: RecyclerView.LayoutManager,
        velocityX: Int,
        velocityY: Int
    ): Int {
        if (layoutManager !is LoopingPagerLayoutManager) {
            return super.findTargetSnapPosition(layoutManager, velocityX, velocityY)
        }
        val centerPosition = layoutManager.findVisibleCenterItemPosition()
        return when {
            velocityX < -velocityToSnapNextPosition -> {
                // Case of swiping from left to right:
                layoutManager.findVisibleLeftMostItemPosition()
            }
            velocityX > velocityToSnapNextPosition -> {
                // Case of swiping from right to left:
                layoutManager.findVisibleRightMostItemPosition()
            }
            else -> centerPosition
        }
    }
}
@file:Suppress("MemberVisibilityCanBePrivate")

package co.sodalabs.pager

import android.graphics.PointF
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.sign

open class LoopingPagerLayoutManager :
    RecyclerView.LayoutManager(),
    RecyclerView.SmoothScroller.ScrollVectorProvider {

    protected var pendingStartPosition = 0
    protected var startPosition: Int = 0
    protected var endPosition: Int = 0

    protected var fixedChildWidth: Int = 0

    protected var hostRecyclerView: RecyclerView? = null

    override fun canScrollVertically(): Boolean = false
    override fun canScrollHorizontally(): Boolean = true
    override fun isAutoMeasureEnabled(): Boolean = true
    override fun supportsPredictiveItemAnimations(): Boolean = false

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onAttachedToWindow(view: RecyclerView?) {
        super.onAttachedToWindow(view)
        hostRecyclerView = view
    }

    override fun onDetachedFromWindow(
        view: RecyclerView,
        recycler: RecyclerView.Recycler
    ) {
        removeAllViews()
        hostRecyclerView = null

        super.onDetachedFromWindow(view, recycler)
    }

    private var scrollState: Int = RecyclerView.SCROLL_STATE_IDLE

    override fun onScrollStateChanged(state: Int) {
        scrollState = state
    }

    override fun onLayoutChildren(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ) {
        ensureMainThread()

        // Ignore layout request while it's scrolling.
        val scrollState = hostRecyclerView?.scrollState ?: return
        if (scrollState != RecyclerView.SCROLL_STATE_IDLE) {
            return
        }

        // Use the pending start position if it's present.
        if (pendingStartPosition != RecyclerView.NO_POSITION &&
            itemCount > 0
        ) {
            println("LoopingPagerLayoutManager layout all the children!")
            startPosition = pendingStartPosition
            endPosition = pendingStartPosition
            // Invalidate the pending position.
            pendingStartPosition = RecyclerView.NO_POSITION

            // Remove all the children views.
            removeAndRecycleAllViews(recycler)

            // Then construct the new ones.
            val parentRight = width - paddingRight
            var left = paddingLeft
            var right: Int
            val top = paddingTop
            val bottom = height - paddingBottom
            val itemCount = state.itemCount
            val startPosition = this.startPosition
            var endPosition = startPosition
            for (i in 0 until itemCount) {
                if (left >= parentRight) {
                    break
                }

                // Constraint the position
                endPosition = startPosition + i
                endPosition = endPosition.constrainedBy(itemCount)

                // Get view on the position from the recycler.
                val view = recycler.getViewForPosition(endPosition)
                // Measure and layout child view.
                measureChildWithMargins(view, 0, 0)
                addView(view, i)

                val viewWidth = getDecoratedMeasuredWidth(view)
                right = left + viewWidth
                layoutDecorated(view, left, top, right, bottom)

                // Cache the first child view width as the fixed child width.
                if (fixedChildWidth == 0) {
                    fixedChildWidth = viewWidth
                }

                // Advance the left side.
                left = right
            }

            // Update the end position
            this.endPosition = endPosition
        }
    }

    override fun scrollHorizontallyBy(
        dx: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        ensureMainThread()

        val itemCount = state.itemCount
        if (itemCount <= 1) {
            // TODO: If you want the elastic drag like iOS, do it here!
            return 0
        }

        // Snapshot the first position and update the value. Then flush it back
        // to the property.
        // var startPosition = this.startPosition

        val scrollBy = dx
        // Move the entire children toward left or right.
        offsetChildrenHorizontal(-scrollBy)

        var startPosition = this.startPosition
        var endPosition = this.endPosition

        // Then calculate the new first position.
        val parentLeft = paddingLeft
        val parentRight = width - paddingRight
        val parentTop = paddingTop
        val parentBottom = height - paddingBottom
        if (dx < 0) {
            // The case of swiping from left to right:
            // The the starting left before any view is recycled!
            findLeftMostChild()?.let { oldLeftView ->
                var right = getDecoratedLeft(oldLeftView)
                while (right > parentLeft) {
                    // Decrement the first children position
                    startPosition = (--startPosition).constrainedBy(itemCount)

                    val child = recycler.getViewForPosition(startPosition)
                    // TODO: Optimization as add views only when they are visible
                    measureChildWithMargins(child, 0, 0)
                    addView(child, 0)
                    val childWidth = getDecoratedMeasuredWidth(child)
                    val left = right - childWidth
                    layoutDecorated(child, left, parentTop, right, parentBottom)

                    // Advance right side.
                    right = left
                }
            }
        } else if (dx > 0) {
            // The case of swiping from right to left:
            // The the starting right before any view is recycled!
            findRightMostChild()?.let { oldRightView ->
                var left = getDecoratedRight(oldRightView)
                while (left < parentRight) {
                    // Decrement the first children position
                    endPosition = (++endPosition).constrainedBy(itemCount)

                    val child = recycler.getViewForPosition(endPosition)
                    // TODO: Optimization as add views only when they are visible
                    measureChildWithMargins(child, 0, 0)
                    addView(child)
                    val childWidth = getDecoratedMeasuredWidth(child)
                    val right = left + childWidth
                    layoutDecorated(child, left, parentTop, right, parentBottom)

                    // Advance right side.
                    left = right
                }
            }
        }

        this.startPosition = startPosition
        this.endPosition = endPosition

        // Recycle the views and then calculate the new position
        recycleViewsOutOfBounds(recycler, itemCount)

        return scrollBy
    }

    override fun scrollToPosition(
        position: Int
    ) {
        ensureMainThread()

        pendingStartPosition = position
        requestLayout()
    }

    override fun smoothScrollToPosition(
        recyclerView: RecyclerView,
        state: RecyclerView.State,
        position: Int
    ) {
        ensureMainThread()

        if (recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE) return

        val anchorPosition = findVisibleCenterItemPosition()
        if (anchorPosition == RecyclerView.NO_POSITION) return
        if (anchorPosition == position) return

        // Find the shorter distance (sign is the direction)
        val itemCount = state.itemCount
        val minDistance = findShortestDirectionalDistance(
            from = anchorPosition,
            to = position,
            itemCount = itemCount
        )

        // Calculate the dx in terms of the minimum distance.
        val direction = minDistance.sign
        if (recyclerView.hasFixedSize()) {
            val childWidth = fixedChildWidth
            val dx = direction * childWidth
            recyclerView.smoothScrollBy(dx, 0)
        } else {
            println("Sorry, we don't support smooth scroll for dynamic sized item yet.")
        }
    }

    fun findVisibleRightMostItemPosition(): Int = endPosition
    fun findVisibleLeftMostItemPosition(): Int = startPosition

    /**
     * @return The position of the visible view most close to the center.
     * [RecyclerView.NO_POSITION] if not found.
     */
    fun findVisibleCenterItemPosition(): Int {
        ensureMainThread()

        val parentLeft = paddingLeft
        val parentRight = width - paddingRight
        val parentCenter = (parentLeft + parentRight) / 2

        val itemCount = itemCount
        val startPosition = startPosition
        val endPosition = endPosition
        return if (startPosition == endPosition) {
            startPosition
        } else {
            var foundView: View? = null
            var foundViewCenter = Int.MIN_VALUE
            var foundViewIndex = RecyclerView.NO_POSITION
            val childCount = childCount
            for (i in 0 until childCount) {
                val view = getChildAt(i)!!
                val viewLeft = getDecoratedLeft(view)
                val viewRight = getDecoratedRight(view)
                val viewCenter = (viewLeft + viewRight) / 2
                val visibleInParent = isViewVisibleInParent(
                    viewLeft, viewRight,
                    parentLeft, parentRight
                )
                if (!visibleInParent) {
                    continue
                }

                if (foundView == null) {
                    foundView = view
                    foundViewCenter = viewCenter
                    foundViewIndex = i
                } else {
                    val viewDistance = Math.abs(viewCenter - parentCenter)
                    val foundViewDistance = Math.abs(foundViewCenter - parentCenter)

                    if (viewDistance < foundViewDistance) {
                        foundView = view
                        foundViewCenter = viewCenter
                        foundViewIndex = i
                    }
                }
            }

            if (foundViewIndex != RecyclerView.NO_POSITION) {
                (startPosition + foundViewIndex).constrainedBy(itemCount)
            } else {
                RecyclerView.NO_POSITION
            }
        }
    }

    // Fling //////////////////////////////////////////////////////////////////

    private val zeroScrollVector = PointF(0f, 0f)

    override fun computeScrollVectorForPosition(
        targetPosition: Int
    ): PointF? {
        if (childCount == 0) {
            return null
        }
        val centerPosition = findVisibleCenterItemPosition()
        return if (centerPosition != RecyclerView.NO_POSITION) {
            val minDistance = findShortestDirectionalDistance(
                from = centerPosition,
                to = targetPosition,
                itemCount = itemCount
            )
            PointF(minDistance.toFloat(), 0f)
        } else {
            zeroScrollVector
        }
    }

    // Save / Restore /////////////////////////////////////////////////////////

    override fun onSaveInstanceState(): Parcelable? {
        return SavedState(
            startPosition,
            endPosition
        )
    }

    override fun onRestoreInstanceState(
        state: Parcelable
    ) {
        if (state is SavedState) {
            startPosition = state.startPosition
            endPosition = state.endPosition
            println("Load saved state, $state")

            // Make sure no pending position and then layout the items.
            pendingStartPosition = RecyclerView.NO_POSITION
            requestLayout()
        } else {
            println("Invalid state was trying to be restored, $state")
        }
    }

    // Position ///////////////////////////////////////////////////////////////

    private fun findLeftMostChild(): View? {
        val childCount = childCount

        var leftMostChild: View? = null
        for (i in 0 until childCount) {
            val child = getChildAt(i)!!
            if (leftMostChild == null) {
                leftMostChild = child
            } else {
                val left = leftMostChild.left
                if (child.left < left) {
                    leftMostChild = child
                }
            }
        }

        return leftMostChild
    }

    private fun findRightMostChild(): View? {
        val childCount = childCount

        var rightMostChild: View? = null
        for (i in 0 until childCount) {
            val child = getChildAt(i)!!
            if (rightMostChild == null) {
                rightMostChild = child
            } else {
                val right = rightMostChild.right
                if (child.right > right) {
                    rightMostChild = child
                }
            }
        }

        return rightMostChild
    }

    private fun recycleViewsOutOfBounds(
        recycler: RecyclerView.Recycler,
        itemCount: Int
    ) {
        val childCount = childCount
        val parentLeft = paddingLeft
        val parentRight = width - paddingRight
        val viewsToRecycle = mutableListOf<View>()

        var startPosition = this.startPosition
        var endPosition = this.endPosition

        // Scan from low to high z-order
        for (i in 0 until childCount) {
            val child = getChildAt(i)!!
            if (getDecoratedLeft(child) >= parentRight ||
                getDecoratedRight(child) <= parentLeft
            ) {
                viewsToRecycle.add(child)

                // Shrink the showing range.
                startPosition = (++startPosition).constrainedBy(itemCount)
            } else {
                break
            }
        }
        // Scan from high to low z-order
        for (j in childCount - 1 downTo 0) {
            val child = getChildAt(j)!!
            if (getDecoratedLeft(child) >= parentRight ||
                getDecoratedRight(child) <= parentLeft
            ) {
                viewsToRecycle.add(child)

                // Shrink the showing range.
                endPosition = (--endPosition).constrainedBy(itemCount)
            } else {
                break
            }
        }

        // Remove the offscreen views
        for (j in 0 until viewsToRecycle.size) {
            val view = viewsToRecycle[j]
            removeAndRecycleView(view, recycler)
        }

        this.startPosition = startPosition
        this.endPosition = endPosition

        println("After recycle offscreen views, first position: $startPosition, end position: $endPosition")
    }
}
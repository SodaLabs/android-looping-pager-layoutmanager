@file:Suppress("MemberVisibilityCanBePrivate")

package co.sodalabs.pager

import android.graphics.PointF
import android.os.Parcelable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.sign

private const val TAG = "LoopingPager"

open class LoopingPagerLayoutManager :
    RecyclerView.LayoutManager(),
    RecyclerView.SmoothScroller.ScrollVectorProvider {

    /**
     * A new position for overriding the entire layout.
     * @see [scrollToPosition]
     */
    protected var pendingStartPosition = 0
    /**
     * The index of most left item in the adapter's item list.
     */
    protected var startPosition: Int = 0
    /**
     * The index of most right item in the adapter's item list.
     */
    protected var endPosition: Int = 0

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

        // FIXME: What case is that the layout is triggered while the RV is
        // FIXME: also scrolling?
        // Ignore layout request while it's scrolling.
        // val scrollState = hostRecyclerView?.scrollState ?: return
        // if (scrollState != RecyclerView.SCROLL_STATE_IDLE) {
        //     return
        // }
        val itemCount = state.itemCount
        if (itemCount <= 0) {
            removeAndRecycleAllViews(recycler)
            return
        }

        // If the pending position is present, that means we need to override the
        // entire current layout.
        if (pendingStartPosition != RecyclerView.NO_POSITION) {
            Log.v(TAG, "LoopingPagerLayoutManager layout all the children!")
            startPosition = pendingStartPosition
            endPosition = pendingStartPosition
            // Invalidate the pending position.
            pendingStartPosition = RecyclerView.NO_POSITION

            // Remove all the children views.
            removeAndRecycleAllViews(recycler)
        }

        val parentRight = width - paddingRight
        var left = if (childCount == 0) {
            paddingLeft
        } else {
            val leftMostView = getChildAt(0)
                ?: throw NullPointerException("Cannot find the view at '0'")
            leftMostView.left
        }
        val top = paddingTop
        val bottom = height - paddingBottom
        val startPosition = this.startPosition
        var endPosition = startPosition

        // Layout the child
        var childIndex = 0
        while (left < parentRight) {
            // Find out the right bound.
            endPosition = startPosition + childIndex
            endPosition = endPosition.constrainedBy(itemCount)

            val view = if (childIndex < childCount) {
                // Give the existing view.
                getChildAt(childIndex) ?: throw IllegalStateException("Cannot find view at '$childIndex'")
            } else {
                // Give new view on the position from the recycler.
                val newView = recycler.getViewForPosition(endPosition)
                addView(newView, childIndex)
                newView
            }
            // Note: Still need to measure the view cause the child view has changed.
            // Measure and layout child view.
            measureChildWithMargins(view, 0, 0)
            // Then layout the child view.
            val viewWidth = getDecoratedMeasuredWidth(view)
            val right = left + viewWidth
            layoutDecorated(view, left, top, right, bottom)

            // Go to next child.
            ++childIndex
            // Advance the left side.
            left = right
        }

        // Update the end position
        this.endPosition = endPosition

        // Recycle the views and then calculate the new position
        recycleViewsOutOfBounds(recycler, itemCount)
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
            val childWidth = width // The parent width.
            val dx = direction * childWidth
            recyclerView.smoothScrollBy(dx, 0)
        } else {
            Log.v(TAG, "Sorry, we don't support smooth scroll for dynamic sized item yet.")
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
            Log.v(TAG, "Load saved state, $state")

            // Make sure no pending position and then layout the items.
            pendingStartPosition = RecyclerView.NO_POSITION
            requestLayout()
        } else {
            Log.v(TAG, "Invalid state was trying to be restored, $state")
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

        var start = this.startPosition
        var end = this.endPosition

        // Scan from low to high z-order
        for (i in 0 until childCount) {
            val child = getChildAt(i)!!
            if (getDecoratedLeft(child) >= parentRight ||
                getDecoratedRight(child) <= parentLeft
            ) {
                viewsToRecycle.add(child)

                // Shrink the showing range.
                start = (++start).constrainedBy(itemCount)
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
                end = (--end).constrainedBy(itemCount)
            } else {
                break
            }
        }

        // Remove the offscreen views
        for (j in 0 until viewsToRecycle.size) {
            val view = viewsToRecycle[j]
            removeAndRecycleView(view, recycler)
        }

        this.startPosition = start
        this.endPosition = end

        Log.v(TAG, "After recycle offscreen views, first position: $startPosition, end position: $endPosition")
    }
}
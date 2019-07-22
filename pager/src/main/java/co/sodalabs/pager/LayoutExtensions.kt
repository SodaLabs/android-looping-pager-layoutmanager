package co.sodalabs.pager

import android.os.Looper
import kotlin.math.absoluteValue

internal fun ensureMainThread() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
        throw IllegalThreadStateException("Must run on UI thread!")
    }
}

@Suppress("ConvertTwoComparisonsToRangeCheck")
internal fun isViewVisibleInParent(
    viewLeft: Int,
    viewRight: Int,
    parentLeft: Int,
    parentRight: Int
): Boolean {
    return (viewLeft >= parentLeft && viewLeft <= parentRight) ||
        (viewRight >= parentLeft && viewRight <= parentRight)
}

internal fun findShortestDirectionalDistance(
    from: Int,
    to: Int,
    itemCount: Int
): Int {
    val distance1 = to - from
    val distance2 = if (distance1 < 0) {
        distance1 + itemCount
    } else {
        distance1 - itemCount
    }
    return if (distance1.absoluteValue < distance2.absoluteValue) {
        distance1
    } else {
        distance2
    }
}

internal fun Int.constrainedBy(
    count: Int
): Int {
    var modified = this

    modified %= count
    if (modified < 0) {
        modified += count
    }

    return modified
}
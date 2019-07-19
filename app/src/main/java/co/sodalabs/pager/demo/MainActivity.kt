package co.sodalabs.pager.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import co.sodalabs.pager.LoopingPagerLayoutManager
import co.sodalabs.pager.PagerSnapHelper
import kotlinx.android.synthetic.main.activity_main.rvContentHost

class MainActivity : AppCompatActivity() {

    private val layoutManager by lazy { LoopingPagerLayoutManager() }
    private val adapter by lazy { MainAdapter(LayoutInflater.from(this)) }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rvContentHost.setItemViewCacheSize(0)
        rvContentHost.setHasFixedSize(true)
        rvContentHost.layoutManager = layoutManager
        rvContentHost.adapter = adapter
        rvContentHost.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            override fun onScrollStateChanged(
                recyclerView: RecyclerView,
                newState: Int
            ) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    // No-op
                }
            }
        })

        val snapHelper = PagerSnapHelper(resources.displayMetrics.density, 500)
        snapHelper.attachToRecyclerView(rvContentHost)
    }

    override fun onCreateOptionsMenu(
        menu: Menu
    ): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(
        item: MenuItem
    ): Boolean {
        return when (item.itemId) {
            R.id.set_one_item -> {
                setItemCount(1)
                true
            }
            R.id.set_five_item -> {
                setItemCount(5)
                true
            }
            R.id.scroll_left -> {
                scrollContentBy(-1)
                true
            }
            R.id.scroll_right -> {
                scrollContentBy(1)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setItemCount(
        itemCount: Int
    ) {
        rvContentHost.stopScroll()
        adapter.itemCount = itemCount
    }

    private fun scrollContentBy(
        deltaPosition: Int
    ) {
        val position = layoutManager.findVisibleCenterItemPosition()
        if (position != -1) {
            val count = adapter.itemCount
            val nextPosition = (position + deltaPosition).constrainedBy(count)
            rvContentHost.smoothScrollToPosition(nextPosition)
        }
    }
}

private fun Int.constrainedBy(
    count: Int
): Int {
    var modified = this

    while (modified < 0) {
        modified += count
    }

    if (this >= count) {
        modified %= count
    }

    return modified
}
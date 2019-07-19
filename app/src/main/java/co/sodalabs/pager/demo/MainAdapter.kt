package co.sodalabs.pager.demo

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import timber.log.Timber

class MainAdapter(
    private val inflater: LayoutInflater
) : RecyclerView.Adapter<MainViewHolder>() {

    private val items = mutableListOf(
        // 0
        0, 1, 2, 3, 4
    )

    fun setItemCount(
        count: Int
    ) {
        items.clear()
        for (i in 0 until count) {
            items.add(i)
        }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MainViewHolder {
        val layout = inflater.inflate(R.layout.item_main, parent, false)
        val holder = MainViewHolder(layout)
        Timber.d("onCreateViewHolder $holder")
        return holder
    }

    override fun onBindViewHolder(
        holder: MainViewHolder,
        position: Int
    ) {
        Timber.d("onBindViewHolder $holder")
        val num = items[position]
        holder.textView.text = num.toString()
    }

    override fun onViewRecycled(holder: MainViewHolder) {
        super.onViewRecycled(holder)
        Timber.d("onViewHolderRecycled $holder")
    }
}
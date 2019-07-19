package co.sodalabs.pager.demo

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MainViewHolder(view: View) : RecyclerView.ViewHolder(view) {

    val textView by lazy { view.findViewById<TextView>(R.id.text) }
}
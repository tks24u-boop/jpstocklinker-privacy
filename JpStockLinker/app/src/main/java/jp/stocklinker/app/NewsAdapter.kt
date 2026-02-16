package jp.stocklinker.app

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * ニュース表示用アダプター
 * 既存のStockAdapterとは完全に独立
 */
class NewsAdapter(
    private val newsList: List<NewsItem>
) : RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {

    inner class NewsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvNewsTitle)
        val tvSource: TextView = itemView.findViewById(R.id.tvNewsSource)
        val tvDate: TextView = itemView.findViewById(R.id.tvNewsDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_news, parent, false)
        return NewsViewHolder(view)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        val item = newsList[position]
        
        holder.tvTitle.text = item.title
        holder.tvSource.text = item.source
        holder.tvDate.text = item.pubDate
        
        // タップでリンクを開く
        holder.itemView.setOnClickListener {
            if (item.link.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.link))
                holder.itemView.context.startActivity(intent)
            }
        }
    }

    override fun getItemCount(): Int = newsList.size
}

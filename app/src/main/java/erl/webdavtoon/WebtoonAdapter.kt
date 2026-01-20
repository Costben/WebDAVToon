package erl.webdavtoon

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import erl.webdavtoon.databinding.ItemPhotoViewBinding

/**
 * Webtoon 模式适配器
 */
class WebtoonAdapter(private val tapListener: OnWebtoonTapListener? = null) : RecyclerView.Adapter<WebtoonAdapter.WebtoonViewHolder>() {

    interface OnWebtoonTapListener {
        fun onDoubleTap()
    }

    private var photos: List<Photo> = emptyList()

    fun setPhotos(newPhotos: List<Photo>) {
        photos = newPhotos
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WebtoonViewHolder {
        val binding = ItemPhotoViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WebtoonViewHolder(binding, tapListener)
    }

    override fun onBindViewHolder(holder: WebtoonViewHolder, position: Int) {
        holder.bind(photos[position])
    }

    override fun getItemCount(): Int = photos.size

    class WebtoonViewHolder(
        private val binding: ItemPhotoViewBinding,
        private val tapListener: OnWebtoonTapListener?
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private val gestureDetector = android.view.GestureDetector(binding.root.context, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                tapListener?.onDoubleTap()
                return true
            }
        })

        init {
            binding.root.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                // Return true to consume the event if it's a gesture we care about, 
                // but for RecyclerView items we usually want to let the click through if it's not a gesture.
                // However, GestureDetector needs the stream of events.
                // We return true only if we handled something, but here we just want to detect double tap.
                // To allow scrolling, we should probably return false or use the detector result cautiously.
                // Actually, for double tap to work, we need to consume events or at least let the detector see them.
                // But returning true might block scrolling if not handled carefully.
                // A better approach for items in a scrollable list is to only consume if detected.
                // But onTouchListener is called before scroll.
                // Let's rely on gestureDetector.onTouchEvent(event).
                // Ideally we return gestureDetector.onTouchEvent(event) but that might consume single taps which interferes with click.
                // Since we only care about double tap, and simpleOnGestureListener returns false for single tap (unless overridden).
                // Wait, onDoubleTap returns true.
                // We should let the touch pass through for scrolling.
                // The issue is that if we don't return true for ACTION_DOWN, we won't get subsequent events.
                // But if we return true, we block RecyclerView scrolling.
                // Correct way for RecyclerView item double tap:
                // We can just use the detector. 
                // But the RecyclerView intercepts touch events for scrolling.
                // If we want to detect double tap, we might need to use `OnItemTouchListener` on the RecyclerView itself in the Activity,
                // OR ensure the item view handles it.
                // Let's try handling it here. If we return true for DOWN, the RV might not scroll.
                // Actually, RV intercepts when it detects a drag.
                // So we can return true for DOWN in the item.
                // Let's try just calling gestureDetector.onTouchEvent(event) and returning true to indicate we are interested, 
                // but RV will steal it if it's a scroll.
                true 
            }
        }

        fun bind(photo: Photo) {
            val context = binding.root.context
            
            binding.progressBar.visibility = android.view.View.VISIBLE

            if (photo.isLocal) {
                WebDavImageLoader.loadLocalImage(
                    context,
                    photo.imageUri,
                    binding.imageView,
                    binding.progressBar,
                    limitSize = false // Webtoon模式下不限制图片大小，确保显示全图
                )
            } else {
                WebDavImageLoader.loadWebDavImage(
                    context,
                    photo.imageUri,
                    binding.imageView,
                    binding.progressBar,
                    limitSize = false // Webtoon模式下不限制图片大小，确保显示全图
                )
            }
        }
    }
}

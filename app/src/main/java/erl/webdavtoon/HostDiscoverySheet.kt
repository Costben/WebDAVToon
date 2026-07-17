package erl.webdavtoon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import erl.webdavtoon.databinding.ItemDiscoveredHostBinding
import erl.webdavtoon.databinding.SheetHostDiscoveryBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bottom sheet listing LAN hosts found via [NetworkDiscovery] (mDNS/NSD).
 * Collection runs in a Job scoped to the activity lifecycle and is cancelled
 * when the sheet is dismissed, which is what stops NSD discovery (the flow's
 * awaitClose tears down listeners/resolutions).
 */
object HostDiscoverySheet {

    private const val EMPTY_STATE_DELAY_MS = 3_000L

    fun show(
        activity: AppCompatActivity,
        onHostSelected: (DiscoveredHost) -> Unit
    ) {
        val binding = SheetHostDiscoveryBinding.inflate(activity.layoutInflater)
        val sheet = BottomSheetDialog(activity)
        sheet.setContentView(binding.root)

        var latestHosts: List<DiscoveredHost> = emptyList()

        val adapter = DiscoveredHostAdapter { host ->
            onHostSelected(host)
            sheet.dismiss()
        }
        binding.hostList.layoutManager = LinearLayoutManager(activity)
        binding.hostList.adapter = adapter

        val collectJob: Job = activity.lifecycleScope.launch {
            launch {
                delay(EMPTY_STATE_DELAY_MS)
                if (latestHosts.isEmpty()) {
                    binding.emptyText.visibility = View.VISIBLE
                }
            }
            NetworkDiscovery(activity.applicationContext).discover().collect { hosts ->
                latestHosts = hosts
                adapter.submitList(hosts)
                if (hosts.isNotEmpty()) {
                    binding.emptyText.visibility = View.GONE
                }
            }
        }

        sheet.setOnDismissListener {
            collectJob.cancel()
        }
        sheet.show()
    }

    private class DiscoveredHostAdapter(
        private val onClick: (DiscoveredHost) -> Unit
    ) : ListAdapter<DiscoveredHost, HostViewHolder>(HostDiffCallback) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HostViewHolder {
            val binding = ItemDiscoveredHostBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return HostViewHolder(binding, onClick)
        }

        override fun onBindViewHolder(holder: HostViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    private class HostViewHolder(
        private val binding: ItemDiscoveredHostBinding,
        private val onClick: (DiscoveredHost) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(host: DiscoveredHost) {
            binding.hostName.text = host.displayName
            val hostForDisplay = ServerConfigDialogHelper.bracketIpv6Literal(host.host)
            binding.hostAddress.text = "${host.protocol}://$hostForDisplay:${host.port}"
            binding.root.setOnClickListener { onClick(host) }
        }
    }

    private object HostDiffCallback : DiffUtil.ItemCallback<DiscoveredHost>() {
        override fun areItemsTheSame(oldItem: DiscoveredHost, newItem: DiscoveredHost): Boolean =
            oldItem.protocol == newItem.protocol &&
                oldItem.host == newItem.host &&
                oldItem.port == newItem.port

        override fun areContentsTheSame(oldItem: DiscoveredHost, newItem: DiscoveredHost): Boolean =
            oldItem == newItem
    }
}

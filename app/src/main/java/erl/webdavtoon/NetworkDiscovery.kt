package erl.webdavtoon

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import java.util.concurrent.Executor

data class DiscoveredHost(
    val protocol: String,
    val displayName: String,
    val host: String,
    val port: Int,
)

class NetworkDiscovery internal constructor(
    private val backend: NsdBackend,
    private val logger: DiscoveryLogger = androidDiscoveryLogger(),
) {
    constructor(
        context: Context,
        logger: DiscoveryLogger = androidDiscoveryLogger(),
    ) : this(
        backend = AndroidNsdBackend(
            context.getSystemService(Context.NSD_SERVICE) as NsdManager,
        ),
        logger = logger,
    )

    fun discover(types: List<String> = DEFAULT_TYPES): Flow<List<DiscoveredHost>> = callbackFlow {
        val state = DiscoveryState()
        val activeListeners = mutableListOf<NsdBackend.DiscoveryListener>()
        val activeResolutions = mutableMapOf<ServiceIdentity, Cancellable>()
        val discoveredTypes = types
            .map(::normalizeDiscoveryType)
            .distinct()
            .filter { protocolForServiceType(it) != null }

        fun emitSnapshot() {
            trySend(state.snapshot())
        }

        fun stopResolution(serviceIdentity: ServiceIdentity) {
            activeResolutions.remove(serviceIdentity)?.cancel()
        }

        fun watchService(service: NsdServiceSnapshot) {
            val serviceIdentity = service.identity() ?: return
            if (activeResolutions.containsKey(serviceIdentity)) {
                return
            }
            activeResolutions[serviceIdentity] = backend.watchService(
                service = service,
                listener = object : NsdBackend.ServiceInfoListener {
                    override fun onResolved(resolved: NsdServiceSnapshot) {
                        val change = state.upsert(resolved)
                        if (change) {
                            emitSnapshot()
                        }
                    }

                    override fun onLost(lost: NsdServiceSnapshot) {
                        stopResolution(serviceIdentity)
                        val change = state.remove(lost)
                        if (change) {
                            emitSnapshot()
                        }
                    }

                    override fun onResolveFailed(service: NsdServiceSnapshot, errorCode: Int) {
                        stopResolution(serviceIdentity)
                        logger(
                            "NSD resolve failed for ${service.serviceName} (${service.serviceType}): $errorCode",
                            null,
                        )
                    }
                },
            )
        }

        emitSnapshot()

        for (serviceType in discoveredTypes) {
            val listener = object : NsdBackend.DiscoveryListener {
                override fun onDiscoveryStarted(startedType: String) = Unit

                override fun onStartDiscoveryFailed(failedType: String, errorCode: Int) {
                    logger("NSD discovery start failed for $failedType: $errorCode", null)
                }

                override fun onStopDiscoveryFailed(failedType: String, errorCode: Int) {
                    logger("NSD discovery stop failed for $failedType: $errorCode", null)
                }

                override fun onServiceFound(service: NsdServiceSnapshot) {
                    watchService(service)
                }

                override fun onServiceLost(service: NsdServiceSnapshot) {
                    val serviceIdentity = service.identity() ?: return
                    stopResolution(serviceIdentity)
                    val change = state.remove(service)
                    if (change) {
                        emitSnapshot()
                    }
                }
            }
            activeListeners += listener
            backend.startDiscovery(serviceType, listener)
        }

        awaitClose {
            activeResolutions.values.forEach(Cancellable::cancel)
            activeResolutions.clear()
            activeListeners.forEach { listener ->
                backend.stopDiscovery(listener)
            }
            activeListeners.clear()
        }
    }

    companion object {
        val DEFAULT_TYPES: List<String> = listOf("_smb._tcp", "_ftp._tcp", "_webdav._tcp")
    }
}

internal typealias DiscoveryLogger = (message: String, throwable: Throwable?) -> Unit

internal fun androidDiscoveryLogger(): DiscoveryLogger = { message, throwable ->
    if (throwable == null) {
        Log.w("NetworkDiscovery", message)
    } else {
        Log.w("NetworkDiscovery", message, throwable)
    }
}

internal interface Cancellable {
    fun cancel()
}

internal interface NsdBackend {
    interface DiscoveryListener {
        fun onDiscoveryStarted(startedType: String)
        fun onStartDiscoveryFailed(failedType: String, errorCode: Int)
        fun onStopDiscoveryFailed(failedType: String, errorCode: Int)
        fun onServiceFound(service: NsdServiceSnapshot)
        fun onServiceLost(service: NsdServiceSnapshot)
    }

    interface ServiceInfoListener {
        fun onResolved(resolved: NsdServiceSnapshot)
        fun onLost(lost: NsdServiceSnapshot)
        fun onResolveFailed(service: NsdServiceSnapshot, errorCode: Int)
    }

    fun startDiscovery(serviceType: String, listener: DiscoveryListener)

    fun stopDiscovery(listener: DiscoveryListener)

    fun watchService(service: NsdServiceSnapshot, listener: ServiceInfoListener): Cancellable
}

internal data class NsdServiceSnapshot(
    val serviceName: String,
    val serviceType: String,
    val host: String? = null,
    val port: Int = 0,
)

internal data class ServiceIdentity(
    val protocol: String,
    val serviceName: String,
    val serviceType: String,
)

internal class DiscoveryState {
    private val resolvedServices = linkedMapOf<ServiceIdentity, DiscoveredHost>()

    fun upsert(service: NsdServiceSnapshot): Boolean {
        val identity = service.identity() ?: return false
        val discoveredHost = service.toDiscoveredHost() ?: return false
        val before = snapshot()
        resolvedServices[identity] = discoveredHost
        return before != snapshot()
    }

    fun remove(service: NsdServiceSnapshot): Boolean {
        val identity = service.identity() ?: return false
        val before = snapshot()
        resolvedServices.remove(identity)
        return before != snapshot()
    }

    fun snapshot(): List<DiscoveredHost> {
        return resolvedServices.values
            .associateBy { DiscoveredKey(it.protocol, it.host, it.port) }
            .values
            .sortedWith(
                compareBy<DiscoveredHost>(
                    { it.displayName.lowercase(Locale.US) },
                    { it.protocol },
                    { it.host },
                    { it.port },
                ),
            )
    }
}

internal data class DiscoveredKey(
    val protocol: String,
    val host: String,
    val port: Int,
)

internal fun NsdServiceSnapshot.identity(): ServiceIdentity? {
    val protocol = protocolForServiceType(serviceType) ?: return null
    return ServiceIdentity(
        protocol = protocol,
        serviceName = serviceName,
        serviceType = normalizeServiceType(serviceType),
    )
}

internal fun NsdServiceSnapshot.toDiscoveredHost(): DiscoveredHost? {
    val protocol = protocolForServiceType(serviceType) ?: return null
    val hostLiteral = host?.takeIf { it.isNotBlank() } ?: return null
    val resolvedPort = resolvePort(protocol, port) ?: return null
    return DiscoveredHost(
        protocol = protocol,
        displayName = serviceName,
        host = hostLiteral,
        port = resolvedPort,
    )
}

internal fun normalizeDiscoveryType(serviceType: String): String = normalizeServiceType(serviceType)

internal fun normalizeServiceType(serviceType: String): String {
    return serviceType.trim().lowercase(Locale.US).trimEnd('.')
}

internal fun protocolForServiceType(serviceType: String): String? {
    return when (normalizeServiceType(serviceType)) {
        "_smb._tcp" -> "smb"
        "_ftp._tcp" -> "ftp"
        "_webdav._tcp" -> "webdav"
        else -> null
    }
}

internal fun resolvePort(protocol: String, port: Int): Int? {
    if (port > 0) {
        return port
    }
    return when (protocol) {
        "smb" -> 445
        "ftp" -> 21
        else -> null
    }
}

internal class AndroidNsdBackend(
    private val nsdManager: NsdManager,
) : NsdBackend {
    private val executor = Executor { runnable -> runnable.run() }
    private val discoveryListeners =
        mutableMapOf<NsdBackend.DiscoveryListener, NsdManager.DiscoveryListener>()

    override fun startDiscovery(
        serviceType: String,
        listener: NsdBackend.DiscoveryListener,
    ) {
        val androidListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                listener.onStartDiscoveryFailed(serviceType, errorCode)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                listener.onStopDiscoveryFailed(serviceType, errorCode)
            }

            override fun onDiscoveryStarted(serviceType: String) {
                listener.onDiscoveryStarted(serviceType)
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                listener.onServiceFound(serviceInfo.toSnapshot())
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                listener.onServiceLost(serviceInfo.toSnapshot())
            }
        }
        discoveryListeners[listener] = androidListener
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, androidListener)
    }

    override fun stopDiscovery(listener: NsdBackend.DiscoveryListener) {
        val androidListener = discoveryListeners.remove(listener) ?: return
        try {
            nsdManager.stopServiceDiscovery(androidListener)
        } catch (_: IllegalArgumentException) {
        }
    }

    override fun watchService(
        service: NsdServiceSnapshot,
        listener: NsdBackend.ServiceInfoListener,
    ): Cancellable {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerServiceCallback(service, listener)
        } else {
            resolveOnce(service, listener)
        }
    }

    private fun registerServiceCallback(
        service: NsdServiceSnapshot,
        listener: NsdBackend.ServiceInfoListener,
    ): Cancellable {
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                listener.onResolved(serviceInfo.toSnapshot())
            }

            override fun onServiceLost() {
                listener.onLost(service)
            }

            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                listener.onResolveFailed(service, errorCode)
            }

            override fun onServiceInfoCallbackUnregistered() = Unit
        }
        nsdManager.registerServiceInfoCallback(service.toNsdServiceInfo(), executor, callback)
        return object : Cancellable {
            override fun cancel() {
                try {
                    nsdManager.unregisterServiceInfoCallback(callback)
                } catch (_: IllegalArgumentException) {
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveOnce(
        service: NsdServiceSnapshot,
        listener: NsdBackend.ServiceInfoListener,
    ): Cancellable {
        nsdManager.resolveService(
            service.toNsdServiceInfo(),
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    listener.onResolveFailed(serviceInfo.toSnapshot(), errorCode)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    listener.onResolved(serviceInfo.toSnapshot())
                }
            },
        )
        return object : Cancellable {
            override fun cancel() = Unit
        }
    }
}

internal fun NsdServiceSnapshot.toNsdServiceInfo(): NsdServiceInfo {
    return NsdServiceInfo().apply {
        serviceName = this@toNsdServiceInfo.serviceName
        serviceType = normalizeServiceType(this@toNsdServiceInfo.serviceType)
        if (port > 0) {
            this.port = port
        }
    }
}

internal fun NsdServiceInfo.toSnapshot(): NsdServiceSnapshot {
    return NsdServiceSnapshot(
        serviceName = serviceName.orEmpty(),
        serviceType = serviceType.orEmpty(),
        host = hostAddressLiteral(),
        port = port,
    )
}

internal fun NsdServiceInfo.hostAddressLiteral(): String? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        hostAddresses.firstOrNull()?.hostAddress ?: host?.hostAddress
    } else {
        host?.hostAddress
    }
}

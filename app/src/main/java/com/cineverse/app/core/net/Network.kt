package com.cineverse.app.core.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Is this connection one the user is not paying by the megabyte for?
 *
 * The question autoplay actually needs answered. Checking for Wi-Fi specifically
 * would get it wrong in both directions: a metered hotspot IS Wi-Fi, and an
 * unlimited 5G plan is not — Android's own NOT_METERED capability is the thing
 * that knows, because it is what the user told the system about their plan.
 *
 * Live, not sampled: it is registered as a callback, so walking out of the house
 * stops the next autoplay rather than the one after the app is restarted.
 */
@Composable
fun rememberUnmetered(): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(isUnmetered(context)) }

    DisposableEffect(context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                state.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }

            override fun onLost(network: Network) {
                // No network is not an unmetered network. Nothing should start
                // downloading a trailer into a connection that does not exist.
                state.value = false
            }
        }
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager?.unregisterNetworkCallback(callback) } }
    }
    return state
}

private fun isUnmetered(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}.getOrDefault(false)

package net.trueog.diamondbankog

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Bukkit

object MainThreadBlock {
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun <T> runOnMainThread(block: () -> T): T {
        return if (Bukkit.isPrimaryThread()) {
            block()
        } else {
            suspendCancellableCoroutine { cont ->
                Bukkit.getScheduler()
                    .runTask(
                        DiamondBankOG.plugin,
                        Runnable {
                            try {
                                cont.resume(block())
                            } catch (e: Throwable) {
                                cont.resumeWithException(e)
                            }
                        },
                    )
            }
        }
    }
}

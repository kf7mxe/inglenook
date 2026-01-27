package com.kf7mxe.inglenook

import com.lightningkite.kotlinercli.cli
import kotlin.system.exitProcess

/**
 * Inglenook Server - Reserved for future server-side functionality
 *
 * Current functionality: None (Jellyfin handles all audiobook serving)
 * Future possibilities:
 * - Custom bookshelf sync across devices
 * - Social features (reading progress sharing)
 * - Backup/restore functionality
 */
fun main(args: Array<String>) {
    cli(args, ::commands)
}

fun commands(
    help: () -> Unit = { println("Inglenook Server - No server functionality currently needed") },
) {
    help()
    exitProcess(0)
}

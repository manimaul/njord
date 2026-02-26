@file:OptIn(ExperimentalForeignApi::class)

package io.madrona.njord.util

import File
import io.madrona.njord.Singletons
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.free
import kotlinx.cinterop.interpretPointed
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.plus
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import libnotify.IN_CREATE
import libnotify.IN_DELETE
import libnotify.IN_MOVED_FROM
import libnotify.IN_MOVED_TO
import libnotify.IN_MODIFY
import libnotify.inotify_add_watch
import libnotify.inotify_event
import libnotify.inotify_rm_watch
import libnotify.njord_inotify_init
import platform.posix.POLLIN
import platform.posix.close
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.read
import kotlin.random.Random

class DistributedLock(
    val lockFile: File = Singletons.lockFile,
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    val uuid = UUID.randomUUID().toString()
    private val lockAcquiredFlow = MutableStateFlow(false)

    val lockAcquired: Boolean
        get() = lockAcquiredFlow.value

    private val watchMask = IN_CREATE.toUInt() or IN_DELETE.toUInt() or
            IN_MOVED_TO.toUInt() or IN_MOVED_FROM.toUInt() or IN_MODIFY.toUInt()

    init {
        setupWatchFile()
    }

    fun tryAcquireLock(): Boolean {
        println("try acquire lock")
        if (lockFile.isEmpty()) {
            lockFile.parentFile()?.let { File(it, "lock.${Random.nextLong(Long.MAX_VALUE)}") }?.let {
                it.write(uuid)
                if (it.renameTo(lockFile.getAbsolutePath().toString()) == null) {
                    it.deleteRecursively()
                }
            }
        }
        val acquired = lockFile.readContents() == uuid
        lockAcquiredFlow.value = acquired
        println("try acquire lock acquired = $acquired")
        return acquired
    }

    fun tryClearLock() : Boolean {
        val clear = lockFile.parentFile()?.let { File(it, "lock.${Random.nextLong(Long.MAX_VALUE)}") }?.let {
            it.touch()
            if (it.renameTo(lockFile.getAbsolutePath().toString()) == null) {
                it.deleteRecursively()
                false
            } else {
                true
            }
        } ?: false
        println("try clear lock $clear")
        return clear
    }

    private fun fallBackWatch() {
        launch {
            println("setting up fallback watch - inotify not available")
            while (isActive) {
                delay(1000)
                lockAcquiredFlow.value = lockFile.readContents() == uuid
            }
            println("fallback watch - shutdown")
            return@launch
        }
    }

    private fun setupWatchFile() {
        // Sync initial state from disk (handles stale abort file across restarts)
        lockFile.parentFile()?.mkdirs()
        lockAcquiredFlow.value = lockFile.readContents() == uuid

        val dirPath: String = lockFile.parentFile()?.getAbsolutePath()?.toString() ?: return

        launch {
            val lockName = lockFile.name

            val fd = njord_inotify_init()
            if (fd < 0) {
                fallBackWatch()
                return@launch
            }

            val wd = inotify_add_watch(fd, dirPath, watchMask)
            if (wd < 0) {
                close(fd)
                fallBackWatch()
                return@launch
            }

            println("setting up watch - inotify")
            val eventSize = sizeOf<inotify_event>()
            val bufLen = 1024 * (eventSize + 16)
            val buffer = nativeHeap.allocArray<ByteVar>(bufLen)
            try {
                memScoped {
                    val pfd = alloc<pollfd>()
                    pfd.fd = fd
                    pfd.events = POLLIN.toShort()
                    while (isActive) {
                        pfd.revents = 0
                        poll(pfd.ptr, 1u, 1000)
                        if (!isActive) break
                        val length = read(fd, buffer, bufLen.toULong())
                        if (length <= 0) continue
                        var i: Long = 0
                        while (i < length) {
                            val event = interpretPointed<inotify_event>((buffer + i)!!.rawValue)
                            if (event.len > 0u) {
                                val name = event.name.toKString()
                                if (name == lockName) {
                                    if ((event.mask and watchMask) != 0u) {
                                        val acquired = lockFile.readContents() == uuid
                                        lockAcquiredFlow.value = acquired
                                        println("watch lock change acquired=${acquired}")
                                    }
                                }
                            }
                            i += eventSize + event.len.toLong()
                        }
                    }
                }
            } finally {
                inotify_rm_watch(fd, wd)
                close(fd)
                nativeHeap.free(buffer)
                println("watch - inotify shutdown")
            }
        }
    }
}
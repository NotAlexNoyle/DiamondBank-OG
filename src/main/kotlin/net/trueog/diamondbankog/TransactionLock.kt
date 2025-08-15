package net.trueog.diamondbankog

import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

class TransactionLock {
    private val locks = ConcurrentHashMap<UUID, Semaphore>()

    sealed class LockResult<out T> {
        data class Acquired<T>(val result: T) : LockResult<T>()

        object Failed : LockResult<Nothing>()
    }

    fun <T> withLock(uuid: UUID, block: () -> T): T {
        val lock = locks.computeIfAbsent(uuid) { Semaphore(1) }
        lock.acquire()
        try {
            return block()
        } finally {
            lock.release()
        }
    }

    suspend fun <T> withLockSuspend(uuid: UUID, block: suspend () -> T): T {
        val lock = locks.computeIfAbsent(uuid) { Semaphore(1) }
        lock.acquire()
        try {
            return block()
        } finally {
            lock.release()
        }
    }

    fun <T> tryWithLock(uuid: UUID, block: () -> T): LockResult<T> {
        val lock = locks.computeIfAbsent(uuid) { Semaphore(1) }
        return if (lock.tryAcquire()) {
            try {
                return LockResult.Acquired(block())
            } finally {
                lock.release()
            }
        } else {
            LockResult.Failed
        }
    }

    suspend fun <T> tryWithLockSuspend(uuid: UUID, block: suspend () -> T): LockResult<T> {
        val lock = locks.computeIfAbsent(uuid) { Semaphore(1) }
        return if (lock.tryAcquire()) {
            try {
                LockResult.Acquired(block())
            } finally {
                lock.release()
            }
        } else {
            LockResult.Failed
        }
    }

    fun isLocked(uuid: UUID): Boolean {
        return locks[uuid]?.availablePermits() == 0
    }

    fun removeAllLocks() {
        locks.clear()
    }
}

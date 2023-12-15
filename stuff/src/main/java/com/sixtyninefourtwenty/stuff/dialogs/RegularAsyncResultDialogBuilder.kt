package com.sixtyninefourtwenty.stuff.dialogs

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.concurrent.futures.DirectExecutor
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.sixtyninefourtwenty.stuff.annotations.BuiltWithDependency
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CompletableFuture

@Suppress("unused")
class RegularAsyncResultDialogBuilder(context: Context) : BaseAsyncResultDialogBuilder<RegularAsyncResultDialogBuilder>(context) {

    override val self: RegularAsyncResultDialogBuilder = this

    fun setMessage(message: CharSequence?) = apply { delegate.setMessage(message) }

    @JvmSynthetic
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun show(): Int = suspendCancellableCoroutine { cont ->
        delegate.create().apply {
            val commonListener = DialogInterface.OnClickListener { _, which ->
                cont.resume(which) { dismiss() }
            }
            setButtonsInternal(commonListener)
            setOnCancelListener { cont.cancel() }
            cont.invokeOnCancellation { dismiss() }
        }.show()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun showAsCompletableFuture(): CompletableFuture<Int> {
        val future = CompletableFuture<Int>()
        val dialog = delegate.create().apply {
            val commonListener = DialogInterface.OnClickListener { _, which ->
                future.complete(which)
            }
            setButtonsInternal(commonListener)
            setOnCancelListener { future.cancel(false) }
        }
        dialog.show()
        future.whenComplete { _: Int?, _: Throwable? -> dialog.dismiss() }
        /*
         * I can't return the second future created with CompletableFuture.whenComplete above -
         * when users cancel that future, the first one won't be canceled which breaks dialog
         * dismissal
         */
        return future
    }

    @BuiltWithDependency(
        dependency = "com.google.guava:guava",
        version = "32.1.3-android"
    )
    fun showAsGuavaFuture(): ListenableFuture<Int> {
        val future = SettableFuture.create<Int>()
        val dialog = delegate.create().apply {
            val commonListener = DialogInterface.OnClickListener { _, which ->
                future.set(which)
            }
            setButtonsInternal(commonListener)
            setOnCancelListener { future.cancel(false) }
        }
        dialog.show()
        return future.apply {
            addListener({ dialog.dismiss() }, MoreExecutors.directExecutor())
        }
    }

    @SuppressLint("RestrictedApi")
    @BuiltWithDependency(
        dependency = "androidx.concurrent:concurrent-futures",
        version = "1.1.0"
    )
    fun showAsAndroidXFuture(): ListenableFuture<Int> {
        return CallbackToFutureAdapter.getFuture { completer ->
            val dialog = delegate.create().apply {
                val commonListener = DialogInterface.OnClickListener { _, which ->
                    completer.set(which)
                }
                setButtonsInternal(commonListener)
                setOnCancelListener { completer.setCancelled() }
            }
            dialog.show()
            completer.addCancellationListener({ dialog.dismiss() }, DirectExecutor.INSTANCE)
        }
    }

}
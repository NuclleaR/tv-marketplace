package com.serhii.tvmarketplace.core.common

import android.util.Log

// privacy by construction: an event has a fixed name and only numbers or enum-like states attached,
// so links, addresses, tokens and device names can't end up in logs
object AppLog {

  fun event(
    name: String,
    vararg values: Pair<String, Number>,
  ) {
    val _ = Log.i(TAG, if (values.isEmpty()) name else name + values.joinToString(prefix = " ") { "${it.first}=${it.second}" })
  }

  fun state(
    name: String,
    state: Enum<*>,
  ) {
    val _ = Log.i(TAG, "$name ${state.name}")
  }

  fun error(
    name: String,
    throwable: Throwable,
  ) {
    // only the type: exception messages may contain addresses or user input
    val _ = Log.w(TAG, "$name ${throwable::class.java.simpleName}")
  }

  private const val TAG = "TvMarketplace"
}

package com.serhii.tvmarketplace.core.common

// set once by the Application; features cast it to the graph interface they contribute
lateinit var rootGraph: Any

inline fun <reified T> rootGraphAs(): T = rootGraph as T

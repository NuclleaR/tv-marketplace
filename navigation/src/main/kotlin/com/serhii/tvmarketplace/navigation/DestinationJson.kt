package com.serhii.tvmarketplace.navigation

import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.json.Json

private val json = Json { classDiscriminator = "type" }

fun decodeBackStack(value: String): List<Destination> = json.decodeFromString(value)

// for rememberSaveable: a screen keeps which destination it opened, arguments included, while it is in the back stack
val DestinationSaver: Saver<Destination, String> = Saver(
  save = { json.encodeToString(Destination.serializer(), it) },
  restore = { json.decodeFromString(Destination.serializer(), it) },
)

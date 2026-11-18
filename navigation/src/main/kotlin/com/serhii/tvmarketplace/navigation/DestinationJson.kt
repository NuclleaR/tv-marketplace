package com.serhii.tvmarketplace.navigation

import kotlinx.serialization.json.Json

private val json = Json { classDiscriminator = "type" }

fun decodeBackStack(value: String): List<Destination> = json.decodeFromString(value)

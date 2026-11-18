package com.serhii.tvmarketplace.companion

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      CompanionTheme {
        Surface(Modifier.fillMaxSize()) {
          PairingScreen(Modifier.safeDrawingPadding().padding(16.dp))
        }
      }
    }
  }
}

@Composable
private fun CompanionTheme(content: @Composable () -> Unit) {
  val dark = isSystemInDarkTheme()
  val context = LocalContext.current
  val colors = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    dark -> darkColorScheme()
    else -> lightColorScheme()
  }
  MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun PairingScreen(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val store = remember { Services.pairing(context) }
  val paired by store.pairedTv.collectAsState(initial = null)
  val scope = rememberCoroutineScope()
  val current = paired
  if (current != null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Text(stringResource(R.string.companion_paired, current.name), style = MaterialTheme.typography.titleLarge)
      OutlinedButton(onClick = { scope.launch { store.clear() } }) { Text(stringResource(R.string.companion_forget)) }
    }
    return
  }
  val tvs by remember { Services.discovery(context).tvs() }.collectAsState(initial = emptyList())
  var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
  var pin by rememberSaveable { mutableStateOf("") }
  var error by remember { mutableStateOf<PairResult?>(null) }
  val selected = tvs.firstOrNull { it.id == selectedId }
  Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text(stringResource(R.string.companion_pick_tv), style = MaterialTheme.typography.headlineSmall)
    if (tvs.isEmpty()) Text(stringResource(R.string.companion_searching))
    LazyColumn {
      items(tvs, key = { it.id }) { tv ->
        Surface(onClick = { selectedId = tv.id }, selected = tv.id == selectedId) {
          ListItem(headlineContent = { Text(tv.name) })
        }
      }
    }
    if (selected != null) {
      Text(stringResource(R.string.companion_enter_pin, selected.name))
      OutlinedTextField(
        value = pin,
        onValueChange = { value -> pin = value.filter(Char::isDigit).take(6) },
        label = { Text(stringResource(R.string.companion_pin)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      val message = when (val failure = error) {
        is PairResult.WrongPin -> stringResource(R.string.companion_wrong_pin, failure.attemptsLeft)
        PairResult.NoSession -> stringResource(R.string.companion_no_session)
        PairResult.Unreachable -> stringResource(R.string.companion_unreachable)
        is PairResult.Paired, null -> null
      }
      message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      Button(
        onClick = {
          scope.launch {
            val result = Services.client.pair(selected, pin, deviceName = "${Build.MANUFACTURER} ${Build.MODEL}")
            if (result is PairResult.Paired) {
              store.save(PairedTv(id = selected.id, name = result.tvName, deviceToken = result.deviceToken))
            } else {
              error = result
            }
          }
        },
        enabled = pin.length == 6,
      ) {
        Text(stringResource(R.string.companion_pair))
      }
    }
  }
}

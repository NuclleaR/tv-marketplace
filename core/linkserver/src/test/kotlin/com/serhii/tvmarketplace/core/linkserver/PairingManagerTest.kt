package com.serhii.tvmarketplace.core.linkserver

import com.serhii.tvmarketplace.core.data.PairedPhoneKind
import com.serhii.tvmarketplace.core.testing.FakePairedPhones
import com.serhii.tvmarketplace.core.testing.MutableTimeSource
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingManagerTest {

  private val time = MutableTimeSource()
  private val phones = FakePairedPhones(time)
  private val pairing = PairingManager(SessionManager(time), phones)

  @Test
  fun `issues a 256-bit token and stores only its hash`() = runTest {
    val (token, phone) = pairing.pair(displayName = "Safari on iPhone", kind = PairedPhoneKind.BROWSER)
    assertEquals(32, Base64.getUrlDecoder().decode(token).size)
    assertTrue(token !in phones.hashes.keys)
    assertEquals(PairedPhoneKind.BROWSER, phone.kind)
  }

  @Test
  fun `authenticates a paired phone and records the use`() = runTest {
    val (token, phone) = pairing.pair("Phone", PairedPhoneKind.BROWSER)
    time.now += Duration.ofDays(1)
    val used = pairing.authenticate(token)
    assertEquals(phone.id, used?.id)
    assertEquals(time.now, used?.lastUsedAt)
    assertNull(pairing.authenticate("not-a-token"))
  }

  @Test
  fun `a removed phone no longer authenticates`() = runTest {
    val (token, phone) = pairing.pair("Phone", PairedPhoneKind.BROWSER)
    phones.remove(phone.id)
    assertNull(pairing.authenticate(token))
  }
}

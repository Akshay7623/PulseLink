package com.example

import com.example.call.CallController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallControllerTest {

  @Test
  fun plainNumbersAreDialledWithoutTheirSpacing() {
    assertEquals("9876543210", CallController.dialableNumber("98765 43210"))
    assertEquals("+919876543210", CallController.dialableNumber("+91 (98765) 432-10"))
    assertEquals("198", CallController.dialableNumber("198"))
  }

  @Test
  fun codesThatChangeSimSettingsAreRefused() {
    // Call forwarding and balance codes
    assertNull(CallController.dialableNumber("**21*9876543210#"))
    assertNull(CallController.dialableNumber("*123#"))
  }

  @Test
  fun anythingThatIsNotAPhoneNumberIsRefused() {
    assertNull(CallController.dialableNumber(""))
    assertNull(CallController.dialableNumber("12"))
    assertNull(CallController.dialableNumber("98765abc"))
    assertNull(CallController.dialableNumber("9876+543210"))
    assertNull(CallController.dialableNumber("1234567890123456"))
  }
}

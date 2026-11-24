package com.example

import com.example.data.repository.PhoneValidationResult
import com.example.data.repository.validateIndianMobileNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {

    @Test
    fun `validateIndianMobileNumber accepts valid 10-digit numbers starting with 6 to 9`() {
        val result = validateIndianMobileNumber("98765-43210")
        assertTrue(result is PhoneValidationResult.Valid)
        val valid = result as PhoneValidationResult.Valid
        assertEquals("+919876543210", valid.e164Phone)
        assertEquals("98765 43210", valid.formattedLocal)
    }

    @Test
    fun `validateIndianMobileNumber rejects short or invalid leading digit numbers`() {
        assertTrue(validateIndianMobileNumber("") is PhoneValidationResult.Invalid)
        assertTrue(validateIndianMobileNumber("98765") is PhoneValidationResult.Invalid)
        assertTrue(validateIndianMobileNumber("1234567890") is PhoneValidationResult.Invalid)
        assertTrue(validateIndianMobileNumber("5987654321") is PhoneValidationResult.Invalid)
    }
}

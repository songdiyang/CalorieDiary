package com.calorie.diary

import com.calorie.diary.data.supabase.AuthInputValidator
import com.calorie.diary.data.supabase.UserIdValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthInputValidatorTest {

    @Test
    fun acceptsEmailAndEightCharacterPassword() {
        assertTrue(AuthInputValidator.isValid("user@example.com", "12345678"))
    }

    @Test
    fun trimsEmailBeforeValidation() {
        assertTrue(AuthInputValidator.isValid(" user@example.com ", "12345678"))
    }

    @Test
    fun rejectsMalformedEmail() {
        val invalidEmails = listOf(
            "not-an-email", "", " ", "@example.com", "user@", "user@example",
            "user@@example.com", "a..b@example.com", ".user@example.com",
            "user.@example.com", "user@example..com", "user@-example.com",
            "user@example-.com", "user@example.com."
        )
        invalidEmails.forEach { email ->
            assertFalse(
                "Expected invalid email: $email",
                AuthInputValidator.isValid(email, "12345678")
            )
        }
    }

    @Test
    fun acceptsCommonEmailVariants() {
        assertTrue(AuthInputValidator.isValid("first.last+tag@example.com", "12345678"))
        assertTrue(AuthInputValidator.isValid("User.Name@Sub.Example.COM", "12345678"))
    }

    @Test
    fun rejectsShortPassword() {
        assertFalse(AuthInputValidator.isValid("user@example.com", "1234567"))
    }

    @Test
    fun acceptsSixToTenDigitEmailCode() {
        assertTrue(AuthInputValidator.isValidOtpCode("123456"))
        assertTrue(AuthInputValidator.isValidOtpCode("12345678"))
        assertTrue(AuthInputValidator.isValidOtpCode("1234567890"))
        assertTrue(AuthInputValidator.isValidOtpCode(" 12345678 "))
        assertFalse(AuthInputValidator.isValidOtpCode(""))
        assertFalse(AuthInputValidator.isValidOtpCode("1234"))
        assertFalse(AuthInputValidator.isValidOtpCode("12345678901"))
        assertFalse(AuthInputValidator.isValidOtpCode("1234ab"))
    }

    @Test
    fun invitationCodeIsOptionalButMustBeEightDigitsWhenFilled() {
        assertTrue(AuthInputValidator.isValidInvitationCode("12345678"))
        assertTrue(AuthInputValidator.isValidInvitationCode(" 12345678 "))
        assertFalse(AuthInputValidator.isValidInvitationCode("1234567"))
        assertFalse(AuthInputValidator.isValidInvitationCode("1234abcd"))
    }

    @Test
    fun validatesEmailForSendingCode() {
        assertTrue(AuthInputValidator.isValidEmail("user@example.com"))
        assertTrue(AuthInputValidator.isValidEmail(" first.last+tag@example.com "))
        assertFalse(AuthInputValidator.isValidEmail("a..b@example.com"))
        assertFalse(AuthInputValidator.isValidEmail("not-an-email"))
    }

    @Test
    fun validatesUuidForAddingFriends() {
        assertTrue(UserIdValidator.isValid("f2930634-0de4-4d5b-be21-a1eb40a1c78b"))
        assertFalse(UserIdValidator.isValid("04462317"))
        assertFalse(UserIdValidator.isValid("not-a-user-id"))
    }

    @Test
    fun validatesNicknameLengthAndControlCharacters() {
        assertTrue(AuthInputValidator.isValidNickname("小明"))
        assertTrue(AuthInputValidator.isValidNickname("  不吃胡萝卜  "))
        assertTrue(AuthInputValidator.isValidNickname("a".repeat(20)))
        assertTrue(AuthInputValidator.isValidNickname("🍎".repeat(20)))
        assertFalse(AuthInputValidator.isValidNickname(""))
        assertFalse(AuthInputValidator.isValidNickname("   "))
        assertFalse(AuthInputValidator.isValidNickname("a".repeat(21)))
        assertFalse(AuthInputValidator.isValidNickname("坏\n名字"))
        assertFalse(AuthInputValidator.isValidNickname("tab\tname"))
    }
}

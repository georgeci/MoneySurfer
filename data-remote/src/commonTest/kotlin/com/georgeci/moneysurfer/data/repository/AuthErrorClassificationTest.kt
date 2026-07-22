package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.domain.auth.AuthError
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * Guards the iOS-only fallback in [classifyAuthErrorByMessage]. gitlive's iOS SDK
 * throws the base FirebaseAuthException for every FIRAuthErrorDomain failure, so
 * the app must recover the classification from the error-code name in the message
 * (issue #219). The messages below are the real ones the iOS SDK produces against
 * the Firebase Auth emulator.
 */
class AuthErrorClassificationTest : StringSpec({

    "no-such-user (FIRAuth 17011) classifies as InvalidCredentials" {
        classifyAuthErrorByMessage(
            "Error Domain=FIRAuthErrorDomain Code=17011 \"There is no user record " +
                "corresponding to this identifier.\" UserInfo={FIRAuthErrorUserInfoNameKey=" +
                "ERROR_USER_NOT_FOUND, NSLocalizedDescription=There is no user record.}",
        ) shouldBe AuthError.Type.InvalidCredentials
    }

    "wrong-password (FIRAuth 17009) classifies as InvalidCredentials" {
        classifyAuthErrorByMessage(
            "Error Domain=FIRAuthErrorDomain Code=17009 \"The password is invalid.\" " +
                "UserInfo={FIRAuthErrorUserInfoNameKey=ERROR_WRONG_PASSWORD}",
        ) shouldBe AuthError.Type.InvalidCredentials
    }

    "consolidated invalid-login-credentials classifies as InvalidCredentials" {
        classifyAuthErrorByMessage(
            "Error Domain=FIRAuthErrorDomain Code=17004 UserInfo=" +
                "{FIRAuthErrorUserInfoNameKey=ERROR_INVALID_LOGIN_CREDENTIALS}",
        ) shouldBe AuthError.Type.InvalidCredentials
    }

    "email-already-in-use classifies as EmailAlreadyInUse" {
        classifyAuthErrorByMessage(
            "Error Domain=FIRAuthErrorDomain Code=17007 UserInfo=" +
                "{FIRAuthErrorUserInfoNameKey=ERROR_EMAIL_ALREADY_IN_USE}",
        ) shouldBe AuthError.Type.EmailAlreadyInUse
    }

    "weak-password classifies as WeakPassword" {
        classifyAuthErrorByMessage(
            "Error Domain=FIRAuthErrorDomain Code=17026 UserInfo=" +
                "{FIRAuthErrorUserInfoNameKey=ERROR_WEAK_PASSWORD}",
        ) shouldBe AuthError.Type.WeakPassword
    }

    "unrecognized message returns null so the caller falls back to Unknown" {
        classifyAuthErrorByMessage(
            "Error Domain=FIRAuthErrorDomain Code=17020 UserInfo=" +
                "{FIRAuthErrorUserInfoNameKey=ERROR_NETWORK_REQUEST_FAILED}",
        ).shouldBeNull()
    }

    "null message returns null" {
        classifyAuthErrorByMessage(null).shouldBeNull()
    }
})

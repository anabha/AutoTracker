package com.tyson.autotracker.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class GoogleAuthManager(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val credentialManager = CredentialManager.create(context)

    // Your Web Client ID
    private val webClientId = "661497086521-1elmkpl3ek4jrbk1utr0shf6hev8db7i.apps.googleusercontent.com"

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    suspend fun signIn(): SignInResult {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            // FIXED: Google wraps the token in a CustomCredential, so we must extract it this way
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {

                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val googleToken = googleIdTokenCredential.idToken

                val firebaseCredential = GoogleAuthProvider.getCredential(googleToken, null)
                val authResult = auth.signInWithCredential(firebaseCredential).await()

                SignInResult.Success(authResult.user)

            } else {
                Log.e("Auth", "Unexpected credential type: ${credential.type}")
                SignInResult.Error("Unexpected credential type")
            }

        } catch (e: Exception) {
            Log.e("Auth", "Sign in failed", e)
            SignInResult.Error(e.message ?: "Unknown error occurred")
        }
    }

    fun signOut() {
        auth.signOut()
    }
}

sealed class SignInResult {
    data class Success(val user: FirebaseUser?) : SignInResult()
    data class Error(val message: String) : SignInResult()
}
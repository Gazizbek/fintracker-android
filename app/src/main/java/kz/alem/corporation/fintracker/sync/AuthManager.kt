package kz.alem.corporation.fintracker.sync

import androidx.credentials.exceptions.NoCredentialException
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kz.alem.corporation.fintracker.AppContainer
import kz.alem.corporation.fintracker.R

/** Thrown when the user dismisses a sign-in UI; callers should stay silent. */
class SignInCancelledException : Exception("Sign-in cancelled")

private const val TAG = "AuthManager"

/**
 * Firebase authentication (port of the iOS AuthManager).
 *
 * Signing in while anonymous links the credential to the anonymous user so its cloud
 * data is kept. If the credential already belongs to another account we sign into that
 * account instead, and because the uid changed the user must confirm replacing local data
 * ([pendingAccountSwitch]).
 */
class AuthManager(private val container: AppContainer) {

    private val auth = FirebaseAuth.getInstance()
    private val prefs get() = container.prefs

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _pendingAccountSwitch = MutableStateFlow<FirebaseUser?>(null)
    /** Non-null while the user decides whether to replace local data with another account's. */
    val pendingAccountSwitch: StateFlow<FirebaseUser?> = _pendingAccountSwitch.asStateFlow()

    val isSignedIn: Boolean get() = _currentUser.value != null
    val isAnonymous: Boolean get() = _currentUser.value?.isAnonymous == true

    // The anonymous user we switched away from while a switch confirmation is pending.
    // Anonymous sessions can't be signed back into, so cancelling must restore this object.
    private var userBeforeSwitch: FirebaseUser? = null

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _currentUser.value = user
            // A restored session on cold start doesn't go through completeSignIn.
            if (user != null) container.syncEngine.scheduleImmediate()
        }
    }

    suspend fun signInAnonymously() {
        val user = auth.signInAnonymously().await().user ?: throw IllegalStateException("No user")
        handlePostSignIn(user)
    }

    suspend fun signIn(email: String, password: String) {
        handlePostSignIn(linkOrSignIn(EmailAuthProvider.getCredential(email, password)))
    }

    suspend fun signUp(email: String, password: String) {
        val user = auth.currentUser
        if (user != null && user.isAnonymous) {
            try {
                val linked = user.linkWithCredential(EmailAuthProvider.getCredential(email, password)).await().user!!
                handlePostSignIn(linked)
            } catch (_: FirebaseAuthUserCollisionException) {
                // The account already exists: sign into it instead of creating a duplicate.
                userBeforeSwitch = user
                handlePostSignIn(auth.signInWithEmailAndPassword(email, password).await().user!!)
            }
        } else {
            handlePostSignIn(auth.createUserWithEmailAndPassword(email, password).await().user!!)
        }
    }

    /** Google sign-in through Credential Manager. Needs an Activity context. */
    suspend fun signInWithGoogle(activityContext: Context) {
        val option = GetSignInWithGoogleOption.Builder(activityContext.getString(R.string.default_web_client_id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request)
        } catch (e: GetCredentialCancellationException) {
            // A signing certificate missing from the Firebase project is also reported as a
            // cancellation ("[16] Account reauth failed") after an account is picked.
            Log.w(TAG, "Google sign-in cancelled: ${e.errorMessage}", e)
            if (e.errorMessage?.contains("[16]") == true) {
                throw IllegalStateException(activityContext.getString(R.string.signInFailed), e)
            }
            throw SignInCancelledException()
        } catch (e: NoCredentialException) {
            // No Google account on the device.
            Log.w(TAG, "Google sign-in: no credential", e)
            throw IllegalStateException(activityContext.getString(R.string.signInFailed), e)
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Google sign-in failed: ${e.type} ${e.errorMessage}", e)
            throw e
        }
        val credential = result.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw IllegalStateException(activityContext.getString(R.string.signInFailed))
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        handlePostSignIn(linkOrSignIn(GoogleAuthProvider.getCredential(idToken, null)))
    }

    /**
     * Links [credential] to the current anonymous user; if it already belongs to an existing
     * account, signs into that account instead.
     */
    private suspend fun linkOrSignIn(credential: AuthCredential): FirebaseUser {
        val user = auth.currentUser
        if (user == null || !user.isAnonymous) {
            return auth.signInWithCredential(credential).await().user!!
        }
        return try {
            user.linkWithCredential(credential).await().user!!
        } catch (e: FirebaseAuthUserCollisionException) {
            userBeforeSwitch = user
            auth.signInWithCredential(e.updatedCredential ?: credential).await().user!!
        }
    }

    fun signOut() {
        auth.signOut()
        _currentUser.value = null
        container.syncEngine.resetState()
        container.appScope.launch { container.pushManager.unregisterCurrentToken() }
    }

    /** Pauses for confirmation when the signed-in uid differs from the last known one. */
    private suspend fun handlePostSignIn(user: FirebaseUser) {
        val lastUid = prefs.lastKnownUid
        if (lastUid != null && lastUid != user.uid) {
            _pendingAccountSwitch.value = user
            return
        }
        completeSignIn(user)
    }

    fun confirmAccountSwitch() {
        val user = _pendingAccountSwitch.value ?: return
        _pendingAccountSwitch.value = null
        userBeforeSwitch = null
        container.appScope.launch {
            container.store.clearAll()
            container.store.clearTombstones()
            container.syncEngine.resetState()
            completeSignIn(user)
        }
    }

    fun cancelAccountSwitch() {
        if (_pendingAccountSwitch.value == null) return
        _pendingAccountSwitch.value = null
        val previous = userBeforeSwitch
        userBeforeSwitch = null
        if (previous == null) {
            auth.signOut()
            return
        }
        container.appScope.launch {
            try {
                auth.updateCurrentUser(previous).await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                auth.signOut()
            }
        }
    }

    private suspend fun completeSignIn(user: FirebaseUser) {
        prefs.lastKnownUid = user.uid
        userBeforeSwitch = null
        _currentUser.value = user
        val subscriptions = container.subscriptionManager
        subscriptions.checkLocalEntitlements()
        // The backend keys subscriptions by uid, so a uid that never made the purchase
        // would get 403 until the Play entitlement is attached to it.
        subscriptions.attachEntitlementToCurrentUser()
        subscriptions.syncWithBackend()
        container.syncEngine.scheduleImmediate()
        container.pushManager.registerIfPossible()
    }

    /** Blocking token fetch for the OkHttp interceptor (runs on OkHttp's threads). */
    fun getIdTokenBlocking(forceRefresh: Boolean): String? {
        val user = auth.currentUser ?: return null
        return runCatching { Tasks.await(user.getIdToken(forceRefresh)).token }.getOrNull()
    }
}

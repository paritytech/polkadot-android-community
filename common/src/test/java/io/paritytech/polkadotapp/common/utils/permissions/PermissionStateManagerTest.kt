package io.paritytech.polkadotapp.common.utils.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.paritytech.polkadotapp.common.data.storage.preferences.Preferences
import io.paritytech.polkadotapp.common.presentation.resources.ContextManager
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.whenever
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.MockedStatic
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic

class PermissionStateManagerTest {
    private val activity = mock(ComponentActivity::class.java)
    private val contextManager = mock(ContextManager::class.java)
    private val preferences = mock(Preferences::class.java)
    private val stored = mutableMapOf<String, Boolean>()

    private lateinit var activityCompat: MockedStatic<ActivityCompat>
    private lateinit var contextCompat: MockedStatic<ContextCompat>

    private val manager = PermissionStateManager(contextManager, preferences)

    @Before
    fun setUp() {
        whenever(contextManager.requireActivity()).thenReturn(activity)
        whenever(contextManager.applicationContext).thenReturn(mock(Context::class.java))
        whenever(preferences.getBoolean(anyString(), anyBoolean())).thenAnswer {
            stored[it.getArgument(0)] ?: it.getArgument(1)
        }
        doAnswer { stored[it.getArgument(0)] = it.getArgument(1) }
            .`when`(preferences).putBoolean(anyString(), anyBoolean())
        activityCompat = mockStatic(ActivityCompat::class.java)
        contextCompat = mockStatic(ContextCompat::class.java)
        contextCompat.`when`<Int> { ContextCompat.checkSelfPermission(any(), anyString()) }
            .thenReturn(PackageManager.PERMISSION_DENIED)
    }

    @After
    fun tearDown() {
        activityCompat.close()
        contextCompat.close()
    }

    @Test
    fun `denial is not permanent when it is the first and has no rationale`() {
        withRationale(false)

        assertEquals(PermissionResult.DENIED, manager.onPermissionResult(CAMERA, isGranted = false))
    }

    @Test
    fun `denial is not permanent when it is the first and has a rationale`() {
        withRationale(true)

        assertEquals(PermissionResult.DENIED, manager.onPermissionResult(CAMERA, isGranted = false))
    }

    @Test
    fun `denial is permanent when there is no rationale after an earlier denial`() {
        withEarlierDenial()
        withRationale(false)

        assertEquals(PermissionResult.DENIED_FOREVER, manager.onPermissionResult(CAMERA, isGranted = false))
    }

    @Test
    fun `denial is permanent when neither it nor the earlier denial had a rationale`() {
        withRationale(false)
        manager.onPermissionResult(CAMERA, isGranted = false)

        assertEquals(PermissionResult.DENIED_FOREVER, manager.onPermissionResult(CAMERA, isGranted = false))
    }

    @Test
    fun `denial is not permanent when there is a rationale after an earlier denial`() {
        withEarlierDenial()
        withRationale(true)

        assertEquals(PermissionResult.DENIED, manager.onPermissionResult(CAMERA, isGranted = false))
    }

    @Test
    fun `earlier denial is forgotten when the permission is granted`() {
        withEarlierDenial()
        manager.onPermissionResult(CAMERA, isGranted = true)
        withRationale(false)

        assertEquals(PermissionResult.DENIED, manager.onPermissionResult(CAMERA, isGranted = false))
    }

    @Test
    fun `denial is not permanent when only another permission was denied before`() {
        withEarlierDenial()
        withRationale(false)

        assertEquals(PermissionResult.DENIED, manager.onPermissionResult(MICROPHONE, isGranted = false))
    }

    @Test
    fun `state is permanent denial when there is no rationale after an earlier denial`() {
        withEarlierDenial()
        withRationale(false)

        assertEquals(PermissionResult.DENIED_FOREVER, manager.getPermissionState(CAMERA))
    }

    @Test
    fun `state stays not permanent when it is only checked`() {
        withRationale(false)
        manager.getPermissionState(CAMERA)

        assertEquals(PermissionResult.DENIED, manager.getPermissionState(CAMERA))
    }

    private fun withRationale(shouldShow: Boolean) {
        activityCompat.`when`<Boolean> { ActivityCompat.shouldShowRequestPermissionRationale(any(), anyString()) }
            .thenReturn(shouldShow)
    }

    private fun withEarlierDenial() {
        withRationale(true)
        manager.onPermissionResult(CAMERA, isGranted = false)
    }

    private companion object {
        const val CAMERA = "android.permission.CAMERA"
        const val MICROPHONE = "android.permission.RECORD_AUDIO"
    }
}

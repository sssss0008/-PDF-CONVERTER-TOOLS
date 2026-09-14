package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("PDF Converter", appName)
  }

  @Test
  fun `verify file size formatting`() {
    assertEquals("500 B", com.example.ui.components.formatFileSize(500))
    assertEquals("2.0 KB", com.example.ui.components.formatFileSize(2048))
    assertEquals("1.5 MB", com.example.ui.components.formatFileSize(1572864))
  }
}

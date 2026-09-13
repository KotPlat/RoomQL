package com.roomql.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.roomql.demo.ui.DemoApp
import com.roomql.demo.ui.theme.RoomQlDemoTheme

/**
 * Entry point of the RoomQL demo app.
 *
 * The app exists to make RoomQL's before/after argument tappable: the flagship screen
 * implements the same search four ways (RoomQL, `IS NULL OR`, overloaded DAOs, and
 * string concatenation) and lets you switch between them at runtime while showing the
 * SQL each one produces.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RoomQlDemoTheme {
                DemoApp()
            }
        }
    }
}

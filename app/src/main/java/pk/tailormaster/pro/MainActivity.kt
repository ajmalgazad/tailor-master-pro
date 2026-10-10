package pk.tailormaster.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import pk.tailormaster.pro.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TailorApp
        setContent { AppRoot(app.repo, app.printer) }
    }
}

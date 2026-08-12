package com.pdg.braceletconnecte

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pdg.braceletconnecte.presentation.navigation.AppNavHost
import com.pdg.braceletconnecte.ui.theme.WebappAppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WebappAppTheme {
                AppNavHost(application = application as BraceletConnecteApplication)
            }
        }
    }
}

package com.google.mlkit.samples.nl.translate.kotlin

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider

class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    val viewModel = ViewModelProvider(this).get(TranslateViewModel::class.java)
    
    setContent {
        TranslateScreen(viewModel)
    }
  }
}

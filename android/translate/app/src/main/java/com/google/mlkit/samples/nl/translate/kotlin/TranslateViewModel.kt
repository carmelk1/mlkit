/*
 * Copyright 2019 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.mlkit.samples.nl.translate.kotlin

import android.app.Application
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.LruCache
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.samples.nl.translate.R
import java.io.File
import java.util.Locale

/**
 * Model class for tracking available models and performing live translations
 */
class TranslateViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

  companion object {
    private const val TAG = "TranslateViewModel"
    private const val NUM_TRANSLATORS = 3
  }

  private val modelManager: RemoteModelManager = RemoteModelManager.getInstance()
  private val pendingDownloads: HashMap<String, Task<Void>> = hashMapOf()
  private val translators =
    object : LruCache<TranslatorOptions, Translator>(NUM_TRANSLATORS) {
      override fun create(options: TranslatorOptions): Translator {
        return Translation.getClient(options)
      }
      override fun entryRemoved(
        evicted: Boolean,
        key: TranslatorOptions,
        oldValue: Translator,
        newValue: Translator?,
      ) {
        oldValue.close()
      }
    }
  val sourceLang = MutableLiveData<Language>()
  val targetLang = MutableLiveData<Language>()
  val sourceText = MutableLiveData<String>()
  val translatedText = MediatorLiveData<ResultOrError>()
  val availableModels = MutableLiveData<List<String>>()

  val availableLanguages: List<Language> = listOf(
    TranslateLanguage.ENGLISH,
    TranslateLanguage.HEBREW,
    TranslateLanguage.ARABIC
  ).map { Language(it) }

  private var tts: TextToSpeech? = null
  private var isTtsInitialized = false

  init {
    copyModelsFromAssets()
    
    // Attempt to initialize with Google TTS engine specifically as it has better offline support
    tts = TextToSpeech(application, this, "com.google.android.tts")

    val processTranslation =
      OnCompleteListener<String> { task ->
        if (task.isSuccessful) {
          translatedText.value = ResultOrError(task.result, null)
        } else {
          translatedText.value = ResultOrError(null, task.exception)
        }
        fetchDownloadedModels()
      }
    translatedText.addSource(sourceText) { translate().addOnCompleteListener(processTranslation) }
    val languageObserver =
      Observer<Language> { translate().addOnCompleteListener(processTranslation) }
    translatedText.addSource(sourceLang, languageObserver)
    translatedText.addSource(targetLang, languageObserver)

    fetchDownloadedModels()

    downloadLanguage(Language(TranslateLanguage.ENGLISH))
    downloadLanguage(Language(TranslateLanguage.HEBREW))
    downloadLanguage(Language(TranslateLanguage.ARABIC))
  }

  override fun onInit(status: Int) {
    if (status == TextToSpeech.SUCCESS) {
      isTtsInitialized = true
      Log.d(TAG, "TTS Initialized successfully with engine: ${tts?.defaultEngine}")
    } else {
      Log.e(TAG, "TTS Initialization failed. Falling back to default engine.")
      // Fallback to default engine if Google engine initialization fails
      tts = TextToSpeech(getApplication(), this)
    }
  }

  fun speak(text: String, language: Language) {
    if (!isTtsInitialized || tts == null) {
        showToast("TTS not ready yet")
        return
    }

    val localesToTry = when (language.code) {
        "he" -> listOf(Locale("iw", "IL"), Locale("iw"), Locale("he", "IL"), Locale("he"))
        "ar" -> listOf(Locale("ar", "SA"), Locale("ar", "EG"), Locale("ar"))
        else -> listOf(Locale(language.code))
    }

    var bestLocale: Locale? = null
    for (locale in localesToTry) {
        val result = tts!!.isLanguageAvailable(locale)
        if (result >= TextToSpeech.LANG_AVAILABLE) {
            bestLocale = locale
            break
        }
    }

    if (bestLocale != null) {
        tts!!.language = bestLocale
        Log.d(TAG, "Speaking text: $text in ${bestLocale.toLanguageTag()}")
        tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TranslationApp")
    } else {
        val errorMsg = "Language ${language.code} not supported or data missing. Check TTS settings."
        Log.e(TAG, errorMsg)
        showToast(errorMsg)
    }
  }

  private fun showToast(message: String) {
    Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
  }

  private fun copyModelsFromAssets() {
    val context = getApplication<Application>()
    val possiblePaths = listOf(
        "com.google.mlkit.nl.translate/models",
        "com.google.mlkit.translate.models"
    )

    possiblePaths.forEach { relativePath ->
        val modelDir = File(context.noBackupFilesDir, relativePath)
        if (!modelDir.exists()) modelDir.mkdirs()

        try {
            val foldersInAssets = context.assets.list("models") ?: return@forEach
            foldersInAssets.forEach { folderName ->
                val destFolder = File(modelDir, folderName)
                if (!destFolder.exists()) {
                    copyAssetFolder(context, "models/$folderName", destFolder.absolutePath)
                    Log.d(TAG, "Successfully copied $folderName to ${destFolder.absolutePath}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying models to ${modelDir.absolutePath}", e)
        }
    }
  }

  private fun copyAssetFolder(context: Application, assetPath: String, destPath: String) {
    val assets = context.assets
    val files = assets.list(assetPath) ?: return
    if (files.isEmpty()) return

    File(destPath).mkdirs()
    for (file in files) {
      val fullAssetPath = "$assetPath/$file"
      val fullDestPath = "$destPath/$file"
      val subFiles = assets.list(fullAssetPath)
      if (subFiles != null && subFiles.isNotEmpty()) {
        copyAssetFolder(context, fullAssetPath, fullDestPath)
      } else {
        assets.open(fullAssetPath).use { input ->
          File(fullDestPath).outputStream().use { output ->
            input.copyTo(output)
          }
        }
      }
    }
  }

  private fun getModel(languageCode: String): TranslateRemoteModel {
    return TranslateRemoteModel.Builder(languageCode).build()
  }

  private fun fetchDownloadedModels() {
    modelManager.getDownloadedModels(TranslateRemoteModel::class.java).addOnSuccessListener {
      remoteModels ->
      availableModels.value = remoteModels.sortedBy { it.language }.map { it.language }
    }
  }

  internal fun downloadLanguage(language: Language) {
    val model = getModel(TranslateLanguage.fromLanguageTag(language.code)!!)
    var downloadTask: Task<Void>?
    if (pendingDownloads.containsKey(language.code)) {
      downloadTask = pendingDownloads[language.code]
      if (downloadTask != null && !downloadTask.isCanceled) {
        return
      }
    }
    downloadTask =
      modelManager.download(model, DownloadConditions.Builder().build()).addOnCompleteListener {
        pendingDownloads.remove(language.code)
        fetchDownloadedModels()
      }
    pendingDownloads[language.code] = downloadTask
  }

  fun requiresModelDownload(
    lang: Language,
    downloadedModels: List<String?>?,
  ): Boolean {
    return if (downloadedModels == null) {
      true
    } else !downloadedModels.contains(lang.code) && !pendingDownloads.containsKey(lang.code)
  }

  internal fun deleteLanguage(language: Language) {
    val model = getModel(TranslateLanguage.fromLanguageTag(language.code)!!)
    modelManager.deleteDownloadedModel(model).addOnCompleteListener { fetchDownloadedModels() }
    pendingDownloads.remove(language.code)
  }

  fun translate(): Task<String> {
    val text = sourceText.value
    val source = sourceLang.value
    val target = targetLang.value
    if (source == null || target == null || text == null || text.isEmpty()) {
      return Tasks.forResult("")
    }
    val sourceLangCode = TranslateLanguage.fromLanguageTag(source.code)!!
    val targetLangCode = TranslateLanguage.fromLanguageTag(target.code)!!
    val options =
      TranslatorOptions.Builder()
        .setSourceLanguage(sourceLangCode)
        .setTargetLanguage(targetLangCode)
        .build()
    return translators[options].downloadModelIfNeeded().continueWithTask { task ->
      if (task.isSuccessful) {
        translators[options].translate(text)
      } else {
        Tasks.forException<String>(
          task.exception ?: Exception("Unknown error")
        )
      }
    }
  }

  inner class ResultOrError(var result: String?, var error: Exception?)

  class Language(val code: String) : Comparable<Language> {
    private val displayName: String
      get() = Locale(code).displayName
    override fun equals(other: Any?): Boolean {
      if (other === this) return true
      if (other !is Language) return false
      return other.code == code
    }
    override fun toString(): String {
      return "$code - $displayName"
    }
    override fun compareTo(other: Language): Int {
      return this.displayName.compareTo(other.displayName)
    }
    override fun hashCode(): Int {
      return code.hashCode()
    }
  }

  override fun onCleared() {
    super.onCleared()
    translators.evictAll()
    tts?.stop()
    tts?.shutdown()
  }
}

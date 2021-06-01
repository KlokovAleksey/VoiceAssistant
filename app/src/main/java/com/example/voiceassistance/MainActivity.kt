package com.example.voiceassistance


import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.SimpleAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.wolfram.alpha.WAEngine
import com.wolfram.alpha.WAPlainText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.collections.HashMap

class MainActivity : AppCompatActivity() {

    private val VOICE_RECOGNITION_REQUEST_CODE = 5555

    val TAG = "MainActivity"

    lateinit var requestInput: TextInputEditText
    lateinit var progressBar: ProgressBar
    lateinit var podsAdapter: SimpleAdapter
    val pods = mutableListOf<HashMap<String, String>>(

    )

    lateinit var waEngine: WAEngine
    lateinit var textToSpeech: TextToSpeech


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initWolframEngine()
        initTts()
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(this) { code ->
            if (code != TextToSpeech.SUCCESS) {
                Log.e(TAG, "TTS is not ready")
                showErrorDialog(getString(R.string.error_tts_is_not_ready))
            }
        }
        textToSpeech.language = Locale.US
    }

    fun initWolframEngine() {
        waEngine = WAEngine()
        waEngine.appID = "EGVK7X-QJX2KRG2KH"
        waEngine.addFormat("plaintext")
    }

    fun showErrorDialog(error: String) {
        Snackbar.make(
            findViewById(android.R.id.content),
            error,
            Snackbar.LENGTH_INDEFINITE
        )
            .apply {
                setAction(android.R.string.ok) {
                    dismiss()
                }
                show()
            }
    }

    fun askWolfram(request: String) {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val query = waEngine.createQuery().apply { input = request }
            kotlin.runCatching {
                waEngine.performQuery(query)
            }.onSuccess { queryResult ->
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (queryResult.isError) {
                        showErrorDialog(queryResult.errorMessage)
                        return@withContext
                    }
                    if (!queryResult.isSuccess) {
                        requestInput.error = getString(R.string.error_do_not_understand)
                        return@withContext
                    }
                    for (pod in queryResult.pods) {
                        if (!pod.isError) {
                            val content = StringBuilder()
                            for (subpod in pod.subpods) {
                                for (element in subpod.contents) {
                                    if (element is WAPlainText) {
                                        content.append(element.text)
                                    }
                                }
                            }
                            pods.add(0, HashMap<String, String>().apply {
                                put("Title", pod.title)
                                put("Content", content.toString())
                            })
                        }
                    }
                    podsAdapter.notifyDataSetChanged()
                }
            }.onFailure { t ->
                Log.e(TAG, "Performing Wolfram query was failed", t)
                withContext((Dispatchers.Main)) {
                    progressBar.visibility = View.GONE
                    showErrorDialog(t.message ?: getString(R.string.error_something_when_wron))
                }
            }
        }
    }

    fun initViews() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        requestInput = findViewById<TextInputEditText>(R.id.text_input_edit)
        requestInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                Log.d(TAG, "IME_ACTION_DONE")
                pods.clear()
                podsAdapter.notifyDataSetChanged()
                val question = requestInput.text.toString()
                askWolfram(question)
            }
            return@setOnEditorActionListener false
        }

        progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        val podsList = findViewById<ListView>(R.id.pods_list)
        podsAdapter = SimpleAdapter(
            applicationContext,
            pods,
            R.layout.item_pod,
            arrayOf("Title", "Content"),
            intArrayOf(R.id.title, R.id.content)
        )
        podsList.adapter = podsAdapter
        podsList.setOnItemClickListener { parent, view, position, id ->
            val title = pods[position]["Title"]
            val content = pods[position]["Content"]
            textToSpeech.speak(content, TextToSpeech.QUEUE_FLUSH, null, title)
        }

        val voiceInputButton = findViewById<FloatingActionButton>(R.id.voice_input_button)
        voiceInputButton.setOnClickListener {
            textToSpeech.stop()
            showVoiceDialog()
        }

    }

    private fun showVoiceDialog() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.request_hint))
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
        }

        runCatching {
            startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE)
        }.onFailure {
            showErrorDialog(getString(R.string.error_voice_rocgnition_unavailable))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> {
                Log.d(TAG, "action_search")
                textToSpeech.stop()
                return true
            }
            R.id.action_clear -> {
                Log.d(TAG, "action_clear")
                requestInput.text?.clear()
                pods.clear()
                podsAdapter.notifyDataSetChanged()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)?.let { question ->
                requestInput.setText(question)
                askWolfram(question)
            }
        }
    }

}
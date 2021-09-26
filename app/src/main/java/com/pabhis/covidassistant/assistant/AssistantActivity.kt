package com.pabhis.covidassistant.assistant

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.pabhis.covidassistant.R
import com.pabhis.covidassistant.api.ApiClient
import com.pabhis.covidassistant.data.AssistantDatabase
import com.pabhis.covidassistant.databinding.ActivityAssistantBinding
import com.pabhis.covidassistant.models.DistrictModel
import com.pabhis.covidassistant.models.StateModel
import kotlinx.coroutines.*
import retrofit2.Response
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class AssistantActivity : AppCompatActivity() {

    lateinit var stateOut: Response<StateModel>
    lateinit var districtOut: Response<ArrayList<DistrictModel>>

    // views
    private lateinit var binding: ActivityAssistantBinding
    private lateinit var assistantViewModel : AssistantViewModel

    // SR & TTS
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private lateinit var keeper : String

    // log statements
    private val logtts = "TTS"
    private val logsr = "SR"
    private val logkeeper = "keeper"

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(R.anim.do_not_move, R.anim.do_not_move)

        // data binding
        binding = DataBindingUtil.setContentView(this, R.layout.activity_assistant)

        val application = requireNotNull(this).application
        val dataSource = AssistantDatabase.getInstance(application).assistantDao
        val viewModelFactory = AssistantViewModelFactory(dataSource, application)


        assistantViewModel =
            ViewModelProvider(
                    this, viewModelFactory
            ).get(AssistantViewModel::class.java)

        binding.assistantViewModel = assistantViewModel

        val adapter = AssistantAdapter()
        binding.recyclerView.adapter = adapter

        assistantViewModel.messages.observe(this, {
            it?.let {
                adapter.data = it
            }
        })

        binding.lifecycleOwner = this

        // Circular Reveal Animation
        if (savedInstanceState == null) {
            binding.assistantConstraintLayout.visibility = View.INVISIBLE
            val viewTreeObserver: ViewTreeObserver = binding.assistantConstraintLayout.viewTreeObserver
            if (viewTreeObserver.isAlive) {
                viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        circularRevealActivity()
                        binding.assistantConstraintLayout.viewTreeObserver
                                .removeOnGlobalLayoutListener(this) }
                })
            }
        }

        // setting on init listener
        textToSpeech = TextToSpeech(this) { status ->

            // check if its success
            if (status == TextToSpeech.SUCCESS) {

                // set language
                val result: Int = textToSpeech.setLanguage(Locale.ENGLISH)

                // check if there is any missing data or the lang is supported or not
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {

                    // if true
                    Log.e(logtts, "Language not supported")
                }
                else{
                    // if false
                    Log.e(logtts, "Language supported")
                }
            }
            else{
                // if success is false
                Log.e(logtts, "Initialization failed")
            }
        }

        // Initializing speech recognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        recognizerIntent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())

        speechRecognizer.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(bundle: Bundle) {}
            override fun onBeginningOfSpeech() {
                Log.d("SR", "started")
            }

            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(bytes: ByteArray) {}
            override fun onEndOfSpeech() {
                Log.d("SR", "ended")
            }

            override fun onError(i: Int) {}

            override fun onResults(bundle: Bundle) {
                // getting data
                val data = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (data != null) {
                    keeper = data[0]
                    Log.d(logkeeper, keeper)
                    when {
                        keeper.contains("thank") -> speak("It's my job, let me know if there is something else")
                        keeper.contains("welcome") -> speak("for what?")
                        keeper.contains("clear") -> assistantViewModel.onClear()
                        keeper.contains("date") -> getDate()
                        keeper.contains("time") -> getTime()
                        keeper.contains("state") -> getStateDetails(keeper)
                        keeper.contains("district") -> getDistrictDetails(keeper)
                        keeper.contains("hello") || keeper.contains(" hi ") || keeper.contains("hey") -> speak("Hello, how can I  help you?")
                        else -> speak("Invalid command, try again")
                    }

                }
            }
            override fun onPartialResults(bundle: Bundle) {}
            override fun onEvent(i: Int, bundle: Bundle) {}
        })

//      on touch for fab
        binding.assistantFloatingActionButton.setOnTouchListener { view, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_UP -> {
                    speechRecognizer.stopListening()

                }
                MotionEvent.ACTION_DOWN -> {
                    textToSpeech.stop()
                    speechRecognizer.startListening(recognizerIntent)

                }
            }
            false
        }

        // check if speech recognition available
        checkIfSpeechRecognizerAvailable()

    }

    private fun checkIfSpeechRecognizerAvailable() {
        if(SpeechRecognizer.isRecognitionAvailable(this))
        {Log.d(logsr, "yes")}
        else
        {Log.d(logsr, "false")}
    }

    // speaking text through text to speech
    fun speak(text: String)
    {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
        assistantViewModel.sendMessageToDatabase(keeper, text)
    }

    fun getTime()
    {
        val calendar = Calendar.getInstance()
        val format = SimpleDateFormat("HH:mm:ss")
        val time: String = format.format(calendar.time)
        speak("The time is $time")
    }

    fun getDate()
    {
        val calendar = Calendar.getInstance()
        val formattedDate = DateFormat.getDateInstance(DateFormat.FULL).format(calendar.time)
        val splitDate = formattedDate.split(",").toTypedArray()
        val date = splitDate[1].trim { it <= ' ' }
        speak("The date is $date")
    }

    private fun getStateDetails(keeper: String)
    {
        GlobalScope.launch(Dispatchers.Main) {
            stateOut = withContext(Dispatchers.IO) {
                ApiClient.api.getStates()
            }

            val stateType = keeper.split("state")
            val stateName = stateType[1].trim()

            for (item in stateOut.body()?.statewise!!){
                if (item.state == stateName){
                    when{
                        keeper.contains("new active") -> speak("New active cases in ${item.state} is ${item.deltaconfirmed}")
                        keeper.contains("new death") -> speak("New deaths in ${item.state} is ${item.deltadeaths}")
                        keeper.contains("new recovered") -> speak("New recovered cases in ${item.state} is ${item.deltarecovered}")
                        keeper.contains("active") -> speak("Active cases in ${item.state} is ${item.active}")
                        keeper.contains("recovered") -> speak("Recovered cases in ${item.state} is ${item.recovered}")
                        keeper.contains("death") -> speak("Deaths in ${item.state} is ${item.deaths}")
                        else -> speak("Could not find data")
                    }
                }
            }
        }
    }

    fun getDistrictDetails(keeper: String) {
        GlobalScope.launch(Dispatchers.Main) {
            districtOut = withContext(Dispatchers.IO) {
                ApiClient.api.getDistrict()
            }

            speak("$keeper")
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun circularRevealActivity() {
        val cx: Int = binding.assistantConstraintLayout.right - getDips(44)
        val cy: Int = binding.assistantConstraintLayout.bottom - getDips(44)
        val finalRadius: Int =
            binding.assistantConstraintLayout.width.coerceAtLeast(binding.assistantConstraintLayout.height)
        val circularReveal = ViewAnimationUtils.createCircularReveal(
                binding.assistantConstraintLayout,
                cx,
                cy, 0f,
                finalRadius.toFloat()
        )
        circularReveal.duration = 500
        binding.assistantConstraintLayout.visibility = View.VISIBLE
        circularReveal.start()
    }

    private fun getDips(dps: Int): Int {
        val resources: Resources = resources
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dps.toFloat(),
                resources.displayMetrics
        ).toInt()
    }

    override fun onBackPressed() {
        val cx: Int = binding.assistantConstraintLayout.width - getDips(44)
        val cy: Int = binding.assistantConstraintLayout.bottom - getDips(44)
        val finalRadius: Int = max(
                binding.assistantConstraintLayout.width,
                binding.assistantConstraintLayout.height
        )
        val circularReveal =
            ViewAnimationUtils.createCircularReveal(
                    binding.assistantConstraintLayout, cx, cy,
                    finalRadius.toFloat(), 0f
            )
        circularReveal.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animator: Animator) {}
            override fun onAnimationEnd(animator: Animator) {
                binding.assistantConstraintLayout.visibility = View.INVISIBLE
                finish()
            }

            override fun onAnimationCancel(animator: Animator) {}
            override fun onAnimationRepeat(animator: Animator) {}
        })
        circularReveal.duration = 1500
        circularReveal.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        // destroying
        textToSpeech.stop()
        textToSpeech.shutdown()
        speechRecognizer.cancel()
        speechRecognizer.destroy()
        Log.i(logsr, "destroy")
        Log.i(logtts, "destroy")
    }
}
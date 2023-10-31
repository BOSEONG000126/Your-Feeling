package com.google.mediapipe.examples.facelandmarker

import android.content.Intent
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.ViewGroup
import android.os.Bundle
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.annotation.RequiresApi
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RelativeLayout
import androidx.fragment.app.Fragment
import com.google.mediapipe.examples.facelandmarker.fragment.CameraFragment
import com.google.mediapipe.examples.facelandmarker.fragment.GalleryFragment
import java.util.*

class startFragment : Fragment(), OnInitListener {
    private var tts: TextToSpeech? = null
    private var btnPressTime: Long = 0
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_start, container, false) as ViewGroup
        tts = TextToSpeech(activity, this)
        speakOut()
        val relativeLayout: RelativeLayout
        relativeLayout = rootView.findViewById(R.id.relativeLayout)
        relativeLayout.setOnLongClickListener {
            tts!!.stop()

            activity?.let{

                val intent = Intent(context , MainActivity::class.java)
                startActivity(intent)


            }


            //

            false
        }
        relativeLayout.setOnClickListener(View.OnClickListener {
            if (System.currentTimeMillis() > btnPressTime + 1000) {
                tts!!.stop()
                btnPressTime = System.currentTimeMillis()
                return@OnClickListener
            }
            if (System.currentTimeMillis() <= btnPressTime + 1000) {

                val transaction = activity!!.supportFragmentManager.beginTransaction()
                val galleryFragment = GalleryFragment()
                transaction.replace(R.id.framelayout, galleryFragment) //번들 보내줌
                transaction.commit()

                //


            }
        })
        return rootView
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun speakOut() {
        tts!!.setPitch(1f) // 음성 톤 높이 지정
        tts!!.setSpeechRate(0.8.toFloat()) // 음성 속도 지정
        tts!!.speak(
            "상대방의 감정을 읽고싶다면 화면을 길게 누르세요 \n 자신의 표정이 어떤지 확인하고 싶다면 화면을 두번 터치하세요.",
            TextToSpeech.QUEUE_FLUSH,
            null,
            "id1"
        )
    }

    override fun onDestroy() {
        if (tts != null) { // 사용한 TTS객체 제거
            tts!!.stop()
            tts!!.shutdown()
        }
        super.onDestroy()
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    override fun onInit(status: Int) { // OnInitListener를 통해서 TTS 초기화
        if (status == TextToSpeech.SUCCESS) {
            val result = tts!!.setLanguage(Locale.KOREA) // TTS언어 한국어로 설정
            if (result == TextToSpeech.LANG_NOT_SUPPORTED || result == TextToSpeech.LANG_MISSING_DATA) {
                Log.e("TTS", "This Language is not supported")
            } else {
                speakOut() // onInit에 음성출력할 텍스트를 넣어줌
            }
        } else {
            Log.e("TTS", "Initialization Failed!")
        }
    }

}
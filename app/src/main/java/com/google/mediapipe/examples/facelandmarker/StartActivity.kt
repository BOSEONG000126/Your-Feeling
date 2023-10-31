package com.google.mediapipe.examples.facelandmarker

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class StartActivity : AppCompatActivity() {
    private var mainFragment: mainFragment? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)
        mainFragment = mainFragment()
        //프래그먼트 매니저 획득
        val fragmentManager = supportFragmentManager
        //프래그먼트 Transaction 획득
        //프래그먼트를 올리거나 교체하는 작업을 Transaction이라고 합니다.
        val fragmentTransaction = fragmentManager.beginTransaction()
        //프래그먼트를 FrameLayout의 자식으로 등록해줍니다.
        fragmentTransaction.add(R.id.framelayout, mainFragment!!)
        //commit을 하면 자식으로 등록된 프래그먼트가 화면에 보여집니다.
        fragmentTransaction.commit()
    }
}
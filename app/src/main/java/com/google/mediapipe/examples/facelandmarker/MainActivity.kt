/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.facelandmarker

import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.KeyEvent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.mediapipe.examples.facelandmarker.databinding.ActivityMainBinding
import com.google.mediapipe.examples.facelandmarker.fragment.GalleryFragment
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var activityMainBinding: ActivityMainBinding
    private val viewModel : MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
        val navController = navHostFragment.navController
        activityMainBinding.navigation.setupWithNavController(navController)
        activityMainBinding.navigation.setOnNavigationItemReselectedListener {
            // ignore the reselection
        }
    }

    override fun onBackPressed() {
        val fragmentManager = this.supportFragmentManager

        // 현재 보여지고 있는 프래그먼트 가져오기
        val currentFragment = fragmentManager.findFragmentById(R.id.fragment_container)

        // 원하는 프래그먼트로 이동할 때의 로직을 작성합니다.
        if (currentFragment is GalleryFragment) {
            // MyFragment에서 뒤로 가기 키를 처리하고 싶은 경우
            // 처리할 로직을 작성하고 프래그먼트를 변경합니다.
            val transaction = this.supportFragmentManager.beginTransaction()
            val startFragment = startFragment()
            transaction.replace(R.id.fragment_container, startFragment)
            transaction.commit()
        } else {
            // 다른 프래그먼트에서 뒤로 가기 키를 처리하고 싶은 경우
            // 기본 동작인 액티비티의 onBackPressed() 메서드를 호출합니다.
            finish()
        }
    }








}

# 시각 장애인 의사 소통 보조 서비스 Your Feeling
<img src="https://github.com/BOSEONG000126/Your-Feeling/assets/116350240/d269d51d-e68b-4990-8ffe-8993d14e500c"  width="1000" height="350">


## Members
  * 김보성 (Team Leader) - Android , modeling
  * 김대현 - data analysis
  * 권상우 - design
<br/>

## Introduction
### Background
  + 사람은 의사소통을 할 때 표정을 통하여 의사를 전달하는 경우가 대다수 있다.
  + 시각 장애인의 경우 상대방의 표정을 파악하지 못하여 의사소통의 어려움을 겪는다.

### Contribution
  + 시각의 청각화로 시각 장애인의 원활한 의사소통을 돕는다.
  + 사진 속 사람의 표정을 읽기 힘든 시각 장애인을 도와준다.
<br/>

## Data Engineering
### [1] 데이터 수집
<img src="https://github.com/BOSEONG000126/Your-Feeling/assets/116350240/22abba01-06ca-49c6-80e8-98e149956c7a"  width="300" height="400">

  + 대상자의 얼굴에 랜드마크 좌표를 찍은 뒤 여러 표정을 짓고 좌푯값을 저장하여 데이터를 수집합니다.
  + 표정은 무표정 , 기쁨 , 화남 , 슬픔 , 놀람 , 경멸로 6가지 표정의 데이터를 수집합니다.
<br/>

### [2] 데이터 추출

<img src="https://github.com/BOSEONG000126/Your-Feeling/assets/116350240/667f7998-7859-4d13-9e66-81e7e6e04b9c" width="900" height="350">

  + Random Forest를 이용하여 랜드마크 좌표의 중요도를 분석합니다.
  + 각 표정과 좌표의 상관계수를 그려 표정에따른 상관계수가 높은 특징을 추출합니다.
  + KDE를 그려 분산 그래프를 분석하여 중요한 랜드마크 좌표 데이터만 추출합니다.
<br/>

### [3] 모델링

 + 추출된 좌표를 이용하여  표정을 구분하는 분류 모델을 만듦니다.
 + 다양한 분류 모델 중 정확도가 높은 모델을 선정하여 모델링 합니다.
<br/>


## 서비스 개발

### [1] 서비스 컨셉
<img src="https://github.com/BOSEONG000126/Your-Feeling/assets/116350240/a14c3bf0-7763-4217-944e-5d6c93a70fd7" width="800" height="300">

 + 상대방의 표정인지가 어려운 시각장애인을 위한 의사소통 보조 서비스를 만들고자 합니다.
 + 청각의 시각화로 앞을 보기 어려운 시각 장애인을 위하여 상대방의 표정을 음성으로 나타내어 줍니다.

<br/>

### [2] 기능
<img src="https://github.com/BOSEONG000126/Your-Feeling/assets/116350240/279a1297-9793-4244-8ef6-2ce6337ff485" width="800" height="400">

  + 실시간 상대방 감정 체크
  + 사진 속 사람의 감정 체크
  + 시각 장애인을 위한 음성안내 , 터치 횟수 조절의 사용자화된 기능
<br/>

### [3] 기대효과
  + 원활한 의사소통으로 인한 사회적 거리감 해소
  + 시각 장애인의 사교성 증가
  + 의사소통으로 인한 자신감 향상

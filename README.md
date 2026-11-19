OplusGyroKR

ColorOS / OPlus Game Assistant의 Hyper Response Engine 및 자이로스코프 관련 기능을 수정·확장하기 위한 LSPosed / libxposed 기반 모듈입니다.

Features

⚡ Hyper Response Engine

OPlus Game Assistant의 Hyper Response Engine 관련 기능을 후킹하여 활성화 및 동작을 수정합니다.

기기 또는 Game Assistant의 기본 설정에 따라 제한되어 있는 응답 관련 기능을 런타임에서 조정하는 것을 목표로 합니다.

🎯 Gyroscope

게임 환경에서 사용되는 자이로스코프 관련 동작을 수정 및 확장합니다.

OPlus Game Assistant와 게임 사이의 자이로 관련 동작을 분석하고, 기기에서 제공되는 자이로 기능을 게임 환경에서 활용할 수 있도록 하는 것을 목표로 합니다.

How It Works

시스템 APK를 직접 수정하지 않고 LSPosed / libxposed를 이용한 런타임 후킹 방식으로 동작합니다.

OPlus Game Assistant 및 관련 프로세스의 클래스와 메서드를 후킹하여 Hyper Response Engine과 자이로스코프 관련 동작을 변경합니다.

Requirements

- Root 권한
- LSPosed 또는 호환되는 libxposed 프레임워크
- ColorOS / OPlus 기반 기기
- OPlus Game Assistant / Game Space

Supported Games

기본으로 포함된 패키지 (별도 설정 불필요):

- com.pubg.krmobile (배틀그라운드 모바일, 한국)
- com.tencent.ig (PUBG MOBILE, 글로벌)
- com.rekoo.pubgm (PUBG MOBILE, 대만)
- com.vng.pubgmobile (PUBG MOBILE, 베트남)

Adding More Games

코드를 수정하거나 다시 빌드하지 않아도, 설정 파일로 대상 게임을 추가할 수 있습니다.

1. 루트 권한 파일 매니저로 아래 경로에 gyrokr_packages.txt 파일을 만듭니다.

   /sdcard/Android/data/com.oplus.games/files/gyrokr_packages.txt

   (위 경로를 읽지 못하는 기기에서는 /data/data/com.oplus.games/files/gyrokr_packages.txt 도 확인합니다. 이 경로는 파일 소유자를 게임 어시스턴트 앱으로 맞춰야 합니다.)

2. 한 줄에 패키지명 하나를 적습니다. # 뒤는 주석입니다.

   com.pubg.imobile   # 예시
   com.example.game

3. 저장하면 약 3초 안에 자동으로 반영됩니다. 게임 어시스턴트를 다시 시작할 필요는 없습니다. 하이퍼 리스폰스 목록은 설정 화면을 다시 열어야 반영될 수 있습니다.

기본 목록은 파일 없이도 항상 포함되며, 파일에 적은 패키지는 그 위에 더해집니다.

패키지명 확인 방법

- 설정 > 앱 > 해당 앱 > 앱 정보 화면 (기기에 따라 표시되지 않을 수 있음)
- adb shell pm list packages | grep 게임이름
- Play 스토어 웹 주소의 id= 뒤 값

동작 확인

logcat에서 OplusGyroKR 태그를 확인합니다. 설정 파일이 읽히면 target games = [...] 로그에 추가한 패키지가 나타납니다. 파일을 읽지 못하면 config read ERROR 로그가 남습니다.

Compatibility

ColorOS 및 OPlus Game Assistant의 내부 구현에 의존합니다.

따라서 다음 환경에 따라 동작 여부가 달라질 수 있습니다.

- Android 버전
- ColorOS 버전
- Game Assistant 버전
- 기기 모델
- 게임

OPlus 시스템 앱이 업데이트되면서 내부 클래스나 메서드가 변경될 경우 모듈이 정상적으로 동작하지 않을 수 있습니다.

Disclaimer

이 모듈은 연구 및 테스트 목적으로 제작되었습니다.

ColorOS / OPlus의 비공개 내부 구현에 의존하며 공식적으로 지원되는 기능이 아닙니다.

사용 환경에 따라 게임 또는 시스템의 동작에 문제가 발생할 수 있으며, 사용에 따른 문제에 대해서는 제작자가 책임지지 않습니다.

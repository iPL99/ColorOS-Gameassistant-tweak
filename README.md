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


# VisualVroom - Hearing Impaired Vehicle Alert Driving Assistance

## Overview
VisualVroom is an Android application designed to assist people with hearing impairments by detecting and identifying the direction of emergency vehicles and other important vehicle alerts. The app uses advanced audio processing and deep learning to recognize different types of vehicle sounds and their directions, providing visual and haptic alerts through both smartphone and wearable devices.

## Features
- Real-time detection of emergency vehicle sirens and car horns
- Directional sound recognition (Left, Right, and Center)
- Support for multiple vehicle types:
  - Ambulance sirens
  - Police car sirens
  - Fire truck sirens
  - Car horns
- Wear OS integration for instant alerts on smartwatches
- Haptic feedback with distinct vibration patterns for different vehicles
- Real-time visual notifications with direction indicators
- Edge computing capabilities for rapid response

## Technical Requirements
- Android 8.0 (API level 26) or higher
- Wear OS 2.0 or higher for smartwatch functionality
- Stereo microphone support
- Internet connection for model updates
- Device with audio recording capabilities

## Installation
1. Clone the repository
2. Open the project in Android Studio
3. Configure your Wear OS device (if using smartwatch features)
4. Build and run the application

## Architecture
The app utilizes a multi-component architecture:

### Mobile Components
- `MainActivity`: Main interface and service coordinator
- `AudioRecordingService`: Handles multi-channel audio recording
- `WearNotificationService`: Manages notifications and Wear OS communication
- `MultiAudioRecorder`: Processes stereo audio input
- `AudioSender`: Handles communication with backend services

### Wear OS Components
- `WearAlertActivity`: Manages smartwatch display and alerts
- Custom vibration patterns for different vehicle types
- Real-time alert display

### Backend Integration
- WebSocket connection for real-time audio processing
- Machine learning model for sound classification
- Edge computing implementation for low-latency response

## Permissions Required
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.VIBRATE"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.INTERNET"/>
```

## Usage
1. Launch the application
2. Grant necessary permissions for audio recording and notifications
3. The app will run in the background, monitoring for vehicle alerts
4. When a vehicle is detected:
   - Phone screen displays vehicle type and direction
   - Smartwatch provides haptic feedback and visual alert
   - Distinct vibration patterns indicate different vehicle types

## Alert Types
- **Ambulance**: Short-short vibration pattern
- **Police**: Medium-medium vibration pattern
- **Fire Truck**: Long-long vibration pattern
- **Car Horn**: Single vibration

## Contributing
Contributions are welcome! Please read our contributing guidelines and submit pull requests for any enhancements.

## Privacy Considerations
- Audio is processed in real-time and is not stored
- All processing is done locally on the device
- No personal data is collected or transmitted

## License
[TBD]

## Support
For issues and feature requests, please use the GitHub issues tracker or contact our support team.

## Acknowledgments
- Built using Android Studio
- Implements TensorFlow Lite for edge ML
- Uses WebSocket for real-time communication
- Integrates with Wear OS for smartwatch functionality

## Version History
- Current Version: 1.0.0
- Release Date: [TBD]


# Gemini AutoPilot (Android)

Experimental Android accessibility helper for Gemini.

## Intended workflow
1. Open Gemini and send a master prompt asking it to generate a long video in sequential one-minute clips.
2. Enable this app's Accessibility Service.
3. Return to Gemini, keep it in the foreground, and start monitoring.
4. When Gemini appears to finish a response, the service waits for the configured quiet period and attempts to send **Next**.

## Limitations
- Experimental prototype; automatic detection and tapping are not guaranteed.
- Gemini UI/accessibility changes can break detection.
- Keep the phone unlocked and Gemini in the foreground.
- Stop monitoring before the final clip; the app cannot know which clip is last.
- Review generated clips; do not rely on unattended operation.

## Build
GitHub Actions builds a debug APK on pushes to the default branch. Download it from the workflow run's Artifacts section.

## Privacy
Accessibility is used to inspect Gemini's visible UI and interact with its composer when a response appears complete.
# Thingsbook v3.0.0-alpha.10 (Pre-Release · Build 86)

## Highlights

### Instant, Zero-Flash Language Switching on Android
- **No More Screen Flash or Activity Reload:** Declared `locale|layoutDirection` in `MainActivity`'s `android:configChanges` and added live reactive Compose locale propagation (`ProvideLiveAppLocale` + `ApplyInheritedLiveAppLocaleIfNeeded`) across both the main workspace and open `Dialog` / `DropdownMenu` overlays.
- **Seamless Parity with ChromeOS & macOS:** Selecting any language (`System Default`, `English`, `Deutsch`, `Italiano`, `Français`, `Español`) in **Settings** or the top bar now updates every visible label in-place on the next frame without destroying `MainActivity`, flashing the window, or re-animating the Settings modal.

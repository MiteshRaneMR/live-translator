# Bayan (بيان)

Android app that listens to a conversation and shows live captions with translation.
Speakers can switch between Arabic and English at any point. The app detects the
language as each sentence is spoken and translates it into the other one.

- Grey line: what was said. White line: the translation.
- Arabic is right-aligned and English is left-aligned.
- The share button exports the full timestamped transcript.
- The screen stays on while the app is listening.

## One-time setup: Azure Speech key

1. Go to portal.azure.com and create a **Speech** resource (Azure AI services → Speech).
   - Pricing tier: **Free F0** to start.
   - Region: pick one close to Riyadh, e.g. **UAE North** (`uaenorth`) or **West Europe** (`westeurope`).
2. Open the resource → **Keys and Endpoint** → copy **KEY 1** and the **Location/Region**.
3. In the app, tap the settings icon, then paste the key and type the region in lowercase with no spaces, e.g. `uaenorth`.

The key is stored only on the phone.

## Build

GitHub Actions builds the APK on every push to `main`. To install it:
**Actions → latest run → Artifacts → Bayan-apk**, then unzip and install the APK.

## License

MIT. See [LICENSE](LICENSE). Contributions welcome, especially Gulf and Levantine dialect handling.

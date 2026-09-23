# FloatRate

FloatRate is a native Android market companion for traders who need a selected crypto pair visible while another app is in the foreground. It uses Binance's public, read-only REST and WebSocket endpoints and does not request a Binance login, API key, wallet access, or trading permission.

## Included in this MVP

- Live last price and 24-hour percentage change.
- Public Binance ticker stream with automatic status states.
- Candlestick, line, and pointer/crosshair chart modes.
- Drag across POINTER mode to inspect a candle's open, high, low, close, and time.
- 1H, 4H, 1D, and 1W viewing presets mapped to 1m, 5m, 15m, and 1h candles.
- Quick pairs: BTC/USDT, ETH/USDT, SOL/USDT, BNB/USDT, and XRP/USDT.
- Custom Binance pair entry, such as `DOGEUSDT`.
- A draggable floating widget using Android's application overlay window.
- Compact mode: tap the up/down control on the widget to collapse it to a small price strip.
- Foreground notification so Android can keep the market stream alive while Binance is open.
- Dark, high-contrast UI designed for a phone screen and quick glances.

## Run it

1. Open the `FloatRate` folder in Android Studio Ladybug or newer.
2. Use JDK 17 and let Android Studio sync the Gradle project.
3. Install to an Android phone or emulator running Android 8.0/API 26 or newer.
4. Open FloatRate and select a pair.
5. Tap **ALLOW** and enable **Display over other apps** for FloatRate in Android Settings.
6. Choose a chart style and time preset, then tap **FLOAT ... ABOVE OTHER APPS**.
7. Open Binance. The widget can be dragged by its handle and collapsed with the `⌃` control.

Android may also ask for notification permission. It is used for the required foreground-service notification; it is not used for advertising.

## Network endpoints

- REST candles: `https://api.binance.com/api/v3/klines`
- WebSocket ticker: `wss://stream.binance.com:9443/ws/<symbol>@ticker`

Some regions, networks, or exchanges may block Binance domains. The app surfaces an `OFFLINE` state rather than fabricating a price.

## Important product notes

- This build is a market-awareness tool, not a trading terminal. There is intentionally no order entry or account connection.
- Overlay permission is a sensitive Android permission and must be granted by the user in system settings. The app only creates its own widget window.
- The foreground notification is required by modern Android versions for a persistent live overlay.
- The chart history uses 80 candles per selected preset. The live ticker updates the most recent candle while the app is connected.

## Project structure

- `app/src/main/java/com/floaterate/app/MainActivity.kt` — dashboard and pair selection.
- `app/src/main/java/com/floaterate/app/FloatingOverlayService.kt` — foreground service and draggable overlay.
- `app/src/main/java/com/floaterate/app/ChartView.kt` — custom Canvas chart with candlesticks, line, and pointer modes.
- `app/src/main/java/com/floaterate/app/BinanceMarketRepository.kt` — read-only Binance REST/WebSocket client.

## Production follow-ups

For a Play Store release, add a branded launcher icon, privacy policy, crash reporting, exponential WebSocket reconnect backoff, exchange-region fallback, accessibility labels for the floating controls, and device-specific overlay testing across Android 12–15.

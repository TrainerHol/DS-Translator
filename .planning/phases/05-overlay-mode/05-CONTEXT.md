# Phase 5: Overlay Mode - Context

**Gathered:** 2026-03-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Users playing dual-screen games (DS/3DS) where the bottom screen shows game content can access translations via a floating overlay on the game screen, instead of the secondary Presentation display. The overlay provides two display modes (overlay-on-source and scrollable panel), full word interaction, and customizable appearance. The app must never take focus or minimize the running game.

Requirements: OVLY-01, OVLY-02, OVLY-03, OVLY-04, OVLY-05

</domain>

<decisions>
## Implementation Decisions

### Two overlay display modes
- **Overlay-on-source mode**: Semi-transparent translation labels placed at the screen coordinates of each OCR text block, visually replacing the Japanese text with English. Labels have an auto-offset option so they don't completely block the original text underneath.
- **Scrollable panel mode**: A floating, resizable panel containing a scrollable translation list — same UI concept as the existing Presentation display (dark theme, Japanese prominent, sentence with inline tappable words), rendered as a draggable overlay instead of on the secondary screen.
- User switches between modes via a bubble menu toggle AND a persistent setting saved per profile.

### Overlay-on-source label behavior
- Labels are semi-transparent with a fixed user-chosen style (e.g., white text on dark semi-transparent background) — no auto-contrast
- Labels are tappable — tapping expands to show word-level detail (dictionary lookup, audio), same as panel mode
- When game dialog advances, old labels are cleaned up and new ones appear smoothly (Claude's discretion on fade/replace animation)
- Auto-offset option available so labels don't completely obscure the original Japanese text

### Scrollable panel
- Default size: small (~20% of screen)
- Resizable — user drags an edge to adjust
- Draggable — user can reposition anywhere on the screen
- Easily minimizable/toggleable back to bubble with a single tap
- Sentence display with inline tappable words (matching existing Presentation display pattern)
- Customizable appearance: transparency, text color, text size — configured in settings page per profile

### Panel interaction
- Tapping a word (in either mode) shows a small tooltip near the tapped word: dictionary definition, reading, JLPT level, audio button. Dismisses on tap-outside.
- **Pin toggle**: Panel can be pinned open so it persists while playing (doesn't auto-collapse on outside tap)
- **Lock toggle**: When locked, all touch events pass through the panel to the game underneath — panel becomes read-only and non-interactive. User must explicitly unlock to interact with the panel again. Critical for touch-heavy games.
- Audio playback controlled by user settings (user decides when audio plays via existing TTS settings)

### Mode switching and bubble menu
- Bubble menu gets two new toggles: overlay mode (overlay-on-source / panel / off) and screen switch (move bubble to other display)
- Bubble menu layout: flat responsive grid (all buttons visible when expanded, no sub-menus)
- Switching modes is clean: when toggling overlay mode on, the Presentation display can optionally stay running or be dismissed (user setting). When switching off overlay mode, all overlay views are removed and cleaned up.
- Bubble physically moves to the other screen when screen switch is tapped (one bubble, relocates — no mirroring)
- Panel and bubble can live on either screen (top or bottom display)

### State cleanup on mode switch
- Switching from overlay-on-source to panel mode: all floating source labels are removed before panel appears
- Switching from panel to overlay-on-source: panel is dismissed and removed from WindowManager before labels appear
- Switching overlay mode off entirely: all overlay views (labels, panel, tooltips) are removed from WindowManager, touch passthrough restored
- Switching bubble between screens: bubble is removed from current screen's WindowManager, re-added to target screen's WindowManager, position reset or restored from per-screen saved position
- Profile switch: all overlay state for previous profile is fully cleaned up before new profile's overlay config is applied
- Service stop / app exit: all overlay views removed, no leaked WindowManager views

### Touch passthrough
- When panel is collapsed (just bubble visible): all touch events pass through to game — FLAG_NOT_TOUCHABLE on overlay areas
- When panel is open but unlocked: panel consumes touches on itself, passes through everywhere else
- When panel is locked: all touches pass through everywhere including the panel area — panel is visible but non-interactive
- Overlay-on-source labels: pass through touches by default, become interactive only on direct tap (or pass through when panel is locked)

### Per-screen position memory
- Overlay panel remembers separate position and size per screen (top display vs. bottom display)
- When bubble moves to a screen, panel restores that screen's saved position/size
- Stored per profile

### App must not steal focus
- Overlay mode is specifically for DS/3DS games where both screens show game content — the app cannot take foreground focus or it will minimize the game
- All overlay UI must use TYPE_APPLICATION_OVERLAY with appropriate flags
- No Activity launches during overlay mode — everything is service-driven WindowManager overlays
- Profile switching, mode toggling, and all overlay interactions happen entirely within the overlay service layer

### Claude's Discretion
- Overlay-on-source label animation (fade in/out, slide, or instant replace)
- Tooltip positioning logic (avoid going off-screen)
- Bubble menu grid layout design (icon arrangement for 8+ buttons)
- Panel resize handle design and drag interaction
- Lock/unlock visual indicator on the panel
- How "auto-offset" is calculated for overlay-on-source labels
- Panel minimize/restore animation
- Exact FLAG combinations for each touch passthrough state

</decisions>

<specifics>
## Specific Ideas

- The overlay-on-source mode is the key differentiator — translations appear right where the game text is, like a live subtitle layer
- Pin + lock is essential for touch-heavy games (many DS games are stylus/touch-based) — locked panel should be completely invisible to touch input
- The app absolutely cannot minimize DS games by taking focus — everything must be service-layer overlays
- Cleanup on every state transition is critical — no ghost views, no leaked WindowManager attachments, no stale touch interceptors
- Panel should feel lightweight and quick to toggle — one tap to show, one tap to hide
- Small default panel size (~20%) because the game is the focus, translations are supplementary

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `FloatingButtonService`: Already implements SYSTEM_ALERT_WINDOW overlay, draggable bubble, expand/collapse menu, FLAG_SECURE, WindowManager lifecycle management. Overlay mode extends this service or creates a sibling service using the same patterns.
- `RegionEditOverlay`: Demonstrates fullscreen WindowManager overlay with touch handling (FLAG_NOT_FOCUSABLE + FLAG_SECURE), confirm/cancel lifecycle. Pattern reusable for overlay panel.
- `TranslationPresentation` / `TranslationListScreen`: Existing Compose UI for translation display (dark theme, sentence list, word segmentation, audio buttons). Panel mode can reuse this composable in a ComposeView within a WindowManager overlay.
- `CaptureService`: Pipeline already produces `OcrTextBlock` with bounding box coordinates — overlay-on-source mode uses these coordinates to position translation labels.
- `SettingsRepository`: Handles all persistent settings (DataStore + EncryptedSharedPreferences). Overlay appearance settings (transparency, colors, sizes, per-screen positions) extend this.
- `PresentationLifecycleOwner`: Custom LifecycleOwner for ComposeView in non-Activity contexts — reusable for overlay ComposeView.
- `TtsManager`: speak() with queue/flush modes — audio in overlay mode uses this directly.

### Established Patterns
- Static companion MutableStateFlow on CaptureService for cross-component state observation
- FLAG_SECURE on all overlay windows to prevent OCR feedback loop
- ObjectAnimator for overlay animations (expand/collapse, pulse)
- CoroutineScope per service with SupervisorJob for lifecycle-safe async work
- RunBlocking for synchronous settings reads in service onCreate (position restore)

### Integration Points
- `CaptureService.captureAndTranslate()`: After OCR, overlay-on-source mode reads OcrTextBlock bounding boxes to position labels; panel mode updates the scrollable list
- `FloatingButtonService`: Bubble menu needs new grid layout, overlay mode toggle, screen switch toggle
- `CaptureService.translations` StateFlow: Panel mode observes this same flow the Presentation display uses
- `SettingsRepository`: New keys for overlay mode, appearance settings, per-screen positions, panel size
- `WindowManager`: Multiple overlay views need careful z-ordering (bubble on top, panel/labels below, game underneath)
- DisplayManager: Moving bubble/panel between screens requires accessing both displays

</code_context>

<deferred>
## Deferred Ideas

- Auto-detect DS/3DS game and suggest overlay mode (explicitly out of scope per PROJECT.md — manual profile selection only)
- OCR region auto-detection for common DS game dialog positions
- Overlay mode for non-DS games (general-purpose overlay translation without secondary display)
- Gesture shortcuts (swipe to dismiss panel, two-finger drag to resize)

</deferred>

---

*Phase: 05-overlay-mode*
*Context gathered: 2026-03-03*

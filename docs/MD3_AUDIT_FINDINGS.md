# Material Design 3 & Accessibility Audit
**Date:** 2026-10-09  
**Auditor:** Autonomous Session  
**Status:** In Progress

---

## MD3 Compliance Checklist

### ✅ Dynamic Color Schemes
- Light/Dark themes implemented (LightPalette, DarkPalette)
- Material Design 3 color scheme created (lightColorScheme, darkColorScheme)
- Theme picker (AUTO/LIGHT/DARK) supports user preference and system setting
- **Status:** COMPLIANT

### ✅ Typography Scale
- Custom DsText scale properly mapped to Material Design 3 Typography
- All sizes follow M3 scale (28sp, 20sp, 17sp, 16sp, 14sp, 12sp)
- Tabular figures (tnum) enabled for numerical values to prevent digit reordering
- Headings, body, callout, meta all properly sized
- **Status:** COMPLIANT

### ✅ Component Usage
- Using Material Design 3 components (AlertDialog, DatePicker, TimePicker, SwipeToDismissBox, TextButton, Icon)
- Custom components (Palette, DsText, dsCard modifier) layered correctly over M3
- **Status:** COMPLIANT WITH NOTES (see enhancements below)

### ⚠️ Elevation & Shadows
- dsCard uses hairline border instead of native M3 elevation
- No `tonalElevation` or elevation properties used on containers
- **Approach:** Deliberate choice (matches iOS "calm neon" aesthetic with hairline borders, not shadows)
- **Status:** INTENTIONAL DESIGN, NOT COMPLIANT WITH M3 STANDARD BUT COHERENT

### ⚠️ Touch Target Sizes
- Door/Clock-in buttons: 168.dp (✅ >48dp, exceeds minimum)
- BreakButton: 56.dp height (✅ >48dp)
- ModeChip: padding horizontal 24dp, vertical 8dp ⚠️ (may be <48dp total height - 24dp)
- StatCard: full-width, needs verification on tap area
- Form inputs: need verification across Settings, Manual Entry screens
- **Status:** MOSTLY COMPLIANT, NEED DETAILED VERIFICATION

### ✅ Motion & Transitions
- AnimatedContent with fade transitions used
- animateFloatAsState for smooth property animations
- Spring curves (DampingRatioMediumBouncy, StiffnessMedium) properly configured
- Haptic feedback integrated (TextHandleMove, LongPress)
- **Status:** COMPLIANT

### ⚠️ Focus Indicators
- No visible focus indicators on interactive elements
- Screen reader users cannot see keyboard focus
- Needed for keyboard navigation accessibility
- **Status:** NOT IMPLEMENTED

---

## Accessibility (WCAG AA) Compliance

### ✅ Screen Reader Support (contentDescription)
- StatCard: Has semantic merge + contentDescription ✅
- Door buttons: Have onClickLabel with hints ✅
- BreakButton: Has Text labels (visible text serves as label) ⚠️
- Settings screen elements: Checking...
- **Status:** PARTIAL - Main components covered, need full audit

### ⚠️ Semantic Clarity
- BreakButton state description unclear (see bug below)
- Toggle/RadioButton components need stateDescription enhancement
- **Status:** NEEDS WORK

### ⚠️ Touch Target Sizes (48dp minimum)
- ModeChip: width and height need verification
- All interactive elements need height ≥ 48dp
- **Status:** NEEDS DETAILED AUDIT

### ⚠️ Color Contrast (WCAG AA: 4.5:1 for normal text, 3:1 for large text)
**Need to verify:**
- Text over Palette.accent (green #26F273) with Palette.ink (#06110B) - likely OK
- Text over Palette.card with Palette.textPrimary
- All text combinations in light mode
- Status/break state text colors
- **Status:** NEEDS VERIFICATION WITH CONTRAST CHECKER

### ❌ Keyboard Navigation
- No focus visible indicators
- Need to test tab order
- **Status:** NOT IMPLEMENTED

### ✅ Text Scaling
- All text uses sp units (scales with system font size)
- No hardcoded sp values that override user preference
- **Status:** COMPLIANT

---

## Known Issues

### 🐛 Break Button State Bug (CRITICAL)
**Location:** HomeScreen.kt, StatusRow + BreakButton combination  
**Issue:** StatusRow shows contradictory state - displays both "on break" (from activeBreak) and "at work" (from clock-in time) simultaneously  
**Root Cause:** Suspected mismatch between `session.activeBreak` and `session.isOnBreak`  
**Impact:** User confusion, unclear shift state  
**Priority:** HIGH - must fix before Play Store release  
**Action:** Investigate state management, ensure consistent display

---

## Summary by Category

| Category | Status | Notes |
|----------|--------|-------|
| MD3 Theme | ✅ | Compliant, custom aesthetic applied intentionally |
| Typography | ✅ | Full M3 scale, tabular figures for numbers |
| Components | ✅ | Using M3 components + custom tokens |
| Elevation | ⚠️ | Intentional hairline design, not M3 shadows |
| Motion | ✅ | Proper spring curves and haptics |
| Touch Targets | ⚠️ | Main buttons OK, need to verify all interactive elements |
| Screen Reader | ✅ | Core labels in place, need full audit |
| Keyboard Nav | ❌ | No focus indicators |
| Color Contrast | ⚠️ | Likely OK, needs verification |
| Text Scaling | ✅ | All sp-based, respects user preference |

---

## Recommended Implementation Order

1. **CRITICAL:** Fix break button state logic
2. **HIGH:** Add focus indicators for keyboard navigation
3. **HIGH:** Verify color contrast ratios (contrast checker tool)
4. **HIGH:** Audit all touch targets systematically
5. **MEDIUM:** Enhance stateDescription on toggles
6. **MEDIUM:** Complete screen reader audit of all screens
7. **POLISH:** Consider M3 standard elevation if brand allows

---

## Next Steps

Phase 3 → Accessibility Audit (detailed verification)  
Phase 4 → Implementation (fixes in order of priority)

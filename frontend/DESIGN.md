# EventPass Frontend Design System

## Direction

EventPass combines editorial clarity, modern SaaS usability, and the energy of live events. The interface should feel premium, confident, restrained, and commercially credible. Event content and photography will lead future product experiences; interface decoration must not compete with them.

Avoid generic dashboard styling, heavy gradients, glass effects, neon palettes, oversized shadows, decorative animation, and indiscriminate pill-shaped controls.

## Foundations

The implemented CSS custom properties live in `src/styles/tokens.css`. Components consume semantic tokens rather than embedding unrelated colors or spacing values.

### Color

The public experience is light-first with a warm neutral background (`#F8F7F4`), white surfaces, near-black text, and a restrained violet accent. Semantic success, warning, error, and information colors each include a quiet surface color for status treatment.

The token set includes:

- background, surface, and elevated surface;
- primary, secondary, and muted text;
- standard and strong borders;
- accent, hover, active, soft, and on-accent colors;
- success, warning, error, and information colors;
- focus, overlay, and compatible dark-surface values.

Status components always pair color with text or a visible mark. Color alone must never communicate a critical state.

### Typography

Manrope Variable is bundled locally through `@fontsource-variable/manrope`, avoiding a runtime font request. The responsive hierarchy provides:

- display;
- heading levels 1–3;
- body and body-small;
- label;
- caption.

Headings use restrained negative tracking and balanced wrapping. Body copy keeps a comfortable line height. Additional font families should not be introduced without a clear product need.

### Spacing

The spacing scale is based on 4 pixels:

```text
4, 8, 12, 16, 20, 24, 32, 40, 48, 64, 80, 96, 128px
```

Use the `--space-*` tokens and layout primitives instead of one-off values. Large section spacing contracts on smaller screens.

### Radius and elevation

Radii are deliberately restrained:

| Usage          | Radius |
| -------------- | -----: |
| Controls       |    8px |
| Cards          |   12px |
| Panels         |   16px |
| Large surfaces |   24px |

The low, medium, and high shadow tokens are reserved for genuine elevation. Cards primarily use border contrast and a low shadow; dialogs and transient toasts receive stronger elevation.

### Motion

Transitions are short and limited to interaction feedback such as hover, focus, and active states. The global reduced-motion rule effectively removes transitions and repeating animations when `prefers-reduced-motion: reduce` is enabled.

## Components

### Actions

`Button` supports `primary`, `secondary`, `outline`, `ghost`, and `danger` variants, three sizes, loading state, native disabled behavior, visible keyboard focus, and pressed feedback. Loading buttons retain a textual label and expose `aria-busy`.

### Forms

The form foundation includes `Label`, `Input`, `Select`, `Textarea`, `Checkbox`, `Radio`, and `FieldError`. Controls support native labels, descriptions, `aria-invalid`, disabled treatment, keyboard focus, and touch-friendly sizing. Product forms remain responsible for connecting label, description, and error identifiers correctly.

### Surfaces and status

- `Card` presents a bounded content unit.
- `Panel` groups larger page regions.
- `Badge` displays compact neutral or semantic metadata.
- `Alert` communicates persistent information, success, warning, or error states.
- `Dialog` builds on the native dialog element for modal focus and Escape-key behavior.
- `Toast` and `ToastViewport` provide an accessible live-region foundation for transient feedback.

Dialogs require a title and close callback. Destructive confirmations must use explicit action copy rather than relying on color.

### Loading and state feedback

`Spinner`, `Loading`, `Skeleton`, `EmptyState`, `ErrorState`, and `SuccessState` provide consistent asynchronous and collection states. Spinners include assistive text; decorative skeletons are hidden from assistive technology; state messages combine a heading, description, and optional recovery action.

### Layout

`Container`, `Section`, `Stack`, and `Grid` cover the recurring layout needs without creating a utility abstraction for every CSS property. Containers cap readable widths, sections provide responsive vertical rhythm, stacks control one-dimensional spacing, and grids collapse from desktop columns to a single mobile column.

## Accessibility requirements

- Use semantic HTML before adding ARIA.
- Every interactive control must be keyboard reachable.
- Preserve the global visible focus treatment.
- Associate form labels and errors through identifiers.
- Keep text and meaningful controls at sufficient contrast.
- Provide text or symbols in addition to semantic color.
- Use native disabled state where available.
- Respect reduced-motion preferences.
- Keep mobile targets comfortably touchable.

## Responsive behavior

The system is mobile-first and supports mobile, tablet, desktop, and large desktop layouts. Containers use responsive gutters; grids reduce columns below desktop and become single-column on mobile; typography scales through `clamp()`; dialogs and toast regions stay within the viewport.

Feature screens must design their mobile information hierarchy intentionally rather than merely shrinking desktop arrangements.

## Current boundary

The root route currently displays a component preview so the implemented tokens and primitives can be reviewed together. It is temporary and is not a homepage, event browser, authentication flow, checkout, ticket experience, or management dashboard. Those product capabilities belong to later commits.

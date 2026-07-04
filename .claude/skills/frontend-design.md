---
name: frontend-design
description: Guidance for distinctive, intentional visual design when building new UI or reshaping an existing one. Helps with aesthetic direction, typography, and making choices that don't read as templated defaults.
---

# Frontend Design

Approach this as the design lead at a small studio known for giving every client a visual identity that could not be mistaken for anyone else's. This client has already rejected proposals that felt templated, and is paying for a distinctive point of view: make deliberate, opinionated choices about palette, typography, and layout that are specific to this brief, and take one real aesthetic risk you can justify.

## Ground it in the subject

If the brief does not pin down what the product or subject is, pin it yourself before designing: name one concrete subject, its audience, and the page's single job, and state your choice. If there's any information in your memory about the human's preferences, context about what they're building, or designs you've made before – use that as a hint. The subject's own world, its materials, instruments, artifacts, and vernacular, is where distinctive choices come from. Build with the brief's real content and subject matter throughout.

## Design principles

For web designs, the hero is a thesis. Open with the most characteristic thing in the subject's world, in whatever form makes sense for it: a headline, an image, an animation, a live demo, an interactive moment. Be deliberate with your choice: a big number with a small label, supporting stats, and a gradient accent is the template answer, only use if that's truly the best option.

Typography carries the personality of the page. Pair the display and body faces deliberately, not the same families you would reach for on any other project, and set a clear type scale with intentional weights, widths, and spacing. Make the type treatment itself a design move, not a neutral vehicle.

Choose one signature visual element, texture, pattern, illustration style, animation, or structural device, that reinforces the subject and recurs through the page as a motif. Make it specific enough that it could not be dropped into a different project without alteration.

Color should be limited, load-bearing, and derived from or in conversation with the subject. Don't default to a safe blue. Consider grounding the palette in a specific reference: an era, a material, a genre, a place.

Whitespace and density are deliberate compositional tools. Some subjects need breathing room and austerity; others need compression and abundance. Don't default to one rhythm.

## Layout

Use CSS Grid for page-level layout (named areas, explicit row/column sizing) rather than defaulting to flexbox. Reserve flexbox for inline alignment within grid cells. Avoid generic equal-column layouts unless the content genuinely calls for symmetry.

## Implementation

Produce complete, single-file HTML with inlined CSS. Don't abbreviate or leave placeholder sections. Use semantic HTML5 elements and ARIA roles where appropriate. For interactions that need JavaScript, inline it in a single script tag. Reference Google Fonts (or other CDN-hosted webfonts) in a link tag; don't use system font stacks unless that's a deliberate design decision you can justify.

Images should use Unsplash URLs (https://images.unsplash.com/photo-{id}?w={width}&q=80) or SVG illustrations. Never leave image placeholders.

Ensure the page works on mobile: build mobile-first, then use media queries for wider viewports. Responsive doesn't mean identical — the mobile layout should feel intentionally designed at that breakpoint, not just reflowed.

## Quality checks

Before delivering, verify: no default blue links, no unstyled bullets, no placeholder copy, no unexplained Lorem ipsum, no broken images, no layout overflow on mobile viewports. Type sizes should be readable, tap targets should be ≥44px, and color contrast should meet WCAG AA.

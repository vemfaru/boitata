# Changelog

## Unreleased
- Fire (`Fire`, `drawFlameTrail`, `fireLight`), cast shadows from any drawing (`CastShadow`), window light (`WindowLight`), rain, snow and fog.
- Playground app (desktop and web), offscreen clip renderer, README GIFs, art style guide.
- `SkyWindow(horizon = ...)`: where the horizon stands, so the same sky works as a window above a desk or as a whole scene.
- Fix: the sky's layers and clouds no longer step a whole pixel at a time. They were placed with integer offsets, so a slow parallax sat still for over a second and then jumped; they now move with float translations, and use mipmapped filtering (about three times cheaper per frame).
- Woodcut art for the playground (`samples/playground/.../composeResources`), the art style guide and the prompts that made it.
- First extraction from The Typist: `boitata-core`, `boitata-scene`, `boitata-light`, `boitata-weather`.

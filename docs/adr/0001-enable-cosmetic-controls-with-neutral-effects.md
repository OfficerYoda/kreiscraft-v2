# Enable cosmetic controls with neutral Effects

Effects and Glow ship enabled so their player controls are immediately available, superseding
ADR-9 in `docs/explanation/design-decisions.md`. Effects remains neutral for players without a
selected particle, and selecting one enables emission, avoiding an unsolicited default trail while
requiring no operator configuration.

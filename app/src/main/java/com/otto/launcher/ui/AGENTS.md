# UI

One job: render state and send user intent through callbacks and ViewModels.

May import core and domain. Composable files never import data packages; ViewModels bridge persistence.

Provisional decision (2026-07-20): LauncherScreen keeps its existing state hooks until a separate state-holder refactor.

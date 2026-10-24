# UML diagrams

This folder contains the UML diagrams of the Pomodoro Habit Tracker as
[PlantUML](https://plantuml.com/) source files (`.puml`), plus a rendered PNG
of each one. The `.puml` files are plain text, so they can be read, edited and
version-controlled like code. The diagrams use the real class and method names
from `src/main/java`.

| File | Diagram | What it shows |
|------|---------|---------------|
| `use-case-diagram.puml` | Use case diagram | What the user can do: view the dashboard, create/edit/delete habits, mark a habit done or undo it, choose a timer mode, start/pause/resume/reset the timer and link a focus session to a habit. A second actor, the *background timer check*, records a completed focus session. Every change *includes* saving to `app-data.json`. |
| `class-diagram.puml` | Class diagram | The packages (`model`, `repository`, `service`, `controller`, `view`, `util`) and their main classes. It shows inheritance (`BaseController` and its five subclasses, `NotFoundException extends RuntimeException`), the `HttpHandler` interface, composition (`AppData` holds habits and sessions, `TimerService` owns one `PomodoroTimer`) and which layer uses which. |
| `sequence-diagram.puml` | Sequence diagram | One focus session from start to finish: the browser sends `POST /api/timer/start`, the controller calls `TimerService.start()`, the timer stores its end time, the page keeps the countdown in sync, and when the time is up the background worker saves a `PomodoroSession` through `JsonDataRepository` (temporary file, then rename). |

Rendered images: [use case](use-case-diagram.png) · [class](class-diagram.png) · [sequence](sequence-diagram.png)

## How to preview or render the diagrams

Pick whichever option is easiest for you.

1. **VS Code**: install the extension *PlantUML* (by jebbs), open a `.puml` file
   and press `Alt+D` to preview it. Local rendering needs Java and Graphviz; you can
   also set the extension's *Render* setting to use the PlantUML server instead.
2. **IntelliJ IDEA**: install the plugin *PlantUML Integration*. The preview
   appears next to the `.puml` file.
3. **Docker** (nothing else to install). Run this from the project folder to
   regenerate all PNG files in `docs/uml/`:

   ```bash
   docker run --rm -v "$PWD/docs/uml:/data" -w /data plantuml/plantuml -tpng "*.puml"
   ```

   On Linux, add `--user "$(id -u):$(id -g)"` after `--rm` so the images belong to you.
   In Windows PowerShell, use `${PWD}` instead of `$PWD`.
4. **PlantUML JAR**: download `plantuml.jar` from <https://plantuml.com/download>
   and run `java -jar plantuml.jar -tpng docs/uml/*.puml`. The use case and class
   diagrams also need [Graphviz](https://graphviz.org/download/).
5. **Online**: paste the text of a `.puml` file into <https://www.plantuml.com/plantuml>.
   Note that this sends the diagram text to the PlantUML website.

The PNG files in this folder were rendered with PlantUML 1.2026.9 using option 3.

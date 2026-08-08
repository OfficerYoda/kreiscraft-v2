# How to write tests

Tests live under `src/test/java/de/kreiscraft/`. Run them all with:

```
./gradlew test
```

HTML report: `build/reports/tests/test/index.html`.

---

## 1. Unit-testing pure logic (no Bukkit)

Use plain JUnit Jupiter + AssertJ. No MockBukkit needed. Mock collaborators with Mockito.

**Example** (`DefaultHackLogServiceTest.java`):

```java
class DefaultHackLogServiceTest {

    @TempDir Path tmp;

    private DefaultHackLogService service;

    @BeforeEach
    void setUp() throws IOException {
        HackLogData data = new HackLogData(tmp);
        data.load();
        service = new DefaultHackLogService(data);
    }

    @Test
    void record_persistsEntry() {
        UUID id = UUID.randomUUID();
        service.record(new HackEntry(id, "Alice", HackEntry.Kind.CHEAT, "fly", Instant.now()));
        assertThat(service.forPlayer(id, HackEntry.Kind.CHEAT)).hasSize(1);
    }
}
```

The same pattern applies to any service that accepts collaborators through its constructor — pass a real or mocked `DataFile<T>`, `MessageService`, etc.

---

## 2. Testing a Bukkit listener or command

Use MockBukkit. It provides a fake `Server`, plugin manager, world, and players.

**Setup / teardown pattern** (`PluginHiderListenerTest.java`):

```java
class MyListenerTest {

    private ServerMock server;
    private PluginMock plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }
}
```

**Register the listener and fire an event:**

```java
@Test
void onCommandSend_filtersDisallowedCommands() {
    MyService service = mock(MyService.class);
    when(service.isAllowed("spawn")).thenReturn(true);

    MyListener listener = new MyListener(service);
    server.getPluginManager().registerEvents(listener, plugin);

    PlayerMock player = server.addPlayer("Alice");
    Set<String> commands = new HashSet<>(Set.of("spawn", "ban"));
    PlayerCommandSendEvent event = new PlayerCommandSendEvent(player, commands);
    server.getPluginManager().callEvent(event);

    assertThat(event.getCommands()).contains("spawn").doesNotContain("ban");
}
```

**Using `MockScheduler` instead of `BukkitScheduler`:**

`MockScheduler` (in `de.kreiscraft.testsupport`) implements the `Scheduler` interface with virtual time. Instantiate it directly — no constructor arguments:

```java
MockScheduler scheduler = new MockScheduler();
```

- `runOnMain(task)` — executes `task` immediately on the calling thread.
- `runLater(delay, task)` / `runRepeating(delay, period, task)` — queue tasks without running them.
- `scheduler.advance(Duration.ofSeconds(5))` — fires all tasks whose virtual fire time falls within the next 5 seconds, in order. Repeating tasks reschedule automatically.

```java
@Test
void someDelayedBehaviour() {
    MockScheduler scheduler = new MockScheduler();
    MyService service = new MyService(scheduler);

    service.scheduleCleanup(); // internally calls scheduler.runLater(Duration.ofMinutes(1), ...)

    assertThat(service.isCleaned()).isFalse();
    scheduler.advance(Duration.ofMinutes(1));
    assertThat(service.isCleaned()).isTrue();
}
```

**`PaperPlatform`:** prefer injecting `PaperPlatform` (a record holding `plugin` and `server`) over calling `Bukkit.getServer()` directly. In tests, construct it with the `PluginMock` and `ServerMock` from MockBukkit.

---

## 3. Testing an API route

Use `JavalinTest.test` from Javalin's test-tools artifact. It starts the app on a random port, runs the lambda, then stops the app.

**Example** (`WhitelistRoutesTest.java`):

```java
@Test
void get_whitelist_correctApiKey_returns200() {
    WhitelistService whitelist = mock(WhitelistService.class);
    when(whitelist.allNames()).thenReturn(List.of("Alice", "Bob"));

    Javalin app = Javalin.create();
    WhitelistRoutes.register(app, configWithKey("secret"), whitelist, SYNC_SCHEDULER);

    JavalinTest.test(app, (server, client) -> {
        var response = client.get("/whitelist",
                req -> req.header("X-Kreiscraft-Api-Key", "secret"));
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body().string()).contains("\"Alice\"");
    });
}
```

`client` is an OkHttp client pre-pointed at the test server. Use `client.get(path)`, `client.post(path, body)`, `client.request(path, builder -> ...)` for full control.

When a route needs a `Scheduler` (e.g. to dispatch work to the main thread), provide a synchronous fake:

```java
private static final Scheduler SYNC_SCHEDULER = new Scheduler() {
    @Override public Task runOnMain(Runnable task) { task.run(); return ...; }
    // runLater / runRepeating → throw UnsupportedOperationException if not needed
};
```

Or use `MockScheduler` if you need scheduling behaviour.

For integration tests that start the full `ApiServer`, grab a free ephemeral port first (`new ServerSocket(0)`), build the config with that port, then call `apiServer.start()` / `apiServer.stop()` in `@BeforeEach` / `@AfterEach` — see `ApiServerTest.java`.

---

## 4. Lang parity tests (automatic)

These two tests run on every build. You do not need to invoke them manually; just keep them green.

### `LangParityTest`

Location: `src/test/java/de/kreiscraft/lang/LangParityTest.java`

Checks that `lang/de.yml` and `lang/en.yml` contain **exactly the same set of dotted leaf-key paths**. Adding a key to one file without the other fails the build. Fix: add the matching key to both files.

### `MessageKeyReferenceTest`

Location: `src/test/java/de/kreiscraft/i18n/MessageKeyReferenceTest.java`

Scans every `.java` file under `src/main/java/de/kreiscraft/` for string-literal keys passed to `messages.send(...)`, `messages.broadcast(...)`, and `messages.render(...)`. Each extracted key is resolved against both `lang/de.yml` and `lang/en.yml`. The test fails if any key is absent from either file.

**Known limitation:** keys passed via variables (not string literals) are not detected and are not checked.

**When you add a new `messages.send` call**, add the matching key to both lang files before committing. The test will tell you exactly which key and file is missing.

---

## 5. Smoke test

`KreiscraftBootstrapTest` verifies that all registered module IDs are unique and follow the kebab-case naming convention. It does not start a server. If you add a new module, add it to the `allModules()` list there and increment `MODULE_COUNT`.

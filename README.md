# launcher
A fully customizable launcher UI prebuilt into RuneLite, transparent to all RL revisions.

Add the LoginScreen class to \runelite-client\src\main\java\net\runelite\client\ui\

In RuneLite.java, in your main method underneath final OkHttpClient okHttpClient = buildHttpClient(options.has("insecure-skip-tls-verification"));
        RuneLiteAPI.CLIENT = okHttpClient;

        // Create final references to be used in the lambda
        final String[] finalArgs = args;
        final OptionSet finalOptions = options;

add this:

// Show login screen first
        LoginScreen.init(() -> {
            try {
                final RuntimeConfigLoader runtimeConfigLoader = new RuntimeConfigLoader(okHttpClient);
                final ClientLoader clientLoader = new ClientLoader(okHttpClient,
                        finalOptions.valueOf(updateMode),
                        runtimeConfigLoader,
                        RuneLiteProperties.getLocalEnabled() ? RuneLiteProperties.getJavConfigLocal() : RuneLiteProperties.getJavConfig());

                SplashScreen.init();
                SplashScreen.stage(0, "Retrieving client", "");

                new Thread(() -> {
                    clientLoader.get();
                    ClassPreloader.preload();
                }, "Preloader").start();

                PROFILES_DIR.mkdirs();

                log.info("Valkarin {} (launcher version {}) starting up, args: {}",
                        RuneLiteProperties.getVersion(),
                        MoreObjects.firstNonNull(RuneLiteProperties.getLauncherVersion(), "unknown"),
                        finalArgs.length == 0 ? "none" : String.join(" ", finalArgs));

                final RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
                log.info("Java VM arguments: {}", String.join(" ", runtime.getInputArguments()));

                final long start = System.currentTimeMillis();

                injector = Guice.createInjector(new RuneLiteModule(
                        okHttpClient,
                        clientLoader,
                        runtimeConfigLoader,
                        true, // developer mode
                        finalOptions.has("safe-mode"),
                        finalOptions.has("enable-telemetry"),
                        finalOptions.valueOf(sessionfile),
                        finalOptions.valueOf(configfile),
                        finalOptions.has(insecureWriteCredentials)));

                injector.getInstance(RuneLite.class).start(finalOptions);

                final long end = System.currentTimeMillis();
                final long uptime = runtime.getUptime();
                log.info("Client initialization took {}ms. Uptime: {}ms", end - start, uptime);

            } catch (Exception e) {
                log.error("Failure during startup", e);
                SwingUtilities.invokeLater(() ->
                        new FatalErrorDialog("Valkarin has encountered an unexpected error during startup.")
                                .addHelpButtons()
                                .open());
            } finally {
                SplashScreen.stop();
            }
        });

make adjusments where needed, run.
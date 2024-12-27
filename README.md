# Valkarin RuneLite Launcher

![Java](https://img.shields.io/badge/Java-100%25-orange)
![License](https://img.shields.io/badge/license-MIT-blue.svg)
![RuneLite](https://img.shields.io/badge/RuneLite-Compatible-brightgreen)

A fully customizable launcher UI that seamlessly integrates with RuneLite, providing a transparent login experience across all RuneLite revisions.

## 🌟 Features

- **Custom Login Screen**: Fully integrated login interface
- **RuneLite Compatible**: Works with all RuneLite versions
- **Transparent Integration**: Seamless integration with existing RuneLite codebase
- **Customizable UI**: Easily modifiable interface elements
- **Secure**: Maintains RuneLite's security standards

## 📋 Prerequisites

- Java Development Kit (JDK) 11 or higher
- RuneLite source code
- Basic understanding of RuneLite's framework

## 🚀 Installation

1. Add the `LoginScreen` class to your RuneLite project:

   ```bash
   Path: runelite-client/src/main/java/net/runelite/client/ui/
   ```

2. Modify the `RuneLite.java` file:

   Locate the `main` method and find the following section:

   ```java
   final OkHttpClient okHttpClient = buildHttpClient(options.has("insecure-skip-tls-verification"));
   RuneLiteAPI.CLIENT = okHttpClient;
   ```

3. Add the launcher initialization code:

   ```java
   // Show login screen first
   LoginScreen.init(() -> {
       try {
           final RuntimeConfigLoader runtimeConfigLoader = new RuntimeConfigLoader(okHttpClient);
           final ClientLoader clientLoader = new ClientLoader(
               okHttpClient,
               finalOptions.valueOf(updateMode),
               runtimeConfigLoader,
               RuneLiteProperties.getLocalEnabled() ? RuneLiteProperties.getJavConfigLocal() : RuneLiteProperties.getJavConfig()
           );

           // ... [rest of the initialization code]
       } catch (Exception e) {
           log.error("Failure during startup", e);
           SwingUtilities.invokeLater(() ->
               new FatalErrorDialog("Valkarin has encountered an unexpected error during startup.")
                   .addHelpButtons()
                   .open()
           );
       } finally {
           SplashScreen.stop();
       }
   });
   ```
4. Add the manifest.json and version.txt to your desired web directory, you change those fields in the LoginScreen class
```java
    private static final String VERSION_URL = "https://valkarin.net/version.txt"; < change to your domain or localhost/ 
    private static final String MANIFEST_URL = "https://valkarin.net/manifest.json"; < change to your domain or localhost/ 
    private static final String UPDATE_BASE_URL = "https://valkarin.net/updates/"; < change to your domain or localhost/ 
    private static final String LOCAL_VERSION_FILE = "version.txt"; 
```

## 💻 Configuration

The launcher can be customized by modifying the following:
- Login screen appearance
- Error handling messages
- Splash screen behavior
- Startup sequence

## 🔧 Development

To modify the launcher:
1. Clone your RuneLite repository.
2. Implement the provided changes.
3. Customize the UI elements as needed.
4. Test thoroughly before deployment.

### Key Components
- **`LoginScreen.java`**: Main login interface implementation.
- **`RuneLite.java`**: Core integration points.
- **`SplashScreen`**: Loading screen implementation.

![launcher](https://github.com/user-attachments/assets/4deb7f09-f2d4-4c79-a81e-e35318816d9d)

## 🐛 Debugging

### Common Issues and Solutions

#### Startup Errors:
- Check Java version compatibility.
- Verify RuneLite source integrity.
- Ensure proper path configuration.

#### UI Issues:
- Review custom UI implementations.
- Check for conflicting style definitions.
- Verify resource loading.

## 🤝 Contributing

Contributions are welcome! To contribute:
1. Fork the repository.
2. Create your feature branch (`git checkout -b feature/Feature`).
3. Commit your changes (`git commit -m 'Add some Feature'`).
4. Push to the branch (`git push origin feature/Feature`).
5. Open a Pull Request.

## 📝 Notes

- Ensure proper error handling implementation.
- Test across different RuneLite versions.
- Maintain security best practices.
- Follow RuneLite's coding standards.

## ⚠️ Important

Make sure to:
- Backup your original RuneLite files before modification.
- Test in a development environment first.
- Follow all security guidelines.
- Keep your RuneLite installation updated.

## 📬 Contact

[@valkarinc](https://github.com/valkarinc/)

## 🙏 Acknowledgments

- RuneLite development team
- Community feedback and support

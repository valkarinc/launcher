package net.runelite.client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.ArrayList;
import java.util.prefs.Preferences;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.ImageUtil;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;

@Slf4j
public class LoginScreen extends JFrame {
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 600;
    private static final int CORNER_RADIUS = 20;
    private static final int PAD = 20;
    private static final String WINDOW_POSITION_X = "window_x";
    private static final String WINDOW_POSITION_Y = "window_y";

    private static LoginScreen INSTANCE;
    private final Color backgroundColor = new Color(17, 24, 39);
    private final Color buttonStartColor = new Color(147, 51, 234);
    private final Color buttonEndColor = new Color(236, 72, 153);
    private final Color sidebarColor = new Color(24, 31, 46);
    private final Runnable onPlayCallback;
    private final List<Particle> particles = new ArrayList<>();
    private Timer particleTimer;
    private final Preferences prefs = Preferences.userNodeForPackage(LoginScreen.class);
    private Timer loadingTimer;
    private boolean isLoading = false;
    private float loadingAngle = 0;
    private JLabel updateStatusLabel;

    private static final String VERSION_URL = "https://valkarin.net/version.txt";
    private static final String MANIFEST_URL = "https://valkarin.net/manifest.json";
    private static final String UPDATE_BASE_URL = "https://valkarin.net/updates/";
    private static final String LOCAL_VERSION_FILE = "version.txt";

    private static class Particle {
        float x, y;
        float speed;
        float size;
        float alpha;

        Particle() {
            reset();
        }

        void reset() {
            x = (float) (Math.random() * WIDTH);
            y = HEIGHT + 10;
            speed = (float) (1 + Math.random() * 2);
            size = (float) (2 + Math.random() * 3);
            alpha = (float) (0.1 + Math.random() * 0.4);
        }

        void update() {
            y -= speed;
            if (y < -10) {
                reset();
            }
        }
    }

    private boolean checkForUpdates() {
        try {
            // Fetch latest version
            URL url = new URL(VERSION_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String latestVersion = reader.readLine().trim();
                String currentVersion = getCurrentVersion();

                return !latestVersion.equals(currentVersion); // Return true if an update is needed
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String getCurrentVersion() {
        try {
            File versionFile = new File(LOCAL_VERSION_FILE);
            if (!versionFile.exists()) return "1.0.0";

            try (BufferedReader reader = new BufferedReader(new FileReader(versionFile))) {
                return reader.readLine().trim();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "1.0.0";
        }
    }

    private void updateClient() {
        SwingWorker<Void, Void> updater = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    // Fetch manifest
                    URL url = new URL(MANIFEST_URL);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");

                    File manifestFile = new File("manifest.json");
                    try (InputStream in = connection.getInputStream();
                         FileOutputStream out = new FileOutputStream(manifestFile)) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }

                    // Parse and download updates
                    parseAndDownloadUpdates(manifestFile);

                    // Save new version
                    Files.write(Paths.get(LOCAL_VERSION_FILE), "NEW_VERSION".getBytes());

                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(LoginScreen.this,
                            "Failed to update the client. Please try again later.",
                            "Update Error", JOptionPane.ERROR_MESSAGE);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // Check for exceptions
                    updateStatusLabel.setText("Update completed! Restart to apply changes.");
                    updateStatusLabel.setForeground(new Color(0, 200, 0));

                    // Show restart dialog
                    int choice = JOptionPane.showConfirmDialog(
                            LoginScreen.this,
                            "The update has been downloaded. Would you like to restart now?",
                            "Update Complete",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE
                    );

                    if (choice == JOptionPane.YES_OPTION) {
                        restartApplication();
                    }
                } catch (Exception e) {
                    updateStatusLabel.setText("Update failed! Please try again.");
                    updateStatusLabel.setForeground(Color.RED);
                    log.error("Update failed", e);
                }
            }
        };

        updater.execute();
    }

    private void restartApplication() {
        try {
            // Get current JAR path
            String jarPath = LoginScreen.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath();

            // Build command to restart application
            List<String> command = new ArrayList<>();
            command.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
            command.add("-jar");
            command.add(jarPath);

            // Start new process
            new ProcessBuilder(command).start();

            // Exit current instance
            System.exit(0);
        } catch (Exception e) {
            log.error("Failed to restart application", e);
        }
    }

    protected void process(List<Integer> chunks) {
        // Update progress in UI
        int latestProgress = chunks.get(chunks.size() - 1);
        updateStatusLabel.setText("Downloading update: " + latestProgress + "%");
        updateStatusLabel.setVisible(true);

        // Add a progress color indicator
        if (latestProgress < 50) {
            updateStatusLabel.setForeground(new Color(255, 165, 0)); // Orange
        } else if (latestProgress < 90) {
            updateStatusLabel.setForeground(new Color(255, 215, 0)); // Gold
        } else {
            updateStatusLabel.setForeground(new Color(0, 255, 127)); // Spring Green
        }
    }

    private void parseAndDownloadUpdates(File manifestFile) throws IOException {
        List<String> filesToUpdate = Files.readAllLines(manifestFile.toPath());

        for (String fileName : filesToUpdate) {
            fileName = fileName.trim(); // Remove extra spaces or newlines
            if (fileName.isEmpty()) continue; // Skip empty lines

            URL fileUrl = new URL(UPDATE_BASE_URL + fileName); // Construct the file URL
            File localFile = new File(fileName); // Save to the same path locally

            try (InputStream in = fileUrl.openStream();
                 FileOutputStream out = new FileOutputStream(localFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            } catch (FileNotFoundException e) {
                System.err.println("File not found on server: " + fileUrl);
            }
        }
    }

    private void showStyledMessage(String message) {
        JDialog dialog = new JDialog(this, "Message", true);
        dialog.setSize(300, 150);
        dialog.setLocationRelativeTo(this);
        dialog.setUndecorated(true);

        // Main panel with styling
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBackground(new Color(34, 45, 67));
        panel.setBorder(BorderFactory.createLineBorder(new Color(147, 51, 234), 2));

        // Message label
        JLabel label = new JLabel(message, SwingConstants.CENTER);
        label.setForeground(Color.WHITE);
        label.setFont(new Font("SansSerif", Font.BOLD, 16));
        panel.add(label, BorderLayout.CENTER);

        // OK Button
        JButton okButton = new JButton("OK");
        okButton.setBackground(new Color(147, 51, 234));
        okButton.setForeground(Color.WHITE);
        okButton.setFocusPainted(false);
        okButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        okButton.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));
        okButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        okButton.addActionListener(e -> dialog.dispose());
        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(new Color(34, 45, 67));
        buttonPanel.add(okButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.add(panel);
        dialog.setVisible(true);
    }

    private JButton createUpdateButton() {
        JButton updateButton = new JButton("Check for Updates") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Gradient background for button
                GradientPaint gradient = new GradientPaint(
                        0, 0, buttonStartColor,
                        getWidth(), 0, buttonEndColor
                );
                g2.setPaint(gradient);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 15, 15));

                if (getModel().isRollover()) {
                    g2.setColor(new Color(255, 255, 255, 50));
                    g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 15, 15));
                }

                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 14));
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = ((getHeight() - fm.getHeight()) / 2) + fm.getAscent();
                g2.drawString(getText(), x, y);

                g2.dispose();
            }
        };

        updateButton.setBorder(BorderFactory.createEmptyBorder());
        updateButton.setContentAreaFilled(false);
        updateButton.setFocusPainted(false);
        updateButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        updateButton.addActionListener(e -> {
            updateStatusLabel.setText("Checking for updates...");
            updateStatusLabel.setForeground(Color.WHITE);
            updateStatusLabel.setVisible(true);

            if (checkForUpdates()) {
                updateStatusLabel.setText("Update available!");
                updateStatusLabel.setForeground(Color.RED);

                int choice = JOptionPane.showConfirmDialog(
                        LoginScreen.this,
                        "An update is available. Would you like to download it now?",
                        "Update Available",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );

                if (choice == JOptionPane.YES_OPTION) {
                    updateStatusLabel.setText("Starting download...");
                    updateStatusLabel.setForeground(Color.WHITE);
                    downloadAndInstallUpdate();

                    // Disable the update button during download
                    ((JButton)e.getSource()).setEnabled(false);
                }
            } else {
                updateStatusLabel.setText("Client is up-to-date!");
                updateStatusLabel.setForeground(new Color(0, 200, 0));
            }
        });

        return updateButton;
    }

    private void downloadAndInstallUpdate() {
        SwingWorker<Void, Integer> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    // Download new JAR
                    URL url = new URL("https://valkarin.net/download/valkarin.jar");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    int fileSize = connection.getContentLength();

                    // Create temp file
                    File tempFile = File.createTempFile("valkarin_new", ".jar");
                    tempFile.deleteOnExit();

                    try (InputStream in = new BufferedInputStream(connection.getInputStream());
                         FileOutputStream out = new FileOutputStream(tempFile)) {

                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        int totalBytesRead = 0;

                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                            totalBytesRead += bytesRead;
                            // Update progress
                            publish((int) ((totalBytesRead * 100.0) / fileSize));
                        }
                    }

                    // Get current JAR location
                    String currentPath = LoginScreen.class.getProtectionDomain()
                            .getCodeSource().getLocation().toURI().getPath();
                    File currentJar = new File(currentPath);

                    // Create backup of current JAR
                    File backupFile = new File(currentJar.getParent(), "valkarin.jar.backup");
                    if (backupFile.exists()) backupFile.delete();
                    Files.copy(currentJar.toPath(), backupFile.toPath());

                    // Replace current JAR with new version
                    Files.move(tempFile.toPath(), currentJar.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);

                    return null;
                } catch (Exception e) {
                    log.error("Failed to update client", e);
                    throw e;
                }
            }
        };
    }

    public LoginScreen(Runnable onPlayCallback) {
        this.onPlayCallback = onPlayCallback;
        BufferedImage logo = ImageUtil.loadImageResource(LoginScreen.class, "/boomscape.png");

        // Basic window setup
        setTitle("Valkarin");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setUndecorated(true);
        setSize(WIDTH, HEIGHT);
        setIconImage(logo);
        setBackground(new Color(0, 0, 0, 0));

        // Restore window position
        int x = prefs.getInt(WINDOW_POSITION_X, -1);
        int y = prefs.getInt(WINDOW_POSITION_Y, -1);
        if (x != -1 && y != -1) {
            setLocation(x, y);
        } else {
            setLocationRelativeTo(null);
        }

        // Initialize particles
        for (int i = 0; i < 50; i++) {
            particles.add(new Particle());
        }

        // Main panel setup
        JPanel mainPanel = createMainPanel();
        setContentPane(mainPanel);

        // Start particle animation
        particleTimer = new Timer(16, e -> {
            particles.forEach(Particle::update);
            mainPanel.repaint();
        });
        particleTimer.start();

        // Add panels
        JPanel leftPanel = createLeftPanel(logo);
        mainPanel.add(leftPanel);
        leftPanel.setBounds(PAD, PAD, 400, HEIGHT - (PAD * 2));

        JPanel rightPanel = createRightPanel();
        mainPanel.add(rightPanel);
        rightPanel.setBounds(420 + PAD, PAD, WIDTH - 440 - PAD, HEIGHT - (PAD * 2));

        // Add window controls
        JPanel windowControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        windowControls.setOpaque(false);
        windowControls.add(createMinimizeButton());
        windowControls.add(createCloseButton());
        mainPanel.add(windowControls);
        windowControls.setBounds(WIDTH - 80, 10, 70, 30);

        // Add window drag support with position saving
        setupWindowDrag();

        // Add keyboard shortcuts
        setupKeyboardShortcuts();

        getRootPane().putClientProperty("Window.shadow", Boolean.TRUE);
    }

    private JPanel createMainPanel() {
        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Paint gradient background
                GradientPaint gradient = new GradientPaint(
                        0, 0, backgroundColor,
                        0, getHeight(), backgroundColor.darker()
                );
                g2.setPaint(gradient);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));

                // Draw particles
                for (Particle p : particles) {
                    g2.setColor(new Color(1f, 1f, 1f, p.alpha));
                    g2.fill(new RoundRectangle2D.Float(p.x, p.y, p.size, p.size, 2, 2));
                }

                // Add subtle border
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(new Color(255, 255, 255, 30));
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, CORNER_RADIUS, CORNER_RADIUS));

                g2.dispose();
            }
        };
        mainPanel.setLayout(null);
        mainPanel.setOpaque(false);
        return mainPanel;
    }

    private JPanel createLeftPanel(BufferedImage logo) {
        JPanel panel = new JPanel(null);
        panel.setOpaque(false);

        // Logo setup (existing code)
        int logoWidth = 200;
        int logoHeight = (int) ((double) logo.getHeight() / logo.getWidth() * logoWidth);
        BufferedImage scaledImage = createHighQualityScaledImage(logo, logoWidth, logoHeight);

        JLabel logoLabel = createGlowingLogoLabel(scaledImage);
        panel.add(logoLabel);

        int centerX = (400 - logoWidth) / 2;
        int centerY = (HEIGHT - logoHeight - 250) / 2;
        logoLabel.setBounds(centerX, centerY, logoWidth, logoHeight);

        JLabel quoteLabel = createAnimatedQuoteLabel();
        panel.add(quoteLabel);
        int quoteY = centerY + logoHeight + 10;
        quoteLabel.setBounds(PAD, quoteY, 360, 40);

        JButton playButton = createPlayButton();
        panel.add(playButton);
        playButton.setBounds(PAD, HEIGHT - 200, 360, 60);

        JLabel versionLabel = createVersionLabel();
        panel.add(versionLabel);
        versionLabel.setBounds(PAD, HEIGHT - 120, 360, 20);

        // Add the Update Button
        JButton updateButton = createUpdateButton();
        panel.add(updateButton);
        updateButton.setBounds(PAD, HEIGHT - 100, 360, 40);

        // Add the Update Status Label
        updateStatusLabel = new JLabel("", SwingConstants.CENTER);
        updateStatusLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        updateStatusLabel.setForeground(Color.WHITE);
        updateStatusLabel.setVisible(false); // Initially hidden
        panel.add(updateStatusLabel);
        updateStatusLabel.setBounds(PAD, HEIGHT - 60, 360, 20);

        return panel;
    }

    private BufferedImage createHighQualityScaledImage(BufferedImage original, int width, int height) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(original, 0, 0, width, height, null);
        g2d.dispose();
        return scaled;
    }

    private JLabel createGlowingLogoLabel(BufferedImage scaledImage) {
        return new JLabel(new ImageIcon(scaledImage)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Create a soft beam glow effect
                int beamWidth = 4;  // Thinner beam
                Color glowColor = new Color(147, 51, 234); // Purple color

                // Draw beam glow
                for (int i = 10; i > 0; i--) {
                    float alpha = i / 10.0f * 0.2f;  // Reduced alpha for subtler effect
                    g2.setColor(new Color(
                            glowColor.getRed(),
                            glowColor.getGreen(),
                            glowColor.getBlue(),
                            (int)(alpha * 255)
                    ));
                    g2.setStroke(new BasicStroke(beamWidth + i));
                    int padding = i * 2;
                    g2.drawRoundRect(
                            padding/2,
                            padding/2,
                            getWidth() - padding,
                            getHeight() - padding,
                            10,
                            10
                    );
                }

                super.paintComponent(g2);
                g2.dispose();
            }
        };
    }

    private JLabel createAnimatedQuoteLabel() {
        JLabel quoteLabel = new JLabel("<html><div style='text-align: center; width: 300px;'>" +
                "Bringing you the experience that you deserve</div></html>") {
            private float alpha = 0f;
            private Timer fadeTimer;

            {
                fadeTimer = new Timer(50, e -> {
                    alpha = Math.min(1f, alpha + 0.05f);
                    if (alpha >= 1f) {
                        ((Timer)e.getSource()).stop();
                    }
                    repaint();
                });
                fadeTimer.start();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                super.paintComponent(g2);
                g2.dispose();
            }
        };

        quoteLabel.setForeground(new Color(156, 163, 175));
        quoteLabel.setHorizontalAlignment(SwingConstants.CENTER);
        quoteLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        return quoteLabel;
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Create glass-like effect
                g2.setColor(new Color(24, 31, 46, 200));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));

                // Add subtle gradient overlay
                GradientPaint glassEffect = new GradientPaint(
                        0, 0, new Color(255, 255, 255, 15),
                        0, getHeight(), new Color(255, 255, 255, 0)
                );
                g2.setPaint(glassEffect);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));

                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(PAD, PAD, PAD, PAD));

        // Create main content panel
        JPanel contentPanel = new JPanel(new BorderLayout(0, 10));
        contentPanel.setOpaque(false);

        // Add header
        contentPanel.add(createAnimatedHeader(), BorderLayout.NORTH);

        // Add patch notes
        contentPanel.add(createPatchNotesScrollPane(), BorderLayout.CENTER);

        // Add navigation buttons
        contentPanel.add(createNavigationPanel(), BorderLayout.SOUTH);

        panel.add(contentPanel);
        return panel;
    }

    private JLabel createAnimatedHeader() {
        JLabel headerLabel = new JLabel("Patch Notes") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                String text = getText();
                g2.setFont(new Font("SansSerif", Font.BOLD, 24));
                FontMetrics fm = g2.getFontMetrics();
                int textX = 0;
                int textY = fm.getAscent();

                // 3D effect shadow layers
                for (int i = 4; i > 0; i--) {
                    g2.setColor(new Color(0, 0, 0, 50));
                    g2.drawString(text, textX + i, textY + i);
                }

                // Main text
                g2.setColor(Color.WHITE);
                g2.drawString(text, textX, textY);

                g2.dispose();
            }
        };

        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        return headerLabel;
    }

    private JScrollPane createPatchNotesScrollPane() {
        JTextArea patchNotes = new JTextArea();
        patchNotes.setEditable(false);
        patchNotes.setLineWrap(true);
        patchNotes.setWrapStyleWord(true);
        patchNotes.setBackground(new Color(0, 0, 0, 0));
        patchNotes.setForeground(new Color(200, 200, 200));
        patchNotes.setFont(new Font("SansSerif", Font.PLAIN, 14));
        patchNotes.setBorder(new EmptyBorder(PAD, 0, 0, 0));
        patchNotes.setText(
                "November 30, 2024 - Update v1.0.0\n\n" +
                        "New Features:\n" +
                        "• Added custom login screen\n" +
                        "• Implemented new user interface\n" +
                        "• Enhanced graphics system\n\n" +
                        "Improvements:\n" +
                        "• Better performance optimization\n" +
                        "• Updated client stability\n" +
                        "• Improved memory management\n" +
                        "• Refined combat balancing mechanics\n\n" +
                        "Bug Fixes:\n" +
                        "• Fixed various crash issues\n" +
                        "• Resolved login connectivity problems\n" +
                        "• Fixed animation glitches\n" +
                        "• Fixed Zulrah behavior and mechanics\n" +
                        "• Corrected Bandosian Might calculation bug\n" +
                        "• Addressed item duplication exploit\n" +
                        "• Resolved NPC pathing issues in raids\n" +
                        "• And more!"
        );

        JScrollPane scrollPane = new JScrollPane(patchNotes) {
            @Override
            public JScrollBar createVerticalScrollBar() {
                return new JScrollBar(JScrollBar.VERTICAL) {
                    @Override
                    public void updateUI() {
                        setUI(new BasicScrollBarUI() {
                            @Override
                            protected void configureScrollBarColors() {
                                this.thumbColor = new Color(255, 255, 255, 50);
                                this.trackColor = new Color(0, 0, 0, 0);
                            }

                            @Override
                            protected JButton createDecreaseButton(int orientation) {
                                return createZeroButton();
                            }

                            @Override
                            protected JButton createIncreaseButton(int orientation) {
                                return createZeroButton();
                            }

                            private JButton createZeroButton() {
                                JButton button = new JButton();
                                button.setPreferredSize(new Dimension(0, 0));
                                return button;
                            }
                        });
                    }
                };
            }
        };
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        return scrollPane;
    }

    private JPanel createNavigationPanel() {
        JPanel navPanel = new JPanel(new GridLayout(2, 3, 10, 10));
        navPanel.setOpaque(false);
        navPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        String[][] navButtons = {
                {"DISCORD", "https://discord.gg/valkarin"},
                {"STORE", "https://valkarin.net/store"},
                {"VOTE", "https://valkarin.net/vote/"},
                {"FORUMS", "https://valkarin.net/forums/"},
                {"HIGHSCORES", "https://valkarin.net/highscores"},
                {"GUIDES", "https://discord.gg/valkarin"}
        };

        for (String[] buttonInfo : navButtons) {
            navPanel.add(createNavButton(buttonInfo[0], buttonInfo[1]));
        }

        return navPanel;
    }

    private JButton createNavButton(String text, String url) {
        JButton button = new JButton(text) {
            private float hoverState = 0f;
            public Timer hoverTimer;

            {
                hoverTimer = new Timer(16, e -> {
                    if (getModel().isRollover() && hoverState < 1f) {
                        hoverState = Math.min(1f, hoverState + 0.1f);
                        repaint();
                    } else if (!getModel().isRollover() && hoverState > 0f) {
                        hoverState = Math.max(0f, hoverState - 0.1f);
                        repaint();
                    } else {
                        ((Timer)e.getSource()).stop();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color bgColor = getModel().isPressed() ?
                        new Color(0, 0, 0, 100) :
                        new Color(255, 255, 255, (int)(20 * hoverState));

                g2.setColor(bgColor);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));

                // Glowing border effect
                g2.setColor(new Color(255, 255, 255, 30 + (int)(50 * hoverState)));
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth()-1, getHeight()-1, 8, 8));

                // Text with glow effect
                if (hoverState > 0) {
                    g2.setColor(new Color(255, 255, 255, (int)(50 * hoverState)));
                    g2.setFont(new Font("SansSerif", Font.BOLD, 12));
                    FontMetrics fm = g2.getFontMetrics();
                    int x = (getWidth() - fm.stringWidth(text)) / 2;
                    int y = ((getHeight() - fm.getHeight()) / 2) + fm.getAscent();
                    g2.drawString(text, x+1, y+1);
                }

                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 12));
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(text)) / 2;
                int y = ((getHeight() - fm.getHeight()) / 2) + fm.getAscent();
                g2.drawString(text, x, y);

                g2.dispose();
            }
        };

        button.setPreferredSize(new Dimension(0, 35));
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
               // ((JButton)e.getSource()).hoverTimer.start();
            }
        });

        button.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception ex) {
                log.error("Error opening URL: " + url, ex);
            }
        });

        return button;
    }

    private JButton createPlayButton() {
        JButton playButton = new JButton() {
            private boolean isLoading = false;
            private float loadingAngle = 0;
            private Timer loadingTimer;

            {
                loadingTimer = new Timer(16, e -> {
                    if (isLoading) {
                        loadingAngle += 10;
                        if (loadingAngle >= 360) {
                            loadingAngle = 0;
                        }
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Button gradient
                GradientPaint gradient = new GradientPaint(
                        0, 0, buttonStartColor,
                        getWidth(), 0, buttonEndColor
                );
                g2.setPaint(gradient);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 15, 15));

                if (isLoading) {
                    int size = Math.min(getWidth(), getHeight()) / 4;
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(3));
                    g2.rotate(Math.toRadians(loadingAngle),
                            getWidth() / 2,
                            getHeight() / 2);
                    g2.drawArc(getWidth()/2 - size/2,
                            getHeight()/2 - size/2,
                            size, size, 0, 270);
                } else {
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("SansSerif", Font.BOLD, 20));
                    FontMetrics fm = g2.getFontMetrics();
                    String text = "PLAY NOW";
                    int x = (getWidth() - fm.stringWidth(text)) / 2;
                    int y = ((getHeight() - fm.getHeight()) / 2) + fm.getAscent();
                    g2.drawString(text, x, y);
                }

                g2.dispose();
            }

            public void setLoading(boolean loading) {
                isLoading = loading;
                if (loading) {
                    loadingTimer.start();
                } else {
                    loadingTimer.stop();
                }
                repaint();
            }
        };

        playButton.setBorder(BorderFactory.createEmptyBorder());
        playButton.setContentAreaFilled(false);
        playButton.setFocusPainted(false);
        playButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        playButton.addActionListener(e -> {
            playButton.setEnabled(false);
             //playButton.setLoading(true);  // Use playButton directly instead of casting e.getSource()
            new Thread(() -> {
                try {
                    if (onPlayCallback != null) {
                        onPlayCallback.run();
                    }
                } finally {
                    SwingUtilities.invokeLater(() -> {
                       // playButton.setLoading(false);  // Use playButton directly
                        playButton.setEnabled(true);
                        dispose();
                    });
                }
            }).start();
        });

        return playButton;
    }

    private JButton createMinimizeButton() {
        JButton minimizeButton = new JButton("−") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (getModel().isPressed()) {
                    g2.setColor(new Color(255, 255, 255, 30));
                } else if (getModel().isRollover()) {
                    g2.setColor(new Color(255, 255, 255, 20));
                } else {
                    g2.setColor(new Color(255, 255, 255, 10));
                }

                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));

                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 18));
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth("−")) / 2;
                int y = ((getHeight() - fm.getHeight()) / 2) + fm.getAscent();
                g2.drawString("−", x, y);

                g2.dispose();
            }
        };

        minimizeButton.setPreferredSize(new Dimension(30, 30));
        minimizeButton.setBorder(BorderFactory.createEmptyBorder());
        minimizeButton.setContentAreaFilled(false);
        minimizeButton.setFocusPainted(false);
        minimizeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        minimizeButton.addActionListener(e -> setState(Frame.ICONIFIED));

        return minimizeButton;
    }

    private JButton createCloseButton() {
        JButton closeButton = new JButton("×") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (getModel().isPressed()) {
                    g2.setColor(new Color(255, 0, 0, 100));
                } else if (getModel().isRollover()) {
                    g2.setColor(new Color(255, 0, 0, 70));
                } else {
                    g2.setColor(new Color(255, 0, 0, 50));
                }

                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));

                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 20));
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth("×")) / 2;
                int y = ((getHeight() - fm.getHeight()) / 2) + fm.getAscent();
                g2.drawString("×", x, y);

                g2.dispose();
            }
        };

        closeButton.setPreferredSize(new Dimension(30, 30));
        closeButton.setBorder(BorderFactory.createEmptyBorder());
        closeButton.setContentAreaFilled(false);
        closeButton.setFocusPainted(false);
        closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeButton.addActionListener(e -> System.exit(0));

        return closeButton;
    }

    private void setupWindowDrag() {
        MouseAdapter dragListener = new MouseAdapter() {
            private Point mouseDownCompCoords;

            @Override
            public void mousePressed(MouseEvent e) {
                mouseDownCompCoords = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Point currCoords = e.getLocationOnScreen();
                setLocation(currCoords.x - mouseDownCompCoords.x,
                        currCoords.y - mouseDownCompCoords.y);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                prefs.putInt(WINDOW_POSITION_X, getX());
                prefs.putInt(WINDOW_POSITION_Y, getY());
            }
        };
        addMouseListener(dragListener);
        addMouseMotionListener(dragListener);
    }

    private void setupKeyboardShortcuts() {
        KeyStroke escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false);
        Action escapeAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        };
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(escapeKeyStroke, "ESCAPE");
        getRootPane().getActionMap().put("ESCAPE", escapeAction);
    }

    private JLabel createVersionLabel() {
        JLabel versionLabel = new JLabel("Version 1.0.0") {
            private boolean hover = false;
            {
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hover = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hover = false;
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hover ? new Color(176, 183, 195) : new Color(156, 163, 175));
                super.paintComponent(g2);
                g2.dispose();
            }
        };
        versionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        versionLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        versionLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return versionLabel;
    }

    private BufferedImage createGradientLogo(BufferedImage original) {
        BufferedImage gradient = new BufferedImage(
                original.getWidth(),
                original.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g2d = gradient.createGraphics();
        g2d.drawImage(original, 0, 0, null);

        // Add gradient overlay
        GradientPaint overlay = new GradientPaint(
                0, 0, new Color(147, 51, 234, 30),
                gradient.getWidth(), gradient.getHeight(), new Color(236, 72, 153, 30)
        );
        g2d.setPaint(overlay);
        g2d.fillRect(0, 0, gradient.getWidth(), gradient.getHeight());
        g2d.dispose();

        return gradient;
    }

    @Override
    public void dispose() {
        if (particleTimer != null) {
            particleTimer.stop();
        }
        super.dispose();
    }

    public static void init(Runnable onPlayCallback) {
        SwingUtilities.invokeLater(() -> {
            if (INSTANCE != null) {
                INSTANCE.dispose();
            }
            INSTANCE = new LoginScreen(onPlayCallback);
            INSTANCE.setVisible(true);
        });
    }
}
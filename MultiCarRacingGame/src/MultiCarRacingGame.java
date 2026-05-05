package multithreading;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.sound.sampled.*;
import java.io.File;

public class MultiCarRacingGame extends JPanel implements Runnable {

    // ============ INNER CLASSES ============

    // Car class representing each racer (implements Runnable for threading)
    class Car implements Runnable {
        String name;
        int x, y;
        int lane;
        int speed;
        int progress = 0;
        int lap = 1;
        Color color;
        boolean finished = false;
        
        boolean crashed = false;
        long finishTime = 0;
        int boostRemaining = 0;

        Car(String name, Color color, int lane, int baseSpeed) {
            this.name = name;
            this.color = color;
            this.lane = lane;
            this.speed = baseSpeed;
            this.x = START_X;
            this.y = LANE_Y[lane];
        }

        @Override
        public void run() {
            while (!finished && gameRunning && !crashed) {
                if (!racePaused && !gameOver) {
                    // Apply boost if available
                    int currentSpeed = speed;
                    if (boostRemaining > 0) {
                        currentSpeed = Math.min(MAX_SPEED, speed + 5);
                        boostRemaining--;
                    }

                    // Random events (traffic, weather, etc.)
                    currentSpeed = applyRandomEvents(currentSpeed);

                    // Move car
                    progress += currentSpeed;
                    x = START_X + progress;

                    // Smooth vertical movement for lane changes
                    int targetY = LANE_Y[lane];
                    if (y < targetY) {
                        y += Math.min(targetY - y, 4);
                    } else if (y > targetY) {
                        y -= Math.min(y - targetY, 4);
                    }

                    // AI occasionally changes lane
                    if (this != playerCar && Math.random() < 0.02) {
                        int dir = (Math.random() < 0.5) ? -1 : 1;
                        int newLane = lane + dir;
                        if (newLane >= 0 && newLane < LANE_Y.length) {
                            boolean safe = true;
                            for (Car other : cars) {
                                if (other != this && other.lane == newLane && Math.abs(other.progress - progress) < 60) {
                                    safe = false;
                                    break;
                                }
                            }
                            if (safe) {
                                lane = newLane;
                            }
                        }
                    }

                    // Check for lap completion
                    if (progress >= TRACK_LENGTH) {
                        completeLap();
                    }

                    // Update UI and check collisions
                    repaint();

                    try {
                        Thread.sleep(50); // Control update rate
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {}
                }
            }
        }

        private int applyRandomEvents(int currentSpeed) {
            Random rand = new Random();

            // Random traffic jam (slow down)
            if (rand.nextInt(100) < trafficJamProbability) {
                currentSpeed = Math.max(MIN_SPEED, currentSpeed - 3);
                addMessage(name + " hit traffic! 🚗");
            }

            // Random oil slick (slow down significantly)
            if (rand.nextInt(100) < oilSlickProbability) {
                currentSpeed = Math.max(MIN_SPEED, currentSpeed - 6);
                addMessage(name + " hit oil slick! ⚠️");
            }

            // Random boost
            if (rand.nextInt(100) < boostProbability && boostRemaining == 0) {
                boostRemaining = 30;
                addMessage(name + " got NOS BOOST! 🚀");
            }

            // Weather effect
            if (weatherEffect && rand.nextInt(100) < 20) {
                currentSpeed = Math.max(MIN_SPEED, currentSpeed - 2);
            }

            return currentSpeed;
        }

        private void completeLap() {
            progress = 0;
            x = START_X;
            lap++;

            addMessage(name + " completed lap " + (lap-1) + "!");
            // Increase speed slightly each lap
            speed = Math.min(MAX_SPEED, speed + 1);
        }

        void handleCrash() {
            crashed = true;
            gameOver = true;
            addMessage("💥 CRASH! " + name + " crashed! Game Over! 💥");
            repaint();
        }

        void setSpeed(int newSpeed) {
            this.speed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, newSpeed));
        }
    }

    // ============ CONSTANTS ============
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 600;
    private static final int START_X = 50;
    private static final int TRACK_LENGTH = 850;
    private static final int[] LANE_Y = {80, 160, 240, 320, 400};
    private static final int MAX_SPEED = 25;
    private static final int MIN_SPEED = 5;

    // ============ GAME VARIABLES ============
    private List<Car> cars = new ArrayList<>();
    private List<Car> finishedOrder = Collections.synchronizedList(new ArrayList<>());
    private boolean gameRunning = true;
    private boolean racePaused = false;
    private boolean gameOver = false;
    private boolean weatherEffect = false;
    private long raceStartTime;
    private int totalLaps = 3;

    // Probability variables for game events
    private int trafficJamProbability = 10;
    private int oilSlickProbability = 5;
    private int boostProbability = 8;

    // Score system
    private int playerScore = 0;
    private int highScore = 0;

    // Thread management
    private ExecutorService raceExecutor;
    private Thread gameLoopThread;
    private JTextArea messageArea;
    private Queue<String> messages = new ConcurrentLinkedQueue<>();

    // UI Components
    private JButton startButton, pauseButton, resetButton;
    private JButton speedUpButton, speedDownButton;
    private JCheckBox weatherCheckBox;
    private JSlider difficultySlider;
    private JLabel scoreLabel, timeLabel, statusLabel;

    // Player car (controlled by keyboard)
    private Car playerCar;
    private int selectedLane = 2; // Middle lane

    // Audio
    private Clip backgroundMusic;

    public MultiCarRacingGame() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(new Color(30, 30, 30));
        setFocusable(true);
        
        setupKeyBindings();

        initUI();
        initGame();
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // Top panel for scores and controls
        JPanel topPanel = new JPanel(new GridLayout(2, 1));
        topPanel.setBackground(new Color(50, 50, 50));

        // Score panel
        JPanel scorePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        scoreLabel = new JLabel("Score: 0");
        timeLabel = new JLabel("Time: 0.0s");
        statusLabel = new JLabel("🏁 Ready to Race! 🏁");
        scorePanel.add(scoreLabel);
        scorePanel.add(Box.createHorizontalStrut(20));
        scorePanel.add(timeLabel);
        scorePanel.add(Box.createHorizontalStrut(20));
        scorePanel.add(statusLabel);

        // Control buttons panel
        JPanel controlPanel = new JPanel(new FlowLayout());
        startButton = new JButton("Start Race");
        pauseButton = new JButton("Pause");
        resetButton = new JButton("Reset");
        speedUpButton = new JButton("+ Speed");
        speedDownButton = new JButton("- Speed");
        weatherCheckBox = new JCheckBox("Weather Effects");

        startButton.addActionListener(e -> startRace());
        pauseButton.addActionListener(e -> togglePause());
        resetButton.addActionListener(e -> resetGame());
        speedUpButton.addActionListener(e -> increasePlayerSpeed());
        speedDownButton.addActionListener(e -> decreasePlayerSpeed());
        weatherCheckBox.addActionListener(e -> weatherEffect = weatherCheckBox.isSelected());

        controlPanel.add(startButton);
        controlPanel.add(pauseButton);
        controlPanel.add(resetButton);
        controlPanel.add(speedUpButton);
        controlPanel.add(speedDownButton);
        controlPanel.add(weatherCheckBox);

        // Difficulty slider
        JPanel diffPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        diffPanel.add(new JLabel("Difficulty:"));
        difficultySlider = new JSlider(1, 10, 5);
        difficultySlider.addChangeListener(e -> updateDifficulty());
        diffPanel.add(difficultySlider);
        controlPanel.add(diffPanel);

        topPanel.add(scorePanel);
        topPanel.add(controlPanel);
        add(topPanel, BorderLayout.NORTH);

        // Message area (right side)
        messageArea = new JTextArea(15, 25);
        messageArea.setEditable(false);
        messageArea.setBackground(Color.BLACK);
        messageArea.setForeground(Color.GREEN);
        messageArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane scrollPane = new JScrollPane(messageArea);
        scrollPane.setPreferredSize(new Dimension(250, HEIGHT));
        add(scrollPane, BorderLayout.EAST);

        // Bottom panel for instructions
        JPanel bottomPanel = new JPanel();
        bottomPanel.setBackground(new Color(50, 50, 50));
        JLabel instructions = new JLabel("Controls: ↑ ↓ to change lanes | SPACE to pause | → to boost | ← to brake");
        instructions.setForeground(Color.WHITE);
        bottomPanel.add(instructions);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void initGame() {
        cars.clear();
        finishedOrder.clear();
        gameOver = false;
        playerScore = 0;

        // Create AI cars with different speeds and colors
        cars.add(new Car("Red Racer", Color.RED, 0, 12));
        cars.add(new Car("Blue Racer", Color.BLUE, 1, 10));
        cars.add(new Car("Green Racer", Color.GREEN, 3, 11));
        cars.add(new Car("Yellow Racer", Color.YELLOW, 4, 9));

        // Create player car
        playerCar = new Car("YOU", Color.CYAN, 2, 13);
        playerCar.x = START_X;
        playerCar.y = LANE_Y[2];
        cars.add(2, playerCar); // Insert at middle
    }

    private void startRace() {
        if (raceExecutor != null && !raceExecutor.isShutdown()) {
            raceExecutor.shutdownNow();
        }

        initGame();
        gameRunning = true;
        racePaused = false;
        gameOver = false;
        raceStartTime = System.currentTimeMillis();

        playBackgroundMusic("src/music.wav");

        // Create thread pool for all cars
        raceExecutor = Executors.newFixedThreadPool(cars.size());

        // Start all car threads with different priorities
        for (int i = 0; i < cars.size(); i++) {
            Car car = cars.get(i);
            Thread thread = new Thread(car, car.name);

            // Set priority based on car speed
            int priority = Thread.NORM_PRIORITY + (car.speed - MIN_SPEED) / 3;
            priority = Math.min(Thread.MAX_PRIORITY, Math.max(Thread.MIN_PRIORITY, priority));
            thread.setPriority(priority);

            raceExecutor.execute(thread);
        }

        // Start game loop for UI updates
        if (gameLoopThread == null || !gameLoopThread.isAlive()) {
            gameLoopThread = new Thread(this);
            gameLoopThread.start();
        }

        addMessage("🏁 RACE STARTED! Endless Mode! 🏁");
        statusLabel.setText("🏁 Racing... 🏁");
        startButton.setEnabled(false);
    }

    private void togglePause() {
        if (gameRunning && !gameOver) {
            racePaused = !racePaused;
            pauseButton.setText(racePaused ? "Resume" : "Pause");
            addMessage(racePaused ? "⏸ Game Paused" : "▶ Game Resumed");
            statusLabel.setText(racePaused ? "⏸ Paused" : "🏁 Racing... 🏁");
        }
    }

    private void resetGame() {
        gameRunning = false;
        if (raceExecutor != null) {
            raceExecutor.shutdownNow();
        }
        initGame();
        repaint();
        addMessage("Game Reset! Press Start Race to begin.");
        statusLabel.setText("🏁 Ready to Race! 🏁");
        startButton.setEnabled(true);
        gameOver = false;
        racePaused = false;
        pauseButton.setText("Pause");
        stopBackgroundMusic();
    }

    private void updateDifficulty() {
        int difficulty = difficultySlider.getValue();
        // Higher difficulty = more obstacles
        trafficJamProbability = 5 + difficulty;
        oilSlickProbability = 3 + difficulty / 2;
        boostProbability = 15 - difficulty;

        // Adjust AI speeds based on difficulty
        for (Car car : cars) {
            if (car != playerCar) {
                int baseSpeed = 8 + difficulty / 2;
                car.setSpeed(baseSpeed);
            }
        }

        addMessage("Difficulty set to " + difficulty + "/10");
    }

    private void increasePlayerSpeed() {
        if (playerCar != null && !gameOver && !racePaused) {
            playerCar.setSpeed(playerCar.speed + 2);
            addMessage("Player speed increased to " + playerCar.speed);
        }
    }

    private void decreasePlayerSpeed() {
        if (playerCar != null && !gameOver && !racePaused) {
            playerCar.setSpeed(playerCar.speed - 2);
            addMessage("Player speed decreased to " + playerCar.speed);
        }
    }

    private void checkCollisions() {
        // Check if player collides with any AI car in same lane
        for (Car car : cars) {
            if (car != playerCar && !car.crashed && !playerCar.crashed) {
                if (car.lane == playerCar.lane &&
                        Math.abs(car.progress - playerCar.progress) < 30) {
                    // CRUSH DETECTION!
                    playerCar.handleCrash();
                    car.handleCrash();
                    addMessage("💥 CRASH! You collided with " + car.name + "! 💥");
                    statusLabel.setText("💥 GAME OVER - CRASH! 💥");
                    startButton.setEnabled(true);
                    raceExecutor.shutdownNow();
                    stopBackgroundMusic();
                    playSoundEffect("src/crash.wav");
                    return;
                }
            }
        }
    }

    private void updateScore() {
        // Score based on progress
        if (!playerCar.crashed) {
            playerScore = playerCar.progress / 10 + (playerCar.lap - 1) * 100;
        }
        highScore = Math.max(highScore, playerScore);

        scoreLabel.setText("Score: " + playerScore + " | High Score: " + highScore);

        // Update time
        if (raceStartTime > 0 && !gameOver) {
            double elapsed = (System.currentTimeMillis() - raceStartTime) / 1000.0;
            timeLabel.setText(String.format("Time: %.1fs", elapsed));
        }
    }

    private void addMessage(String msg) {
        messages.add(msg);
        // Limit message queue size
        if (messages.size() > 50) {
            messages.poll();
        }
    }

    private void updateMessages() {
        while (!messages.isEmpty()) {
            String msg = messages.poll();
            messageArea.append("> " + msg + "\n");
            // Auto-scroll
            messageArea.setCaretPosition(messageArea.getDocument().getLength());
        }
    }

    private void playBackgroundMusic(String filePath) {
        try {
            File musicPath = new File(filePath);
            if (musicPath.exists()) {
                if (backgroundMusic != null && backgroundMusic.isRunning()) {
                    backgroundMusic.stop();
                }
                AudioInputStream audioInput = AudioSystem.getAudioInputStream(musicPath);
                backgroundMusic = AudioSystem.getClip();
                backgroundMusic.open(audioInput);
                backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY);
                backgroundMusic.start();
            } else {
                addMessage("🎵 Tip: Add a 'music.wav' file to the 'src' folder to hear music!");
            }
        } catch (Exception e) {
            System.out.println("Error playing music: " + e.getMessage());
        }
    }

    private void stopBackgroundMusic() {
        if (backgroundMusic != null && backgroundMusic.isRunning()) {
            backgroundMusic.stop();
        }
    }

    private void playSoundEffect(String filePath) {
        try {
            File soundPath = new File(filePath);
            if (soundPath.exists()) {
                AudioInputStream audioInput = AudioSystem.getAudioInputStream(soundPath);
                Clip clip = AudioSystem.getClip();
                clip.open(audioInput);
                clip.start();
            }
        } catch (Exception e) {
            System.out.println("Error playing sound effect: " + e.getMessage());
        }
    }

    // ============ GAME LOOP ============
    @Override
    public void run() {
        while (gameRunning) {
            if (!racePaused && !gameOver) {
                checkCollisions();
                updateScore();
            }

            // Always repaint to ensure explosions and game over overlay are drawn
            repaint();

            updateMessages();



            try {
                Thread.sleep(16); // ~60 FPS
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    // ============ GRAPHICS ============
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Anti-aliasing for smooth graphics
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw track
        drawTrack(g2d);

        // Draw cars
        for (Car car : cars) {
            drawCar(g2d, car);
        }

        // Draw lap counter for each lane
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        for (int i = 0; i < LANE_Y.length; i++) {
            Car carInLane = getCarInLane(i);
            if (carInLane != null) {
                String lapText = "Lap: " + carInLane.lap;
                g2d.drawString(lapText, 5, LANE_Y[i] + 15);

                // Speed indicator
                int speedPercent = (carInLane.speed - MIN_SPEED) * 100 / (MAX_SPEED - MIN_SPEED);
                g2d.setColor(speedPercent > 70 ? Color.GREEN : speedPercent > 40 ? Color.YELLOW : Color.RED);
                g2d.fillRect(5, LANE_Y[i] + 22, Math.max(0, speedPercent / 2), 5);
                g2d.setColor(Color.WHITE);
                g2d.drawRect(5, LANE_Y[i] + 22, 50, 5);
            }
        }

        // Draw weather effect
        if (weatherEffect) {
            g2d.setColor(new Color(100, 100, 255, 50));
            g2d.fillRect(0, 0, WIDTH, HEIGHT);
            drawRain(g2d);
        }

        // Draw finish line
        g2d.setColor(Color.WHITE);
        for (int i = 0; i < LANE_Y.length; i++) {
            for (int j = 0; j < 8; j++) {
                if ((j + System.currentTimeMillis() / 100) % 2 == 0) {
                    g2d.fillRect(START_X + TRACK_LENGTH - 20 + (j * 5), LANE_Y[i], 3, 40);
                }
            }
        }

        // Draw crash overlay if game over
        if (gameOver) {
            g2d.setColor(new Color(0, 0, 0, 100)); // Make it semi-transparent so you can still see the explosion
            g2d.fillRect(0, 0, WIDTH, HEIGHT);
            
            g2d.setColor(Color.RED);
            g2d.setFont(new Font("Arial", Font.BOLD, 60)); // Make the text even bigger
            String crashText = "💥 CRASH! GAME OVER 💥";
            FontMetrics fm = g2d.getFontMetrics();
            
            // Draw text outline for better visibility
            g2d.setColor(Color.BLACK);
            g2d.drawString(crashText, (WIDTH - fm.stringWidth(crashText)) / 2 + 2, HEIGHT / 2 + 2 - 40);
            
            g2d.setColor(Color.RED);
            g2d.drawString(crashText, (WIDTH - fm.stringWidth(crashText)) / 2, HEIGHT / 2 - 40);
            
            // Draw Score
            g2d.setFont(new Font("Arial", Font.BOLD, 40));
            String scoreText = "Total Score: " + playerScore;
            FontMetrics fmScore = g2d.getFontMetrics();
            
            g2d.setColor(Color.BLACK);
            g2d.drawString(scoreText, (WIDTH - fmScore.stringWidth(scoreText)) / 2 + 2, HEIGHT / 2 + 40 + 2);
            
            g2d.setColor(Color.YELLOW);
            g2d.drawString(scoreText, (WIDTH - fmScore.stringWidth(scoreText)) / 2, HEIGHT / 2 + 40);
        }
    }

    private void drawTrack(Graphics2D g) {
        // Draw lanes
        for (int i = 0; i < LANE_Y.length; i++) {
            // Lane background
            g.setColor(i == selectedLane ? new Color(40, 40, 60) : new Color(50, 50, 50));
            g.fillRect(0, LANE_Y[i] - 20, WIDTH, 60);

            // Lane separator
            g.setColor(Color.GRAY);
            g.drawLine(0, LANE_Y[i] + 40, WIDTH, LANE_Y[i] + 40);

            // Lane number
            g.setColor(Color.LIGHT_GRAY);
            g.setFont(new Font("Arial", Font.BOLD, 12));
            g.drawString("Lane " + (i + 1), 5, LANE_Y[i] - 5);
        }

        // Draw start line
        g.setColor(Color.GREEN);
        g.setStroke(new BasicStroke(3));
        g.drawLine(START_X, 50, START_X, HEIGHT - 80);

        // Draw track boundaries
        g.setColor(Color.WHITE);
        g.drawRect(20, 50, WIDTH - 300, HEIGHT - 130);
    }

    private void drawCar(Graphics2D g, Car car) {
        if (car.crashed) {
            drawCrashedCar(g, car);
            return;
        }

        // Car shadow
        g.setColor(new Color(0, 0, 0, 50));
        g.fillRoundRect(car.x + 2, car.y - 13, 50, 30, 10, 10);

        // Car body
        g.setColor(car.color);
        g.fillRoundRect(car.x, car.y - 15, 50, 30, 10, 10);
        
        // Windshield and rear window
        g.setColor(new Color(30, 30, 30));
        g.fillRoundRect(car.x + 12, car.y - 12, 12, 24, 4, 4); // Rear window equivalent
        g.fillRoundRect(car.x + 30, car.y - 10, 8, 20, 3, 3); // Windshield equivalent
        
        // Roof
        g.setColor(car.color.brighter());
        g.fillRect(car.x + 20, car.y - 10, 12, 20);

        // Spoiler
        g.setColor(car.color.darker());
        g.fillRoundRect(car.x + 2, car.y - 12, 4, 24, 2, 2);

        // Wheels
        g.setColor(Color.BLACK);
        g.fillRoundRect(car.x + 8, car.y - 17, 10, 4, 2, 2); // Top left
        g.fillRoundRect(car.x + 35, car.y - 17, 10, 4, 2, 2); // Top right
        g.fillRoundRect(car.x + 8, car.y + 13, 10, 4, 2, 2); // Bottom left
        g.fillRoundRect(car.x + 35, car.y + 13, 10, 4, 2, 2); // Bottom right

        // Headlights
        g.setColor(Color.YELLOW);
        g.fillOval(car.x + 46, car.y - 12, 4, 6);
        g.fillOval(car.x + 46, car.y + 6, 4, 6);

        // Name label
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 10));
        g.drawString(car.name, car.x + 5, car.y - 20);

        // Speed effect
        if (car.boostRemaining > 0) {
            g.setColor(Color.ORANGE);
            for (int i = 0; i < 3; i++) {
                g.fillRect(car.x - 5 - i*4, car.y - 2, 4, 4);
            }
            g.setColor(Color.RED);
            for (int i = 0; i < 3; i++) {
                g.fillRect(car.x - 5 - i*4, car.y - 4, 3, 2);
                g.fillRect(car.x - 5 - i*4, car.y + 2, 3, 2);
            }
        }
    }

    private void drawCrashedCar(Graphics2D g, Car car) {
        // Draw a massive explosion effect
        g.setColor(new Color(255, 100, 0, 200)); // Transparent orange
        g.fillOval(car.x - 20, car.y - 30, 90, 60);
        
        g.setColor(Color.RED);
        g.fillOval(car.x - 5, car.y - 15, 60, 30);
        
        g.setColor(Color.YELLOW);
        g.fillOval(car.x + 10, car.y - 5, 30, 10);
        
        // Draw the charred car body
        g.setColor(Color.DARK_GRAY);
        g.fillRoundRect(car.x, car.y - 15, 50, 30, 10, 10);
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString("💥", car.x + 10, car.y + 10);
    }

    private void drawRain(Graphics2D g) {
        Random rand = new Random();
        g.setColor(new Color(150, 150, 255, 100));
        for (int i = 0; i < 100; i++) {
            int x = rand.nextInt(WIDTH);
            int y = (int) (rand.nextInt(HEIGHT) + System.currentTimeMillis() / 10) % HEIGHT;
            g.drawLine(x, y, x - 5, y + 10);
        }
    }

    private Car getCarInLane(int lane) {
        for (Car car : cars) {
            if (car.lane == lane && !car.crashed) {
                return car;
            }
        }
        return null;
    }

    // ============ KEYBOARD CONTROLS ============
    private void setupKeyBindings() {
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "up");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "down");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "left");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "right");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "space");

        am.put("up", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (selectedLane > 0 && playerCar != null && !gameOver && !racePaused) {
                    selectedLane--;
                    playerCar.lane = selectedLane;
                    addMessage("Moving to lane " + (selectedLane + 1));
                }
            }
        });
        
        am.put("down", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (selectedLane < LANE_Y.length - 1 && playerCar != null && !gameOver && !racePaused) {
                    selectedLane++;
                    playerCar.lane = selectedLane;
                    addMessage("Moving to lane " + (selectedLane + 1));
                }
            }
        });
        
        am.put("left", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (playerCar != null && !gameOver && !racePaused) {
                    playerCar.setSpeed(playerCar.speed - 2);
                    addMessage("Braking!");
                }
            }
        });
        
        am.put("right", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (playerCar != null && !gameOver && !racePaused) {
                    playerCar.boostRemaining = 20;
                    addMessage("🏎️ BOOST ACTIVATED! 🏎️");
                    playSoundEffect("src/boost.wav");
                }
            }
        });
        
        am.put("space", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                togglePause();
            }
        });
    }

    // ============ MAIN METHOD ============
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("🏎️ MULTI-CAR RACING GAME 🏎️");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.add(new MultiCarRacingGame());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            // Display game info
            System.out.println("=== MULTI-CAR RACING GAME ===");
            System.out.println("Multithreading Features Demonstrated:");
            System.out.println("✓ Multiple concurrent car threads");
            System.out.println("✓ Thread priorities based on car speed");
            System.out.println("✓ ExecutorService thread pool management");
            System.out.println("✓ Synchronized collections for race results");
            System.out.println("✓ Real-time collision detection (CRUSH)");
            System.out.println("✓ Dynamic scoring system");
            System.out.println("✓ Variable speed control (boost/slowdown)");
            System.out.println("✓ Weather effects and random events");
            System.out.println("\nInstructions:");
            System.out.println("- Use UP/DOWN arrows to change lanes");
            System.out.println("- Press RIGHT for speed boost, LEFT to brake");
            System.out.println("- Press SPACE to pause/resume");
            System.out.println("- Survive as long as you can!");
            System.out.println("- Avoid collisions with other cars!\n");
        });
    }
}
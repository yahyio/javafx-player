package dev.yahya.player;

import java.io.File;
import java.util.List;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

public class MusicPlayerApp extends Application {

    private static final int BANDS = 48;

    private final ObservableList<File> playlist = FXCollections.observableArrayList();
    private MediaPlayer player;
    private ListView<File> listView;
    private Label trackLabel;
    private Label timeLabel;
    private Button playButton;
    private Slider seekSlider;
    private Slider volumeSlider;
    private Canvas spectrum;
    private boolean seeking;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #121016;");

        listView = new ListView<>(playlist);
        listView.setPrefWidth(240);
        listView.setStyle(
            "-fx-control-inner-background: #1a1720;" +
            "-fx-background-color: #1a1720;" +
            "-fx-text-fill: #e8e4dc;"
        );
        listView.setCellFactory(view -> new ListCell<File>() {
            @Override
            protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : stripExtension(item.getName()));
                setStyle("-fx-text-fill: #c9c4ba; -fx-background-color: transparent; -fx-padding: 8 12;");
            }
        });
        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, file) -> {
            if (file != null) {
                play(file);
            }
        });

        Button addButton = styledButton("+ Add songs");
        addButton.setMaxWidth(Double.MAX_VALUE);
        addButton.setOnAction(e -> pickFiles(stage));

        VBox left = new VBox(10, addButton, listView);
        VBox.setVgrow(listView, Priority.ALWAYS);
        left.setPadding(new Insets(14));

        spectrum = new Canvas(560, 260);
        trackLabel = new Label("Nothing playing");
        trackLabel.setStyle("-fx-text-fill: #e8e4dc; -fx-font-size: 19px; -fx-font-weight: bold;");
        timeLabel = new Label("0:00 / 0:00");
        timeLabel.setStyle("-fx-text-fill: #8d889e; -fx-font-size: 12px;");

        seekSlider = new Slider(0, 1, 0);
        seekSlider.setOnMousePressed(e -> seeking = true);
        seekSlider.setOnMouseReleased(e -> {
            if (player != null) {
                player.seek(player.getTotalDuration().multiply(seekSlider.getValue()));
            }
            seeking = false;
        });

        playButton = styledButton("▶");
        playButton.setPrefWidth(64);
        playButton.setOnAction(e -> togglePlay());

        Button prevButton = styledButton("⏮");
        prevButton.setOnAction(e -> skip(-1));
        Button nextButton = styledButton("⏭");
        nextButton.setOnAction(e -> skip(1));

        volumeSlider = new Slider(0, 1, 0.8);
        volumeSlider.setPrefWidth(110);
        volumeSlider.valueProperty().addListener((obs, old, value) -> {
            if (player != null) {
                player.setVolume(value.doubleValue());
            }
        });

        HBox controls = new HBox(14, prevButton, playButton, nextButton,
                new Label("  "), volumeSlider);
        controls.setAlignment(Pos.CENTER);

        VBox center = new VBox(14, spectrum, trackLabel, timeLabel, seekSlider, controls);
        center.setAlignment(Pos.CENTER);
        center.setPadding(new Insets(24));

        root.setLeft(left);
        root.setCenter(center);

        drawIdleSpectrum();

        stage.setTitle("Lumen Player");
        stage.setScene(new Scene(root, 880, 540));
        stage.show();
    }

    private Button styledButton(String text) {
        Button button = new Button(text);
        button.setStyle(
            "-fx-background-color: #2a2433;" +
            "-fx-text-fill: #e8e4dc;" +
            "-fx-background-radius: 10;" +
            "-fx-padding: 8 16;" +
            "-fx-cursor: hand;"
        );
        button.setOnMouseEntered(e -> button.setStyle(button.getStyle() + "-fx-background-color: #8d6bff;"));
        button.setOnMouseExited(e -> button.setStyle(button.getStyle().replace("-fx-background-color: #8d6bff;", "")));
        return button;
    }

    private void pickFiles(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add songs");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Audio", "*.mp3", "*.wav", "*.m4a", "*.aac")
        );
        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files != null) {
            playlist.addAll(files);
            if (player == null && !playlist.isEmpty()) {
                listView.getSelectionModel().select(0);
            }
        }
    }

    private void play(File file) {
        if (player != null) {
            player.dispose();
        }

        player = new MediaPlayer(new Media(file.toURI().toString()));
        player.setVolume(volumeSlider.getValue());
        player.setAudioSpectrumNumBands(BANDS);
        player.setAudioSpectrumInterval(0.05);
        player.setAudioSpectrumListener((timestamp, duration, magnitudes, phases) -> drawSpectrum(magnitudes));

        player.currentTimeProperty().addListener((obs, old, time) -> {
            if (!seeking && player.getTotalDuration() != null) {
                seekSlider.setValue(time.toMillis() / player.getTotalDuration().toMillis());
            }
            timeLabel.setText(format(time) + " / " + format(player.getTotalDuration()));
        });

        player.setOnEndOfMedia(() -> skip(1));
        player.play();

        trackLabel.setText(stripExtension(file.getName()));
        playButton.setText("⏸");
    }

    private void togglePlay() {
        if (player == null) {
            return;
        }
        if (player.getStatus() == MediaPlayer.Status.PLAYING) {
            player.pause();
            playButton.setText("▶");
        } else {
            player.play();
            playButton.setText("⏸");
        }
    }

    private void skip(int direction) {
        int index = listView.getSelectionModel().getSelectedIndex();
        int next = index + direction;
        if (next >= 0 && next < playlist.size()) {
            listView.getSelectionModel().select(next);
        }
    }

    private void drawSpectrum(float[] magnitudes) {
        GraphicsContext g = spectrum.getGraphicsContext2D();
        double w = spectrum.getWidth();
        double h = spectrum.getHeight();
        g.clearRect(0, 0, w, h);

        double barWidth = w / BANDS;
        LinearGradient gradient = new LinearGradient(0, 1, 0, 0, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#8d6bff")), new Stop(1, Color.web("#4cc9a8")));
        g.setFill(gradient);

        for (int i = 0; i < BANDS; i++) {
            double value = Math.max(0, magnitudes[i] + 60) / 60.0;
            double barHeight = Math.max(3, value * h * 0.9);
            g.fillRoundRect(i * barWidth + 2, h - barHeight, barWidth - 4, barHeight, 6, 6);
        }
    }

    private void drawIdleSpectrum() {
        GraphicsContext g = spectrum.getGraphicsContext2D();
        double w = spectrum.getWidth();
        double h = spectrum.getHeight();
        g.setFill(Color.web("#1a1720"));
        g.fillRoundRect(0, 0, w, h, 16, 16);
        g.setFill(Color.web("#8d889e"));
        g.fillText("Add songs and press play", w / 2 - 70, h / 2);
    }

    private static String format(Duration duration) {
        if (duration == null || duration.isUnknown()) {
            return "0:00";
        }
        int seconds = (int) duration.toSeconds();
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}

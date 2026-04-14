package org.bytehook.gui;

import javafx.application.Application;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.bytehook.core.instrument.ByteHookTransformer;
import org.bytehook.decompiler.ByteHookDecompiler;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Main extends Application {

    private final ByteHookDecompiler decompiler = new ByteHookDecompiler();
    private final ByteHookTransformer transformer = new ByteHookTransformer();

    private CodeArea originalSourceView;
    private CodeArea instrumentedSourceView;
    private TextField hookMessageInput;
    private ComboBox<ByteHookTransformer.HookType> hookTypePicker;
    private CheckBox showBytecodeToggle;
    private ComboBox<String> fontPicker;
    private ComboBox<String> themePicker;
    private Spinner<Integer> fontSizeSpinner;
    private byte[] currentClassBytes;
    private BorderPane root;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("ByteHook Workbench");

        root = new BorderPane();
        root.getStyleClass().add("theme-dark");

        // Top Toolbar
        ToolBar toolBar = new ToolBar();
        Button openBtn = new Button("Open .class File");
        openBtn.setOnAction(e -> openFile(primaryStage));
        
        hookMessageInput = new TextField("Hook Injected");
        hookMessageInput.setPromptText("Hook Message");
        
        hookTypePicker = new ComboBox<>();
        hookTypePicker.getItems().addAll(ByteHookTransformer.HookType.values());
        hookTypePicker.setValue(ByteHookTransformer.HookType.LOGGING);
        hookTypePicker.setOnAction(e -> applyHook());

        Button applyBtn = new Button("Apply Hook");
        applyBtn.setOnAction(e -> applyHook());

        showBytecodeToggle = new CheckBox("Show Bytecode");
        showBytecodeToggle.setSelected(false);
        showBytecodeToggle.setOnAction(e -> applyHook());

        // Font Settings
        fontPicker = new ComboBox<>();
        fontPicker.getItems().addAll("Consolas", "Monaco", "Courier New", "monospace");
        fontPicker.setValue("Consolas");
        fontPicker.setOnAction(e -> updateFont());

        fontSizeSpinner = new Spinner<>(8, 32, 13);
        fontSizeSpinner.setPrefWidth(100); 
        fontSizeSpinner.valueProperty().addListener((obs, oldVal, newVal) -> updateFont());

        // Theme Settings
        themePicker = new ComboBox<>();
        themePicker.getItems().addAll("Eclipse Dark", "Eclipse Light");
        themePicker.setValue("Eclipse Dark");
        themePicker.setPrefWidth(120);
        themePicker.setOnAction(e -> updateTheme());

        toolBar.getItems().addAll(
            openBtn, new Separator(), 
            new Label("Type:"), hookTypePicker,
            new Label("Message:"), hookMessageInput, applyBtn, 
            new Separator(), 
            showBytecodeToggle,
            new Separator(),
            new Label("Font:"), fontPicker, fontSizeSpinner,
            new Separator(),
            new Label("Theme:"), themePicker
        );
        root.setTop(toolBar);

        // Center: Split Views
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.HORIZONTAL);

        originalSourceView = createCodeArea();
        instrumentedSourceView = createCodeArea();

        VBox leftBox = new VBox(new Label(" ORIGINAL SOURCE"), originalSourceView);
        VBox rightBox = new VBox(new Label(" INSTRUMENTED PREVIEW"), instrumentedSourceView);
        
        VBox.setVgrow(originalSourceView, javafx.scene.layout.Priority.ALWAYS);
        VBox.setVgrow(instrumentedSourceView, javafx.scene.layout.Priority.ALWAYS);

        splitPane.getItems().addAll(leftBox, rightBox);
        splitPane.setDividerPositions(0.5);

        root.setCenter(splitPane);

        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private CodeArea createCodeArea() {
        CodeArea area = new CodeArea();
        area.setEditable(false);
        area.setParagraphGraphicFactory(LineNumberFactory.get(area));
        area.textProperty().addListener((obs, oldText, newText) -> {
            area.setStyleSpans(0, JavaSyntaxHighlighter.computeHighlighting(newText));
        });
        return area;
    }

    private void updateFont() {
        String family = fontPicker.getValue();
        int size = fontSizeSpinner.getValue();
        String style = String.format("-fx-font-family: '%s'; -fx-font-size: %dpx;", family, size);
        originalSourceView.setStyle(style);
        instrumentedSourceView.setStyle(style);
    }

    private void updateTheme() {
        root.getStyleClass().removeAll("theme-dark", "theme-light");
        String selection = themePicker.getValue();
        if (selection.equals("Eclipse Light")) {
            root.getStyleClass().add("theme-light");
        } else {
            root.getStyleClass().add("theme-dark");
        }
    }

    private void openFile(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Class Files", "*.class"));
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            try {
                currentClassBytes = Files.readAllBytes(file.toPath());
                
                // Reset views
                boolean showBytecode = showBytecodeToggle.isSelected();
                String origDecompiled = decompiler.decompile(currentClassBytes, showBytecode);
                originalSourceView.replaceText(origDecompiled);
                originalSourceView.setStyleSpans(0, JavaSyntaxHighlighter.computeHighlighting(origDecompiled));
                
                instrumentedSourceView.replaceText(""); // Clear the preview
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void applyHook() {
        if (currentClassBytes != null) {
            try {
                boolean showBytecode = showBytecodeToggle.isSelected();
                
                // Instrumented
                byte[] transformed = transformer.transform(currentClassBytes, hookMessageInput.getText(), hookTypePicker.getValue());
                String instDecompiled = decompiler.decompile(transformed, showBytecode);
                instrumentedSourceView.replaceText(instDecompiled);
                instrumentedSourceView.setStyleSpans(0, JavaSyntaxHighlighter.computeHighlighting(instDecompiled));
                
                // Also refresh original in case "Show Bytecode" changed
                String origDecompiled = decompiler.decompile(currentClassBytes, showBytecode);
                originalSourceView.replaceText(origDecompiled);
                originalSourceView.setStyleSpans(0, JavaSyntaxHighlighter.computeHighlighting(origDecompiled));

            } catch (Exception e) {
                instrumentedSourceView.replaceText("Error transforming: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

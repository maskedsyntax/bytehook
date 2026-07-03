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
import org.bytehook.core.instrument.JarProcessor;
import org.bytehook.decompiler.ByteHookDecompiler;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Main extends Application {

    private final ByteHookDecompiler decompiler = new ByteHookDecompiler();
    private final ByteHookTransformer transformer = new ByteHookTransformer();
    private final JarProcessor jarProcessor = new JarProcessor();

    private CodeArea originalSourceView;
    private CodeArea instrumentedSourceView;
    private TreeView<String> jarTreeView;
    private TextField hookMessageInput;
    private TextField methodFilterInput;
    private ComboBox<ByteHookTransformer.HookType> hookTypePicker;
    private CheckBox showBytecodeToggle;
    private ComboBox<String> fontPicker;
    private ComboBox<String> themePicker;
    private Spinner<Integer> fontSizeSpinner;
    private byte[] currentClassBytes;
    private File currentJarFile;
    private BorderPane root;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("ByteHook Workbench");

        root = new BorderPane();
        root.getStyleClass().add("theme-dark");

        // Top Toolbar
        ToolBar toolBar = new ToolBar();
        Button openBtn = new Button("Open File");
        openBtn.setOnAction(e -> openFile(primaryStage));

        Button exportBtn = new Button("Export JAR");
        exportBtn.setOnAction(e -> exportJar(primaryStage));
        
        hookMessageInput = new TextField("Hook Injected");
        hookMessageInput.setPromptText("Hook Message");
        
        methodFilterInput = new TextField(".*");
        methodFilterInput.setPromptText("Method Regex");
        methodFilterInput.setPrefWidth(80);

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
            openBtn, exportBtn, new Separator(), 
            new Label("Type:"), hookTypePicker,
            new Label("Filter:"), methodFilterInput,
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

        jarTreeView = new TreeView<>();
        jarTreeView.setPrefWidth(250);
        jarTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.isLeaf() && newVal.getValue().endsWith(".class")) {
                loadClassFromJar();
            }
        });

        originalSourceView = createCodeArea();
        instrumentedSourceView = createCodeArea();

        VBox leftBox = new VBox(new Label(" ORIGINAL SOURCE"), originalSourceView);
        VBox rightBox = new VBox(new Label(" INSTRUMENTED PREVIEW"), instrumentedSourceView);
        
        VBox.setVgrow(originalSourceView, javafx.scene.layout.Priority.ALWAYS);
        VBox.setVgrow(instrumentedSourceView, javafx.scene.layout.Priority.ALWAYS);

        splitPane.getItems().addAll(jarTreeView, leftBox, rightBox);
        splitPane.setDividerPositions(0.2, 0.6);

        root.setCenter(splitPane);

        Scene scene = new Scene(root, 1400, 800);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private CodeArea createCodeArea() {
        CodeArea area = new CodeArea();
        area.setEditable(false);
        area.setParagraphGraphicFactory(LineNumberFactory.get(area));
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
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Java Files", "*.class", "*.jar")
        );
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            if (file.getName().endsWith(".jar")) {
                currentJarFile = file;
                loadJarTree(file);
            } else {
                try {
                    currentJarFile = null;
                    currentClassBytes = Files.readAllBytes(file.toPath());
                    jarTreeView.setRoot(new TreeItem<>(file.getName()));
                    applyHook(); 
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void loadJarTree(File jarFile) {
        TreeItem<String> rootItem = new TreeItem<>(jarFile.getName());
        rootItem.setExpanded(true);
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile)) {
            jar.stream().forEach(entry -> {
                String name = entry.getName();
                String[] parts = name.split("/");
                TreeItem<String> current = rootItem;
                for (String part : parts) {
                    TreeItem<String> next = null;
                    for (TreeItem<String> child : current.getChildren()) {
                        if (child.getValue().equals(part)) {
                            next = child;
                            break;
                        }
                    }
                    if (next == null) {
                        next = new TreeItem<>(part);
                        current.getChildren().add(next);
                    }
                    current = next;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        jarTreeView.setRoot(rootItem);
    }

    private void loadClassFromJar() {
        StringBuilder pathBuilder = new StringBuilder();
        TreeItem<String> item = jarTreeView.getSelectionModel().getSelectedItem();
        while (item != null && item.getParent() != null) {
            if (pathBuilder.length() > 0) pathBuilder.insert(0, "/");
            pathBuilder.insert(0, item.getValue());
            item = item.getParent();
        }
        String entryPath = pathBuilder.toString();

        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(currentJarFile)) {
            java.util.jar.JarEntry entry = jar.getJarEntry(entryPath);
            if (entry != null) {
                currentClassBytes = jar.getInputStream(entry).readAllBytes();
                applyHook();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void exportJar(Stage stage) {
        if (currentJarFile == null) {
            new Alert(Alert.AlertType.WARNING, "Please open a JAR file first.").show();
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName(currentJarFile.getName().replace(".jar", "-hooked.jar"));
        File saveFile = fileChooser.showSaveDialog(stage);

        if (saveFile != null) {
            try {
                jarProcessor.processJar(
                    currentJarFile, saveFile, 
                    hookMessageInput.getText(), 
                    hookTypePicker.getValue(), 
                    methodFilterInput.getText()
                );
                new Alert(Alert.AlertType.INFORMATION, "JAR Exported Successfully!").show();
            } catch (IOException e) {
                new Alert(Alert.AlertType.ERROR, "Export Failed: " + e.getMessage()).show();
            }
        }
    }

    private void applyHook() {
        if (currentClassBytes != null) {
            try {
                boolean showBytecode = showBytecodeToggle.isSelected();
                
                // Original
                String origDecompiled = decompiler.decompile(currentClassBytes, showBytecode);
                originalSourceView.replaceText(origDecompiled);
                originalSourceView.setStyleSpans(0, JavaSyntaxHighlighter.computeHighlighting(origDecompiled));

                // Instrumented
                byte[] transformed = transformer.transform(currentClassBytes, hookMessageInput.getText(), hookTypePicker.getValue(), methodFilterInput.getText());
                String instDecompiled = decompiler.decompile(transformed, showBytecode);
                instrumentedSourceView.replaceText(instDecompiled);
                instrumentedSourceView.setStyleSpans(0, JavaSyntaxHighlighter.computeHighlighting(instDecompiled));
                
                // Also refresh original in case "Show Bytecode" changed
                String refreshedOrig = decompiler.decompile(currentClassBytes, showBytecode);
                originalSourceView.replaceText(refreshedOrig);
                originalSourceView.setStyleSpans(0, JavaSyntaxHighlighter.computeHighlighting(refreshedOrig));

            } catch (Exception e) {
                instrumentedSourceView.replaceText("Error transforming: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

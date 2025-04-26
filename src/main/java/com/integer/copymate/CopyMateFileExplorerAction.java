package com.integer.copymate;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CopyMateFileExplorerAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }

        // Create a custom file selection dialog
        FileSelectionDialog dialog = new FileSelectionDialog(project);
        dialog.show();
    }

    private static class FileSelectionDialog extends DialogWrapper {
        private final Project project;
        private CheckboxTree fileTree;
        private CheckedTreeNode rootNode;
        private JBCheckBox copyMethodsOnlyCheckbox;
        private boolean isJavaAvailable;

        public FileSelectionDialog(Project project) {
            super(project);
            this.project = project;
            setTitle("Copy Mate (Select Files To Copy)");
            setModal(true);

            // Check if Java functionality is available in this IDE
            isJavaAvailable = isJavaPluginAvailable();

            init();
        }

        /**
         * Check if Java functionality is available in the current IDE
         * This handles cross-IDE compatibility
         */
        private boolean isJavaPluginAvailable() {
            try {
                // Try to load a Java-specific class
                Class.forName("com.intellij.psi.PsiJavaFile");
                return true;
            } catch (ClassNotFoundException e) {
                // Java plugin is not available
                return false;
            }
        }

        @Override
        protected JComponent createCenterPanel() {
            JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // Create root node
            rootNode = new CheckedTreeNode("Project Files");
            rootNode.setChecked(false);  // Ensure root is not checked by default

            // Build file tree
            VirtualFile baseDir = project.getBaseDir();
            if (baseDir != null) {
                addFilesRecursively(rootNode, baseDir);
            }

            // Create checkbox tree with custom cell renderer
            fileTree = new CheckboxTree(new CheckboxTree.CheckboxTreeCellRenderer() {
                @Override
                public void customizeRenderer(JTree tree, Object value, boolean selected,
                                              boolean expanded, boolean leaf, int row, boolean hasFocus) {
                    if (value instanceof CheckedTreeNode) {
                        CheckedTreeNode node = (CheckedTreeNode) value;
                        ColoredTreeCellRenderer renderer = getTextRenderer();

                        if (node.getUserObject() instanceof VirtualFile) {
                            VirtualFile file = (VirtualFile) node.getUserObject();

                            // Set appropriate icon based on file type
                            if (file.isDirectory()) {
                                renderer.setIcon(AllIcons.Nodes.Folder);
                            } else if (isJavaAvailable && file.getExtension() != null && file.getExtension().equals("java")) {
                                renderer.setIcon(AllIcons.FileTypes.Java);
                            } else {
                                renderer.setIcon(AllIcons.FileTypes.Text);
                            }

                            // Customize text appearance
                            renderer.append(file.getName(),
                                    file.isDirectory()
                                            ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                                            : SimpleTextAttributes.REGULAR_ATTRIBUTES
                            );

                            // Add path as secondary information
                            String relativePath = getRelativePath(baseDir, file);
                            if (!relativePath.isEmpty()) {
                                renderer.append(" (" + relativePath + ")",
                                        SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
                            }
                        } else {
                            renderer.append(node.getUserObject().toString(),
                                    SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                        }
                    }
                }
            }, rootNode);

            // Customize tree appearance and behavior
            fileTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
            fileTree.setRootVisible(false);  // Hide root node
            fileTree.setShowsRootHandles(true);

            // Wrap tree in scroll pane with improved styling
            JBScrollPane scrollPane = new JBScrollPane(fileTree);
            scrollPane.setPreferredSize(new Dimension(550, 500));

            // Add tree panel with title
            JPanel treePanel = new JPanel(new BorderLayout());
            treePanel.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(new Color(75, 110, 175), 1),
                    "Project Files",
                    TitledBorder.LEFT,
                    TitledBorder.TOP,
                    null,
                    new Color(75, 110, 175)
            ));
            treePanel.add(scrollPane, BorderLayout.CENTER);

            // Create options panel
            JPanel optionsPanel = new JPanel();
            optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
            optionsPanel.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(new Color(75, 110, 175), 1),
                    "Copy Options",
                    TitledBorder.LEFT,
                    TitledBorder.TOP,
                    null,
                    new Color(75, 110, 175)
            ));

            // Add "Copy Methods Only" checkbox only if Java is available
            copyMethodsOnlyCheckbox = new JBCheckBox("Copy Java Methods Signatures Only");
            copyMethodsOnlyCheckbox.setToolTipText("When enabled, only method signatures will be copied for Java files");
            copyMethodsOnlyCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
            copyMethodsOnlyCheckbox.setEnabled(isJavaAvailable);

            // Add description
            JLabel optionsDescription;
            if (isJavaAvailable) {
                optionsDescription = new JLabel(
                        "<html>When enabled, Java files will only include class and method signatures.<br>" +
                                "For all other file types, the full content will be copied.</html>");
            } else {
                optionsDescription = new JLabel(
                        "<html>Java plugin is not available in this IDE.<br>" +
                                "Method signature extraction is disabled.</html>");
                optionsDescription.setForeground(Color.RED);
            }
            optionsDescription.setAlignmentX(Component.LEFT_ALIGNMENT);

            optionsPanel.add(copyMethodsOnlyCheckbox);
            optionsPanel.add(Box.createVerticalStrut(5));
            optionsPanel.add(optionsDescription);
            optionsPanel.add(Box.createVerticalStrut(10));
            optionsPanel.setPreferredSize(new Dimension(550, 100));

            // Split the main panel
            JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
            splitPane.setTopComponent(treePanel);
            splitPane.setBottomComponent(optionsPanel);
            splitPane.setResizeWeight(0.8);
            splitPane.setDividerLocation(400);

            mainPanel.add(splitPane, BorderLayout.CENTER);

            return mainPanel;
        }

        private String getRelativePath(VirtualFile base, VirtualFile file) {
            String basePath = base.getPath();
            String filePath = file.getPath();

            if (filePath.startsWith(basePath)) {
                String relativePath = filePath.substring(basePath.length());
                if (relativePath.startsWith("/")) {
                    relativePath = relativePath.substring(1);
                }

                // Don't show the file name itself in the path
                int lastSeparator = relativePath.lastIndexOf('/');
                if (lastSeparator != -1) {
                    return relativePath.substring(0, lastSeparator);
                }
            }

            return "";
        }

        private void addFilesRecursively(CheckedTreeNode parentNode, VirtualFile directory) {
            try {
                for (VirtualFile child : directory.getChildren()) {
                    CheckedTreeNode childNode = new CheckedTreeNode(child);
                    childNode.setChecked(false);  // Ensure no files/folders are checked by default
                    parentNode.add(childNode);

                    if (child.isDirectory()) {
                        addFilesRecursively(childNode, child);
                    }
                }
            } catch (Exception e) {
                // Handle any potential errors during file listing
                e.printStackTrace();
            }
        }

        @Override
        protected JComponent createSouthPanel() {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // Common button styling
            Color buttonColor = new Color(75, 110, 175);
            Color textColor = Color.WHITE;

            // Selection buttons
            JButton selectAllButton = new JButton("Select All");
            styleButton(selectAllButton, buttonColor, textColor);
            selectAllButton.addActionListener(e -> selectAllFiles(true));

            JButton deselectAllButton = new JButton("Deselect All");
            styleButton(deselectAllButton, buttonColor, textColor);
            deselectAllButton.addActionListener(e -> selectAllFiles(false));

            // Copy content button
            JButton copyContentButton = new JButton("Copy Content");
            styleButton(copyContentButton, buttonColor, textColor);
            copyContentButton.addActionListener(e -> copySelectedContent());

            // Copy structure button
            JButton copyStructureButton = new JButton("Copy Structure");
            styleButton(copyStructureButton, buttonColor, textColor);
            copyStructureButton.addActionListener(e -> copyFileStructure());

            // Add buttons to panel
            panel.add(selectAllButton);
            panel.add(deselectAllButton);
            panel.add(copyContentButton);
            panel.add(copyStructureButton);

            return panel;
        }

        private void styleButton(JButton button, Color bgColor, Color fgColor) {
            button.setBackground(bgColor);
            button.setForeground(fgColor);
            button.setFocusPainted(false);
            button.setBorderPainted(false);
            button.setOpaque(true);
            button.setPreferredSize(new Dimension(120, 30));
            button.setFont(button.getFont().deriveFont(Font.BOLD));
        }

        private void selectAllFiles(boolean select) {
            setNodeCheckedRecursively(rootNode, select);
            fileTree.repaint();
        }

        private void setNodeCheckedRecursively(CheckedTreeNode node, boolean checked) {
            node.setChecked(checked);
            for (int i = 0; i < node.getChildCount(); i++) {
                CheckedTreeNode childNode = (CheckedTreeNode) node.getChildAt(i);
                setNodeCheckedRecursively(childNode, checked);
            }
        }

        private void copySelectedContent() {
            List<VirtualFile> selectedFiles = new ArrayList<>();

            // Traverse the tree and collect selected files
            collectSelectedFiles(rootNode, selectedFiles);

            // Check if any files were selected
            if (selectedFiles.isEmpty()) {
                JOptionPane.showMessageDialog(
                        null,
                        "No files selected",
                        "Copy Mate",
                        JOptionPane.INFORMATION_MESSAGE
                );
                return;
            }

            // Get the checkbox state - only apply if Java is available
            boolean copyMethodsOnly = isJavaAvailable && copyMethodsOnlyCheckbox.isSelected();

            // Copy selected files to clipboard
            StringBuilder contentToCopy = new StringBuilder();

            for (VirtualFile file : selectedFiles) {
                try {
                    contentToCopy.append("File Path: ").append(file.getPath()).append("\n");

                    if (copyMethodsOnly && file.getExtension() != null && file.getExtension().equals("java")) {
                        // Extract method signatures for Java files
                        String methodSignatures = extractMethodSignatures(file);
                        contentToCopy.append("Method Signatures:\n").append(methodSignatures);
                    } else {
                        // Copy full content for other files
                        String content = new String(Files.readAllBytes(Paths.get(file.getPath())));
                        contentToCopy.append("Content:\n").append(content);
                    }

                    contentToCopy.append("\n\n");
                } catch (IOException ioException) {
                    contentToCopy.append("Error reading file: ").append(ioException.getMessage()).append("\n\n");
                }
            }

            // Copy to system clipboard
            copyToClipboard(contentToCopy.toString());
        }

        private String extractMethodSignatures(VirtualFile file) {
            StringBuilder signatures = new StringBuilder();

            try {
                // Read file content as text
                String content = new String(Files.readAllBytes(Paths.get(file.getPath())));

                // Extract package
                Pattern packagePattern = Pattern.compile("package\\s+([\\w.]+)\\s*;");
                Matcher packageMatcher = packagePattern.matcher(content);
                if (packageMatcher.find()) {
                    signatures.append("package ").append(packageMatcher.group(1)).append(";\n\n");
                }

                // Track state
                boolean inClass = false;
                int braceDepth = 0;
                StringBuilder currentClass = new StringBuilder();

                // Process line by line
                String[] lines = content.split("\n");
                for (String line : lines) {
                    String trimmedLine = line.trim();

                    // Skip empty lines and comments
                    if (trimmedLine.isEmpty() || trimmedLine.startsWith("//") || trimmedLine.startsWith("/*") || trimmedLine.startsWith("*")) {
                        continue;
                    }

                    // Look for class/interface/enum declarations
                    if (!inClass && (trimmedLine.matches(".*\\b(class|interface|enum)\\b.*") && !trimmedLine.contains(";"))) {
                        inClass = true;
                        currentClass.append(trimmedLine);

                        // If class definition doesn't end with opening brace, just add it
                        if (!trimmedLine.contains("{")) {
                            currentClass.append(" {");
                        }

                        signatures.append(currentClass.toString()).append("\n");
                        currentClass.setLength(0);

                        // Count braces
                        for (char c : trimmedLine.toCharArray()) {
                            if (c == '{') braceDepth++;
                            if (c == '}') braceDepth--;
                        }
                        continue;
                    }

                    // Look for method declarations when in a class
                    if (inClass &&
                            (trimmedLine.matches("\\s*(public|private|protected|static|final|native|synchronized|abstract|transient)+\\s+[\\w\\<\\>\\[\\]]+\\s+[\\w]+\\s*\\([^\\)]*\\).*") ||
                                    trimmedLine.matches("\\s*[\\w\\<\\>\\[\\]]+\\s+[\\w]+\\s*\\([^\\)]*\\).*")) &&
                            !trimmedLine.contains(";")) {

                        // Get method signature, removing comments and body
                        String methodSignature = trimmedLine;
                        if (methodSignature.contains("{")) {
                            methodSignature = methodSignature.substring(0, methodSignature.indexOf("{")).trim();
                        }

                        // Add placeholder for method body
                        signatures.append("    ").append(methodSignature).append(" {}\n");
                    }

                    // Count braces to track nesting level
                    for (char c : trimmedLine.toCharArray()) {
                        if (c == '{') braceDepth++;
                        if (c == '}') braceDepth--;
                    }

                    // If we've exited the class, add closing brace
                    if (inClass && braceDepth == 0 && trimmedLine.contains("}")) {
                        signatures.append("}\n\n");
                        inClass = false;
                    }
                }

                // In case we didn't close the class properly
                if (inClass) {
                    signatures.append("}\n");
                }

            } catch (IOException e) {
                signatures.append("Error extracting method signatures: ").append(e.getMessage());
            }

            return signatures.toString();
        }

        private void copyFileStructure() {
            List<VirtualFile> selectedFiles = new ArrayList<>();
            List<VirtualFile> selectedDirectories = new ArrayList<>();

            // Collect selected files and directories
            collectSelectedFilesAndDirectories(rootNode, selectedFiles, selectedDirectories);

            if (selectedFiles.isEmpty() && selectedDirectories.isEmpty()) {
                JOptionPane.showMessageDialog(
                        null,
                        "No files or directories selected",
                        "Copy Mate",
                        JOptionPane.INFORMATION_MESSAGE
                );
                return;
            }

            StringBuilder structureBuilder = new StringBuilder();

            // Process directories first
            for (VirtualFile directory : selectedDirectories) {
                structureBuilder.append(generateDirectoryStructure(directory, 0));
            }

            // Process individual files that aren't in selected directories
            for (VirtualFile file : selectedFiles) {
                if (!isChildOfAnyDirectory(file, selectedDirectories)) {
                    structureBuilder.append(file.getName()).append("\n");
                }
            }

            // Copy to clipboard
            copyToClipboard(structureBuilder.toString());
        }

        private boolean isChildOfAnyDirectory(VirtualFile file, List<VirtualFile> directories) {
            for (VirtualFile dir : directories) {
                if (isChildOf(file, dir)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isChildOf(VirtualFile child, VirtualFile parent) {
            VirtualFile current = child.getParent();
            while (current != null) {
                if (current.equals(parent)) {
                    return true;
                }
                current = current.getParent();
            }
            return false;
        }

        private String generateDirectoryStructure(VirtualFile directory, int level) {
            StringBuilder result = new StringBuilder();
            String indent = "  ".repeat(level);

            // Add directory name
            result.append(indent).append(directory.getName()).append("/\n");

            // Add children
            try {
                for (VirtualFile child : directory.getChildren()) {
                    if (child.isDirectory()) {
                        result.append(generateDirectoryStructure(child, level + 1));
                    } else {
                        result.append(indent).append("  ").append(child.getName()).append("\n");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            return result.toString();
        }

        private void collectSelectedFiles(CheckedTreeNode node, List<VirtualFile> selectedFiles) {
            for (int i = 0; i < node.getChildCount(); i++) {
                CheckedTreeNode childNode = (CheckedTreeNode) node.getChildAt(i);

                if (childNode.isChecked()) {
                    Object userObject = childNode.getUserObject();
                    if (userObject instanceof VirtualFile) {
                        VirtualFile file = (VirtualFile) userObject;
                        if (!file.isDirectory()) {
                            selectedFiles.add(file);
                        }
                    }
                }

                // Recursively check child nodes
                collectSelectedFiles(childNode, selectedFiles);
            }
        }

        private void collectSelectedFilesAndDirectories(CheckedTreeNode node,
                                                        List<VirtualFile> selectedFiles,
                                                        List<VirtualFile> selectedDirectories) {
            for (int i = 0; i < node.getChildCount(); i++) {
                CheckedTreeNode childNode = (CheckedTreeNode) node.getChildAt(i);

                if (childNode.isChecked()) {
                    Object userObject = childNode.getUserObject();
                    if (userObject instanceof VirtualFile) {
                        VirtualFile file = (VirtualFile) userObject;
                        if (file.isDirectory()) {
                            selectedDirectories.add(file);
                        } else {
                            selectedFiles.add(file);
                        }
                    }
                }

                // Recursively check child nodes
                collectSelectedFilesAndDirectories(childNode, selectedFiles, selectedDirectories);
            }
        }

        private void copyToClipboard(String content) {
            try {
                StringSelection stringSelection = new StringSelection(content);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);

                JOptionPane.showMessageDialog(
                        null,
                        "Content copied to clipboard successfully",
                        "Copy Mate",
                        JOptionPane.INFORMATION_MESSAGE
                );
            } catch (Exception e) {
                JOptionPane.showMessageDialog(
                        null,
                        "Failed to copy to clipboard: " + e.getMessage(),
                        "Copy Mate Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }
}
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
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        private Map<VirtualFile, JBCheckBox> methodSignatureCheckboxes;

        public FileSelectionDialog(Project project) {
            super(project);
            this.project = project;
            this.methodSignatureCheckboxes = new HashMap<>();
            setTitle("Copy Mate (Select File To Copy)");
            setModal(true);
            init();
        }

        @Override
        protected JComponent createCenterPanel() {
            JPanel mainPanel = new JPanel(new BorderLayout());

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
                            } else {
                                renderer.setIcon(AllIcons.FileTypes.Text);
                            }

                            // Customize text appearance
                            renderer.append(file.getName(),
                                    file.isDirectory()
                                            ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                                            : SimpleTextAttributes.REGULAR_ATTRIBUTES
                            );

                            // Add "Copy Methods Only" checkbox for Java files
                            if (!file.isDirectory() && file.getExtension() != null && file.getExtension().equals("java")) {
                                if (!methodSignatureCheckboxes.containsKey(file)) {
                                    JBCheckBox methodCheckbox = new JBCheckBox("Copy Methods Only");
                                    methodSignatureCheckboxes.put(file, methodCheckbox);
                                }
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

            // Add tree selection listener to show/hide method checkboxes
            fileTree.addTreeSelectionListener(e -> {
                TreePath path = e.getPath();
                if (path != null && path.getLastPathComponent() instanceof CheckedTreeNode) {
                    CheckedTreeNode node = (CheckedTreeNode) path.getLastPathComponent();
                    if (node.getUserObject() instanceof VirtualFile) {
                        VirtualFile file = (VirtualFile) node.getUserObject();
                        updateMethodCheckboxVisibility(file);
                    }
                }
            });

            // Wrap tree in scroll pane with improved styling
            JBScrollPane scrollPane = new JBScrollPane(fileTree);
            scrollPane.setPreferredSize(new Dimension(500, 600));
            scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // Method signature panel
            JPanel methodSignaturePanel = new JPanel();
            methodSignaturePanel.setLayout(new BoxLayout(methodSignaturePanel, BoxLayout.Y_AXIS));
            methodSignaturePanel.setBorder(BorderFactory.createTitledBorder("File Options"));

            // Add method signature checkbox to panel
            for (Map.Entry<VirtualFile, JBCheckBox> entry : methodSignatureCheckboxes.entrySet()) {
                JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                filePanel.add(new JLabel(entry.getKey().getName()));
                filePanel.add(entry.getValue());
                methodSignaturePanel.add(filePanel);
                // Initially hide all
                filePanel.setVisible(false);
            }

            JScrollPane methodScrollPane = new JBScrollPane(methodSignaturePanel);
            methodScrollPane.setPreferredSize(new Dimension(500, 100));

            mainPanel.add(scrollPane, BorderLayout.CENTER);
            mainPanel.add(methodScrollPane, BorderLayout.SOUTH);

            return mainPanel;
        }

        private void updateMethodCheckboxVisibility(VirtualFile file) {
            JBCheckBox checkbox = methodSignatureCheckboxes.get(file);
            if (checkbox != null) {
                Container parent = checkbox.getParent();
                if (parent != null) {
                    parent.setVisible(true);
                }
            }
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
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

            // Button styles
            Color buttonColor = new Color(75, 110, 175);
            Color textColor = Color.WHITE;

            // Select/Deselect buttons
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

        // Copy selected content (with method signature option)
        private void copySelectedContent() {
            List<VirtualFile> selectedFiles = new ArrayList<>();

            // Traverse the tree and collect selected files
            collectSelectedFiles(rootNode, selectedFiles);

            // Copy selected files to clipboard
            if (!selectedFiles.isEmpty()) {
                StringBuilder contentToCopy = new StringBuilder();

                for (VirtualFile file : selectedFiles) {
                    JBCheckBox methodCheckbox = methodSignatureCheckboxes.get(file);
                    boolean copyMethodsOnly = methodCheckbox != null && methodCheckbox.isSelected();

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
                        ioException.printStackTrace();
                    }
                }

                // Copy to system clipboard
                copyToClipboard(contentToCopy.toString());
            } else {
                JOptionPane.showMessageDialog(
                        null,
                        "No files selected",
                        "Copy Mate",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }
        }

        // Copy file structure
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

            // Process individual files
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

        private String extractMethodSignatures(VirtualFile file) {
            StringBuilder signatures = new StringBuilder();

            try {
                // Read file content as text
                String content = new String(Files.readAllBytes(Paths.get(file.getPath())));

                // Simple parsing for class and method declarations
                String[] lines = content.split("\n");
                StringBuilder currentClass = new StringBuilder();
                boolean inClass = false;

                for (String line : lines) {
                    String trimmedLine = line.trim();

                    // Detect class/interface declarations
                    if (trimmedLine.matches(".*\\b(class|interface|enum)\\b.*\\{.*") ||
                            (trimmedLine.matches(".*\\b(class|interface|enum)\\b.*") && !trimmedLine.contains(";"))) {

                        currentClass.append(trimmedLine);
                        if (!trimmedLine.contains("{")) {
                            currentClass.append(" {");
                        }
                        signatures.append(currentClass.toString()).append("\n");
                        currentClass.setLength(0);
                        inClass = true;
                    }

                    // Detect method declarations
                    if (inClass && trimmedLine.matches(".*\\b(public|private|protected)\\b.*\\(.*\\).*") &&
                            !trimmedLine.contains(";") && !trimmedLine.contains("//")) {

                        // Eliminate body, keeping just the signature
                        String methodSig = trimmedLine;
                        if (methodSig.contains("{")) {
                            methodSig = methodSig.substring(0, methodSig.indexOf("{")).trim() + " {}";
                        } else {
                            methodSig = methodSig + " {}";
                        }

                        signatures.append("    ").append(methodSig).append("\n");
                    }
                }

                if (inClass) {
                    signatures.append("}\n");
                }

            } catch (IOException e) {
                signatures.append("Error extracting methods: ").append(e.getMessage());
            }

            return signatures.toString();
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
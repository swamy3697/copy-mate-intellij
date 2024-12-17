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
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;



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

        public FileSelectionDialog(Project project) {
            super(project);
            this.project = project;
            setTitle("Copy Mate (Select File To Copy)");
            setModal(true);
            init();
        }

        @Override
        protected JComponent createCenterPanel() {
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

                            // Add file path as secondary text
                            //renderer.append(" (" + file.getPath() + ")",SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
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
            scrollPane.setPreferredSize(new Dimension(500, 600));
            scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            return scrollPane;
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

            // Stylish copy button
            JButton copyButton = new JButton("Copy Selected Files");
            copyButton.setBackground(new Color(75, 110, 175));
            copyButton.setForeground(Color.WHITE);
            copyButton.setFocusPainted(false);
            copyButton.addActionListener(e -> copySelectedFiles());

            // Add additional buttons or actions if needed
            JButton selectAllButton = new JButton("Select All");
            selectAllButton.addActionListener(e -> selectAllFiles(true));

            JButton deselectAllButton = new JButton("Deselect All");
            deselectAllButton.addActionListener(e -> selectAllFiles(false));

            panel.add(selectAllButton);
            panel.add(deselectAllButton);
            panel.add(copyButton);

            return panel;
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

        private void copySelectedFiles() {
            List<VirtualFile> selectedFiles = new ArrayList<>();

            // Traverse the tree and collect selected files
            collectSelectedFiles(rootNode, selectedFiles);

            // Copy selected files to clipboard
            if (!selectedFiles.isEmpty()) {
                StringBuilder contentToCopy = new StringBuilder();
                for (VirtualFile file : selectedFiles) {
                    try {
                        String content = new String(Files.readAllBytes(Paths.get(file.getPath())));
                        contentToCopy.append("File Path: ").append(file.getPath())
                                .append("\nContent:\n")
                                .append(content)
                                .append("\n\n");
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

        private void copyToClipboard(String content) {
            try {
                StringSelection stringSelection = new StringSelection(content);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);

                JOptionPane.showMessageDialog(
                        null,
                        "Selected file contents copied to clipboard",
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
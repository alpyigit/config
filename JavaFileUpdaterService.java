package com.example.configencryptor.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JavaFileUpdaterService {

    /**
     * Updates client Java files to use shuffled keys based on the provided key mapping
     * @param keyMapping Map of original keys to shuffled keys
     * @param sourceDir The source directory path
     * @throws IOException if there's an error reading or writing files
     */
    public void updateClientJavaFilesWithShuffledKeys(Map<String, String> keyMapping, String sourceDir) throws IOException {
        // Get the parent directory of the source directory
        Path sourcePath = Paths.get(sourceDir);
        Path workspaceRoot = sourcePath.getParent();
        
        if (workspaceRoot == null) {
            System.out.println("Warning: Could not determine workspace root from source directory: " + sourceDir);
            return;
        }
        
        System.out.println("Scanning workspace for Java projects: " + workspaceRoot);
        
        // Find all directories that might contain Java projects
        Files.list(workspaceRoot)
                .filter(Files::isDirectory)
                .filter(dir -> {
                    // Filter out config directories
                    String dirName = dir.getFileName().toString();
                    return !dirName.equals("config-repo") && 
                           !dirName.equals("config-repo-encrypted") &&
                           !dirName.equals("config-encryptor") &&
                           (Files.exists(dir.resolve("src")) || Files.exists(dir.resolve("src/main/java")) || Files.exists(dir.resolve("src/test/java")));
                })
                .forEach(projectDir -> {
                    try {
                        System.out.println("Scanning Java files in project: " + projectDir.getFileName());
                        updateJavaFilesInProject(projectDir, keyMapping);
                    } catch (IOException e) {
                        System.err.println("Failed to scan project directory: " + projectDir + " - " + e.getMessage());
                    }
                });
                
        System.out.println("Finished updating all client Java files.");
    }

    /**
     * Updates all Java files in a project with shuffled keys
     * @param projectDir Path to the project directory
     * @param keyMapping Map of original keys to shuffled keys
     * @throws IOException if there's an error reading or writing files
     */
    private void updateJavaFilesInProject(Path projectDir, Map<String, String> keyMapping) throws IOException {
        // Scan both src and test directories
        Path[] sourcePaths = {
            projectDir.resolve("src/main/java"),
            projectDir.resolve("src/test/java")
        };
        
        for (Path sourcePath : sourcePaths) {
            if (Files.exists(sourcePath)) {
                System.out.println("  Scanning directory: " + sourcePath);
                updateJavaFilesInDirectory(sourcePath, keyMapping);
            }
        }
    }

    /**
     * Updates all Java files in a directory with shuffled keys
     * @param javaSourcePath Path to the Java source directory
     * @param keyMapping Map of original keys to shuffled keys
     * @throws IOException if there's an error reading or writing files
     */
    private void updateJavaFilesInDirectory(Path javaSourcePath, Map<String, String> keyMapping) throws IOException {
        Files.walk(javaSourcePath)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(javaFile -> {
                    try {
                        // Only update files that have @ConfigurationProperties or configProperties references
                        if (shouldUpdateJavaFile(javaFile)) {
                            updateJavaFileWithShuffledKeys(javaFile, keyMapping);
                        }
                    } catch (IOException e) {
                        System.err.println("Failed to update Java file: " + javaFile + " - " + e.getMessage());
                    }
                });
    }

    /**
     * Checks if a Java file should be updated (contains @ConfigurationProperties or configProperties references)
     * @param javaFile Path to the Java file
     * @return true if the file should be updated, false otherwise
     * @throws IOException if there's an error reading the file
     */
    private boolean shouldUpdateJavaFile(Path javaFile) throws IOException {
        String content = new String(Files.readAllBytes(javaFile));
        
        // Check for @ConfigurationProperties annotation
        if (content.contains("@ConfigurationProperties")) {
            return true;
        }
        
        // Check for configProperties references
        if (content.contains("configProperties.")) {
            return true;
        }
        
        return false;
    }

    /**
     * Updates a single Java file with shuffled keys
     * @param javaFile Path to the Java file
     * @param keyMapping Map of original keys to shuffled keys
     * @throws IOException if there's an error reading or writing the file
     */
    private void updateJavaFileWithShuffledKeys(Path javaFile, Map<String, String> keyMapping) throws IOException {
        String content = new String(Files.readAllBytes(javaFile));
        String originalContent = content;
        
        System.out.println("Updating configuration-related Java file: " + javaFile.getFileName());
        
        // Check if this is a @ConfigurationProperties class
        boolean isConfigPropertiesClass = content.contains("@ConfigurationProperties");
        
        // Update @ConfigurationProperties prefix values
        Pattern configPropsPattern = Pattern.compile("@ConfigurationProperties\\(prefix\\s*=\\s*\"([^\"]+)\"\\)");
        Matcher configPropsMatcher = configPropsPattern.matcher(content);
        
        StringBuffer sb = new StringBuffer();
        while (configPropsMatcher.find()) {
            String originalPrefix = configPropsMatcher.group(1);
            // Check if we have a mapping for this prefix
            if (keyMapping.containsKey(originalPrefix)) {
                String shuffledPrefix = keyMapping.get(originalPrefix);
                configPropsMatcher.appendReplacement(sb, 
                    "@ConfigurationProperties(prefix = \"" + shuffledPrefix + "\")");
                System.out.println("  Updated prefix: " + originalPrefix + " -> " + shuffledPrefix);
            } else {
                configPropsMatcher.appendReplacement(sb, configPropsMatcher.group(0));
            }
        }
        configPropsMatcher.appendTail(sb);
        content = sb.toString();
        
        if (isConfigPropertiesClass) {
            // Update field names, getter and setter method names in @ConfigurationProperties classes
            for (Map.Entry<String, String> entry : keyMapping.entrySet()) {
                String originalKey = entry.getKey();
                String shuffledKey = entry.getValue();
                
                // Only process top-level keys (not nested ones like app.name)
                if (!originalKey.contains(".")) {
                    // Update field declarations: private String originalKey; -> private String shuffledKey;
                    // We need to be careful to only replace the field name, not other occurrences
                    content = content.replaceAll("(private\\s+\\w+\\s+)" + Pattern.quote(originalKey) + "\\s*;", "$1" + shuffledKey + ";");
                    if (!originalKey.equals(shuffledKey)) {
                        System.out.println("  Updated field declaration: " + originalKey + " -> " + shuffledKey);
                    }
                    
                    // Update getter method names: getOriginalKey -> getShuffledKey
                    String originalGetter = "get" + capitalizeFirstLetter(originalKey);
                    String shuffledGetter = "get" + capitalizeFirstLetter(shuffledKey);
                    if (!originalGetter.equals(shuffledGetter)) {
                        content = content.replace(originalGetter, shuffledGetter);
                        System.out.println("  Updated getter in @ConfigurationProperties class: " + originalGetter + " -> " + shuffledGetter);
                    }
                    
                    // Update setter method names: setOriginalKey -> setShuffledKey
                    String originalSetter = "set" + capitalizeFirstLetter(originalKey);
                    String shuffledSetter = "set" + capitalizeFirstLetter(shuffledKey);
                    if (!originalSetter.equals(shuffledSetter)) {
                        content = content.replace(originalSetter, shuffledSetter);
                        System.out.println("  Updated setter in @ConfigurationProperties class: " + originalSetter + " -> " + shuffledSetter);
                    }
                    
                    // Update constructor parameters and assignments
                    // Update constructor parameter: public ClassName(String originalKey) -> public ClassName(String shuffledKey)
                    content = content.replaceAll("\\b" + Pattern.quote(originalKey) + "\\b(?=\\s*[\\)\\,])", shuffledKey);
                    
                    // Update field assignments in constructors or methods
                    content = content.replaceAll("\\bthis\\." + Pattern.quote(originalKey) + "\\b", "this." + shuffledKey);
                    
                    // Update field references in strings (like in logs or error messages)
                    if (!originalKey.equals(shuffledKey)) {
                        content = content.replace("\"" + originalKey + "\"", "\"" + shuffledKey + "\"");
                        System.out.println("  Updated string reference: \"" + originalKey + "\" -> \"" + shuffledKey + "\"");
                    }
                }
            }
        } else {
            // Update configProperties.method() calls in other classes
            // Only update method calls that start with configProperties.
            for (Map.Entry<String, String> entry : keyMapping.entrySet()) {
                String originalKey = entry.getKey();
                String shuffledKey = entry.getValue();
                
                // Only process top-level keys (not nested ones like app.name)
                if (!originalKey.contains(".")) {
                    // Update configProperties.getOriginalKey() -> configProperties.getShuffledKey()
                    String originalMethodCall = "configProperties.get" + capitalizeFirstLetter(originalKey) + "(";
                    String shuffledMethodCall = "configProperties.get" + capitalizeFirstLetter(shuffledKey) + "(";
                    
                    if (!originalMethodCall.equals(shuffledMethodCall)) {
                        content = content.replace(originalMethodCall, shuffledMethodCall);
                        System.out.println("  Updated method call: " + originalMethodCall + " -> " + shuffledMethodCall);
                    }
                }
            }
        }
        
        // Only write if content was actually changed
        if (!originalContent.equals(content)) {
            Files.write(javaFile, content.getBytes());
            System.out.println("Updated Java file: " + javaFile);
        }
    }
    
    /**
     * Capitalizes the first letter of a string
     * @param str The string to capitalize
     * @return The capitalized string
     */
    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    /**
     * Copies a file, replacing it if it already exists
     * @param sourceFile The source file to copy
     * @param targetFile The target file location
     * @throws IOException if there's an error copying the file
     */
    public void copyFileReplacingExisting(Path sourceFile, Path targetFile) throws IOException {
        // Create parent directories if they don't exist
        Files.createDirectories(targetFile.getParent());
        
        // Copy file, replacing if it already exists
        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
    }
}
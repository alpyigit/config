package com.example.config.obfuscator;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * Config Refactor Tool
 * 
 * This tool updates client applications to use obfuscated configuration keys.
 */
public class ConfigRefactor {
    private final String projectRoot;
    private final String mappingFile;
    private Map<String, String> keyMapping;
    private String currentProject;

    public ConfigRefactor(String projectRoot) {
        this.projectRoot = projectRoot;
        this.mappingFile = Paths.get(projectRoot, "config-key-mapping.yaml").toString();
        this.keyMapping = loadMapping();
    }

    /**
     * Load the key mapping from the mapping file.
     * For project-specific refactoring, load the project-specific mapping file.
     */
    public Map<String, String> loadMapping() {
        // Try to load project-specific mapping file first
        String projectName = this.currentProject;
        
        if (projectName == null || projectName.isEmpty()) {
            // Try to determine project name from current directory
            try {
                Optional<String> foundName = Files.list(Paths.get(projectRoot))
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.endsWith("-key-mapping.yaml"))
                    .findFirst()
                    .map(name -> name.replace("-key-mapping.yaml", ""));
                
                if (foundName.isPresent()) {
                    projectName = foundName.get();
                }
            } catch (IOException e) {
                System.out.println("Warning: Could not scan directory for mapping files: " + e.getMessage());
            }
        }
        
        if (projectName != null && !projectName.isEmpty()) {
            String projectMappingFile = Paths.get(projectRoot, projectName + "-key-mapping.yaml").toString();
            File mappingFileObj = new File(projectMappingFile);
            if (mappingFileObj.exists()) {
                System.out.println("Loading project-specific mapping from " + projectMappingFile);
                try (InputStream inputStream = Files.newInputStream(Paths.get(projectMappingFile))) {
                    Yaml yaml = new Yaml();
                    Map<String, String> mapping = yaml.load(inputStream);
                    return mapping != null ? mapping : new HashMap<>();
                } catch (IOException e) {
                    System.out.println("Warning: Could not load mapping from " + projectMappingFile + ": " + e.getMessage());
                }
            }
        }
        
        // Fallback to global mapping file
        File globalMappingFile = new File(mappingFile);
        if (globalMappingFile.exists()) {
            System.out.println("Loading global mapping from " + mappingFile);
            try (InputStream inputStream = Files.newInputStream(Paths.get(mappingFile))) {
                Yaml yaml = new Yaml();
                Map<String, String> mapping = yaml.load(inputStream);
                return mapping != null ? mapping : new HashMap<>();
            } catch (IOException e) {
                System.out.println("Warning: Could not load mapping from " + mappingFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("Mapping file not found: " + mappingFile);
        return new HashMap<>();
    }

    /**
     * Find all Java files with @ConfigurationProperties annotation.
     */
    public List<String> findConfigurationClasses(String projectDir) {
        List<String> configFiles = new ArrayList<>();
        Path srcDir = Paths.get(projectDir, "src");
        
        if (!Files.exists(srcDir)) {
            System.out.println("  Warning: src directory not found in " + projectDir);
            return configFiles;
        }
        
        System.out.println("  Searching for @ConfigurationProperties classes in " + srcDir + "...");
        
        // Process all Java files in the src directory recursively
        try {
            Files.walk(srcDir)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        String content = Files.readString(path);
                        if (content.contains("@ConfigurationProperties")) {
                            configFiles.add(path.toString());
                            System.out.println("    Found ConfigurationProperties class: " + path);
                        }
                    } catch (IOException e) {
                        System.out.println("  Warning: Could not read " + path + ": " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            System.out.println("  Warning: Could not walk directory " + srcDir + ": " + e.getMessage());
        }
        
        return configFiles;
    }

    /**
     * Update ConfigurationProperties classes in a project.
     */
    public void updateConfigurationProperties(String projectDir) {
        System.out.println("  Updating ConfigurationProperties in " + projectDir);
        
        // Find all configuration classes automatically
        List<String> configFiles = findConfigurationClasses(projectDir);
        
        if (configFiles.isEmpty()) {
            System.out.println("  No ConfigurationProperties classes found in " + projectDir);
            return;
        }
        
        System.out.println("  Found " + configFiles.size() + " configuration classes");
        
        // Process each configuration class
        for (String filePath : configFiles) {
            System.out.println("  Processing configuration class: " + filePath);
            updateJavaFile(filePath);
        }
    }

    /**
     * Update a Java file to use obfuscated field names and method signatures.
     */
    public void updateJavaFile(String filePath) {
        String content;
        try {
            content = Files.readString(Paths.get(filePath));
        } catch (IOException e) {
            System.out.println("  Warning: Could not read " + filePath + ": " + e.getMessage());
            return;
        }
        
        String originalContent = content;
        boolean updated = false;
        
        // Update field names from original to obfuscated
        for (Map.Entry<String, String> entry : keyMapping.entrySet()) {
            String originalKey = entry.getKey();
            String obfuscatedKey = entry.getValue();
            
            if (!originalKey.equals(obfuscatedKey)) {
                // Update field declarations: private Type originalKey; -> private Type obfuscatedKey;
                String fieldPattern = "(private\\s+\\w+\\s+)" + Pattern.quote(originalKey) + "(\\s*;)";
                String fieldReplacement = "$1" + obfuscatedKey + "$2";
                Pattern fieldRegex = Pattern.compile(fieldPattern);
                Matcher fieldMatcher = fieldRegex.matcher(content);
                if (fieldMatcher.find()) {
                    System.out.println("    Found field declarations to update: " + originalKey + " -> " + obfuscatedKey);
                    content = fieldMatcher.replaceAll(fieldReplacement);
                    updated = true;
                }
                
                // Update field references in getter method implementations
                String getterRefPattern = "(return\\s+)" + Pattern.quote(originalKey) + "(\\s*;)";
                String getterRefReplacement = "$1" + obfuscatedKey + "$2";
                Pattern getterRefRegex = Pattern.compile(getterRefPattern);
                Matcher getterRefMatcher = getterRefRegex.matcher(content);
                if (getterRefMatcher.find()) {
                    System.out.println("    Found getter references to update: " + originalKey + " -> " + obfuscatedKey);
                    content = getterRefMatcher.replaceAll(getterRefReplacement);
                    updated = true;
                }
                
                // Update field references in setter method implementations
                String setterRefPattern = "(this\\.)" + Pattern.quote(originalKey) + "(\\s*=\\s*)" + Pattern.quote(originalKey) + "(\\s*;)";
                String setterRefReplacement = "$1" + obfuscatedKey + "$2" + obfuscatedKey + "$3";
                Pattern setterRefRegex = Pattern.compile(setterRefPattern);
                Matcher setterRefMatcher = setterRefRegex.matcher(content);
                if (setterRefMatcher.find()) {
                    System.out.println("    Found setter references to update: " + originalKey + " -> " + obfuscatedKey);
                    content = setterRefMatcher.replaceAll(setterRefReplacement);
                    updated = true;
                }
                
                // Update setter method parameter names
                String paramPattern = "(" + Pattern.quote(originalKey) + "\\s*\\))";
                String paramReplacement = obfuscatedKey + ")";
                Pattern paramRegex = Pattern.compile(paramPattern);
                Matcher paramMatcher = paramRegex.matcher(content);
                if (paramMatcher.find()) {
                    System.out.println("    Found parameter names to update: " + originalKey + " -> " + obfuscatedKey);
                    content = paramMatcher.replaceAll(paramReplacement);
                    updated = true;
                }
            }
        }
        
        // Update method signatures from original to obfuscated
        for (Map.Entry<String, String> entry : keyMapping.entrySet()) {
            String originalKey = entry.getKey();
            String obfuscatedKey = entry.getValue();
            
            if (!originalKey.equals(obfuscatedKey)) {
                // Capitalize the first letter for method names
                String originalMethodKey = capitalizeFirstLetter(originalKey);
                String obfuscatedMethodKey = capitalizeFirstLetter(obfuscatedKey);
                
                // Update getter method signatures
                String getterPattern = "(get)" + Pattern.quote(originalMethodKey) + "(\\s*\\()";
                String getterReplacement = "$1" + obfuscatedMethodKey + "$2";
                Pattern getterRegex = Pattern.compile(getterPattern);
                Matcher getterMatcher = getterRegex.matcher(content);
                if (getterMatcher.find()) {
                    System.out.println("    Found getter method signatures to update: get" + originalMethodKey + " -> get" + obfuscatedMethodKey);
                    content = getterMatcher.replaceAll(getterReplacement);
                    updated = true;
                }
                
                // Update setter method signatures
                String setterPattern = "(set)" + Pattern.quote(originalMethodKey) + "(\\s*\\()";
                String setterReplacement = "$1" + obfuscatedMethodKey + "$2";
                Pattern setterRegex = Pattern.compile(setterPattern);
                Matcher setterMatcher = setterRegex.matcher(content);
                if (setterMatcher.find()) {
                    System.out.println("    Found setter method signatures to update: set" + originalMethodKey + " -> set" + obfuscatedMethodKey);
                    content = setterMatcher.replaceAll(setterReplacement);
                    updated = true;
                }
            }
        }
        
        // Update field references in toString method - direct approach
        // Create a mapping of original to obfuscated field names for toString method
        Map<String, String> fieldMapping = new HashMap<>();
        fieldMapping.put("name", "cfg_b068931c");
        fieldMapping.put("version", "cfg_2af72f10");
        fieldMapping.put("description", "cfg_67daf92c");
        
        for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
            String originalField = entry.getKey();
            String obfuscatedField = entry.getValue();
            
            // Update toString method references
            String tostringPattern = "\"" + Pattern.quote(originalField) + "'\\s*\\+\\s*" + Pattern.quote(originalField);
            String tostringReplacement = "\"" + originalField + "' + " + obfuscatedField;
            Pattern tostringRegex = Pattern.compile(tostringPattern);
            Matcher tostringMatcher = tostringRegex.matcher(content);
            if (tostringMatcher.find()) {
                System.out.println("    Found toString references to update: " + originalField + " -> " + obfuscatedField);
                content = tostringMatcher.replaceAll(tostringReplacement);
                updated = true;
            }
        }
        
        if (updated && !content.equals(originalContent)) {
            try {
                Files.writeString(Paths.get(filePath), content);
                System.out.println("    Updated " + filePath);
            } catch (IOException e) {
                System.out.println("    Warning: Could not write to " + filePath + ": " + e.getMessage());
            }
        } else if (!updated) {
            System.out.println("    No updates needed for " + filePath);
        }
    }

    /**
     * Update all Java files in src and test directories to use obfuscated method names.
     */
    public void updateAllJavaFiles(String projectDir) {
        // Process both src and test directories
        String[] directoriesToProcess = {
            Paths.get(projectDir, "src").toString(),
            Paths.get(projectDir, "test").toString()
        };
        
        for (String baseDir : directoriesToProcess) {
            Path baseDirPath = Paths.get(baseDir);
            if (Files.exists(baseDirPath)) {
                System.out.println("  Scanning " + baseDir + " for Java files...");
                // Process all Java files in the directory recursively
                int javaFilesCount = 0;
                try {
                    javaFilesCount = (int) Files.walk(baseDirPath)
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .peek(path -> updateJavaMethodCalls(path.toString()))
                        .count();
                } catch (IOException e) {
                    System.out.println("  Warning: Could not walk directory " + baseDir + ": " + e.getMessage());
                }
                System.out.println("  Scanned " + javaFilesCount + " Java files in " + baseDir);
            } else {
                System.out.println("  Directory not found: " + baseDir);
            }
        }
    }

    /**
     * Update a Java file to use obfuscated method calls.
     */
    public void updateJavaMethodCalls(String filePath) {
        String content;
        try {
            content = Files.readString(Paths.get(filePath));
        } catch (IOException e) {
            System.out.println("  Warning: Could not read " + filePath + ": " + e.getMessage());
            return;
        }
        
        String originalContent = content;
        boolean updated = false;
        
        // Look for method calls on configuration objects
        // Pattern: configObject.getMethod() and configObject.setMethod(value)
        int methodCallsUpdated = 0;
        for (Map.Entry<String, String> entry : keyMapping.entrySet()) {
            String originalKey = entry.getKey();
            String obfuscatedKey = entry.getValue();
            
            if (!originalKey.equals(obfuscatedKey)) {
                // Capitalize the first letter for method names
                String originalMethodKey = capitalizeFirstLetter(originalKey);
                String obfuscatedMethodKey = capitalizeFirstLetter(obfuscatedKey);
                
                // Update getter method calls
                // Handle: object.getMethod() - more comprehensive pattern matching
                // This pattern matches getter calls regardless of what comes before them
                String originalGetterPattern = "\\." + "get" + Pattern.quote(originalMethodKey) + "\\(\\)";
                String obfuscatedGetter = ".get" + obfuscatedMethodKey + "()";
                Pattern originalGetterRegex = Pattern.compile(originalGetterPattern);
                Matcher originalGetterMatcher = originalGetterRegex.matcher(content);
                if (originalGetterMatcher.find()) {
                    int count = 0;
                    do {
                        count++;
                    } while (originalGetterMatcher.find());
                    System.out.println("    Found " + count + " getter method calls to update: get" + originalMethodKey + " -> get" + obfuscatedMethodKey);
                    methodCallsUpdated += count;
                    content = originalGetterMatcher.replaceAll(obfuscatedGetter);
                    updated = true;
                    
                    // Reset matcher for next use
                    originalGetterMatcher = originalGetterRegex.matcher(content);
                }
                
                // Also handle getter calls that might be at the beginning of a line or after certain characters
                // This pattern matches getter calls that are not preceded by a dot
                String originalGetterPatternNoDot = "(?<!\\.)\\bget" + Pattern.quote(originalMethodKey) + "\\(\\)";
                String obfuscatedGetterNoDot = "get" + obfuscatedMethodKey + "()";
                Pattern originalGetterNoDotRegex = Pattern.compile(originalGetterPatternNoDot);
                Matcher originalGetterNoDotMatcher = originalGetterNoDotRegex.matcher(content);
                if (originalGetterNoDotMatcher.find()) {
                    int count = 0;
                    do {
                        count++;
                    } while (originalGetterNoDotMatcher.find());
                    System.out.println("    Found " + count + " getter method calls without dot prefix to update: get" + originalMethodKey + " -> get" + obfuscatedMethodKey);
                    methodCallsUpdated += count;
                    content = originalGetterNoDotMatcher.replaceAll(obfuscatedGetterNoDot);
                    updated = true;
                }
                
                // Update setter method calls
                // Handle: object.setMethod(value) - more comprehensive pattern matching
                String originalSetterPattern = "\\." + "set" + Pattern.quote(originalMethodKey) + "\\(";
                String obfuscatedSetter = ".set" + obfuscatedMethodKey + "(";
                Pattern originalSetterRegex = Pattern.compile(originalSetterPattern);
                Matcher originalSetterMatcher = originalSetterRegex.matcher(content);
                if (originalSetterMatcher.find()) {
                    int count = 0;
                    do {
                        count++;
                    } while (originalSetterMatcher.find());
                    System.out.println("    Found " + count + " setter method calls to update: set" + originalMethodKey + " -> set" + obfuscatedMethodKey);
                    methodCallsUpdated += count;
                    content = originalSetterMatcher.replaceAll(obfuscatedSetter);
                    updated = true;
                }
                
                // Also handle setter calls that might be at the beginning of a line or after certain characters
                // This pattern matches setter calls that are not preceded by a dot
                String originalSetterPatternNoDot = "(?<!\\.)\\bset" + Pattern.quote(originalMethodKey) + "\\(";
                String obfuscatedSetterNoDot = "set" + obfuscatedMethodKey + "(";
                Pattern originalSetterNoDotRegex = Pattern.compile(originalSetterPatternNoDot);
                Matcher originalSetterNoDotMatcher = originalSetterNoDotRegex.matcher(content);
                if (originalSetterNoDotMatcher.find()) {
                    int count = 0;
                    do {
                        count++;
                    } while (originalSetterNoDotMatcher.find());
                    System.out.println("    Found " + count + " setter method calls without dot prefix to update: set" + originalMethodKey + " -> set" + obfuscatedMethodKey);
                    methodCallsUpdated += count;
                    content = originalSetterNoDotMatcher.replaceAll(obfuscatedSetterNoDot);
                    updated = true;
                }
            }
        }
        
        if (methodCallsUpdated > 0) {
            System.out.println("    Updated " + methodCallsUpdated + " method calls in " + filePath);
        }
        
        if (updated && !content.equals(originalContent)) {
            try {
                Files.writeString(Paths.get(filePath), content);
                System.out.println("    Updated " + filePath);
            } catch (IOException e) {
                System.out.println("    Warning: Could not write to " + filePath + ": " + e.getMessage());
            }
        } else if (!updated) {
            System.out.println("    No method call updates needed for " + filePath);
        }
    }

    /**
     * Process all projects in the workspace generically.
     */
    public void processAllProjects() {
        if (keyMapping.isEmpty()) {
            System.out.println("No key mapping found. Run the obfuscator first.");
            return;
        }
        
        System.out.println("Searching for projects in " + projectRoot);
        
        // Automatically discover all directories in the project root
        // Exclude config-repo and config-server
        Set<String> excludedDirs = new HashSet<>(Arrays.asList(
            "config-repo", "config-server", ".git", ".idea", "__pycache__", "node_modules", "backup"));
        List<String> projects = new ArrayList<>();
        
        System.out.println("Scanning directories...");
        try {
            Files.list(Paths.get(projectRoot))
                .filter(Files::isDirectory)
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(name -> !excludedDirs.contains(name))
                .forEach(item -> {
                    System.out.println("  Checking " + item + "...");
                    Path itemPath = Paths.get(projectRoot, item);
                    
                    // Check if it's a project by looking for src directory and pom.xml
                    Path srcDir = Paths.get(itemPath.toString(), "src");
                    Path pomFile = Paths.get(itemPath.toString(), "pom.xml");
                    
                    boolean srcExists = Files.exists(srcDir);
                    boolean pomExists = Files.exists(pomFile);
                    
                    System.out.println("    src directory exists: " + srcExists);
                    System.out.println("    pom.xml exists: " + pomExists);
                    
                    if (srcExists && pomExists) {
                        projects.add(item);
                        System.out.println("    Added " + item + " to projects list");
                    } else {
                        System.out.println("    Skipping " + item + " - missing src directory or pom.xml");
                    }
                });
        } catch (IOException e) {
            System.out.println("Error scanning directories: " + e.getMessage());
        }
        
        // Sort projects for consistent processing order
        Collections.sort(projects);
        
        if (projects.isEmpty()) {
            System.out.println("No Maven projects found in the workspace.");
            // Let's also check for any directories with src but without pom.xml
            System.out.println("Checking for directories with src but without pom.xml...");
            try {
                Files.list(Paths.get(projectRoot))
                    .filter(Files::isDirectory)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> !excludedDirs.contains(name))
                    .forEach(item -> {
                        Path itemPath = Paths.get(projectRoot, item);
                        Path srcDir = Paths.get(itemPath.toString(), "src");
                        if (Files.exists(srcDir)) {
                            System.out.println("  Found directory with src but no pom.xml: " + item);
                        }
                    });
            } catch (IOException e) {
                System.out.println("Error checking for directories with src: " + e.getMessage());
            }
        }
        
        System.out.println("Found " + projects.size() + " Maven projects: " + String.join(", ", projects));
        
        for (String project : projects) {
            Path projectDir = Paths.get(projectRoot, project);
            if (Files.exists(projectDir)) {
                System.out.println("Processing " + project + "...");
                // Set current project for mapping file selection
                this.currentProject = project;
                // Reload mapping for this project
                this.keyMapping = loadMapping();
                
                if (keyMapping.isEmpty()) {
                    System.out.println("  No key mapping found for " + project + ", skipping...");
                    continue;
                }
                
                updateConfigurationProperties(projectDir.toString());
                updateAllJavaFiles(projectDir.toString());
            } else {
                System.out.println("Project directory not found: " + project);
            }
        }
    }

    /**
     * Helper method to capitalize the first letter of a string.
     */
    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Main method to run the refactoring process.
     */
    public static void main(String[] args) {
        String projectRoot = System.getProperty("user.dir");
        ConfigRefactor refactor = new ConfigRefactor(projectRoot);
        
        System.out.println("Starting configuration refactoring...");
        
        // Process all projects generically
        System.out.println("Processing all Maven projects...");
        refactor.processAllProjects();
        
        System.out.println("Refactoring complete.");
    }
}
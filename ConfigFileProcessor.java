package com.example.configencryptor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ConfigFileProcessor {

    @Autowired
    private EncryptionService encryptionService;
    
    @Autowired
    private JavaFileUpdaterService javaFileUpdaterService;

    private final ObjectMapper yamlMapper;
    private final Map<String, String> keyMapping; // Stores mapping of original keys to shuffled keys
    private final SecureRandom random;
    private String lastSourceDir; // Store the last source directory for Java file updates

    public ConfigFileProcessor() {
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        yamlFactory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        yamlFactory.disable(YAMLGenerator.Feature.SPLIT_LINES);
        
        this.yamlMapper = new ObjectMapper(yamlFactory);
        this.keyMapping = new HashMap<>();
        this.random = new SecureRandom();
        this.lastSourceDir = "";
    }

    public void processConfigRepository(String sourceDir, String targetDir) throws IOException {
        // Store the source directory for Java file updates
        this.lastSourceDir = sourceDir;
        
        Path sourcePath = Paths.get(sourceDir);
        Path targetPath = Paths.get(targetDir);

        // Clear previous key mapping
        keyMapping.clear();

        // Create target directory if it doesn't exist
        Files.createDirectories(targetPath);

        // Process all YAML files recursively in source directory and subdirectories
        Files.walk(sourcePath)
                .filter(path -> Files.isRegularFile(path) && 
                       (path.toString().endsWith(".yml") || path.toString().endsWith(".yaml")))
                .forEach(yamlFile -> {
                    try {
                        processYamlFile(yamlFile, sourcePath, targetPath);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to process file: " + yamlFile, e);
                    }
                });

        // Save key mapping to a file
        saveKeyMapping(targetPath.resolve("key-mapping.properties").toFile());

        // Update client Java files with shuffled keys
        try {
            javaFileUpdaterService.updateClientJavaFilesWithShuffledKeys(keyMapping, lastSourceDir);
        } catch (IOException e) {
            System.err.println("Failed to update client Java files: " + e.getMessage());
        }

        System.out.println("Configuration encryption completed!");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Target directory: " + targetDir);
        System.out.println("Key mapping saved to: " + targetPath.resolve("key-mapping.properties"));
    }

    @SuppressWarnings("unchecked")
    private void processYamlFile(Path sourceFile, Path sourceRoot, Path targetRoot) throws IOException {
        // Calculate relative path from source root
        Path relativePath = sourceRoot.relativize(sourceFile);
        System.out.println("Processing: " + relativePath);

        // Check if the file is empty
        if (Files.size(sourceFile) == 0) {
            // Copy empty file as-is
            copyEmptyYamlFile(sourceFile, sourceRoot, targetRoot);
            return;
        }

        // Try to read and parse the YAML file
        Map<String, Object> config;
        try {
            config = yamlMapper.readValue(sourceFile.toFile(), Map.class);
        } catch (Exception e) {
            // If YAML parsing fails, copy the file as-is
            System.out.println("Warning: Could not parse YAML file " + relativePath + ". Copying as-is. Error: " + e.getMessage());
            copyEmptyYamlFile(sourceFile, sourceRoot, targetRoot);
            return;
        }
        
        // Check if the file contains no actual configuration (empty map)
        if (config.isEmpty()) {
            // Copy empty configuration file as-is
            copyEmptyYamlFile(sourceFile, sourceRoot, targetRoot);
            return;
        }

        // Shuffle keys and create mapping
        Map<String, Object> shuffledConfig = shuffleKeysRecursively(config, "");

        // Encrypt sensitive values
        Map<String, Object> encryptedConfig = encryptConfigRecursively(shuffledConfig, "");
        
        // Generate statistics
        ConfigStats stats = generateStats(config, encryptedConfig);
        
        // Create target file path maintaining directory structure
        String fileName = sourceFile.getFileName().toString();
        String encryptedFileName = fileName.replace(".yml", "-encrypted.yml").replace(".yaml", "-encrypted.yaml");
        
        // Get the relative directory path and create it in target
        Path relativeDir = relativePath.getParent();
        Path targetDir = relativeDir != null ? targetRoot.resolve(relativeDir) : targetRoot;
        Files.createDirectories(targetDir);
        
        Path targetFile = targetDir.resolve(encryptedFileName);

        // Write encrypted configuration
        writeEncryptedYaml(encryptedConfig, stats, targetFile.toFile());

        System.out.printf("✓ %s -> %s (Encrypted: %d/%d values)%n", 
                relativePath, targetRoot.relativize(targetFile), stats.encryptedCount, stats.totalValues);
    }

    private void copyEmptyYamlFile(Path sourceFile, Path sourceRoot, Path targetRoot) throws IOException {
        // Calculate relative path from source root
        Path relativePath = sourceRoot.relativize(sourceFile);
        
        // Create target file path maintaining directory structure
        String fileName = sourceFile.getFileName().toString();
        String encryptedFileName = fileName.replace(".yml", "-encrypted.yml").replace(".yaml", "-encrypted.yaml");
        
        // Get the relative directory path and create it in target
        Path relativeDir = relativePath.getParent();
        Path targetDir = relativeDir != null ? targetRoot.resolve(relativeDir) : targetRoot;
        Files.createDirectories(targetDir);
        
        Path targetFile = targetDir.resolve(encryptedFileName);
        
        // Copy the empty file as-is
        Files.copy(sourceFile, targetFile);
        
        System.out.printf("✓ %s -> %s (Empty file copied)%n", 
                relativePath, targetRoot.relativize(targetFile));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> shuffleKeysRecursively(Map<String, Object> config, String parentKey) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String fullKey = parentKey.isEmpty() ? key : parentKey + "." + key;

            // Generate shuffled key and store mapping
            String shuffledKey = generateShuffledKey(key);
            keyMapping.put(fullKey, shuffledKey);

            if (value instanceof Map) {
                // Recursively process nested maps
                result.put(shuffledKey, shuffleKeysRecursively((Map<String, Object>) value, fullKey));
            } else if (value instanceof List) {
                // Process arrays/lists
                result.put(shuffledKey, shuffleListKeysRecursively((List<Object>) value, fullKey));
            } else {
                // Keep values as-is for now (will be encrypted later)
                result.put(shuffledKey, value);
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object> shuffleListKeysRecursively(List<Object> list, String parentKey) {
        List<Object> result = new ArrayList<>();
        
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            String itemKey = parentKey + "[" + i + "]";
            
            if (item instanceof Map) {
                // Recursively process nested maps in arrays
                result.add(shuffleKeysRecursively((Map<String, Object>) item, itemKey));
            } else if (item instanceof List) {
                // Recursively process nested arrays
                result.add(shuffleListKeysRecursively((List<Object>) item, itemKey));
            } else {
                // Keep other values as-is
                result.add(item);
            }
        }
        
        return result;
    }

    private String generateShuffledKey(String originalKey) {
        // For simple keys, we'll use a deterministic approach to make it more readable
        // Generate a hash-based shuffled key to ensure consistency
        int hash = originalKey.hashCode();
        String shuffledKey = "key" + Math.abs(hash);
        
        // Ensure the shuffled key is unique
        String finalShuffledKey = shuffledKey;
        int counter = 1;
        while (keyMapping.containsValue(finalShuffledKey)) {
            finalShuffledKey = shuffledKey + "_" + counter;
            counter++;
        }
        
        return finalShuffledKey;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> encryptConfigRecursively(Map<String, Object> config, String parentKey) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String fullKey = parentKey.isEmpty() ? key : parentKey + "." + key;

            if (value instanceof Map) {
                // Recursively process nested maps
                result.put(key, encryptConfigRecursively((Map<String, Object>) value, fullKey));
            } else if (value instanceof List) {
                // Process arrays/lists
                result.put(key, encryptListRecursively((List<Object>) value, fullKey));
            } else if (value instanceof String) {
                String stringValue = (String) value;
                
                // Encrypt all string values
                if (shouldEncrypt(stringValue)) {
                    result.put(key, encryptionService.encrypt(stringValue));
                } else {
                    result.put(key, value);
                }
            } else if (value instanceof Boolean) {
                // Convert boolean to string and encrypt it
                String booleanAsString = value.toString();
                result.put(key, encryptionService.encrypt(booleanAsString));
            } else if (value instanceof Number) {
                // Convert numbers to string and encrypt them
                String numberAsString = value.toString();
                result.put(key, encryptionService.encrypt(numberAsString));
            } else {
                // Keep other values as-is
                result.put(key, value);
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object> encryptListRecursively(List<Object> list, String parentKey) {
        List<Object> result = new ArrayList<>();
        
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            String itemKey = parentKey + "[" + i + "]";
            
            if (item instanceof Map) {
                // Recursively process nested maps in arrays
                result.add(encryptConfigRecursively((Map<String, Object>) item, itemKey));
            } else if (item instanceof List) {
                // Recursively process nested arrays
                result.add(encryptListRecursively((List<Object>) item, itemKey));
            } else if (item instanceof String) {
                String stringValue = (String) item;
                
                // Encrypt all string values in arrays
                if (shouldEncrypt(stringValue)) {
                    result.add(encryptionService.encrypt(stringValue));
                } else {
                    result.add(item);
                }
            } else if (item instanceof Boolean) {
                // Convert boolean to string and encrypt it
                String booleanAsString = item.toString();
                result.add(encryptionService.encrypt(booleanAsString));
            } else if (item instanceof Number) {
                // Convert numbers to string and encrypt them
                String numberAsString = item.toString();
                result.add(encryptionService.encrypt(numberAsString));
            } else {
                // Keep other values as-is
                result.add(item);
            }
        }
        
        return result;
    }

    private boolean shouldEncrypt(String value) {
        // Don't encrypt already encrypted values
        if (encryptionService.isEncrypted(value)) {
            return false;
        }
        
        // Don't encrypt null or empty values
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        
        // ENCRYPT ALL NON-EMPTY STRING VALUES
        return true;
    }

    @SuppressWarnings("unchecked")
    private ConfigStats generateStats(Map<String, Object> original, Map<String, Object> encrypted) {
        ConfigStats stats = new ConfigStats();
        countValues(original, stats, false);
        countValues(encrypted, stats, true);
        return stats;
    }

    @SuppressWarnings("unchecked")
    private void countValues(Map<String, Object> config, ConfigStats stats, boolean countingEncrypted) {
        for (Object value : config.values()) {
            if (value instanceof Map) {
                countValues((Map<String, Object>) value, stats, countingEncrypted);
            } else if (value instanceof String) {
                if (!countingEncrypted) {
                    stats.totalValues++;
                } else if (encryptionService.isEncrypted((String) value)) {
                    stats.encryptedCount++;
                }
            }
        }
    }

    private void writeEncryptedYaml(Map<String, Object> config, ConfigStats stats, File targetFile) throws IOException {
        try (FileWriter writer = new FileWriter(targetFile)) {
            // Write header comment
            writer.write("# Encrypted Configuration File\n");
            writer.write("# Generated by Config Encryptor\n");
            writer.write("# Total values: " + stats.totalValues + "\n");
            writer.write("# Encrypted values: " + stats.encryptedCount + "\n");
            writer.write("# Plain values: " + (stats.totalValues - stats.encryptedCount) + "\n");
            writer.write("# Encryption percentage: " + String.format("%.1f", (stats.encryptedCount * 100.0 / stats.totalValues)) + "%\n");
            writer.write("# WARNING: This file contains encrypted values. Do not edit manually.\n\n");
            
            // Write YAML content
            String yamlContent = yamlMapper.writeValueAsString(config);
            writer.write(yamlContent);
        }
    }

    private void saveKeyMapping(File mappingFile) throws IOException {
        try (FileWriter writer = new FileWriter(mappingFile)) {
            writer.write("# Key Mapping File\n");
            writer.write("# Original Key -> Shuffled Key\n\n");
            
            // Sort the keys for consistent output
            Map<String, String> sortedMapping = new TreeMap<>(keyMapping);
            
            for (Map.Entry<String, String> entry : sortedMapping.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
            }
        }
    }

    private static class ConfigStats {
        int totalValues = 0;
        int encryptedCount = 0;
    }
    
    // Getters for key mapping (for testing or external use)
    public Map<String, String> getKeyMapping() {
        return new HashMap<>(keyMapping);
    }
}